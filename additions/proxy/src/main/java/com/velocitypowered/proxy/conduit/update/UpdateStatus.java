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

/**
 * The immutable outcome of an update check.
 *
 * <p>Instances are produced by {@link UpdateChecker} and consumed both by the console startup
 * notice and by {@link UpdateNotifier} when an eligible player joins.</p>
 *
 * @param availability     whether the check produced a usable comparison
 * @param currentVersion   the running Conduit version string
 * @param latestVersion    the latest applicable release version, or {@code null} when unknown
 * @param outdated         whether a newer applicable release exists
 * @param releasesBehind   how many published releases are newer than the running build
 * @param latestUrl        a browser-facing URL for the latest release, or {@code null}
 * @param currentPrerelease whether the running build is itself a pre-release
 */
public record UpdateStatus(
    Availability availability,
    String currentVersion,
    String latestVersion,
    boolean outdated,
    int releasesBehind,
    String latestUrl,
    boolean currentPrerelease) {

  /** Whether the check succeeded and a meaningful comparison is available. */
  public enum Availability {
    /** A latest release was resolved and compared against the running build. */
    OK,
    /** The running version or the release set could not be resolved (no hard error). */
    UNKNOWN,
    /** The provider failed (network error, rate limit, malformed response). */
    ERROR
  }

  /** Builds an {@link Availability#OK} status. */
  public static UpdateStatus ok(String current, String latest, boolean outdated,
      int releasesBehind, String latestUrl, boolean currentPrerelease) {
    return new UpdateStatus(Availability.OK, current, latest, outdated, releasesBehind,
        latestUrl, currentPrerelease);
  }

  /** Builds an {@link Availability#UNKNOWN} status (nothing to compare against). */
  public static UpdateStatus unknown(String current, boolean currentPrerelease) {
    return new UpdateStatus(Availability.UNKNOWN, current, null, false, 0, null,
        currentPrerelease);
  }

  /** Builds an {@link Availability#ERROR} status (the provider failed). */
  public static UpdateStatus error(String current, boolean currentPrerelease) {
    return new UpdateStatus(Availability.ERROR, current, null, false, 0, null,
        currentPrerelease);
  }

  /** Whether this status represents a newer release the operator could upgrade to. */
  public boolean hasUpdate() {
    return availability == Availability.OK && outdated;
  }
}
