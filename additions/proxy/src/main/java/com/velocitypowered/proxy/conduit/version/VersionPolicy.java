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

package com.velocitypowered.proxy.conduit.version;

import com.velocitypowered.api.network.ProtocolVersion;
import java.util.Locale;

/**
 * The immutable client-version range an operator advertises and accepts, parsed from the
 * {@code [versions]} section of {@code conduit.toml}.
 *
 * <p>A policy answers three questions:
 *
 * <ul>
 *   <li>{@link #allows(ProtocolVersion)} — may this client join?</li>
 *   <li>{@link #pingVersionName()} — what goes in the server-list {@code version.name} slot shown
 *       to clients the policy rejects (the vanilla client renders that slot, plus a red cross in
 *       place of the ping bars, whenever the advertised protocol differs from its own).</li>
 *   <li>{@link #renderKickMessage()} — what a rejected client is told when it tries to join.</li>
 * </ul>
 *
 * <p>Both bounds are optional: a policy may pin an exact version ({@code minimum == maximum}), set
 * only a floor, only a ceiling, or neither (in which case it accepts everything the proxy itself
 * supports). {@link #DISABLED} is the no-op policy used when the feature is switched off.
 */
public final class VersionPolicy {

  /** En dash used between the two ends of a version range in operator-facing text. */
  private static final String RANGE_SEPARATOR = "–";

  /** Policy that restricts nothing — every version the proxy supports may join. */
  public static final VersionPolicy DISABLED =
      new VersionPolicy(false, null, null, "", "", "");

  private final boolean enabled;
  private final ProtocolVersion minimum;
  private final ProtocolVersion maximum;
  private final String pingVersionNameTemplate;
  private final String kickMessageTemplate;
  private final String kickMessageRangeTemplate;
  private final String versionsLabel;

  /**
   * Constructs a policy.
   *
   * @param enabled                  whether the restriction is active at all
   * @param minimum                  lowest accepted version, or {@code null} for no floor
   * @param maximum                  highest accepted version, or {@code null} for no ceiling
   * @param pingVersionNameTemplate  server-list version label shown to rejected clients
   * @param kickMessageTemplate      MiniMessage kick text used when a single version is allowed
   * @param kickMessageRangeTemplate MiniMessage kick text used when a range is allowed
   */
  public VersionPolicy(boolean enabled, ProtocolVersion minimum, ProtocolVersion maximum,
      String pingVersionNameTemplate, String kickMessageTemplate,
      String kickMessageRangeTemplate) {
    this.enabled = enabled;
    this.minimum = minimum;
    this.maximum = maximum;
    this.pingVersionNameTemplate = pingVersionNameTemplate == null ? "" : pingVersionNameTemplate;
    this.kickMessageTemplate = kickMessageTemplate == null ? "" : kickMessageTemplate;
    this.kickMessageRangeTemplate =
        kickMessageRangeTemplate == null ? "" : kickMessageRangeTemplate;
    this.versionsLabel = buildVersionsLabel();
  }

  /**
   * Resolves a configured version string to a {@link ProtocolVersion}.
   *
   * <p>Accepts either a user-facing Minecraft version name ({@code "1.21.11"}) or a raw protocol
   * number ({@code "774"}), so operators can pin versions the running proxy build does not name
   * yet. A blank string means "no bound" and yields {@code null}.
   *
   * @param key   the {@code conduit.toml} key being parsed, used in the error message
   * @param value the configured value
   * @return the resolved version, or {@code null} when {@code value} is blank
   * @throws IllegalArgumentException if the value names no version the proxy supports
   */
  public static ProtocolVersion parseVersion(String key, String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    for (ProtocolVersion version : ProtocolVersion.SUPPORTED_VERSIONS) {
      if (version.getVersionsSupportedBy().contains(trimmed)) {
        return version;
      }
    }
    try {
      ProtocolVersion byProtocol =
          ProtocolVersion.getProtocolVersion(Integer.parseInt(trimmed));
      if (byProtocol.isSupported()) {
        return byProtocol;
      }
    } catch (NumberFormatException ignored) {
      // fall through to the shared error below
    }
    throw new IllegalArgumentException("conduit.toml: " + key + " — '" + value
        + "' is not a Minecraft version this proxy supports. Use a version name such as '"
        + ProtocolVersion.MAXIMUM_VERSION.getMostRecentSupportedVersion()
        + "' or a protocol number such as '" + ProtocolVersion.MAXIMUM_VERSION.getProtocol()
        + "'. Supported: " + ProtocolVersion.SUPPORTED_VERSION_STRING);
  }

