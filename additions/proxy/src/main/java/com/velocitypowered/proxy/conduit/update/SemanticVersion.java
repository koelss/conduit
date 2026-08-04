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

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal, self-contained <a href="https://semver.org/">Semantic Versioning</a> value used by
 * the Conduit update checker to compare the running build against published releases.
 *
 * <p>Parsing is deliberately lenient about a leading {@code v} (so both {@code 1.3.5} and
 * {@code v1.3.5} are accepted) and about a missing patch component ({@code 1.3} is treated as
 * {@code 1.3.0}). Build metadata after a {@code +} is ignored for ordering, exactly as the
 * specification requires. A pre-release suffix (after {@code -}) participates in ordering: a
 * version carrying one sorts <em>before</em> the same version without one.</p>
 */
public final class SemanticVersion implements Comparable<SemanticVersion> {

  private static final Pattern PATTERN = Pattern.compile(
      "^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$");

  private final int major;
  private final int minor;
  private final int patch;
  private final String prerelease;

  private SemanticVersion(int major, int minor, int patch, String prerelease) {
    this.major = major;
    this.minor = minor;
    this.patch = patch;
    this.prerelease = prerelease;
  }

  /**
   * Attempts to parse a version string. Returns {@link Optional#empty()} when the input is null or
   * does not resemble a semantic version (for example the {@code dev} placeholder used when no
   * build metadata is embedded).
   *
   * @param raw the raw version or release-tag string
   * @return the parsed version, or empty if it could not be parsed
   */
  public static Optional<SemanticVersion> parse(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    Matcher matcher = PATTERN.matcher(raw.trim());
    if (!matcher.matches()) {
      return Optional.empty();
    }
    int major = Integer.parseInt(matcher.group(1));
    int minor = Integer.parseInt(matcher.group(2));
    int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
    String pre = matcher.group(4);
    if (pre != null && pre.isEmpty()) {
      pre = null;
    }
    return Optional.of(new SemanticVersion(major, minor, patch, pre));
  }

  /** Returns whether this version carries a pre-release suffix (e.g. {@code -beta.1}). */
  public boolean isPrerelease() {
    return prerelease != null;
  }

  @Override
  public int compareTo(SemanticVersion other) {
    int cmp = Integer.compare(major, other.major);
    if (cmp != 0) {
      return cmp;
    }
    cmp = Integer.compare(minor, other.minor);
    if (cmp != 0) {
      return cmp;
    }
    cmp = Integer.compare(patch, other.patch);
    if (cmp != 0) {
      return cmp;
    }
    return comparePrerelease(prerelease, other.prerelease);
  }

  /**
   * Compares two pre-release suffixes per the Semantic Versioning precedence rules. A missing
   * suffix outranks a present one; otherwise identifiers are compared left to right, numerically
   * when both are numeric and lexically otherwise.
   */
  private static int comparePrerelease(String a, String b) {
    if (Objects.equals(a, b)) {
      return 0;
    }
    if (a == null) {
      return 1; // no pre-release is greater than any pre-release
    }
    if (b == null) {
      return -1;
    }

    String[] partsA = a.split("\\.");
    String[] partsB = b.split("\\.");
    int length = Math.min(partsA.length, partsB.length);
    for (int i = 0; i < length; i++) {
      String idA = partsA[i];
      String idB = partsB[i];
      boolean numA = idA.chars().allMatch(Character::isDigit);
      boolean numB = idB.chars().allMatch(Character::isDigit);
      int cmp;
      if (numA && numB) {
        cmp = Long.compare(Long.parseLong(idA), Long.parseLong(idB));
      } else if (numA) {
        cmp = -1; // numeric identifiers have lower precedence than alphanumeric
      } else if (numB) {
        cmp = 1;
      } else {
        cmp = idA.compareTo(idB);
      }
      if (cmp != 0) {
        return cmp;
      }
    }
    return Integer.compare(partsA.length, partsB.length);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SemanticVersion that)) {
      return false;
    }
    return major == that.major && minor == that.minor && patch == that.patch
        && Objects.equals(prerelease, that.prerelease);
  }

  @Override
  public int hashCode() {
    return Objects.hash(major, minor, patch, prerelease);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder().append(major).append('.').append(minor)
        .append('.').append(patch);
    if (prerelease != null) {
      sb.append('-').append(prerelease);
    }
    return sb.toString();
  }
}
