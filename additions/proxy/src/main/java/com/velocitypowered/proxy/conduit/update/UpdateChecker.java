/*
 * Copyright (C) 2026 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.proxy.conduit.update;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Coordinates update checking for Conduit.
 *
 * <p>Design goals, mirroring the rest of the Conduit subsystems:</p>
 * <ul>
 *   <li><b>Never blocks startup.</b> {@link #checkAsync()} runs the whole check on a single daemon
 *       thread; the proxy is already accepting players by the time it completes.</li>
 *   <li><b>Cached.</b> The most recent {@link UpdateStatus} is retained and reused for up to
 *       {@code cacheTtlMillis}. {@link #getStatus()} returns the cached value immediately and only
 *       schedules a background refresh when the cache is stale, so player joins and
 *       {@code /velocity info} never trigger a synchronous network call.</li>
 *   <li><b>Fail-soft.</b> Provider errors become an {@link UpdateStatus.Availability#ERROR}
 *       result, never an exception that could disrupt a join or a command.</li>
 *   <li><b>Provider-agnostic.</b> All GitHub specifics live in {@link GitHubReleaseProvider}; this
 *       class only knows the {@link UpdateProvider} contract, so another backend can be swapped in
 *       without touching the comparison or caching logic.</li>
 * </ul>
 */
public final class UpdateChecker {

  private static final Logger logger = LogManager.getLogger(UpdateChecker.class);

  private final UpdateProvider provider;
  private final String currentVersionString;
  private final Optional<SemanticVersion> currentVersion;
  private final boolean includePrereleases;
  private final long cacheTtlMillis;

  private final AtomicBoolean refreshing = new AtomicBoolean(false);
  private volatile UpdateStatus lastStatus;
  private volatile long lastCheckAt;

  /**
   * Creates an update checker.
   *
   * @param provider           the release source to query
   * @param currentVersion     the running Conduit version (e.g. {@code 1.3.5})
   * @param includePrereleases whether pre-release builds should be considered as upgrade targets;
   *                           this is forced on when the running build is itself a pre-release
   * @param cacheTtlMillis     how long a computed {@link UpdateStatus} is reused before a refresh
   */
  public UpdateChecker(UpdateProvider provider, String currentVersion, boolean includePrereleases,
      long cacheTtlMillis) {
    this.provider = provider;
    this.currentVersionString = currentVersion;
    this.currentVersion = SemanticVersion.parse(currentVersion);
    // A pre-release build should be told about newer pre-releases, so honour them regardless of
    // the configured preference in that case.
    this.includePrereleases = includePrereleases
        || this.currentVersion.map(SemanticVersion::isPrerelease).orElse(false);
    this.cacheTtlMillis = cacheTtlMillis;
  }

  /**
   * Kicks off the initial check on a daemon thread and logs a single line summarising the result.
   * Safe to call once during startup; it returns immediately.
   */
  public void checkAsync() {
    Thread thread = new Thread(() -> {
      UpdateStatus status = refresh();
      logStatus(status);
    }, "Conduit Update Check");
    thread.setDaemon(true);
    thread.start();
  }

  /**
   * Returns the most recent {@link UpdateStatus}, computing an initial one lazily and scheduling a
   * non-blocking background refresh when the cached value has aged past the TTL. Never blocks and
   * never returns {@code null}.
   *
   * @return the cached update status
   */
  public UpdateStatus getStatus() {
    UpdateStatus cached = lastStatus;
    long age = System.currentTimeMillis() - lastCheckAt;
    if (cached == null) {
      // No check has completed yet; return a soft "unknown" and let the async check populate it.
      triggerBackgroundRefresh();
      return UpdateStatus.unknown(currentVersionString,
          currentVersion.map(SemanticVersion::isPrerelease).orElse(false));
    }
    if (age > cacheTtlMillis) {
      triggerBackgroundRefresh();
    }
    return cached;
  }

  private void triggerBackgroundRefresh() {
    if (refreshing.compareAndSet(false, true)) {
      Thread thread = new Thread(() -> {
        try {
          refresh();
        } finally {
          refreshing.set(false);
        }
      }, "Conduit Update Check (refresh)");
      thread.setDaemon(true);
      thread.start();
    }
  }

  /**
   * Performs the actual check and updates the cache. Synchronous; only ever invoked from the
   * daemon threads above.
   */
  private UpdateStatus refresh() {
    UpdateStatus status = computeStatus();
    lastStatus = status;
    lastCheckAt = System.currentTimeMillis();
    return status;
  }

  private UpdateStatus computeStatus() {
    boolean currentPre = currentVersion.map(SemanticVersion::isPrerelease).orElse(false);
    if (currentVersion.isEmpty()) {
      // Development / unversioned build — nothing meaningful to compare.
      return UpdateStatus.unknown(currentVersionString, currentPre);
    }

    List<Release> releases;
    try {
      releases = provider.fetchReleases();
    } catch (Exception e) {
      logger.debug("[Conduit] Update check via {} failed: {}", provider.id(), e.getMessage());
      return UpdateStatus.error(currentVersionString, currentPre);
    }

    SemanticVersion current = currentVersion.get();
    Release latest = null;
    int behind = 0;
    for (Release release : releases) {
      if (release.prerelease() && !includePrereleases) {
        continue;
      }
      if (latest == null || release.version().compareTo(latest.version()) > 0) {
        latest = release;
      }
      if (release.version().compareTo(current) > 0) {
        behind++;
      }
    }

    if (latest == null) {
      return UpdateStatus.unknown(currentVersionString, currentPre);
    }

    boolean outdated = current.compareTo(latest.version()) < 0;
    return UpdateStatus.ok(currentVersionString, latest.tag(), outdated, behind,
        latest.url(), currentPre);
  }

  private void logStatus(UpdateStatus status) {
    switch (status.availability()) {
      case OK -> {
        if (status.outdated()) {
          logger.warn("[Conduit] A new release is available: {} (you are running {}). "
                  + "You are {} release(s) behind. Download: {}",
              status.latestVersion(), status.currentVersion(), status.releasesBehind(),
              status.latestUrl() != null ? status.latestUrl()
                  : "https://github.com/tame-gg/conduit/releases");
        } else {
          logger.info("[Conduit] You are running the latest version ({}).",
              status.currentVersion());
        }
      }
      case UNKNOWN -> logger.info(
          "[Conduit] Update check skipped: no comparable release found for version {}.",
          status.currentVersion());
      case ERROR -> logger.info(
          "[Conduit] Update check could not be completed; will retry later.");
      default -> {
        // exhaustive
      }
    }
  }
}