  /** Returns whether this policy restricts anything at all. */
  public boolean isEnabled() {
    return enabled && (minimum != null || maximum != null);
  }

  /** Returns the lowest accepted version, or {@code null} when there is no floor. */
  public ProtocolVersion getMinimum() {
    return minimum;
  }

  /** Returns the highest accepted version, or {@code null} when there is no ceiling. */
  public ProtocolVersion getMaximum() {
    return maximum;
  }

  /**
   * Returns whether a client on the given protocol may join.
   *
   * <p>Versions the proxy itself cannot speak ({@link ProtocolVersion#UNKNOWN},
   * {@link ProtocolVersion#LEGACY}) are left to Velocity's own handling and are never rejected
   * here, so the operator's range only ever narrows the supported set.
   */
  public boolean allows(ProtocolVersion version) {
    if (!isEnabled() || version == null || !version.isSupported()) {
      return true;
    }
    if (minimum != null && version.lessThan(minimum)) {
      return false;
    }
    return maximum == null || !version.greaterThan(maximum);
  }

  /** Returns {@code true} when exactly one protocol version is accepted. */
  public boolean isSingleVersion() {
    return minimum != null && minimum == maximum;
  }

  /**
   * Returns the operator-facing description of the accepted range — {@code "1.21.11"} when a single
   * version is pinned, {@code "1.21.4–1.21.11"} for a range, and open-ended forms such as
   * {@code "1.21.4 or newer"} when only one bound is set.
   */
  public String getVersionsLabel() {
    return versionsLabel;
  }

  /**
   * Returns the plain-text label placed in the server-list {@code version.name} field for clients
   * outside the range.
   */
  public String pingVersionName() {
    return applyPlaceholders(pingVersionNameTemplate);
  }

  /**
   * Returns the protocol number advertised to clients outside the range.
   *
   * <p>It is deliberately one the rejected client cannot be speaking (an accepted bound), which is
   * exactly what makes the vanilla client render {@link #pingVersionName()} and the red cross
   * instead of the ping bars.
   */
  public int advertisedProtocol() {
    ProtocolVersion advertised = maximum != null ? maximum : minimum;
    return advertised == null ? ProtocolVersion.MAXIMUM_VERSION.getProtocol()
        : advertised.getProtocol();
  }

  /** Returns the MiniMessage kick text for a client outside the range, placeholders applied. */
  public String renderKickMessage() {
    String template = isSingleVersion() ? kickMessageTemplate : kickMessageRangeTemplate;
    if (template.isBlank()) {
      template = isSingleVersion()
          ? "<red>This network only allows players to join on version <white>{versions}</white>."
          : "<red>This network only allows players to join on versions <white>{versions}</white>.";
    }
    return applyPlaceholders(template);
  }

  private String applyPlaceholders(String template) {
    return template
        .replace("{versions}", versionsLabel)
        .replace("{min}", minimum == null ? "" : minimum.getVersionIntroducedIn())
        .replace("{max}", maximum == null ? "" : maximum.getMostRecentSupportedVersion());
  }

  private String buildVersionsLabel() {
    if (minimum == null && maximum == null) {
      return ProtocolVersion.SUPPORTED_VERSION_STRING;
    }
    if (minimum == maximum) {
      return singleLabel(minimum);
    }
    if (minimum == null) {
      return maximum.getMostRecentSupportedVersion() + " or older";
    }
    if (maximum == null) {
      return minimum.getVersionIntroducedIn() + " or newer";
    }
    return minimum.getVersionIntroducedIn() + RANGE_SEPARATOR
        + maximum.getMostRecentSupportedVersion();
  }

  /**
   * Renders a single protocol version. A protocol shared by several Minecraft releases (1.21.9 and
   * 1.21.10, say) is shown as its own small range so players are not told to use a version that is
   * only half of what is actually accepted.
   */
  private static String singleLabel(ProtocolVersion version) {
    String first = version.getVersionIntroducedIn();
    String last = version.getMostRecentSupportedVersion();
    return first.equals(last) ? first : first + RANGE_SEPARATOR + last;
  }

  @Override
  public String toString() {
    return "VersionPolicy{enabled=" + isEnabled() + ", versions="
        + versionsLabel.toLowerCase(Locale.ROOT) + "}";
  }
}
