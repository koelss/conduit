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
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

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
 * <p>The accepted set is described in one of two ways. An explicit {@code allow} list names
 * individual versions and is the only way to accept a non-contiguous set (1.8 and 1.21.11 but
 * nothing between them, say). Otherwise a {@code minimum}/{@code maximum} range is used, where both
 * bounds are optional: a policy may pin an exact version ({@code minimum == maximum}), set only a
 * floor, only a ceiling, or neither (in which case it accepts everything the proxy itself
 * supports). A non-empty allow list takes precedence over the range. {@link #DISABLED} is the no-op
 * policy used when the feature is switched off.
 */
public final class VersionPolicy {

  /** En dash used between the two ends of a version range in operator-facing text. */
  private static final String RANGE_SEPARATOR = "–";

  /** Separator between the entries of an explicit allow list in operator-facing text. */
  private static final String LIST_SEPARATOR = ", ";

  /** Policy that restricts nothing — every version the proxy supports may join. */
  public static final VersionPolicy DISABLED =
      new VersionPolicy(false, List.of(), null, null, "", "", "");

  private final boolean enabled;
  private final List<ProtocolVersion> allowed;
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
   * @param allowed                  explicitly accepted versions; when non-empty this is the whole
   *                                 accepted set and the bounds below are ignored. Order and
   *                                 duplicates do not matter — the list is normalised to ascending
   *                                 protocol order
   * @param minimum                  lowest accepted version, or {@code null} for no floor
   * @param maximum                  highest accepted version, or {@code null} for no ceiling
   * @param pingVersionNameTemplate  server-list version label shown to rejected clients
   * @param kickMessageTemplate      MiniMessage kick text used when a single version is allowed
   * @param kickMessageRangeTemplate MiniMessage kick text used when several versions are allowed
   */
  public VersionPolicy(boolean enabled, Collection<ProtocolVersion> allowed,
      ProtocolVersion minimum, ProtocolVersion maximum,
      String pingVersionNameTemplate, String kickMessageTemplate,
      String kickMessageRangeTemplate) {
    this.enabled = enabled;
    this.allowed = normalise(allowed);
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
    return enabled && (!allowed.isEmpty() || minimum != null || maximum != null);
  }

  /**
   * Returns the explicitly allowed versions in ascending protocol order, or an empty list when the
   * policy uses a {@code minimum}/{@code maximum} range instead.
   */
  public List<ProtocolVersion> getAllowed() {
    return allowed;
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
    if (!allowed.isEmpty()) {
      return allowed.contains(version);
    }
    if (minimum != null && version.lessThan(minimum)) {
      return false;
    }
    return maximum == null || !version.greaterThan(maximum);
  }

  /** Returns {@code true} when exactly one protocol version is accepted. */
  public boolean isSingleVersion() {
    if (!allowed.isEmpty()) {
      return allowed.size() == 1;
    }
    return minimum != null && minimum == maximum;
  }

  /**
   * Returns the operator-facing description of the accepted versions — {@code "1.21.11"} when a
   * single version is pinned, {@code "1.21.4–1.21.11"} for a range, open-ended forms such as
   * {@code "1.21.4 or newer"} when only one bound is set, and a comma-separated enumeration such as
   * {@code "1.8, 1.21.4, 1.21.11"} for an explicit allow list.
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
    if (!allowed.isEmpty()) {
      return allowed.get(allowed.size() - 1).getProtocol();
    }
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
    ProtocolVersion low = allowed.isEmpty() ? minimum : allowed.get(0);
    ProtocolVersion high = allowed.isEmpty() ? maximum : allowed.get(allowed.size() - 1);
    return template
        .replace("{versions}", versionsLabel)
        .replace("{min}", low == null ? "" : low.getVersionIntroducedIn())
        .replace("{max}", high == null ? "" : high.getMostRecentSupportedVersion());
  }

  private String buildVersionsLabel() {
    if (!allowed.isEmpty()) {
      return allowed.stream()
          .map(VersionPolicy::singleLabel)
          .collect(Collectors.joining(LIST_SEPARATOR));
    }
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

  /**
   * Sorts an allow list into ascending protocol order and drops duplicates and nulls, so the
   * operator can list versions in any order (and name two releases that share one protocol) without
   * changing what the policy accepts or how it describes itself.
   */
  private static List<ProtocolVersion> normalise(Collection<ProtocolVersion> versions) {
    if (versions == null || versions.isEmpty()) {
      return List.of();
    }
    EnumSet<ProtocolVersion> distinct = EnumSet.noneOf(ProtocolVersion.class);
    for (ProtocolVersion version : versions) {
      if (version != null) {
        distinct.add(version);
      }
    }
    // EnumSet iterates in declaration order, which is ascending protocol order.
    return List.copyOf(new ArrayList<>(distinct));
  }

  @Override
  public String toString() {
    return "VersionPolicy{enabled=" + isEnabled() + ", versions="
        + versionsLabel.toLowerCase(Locale.ROOT) + "}";
  }
}
