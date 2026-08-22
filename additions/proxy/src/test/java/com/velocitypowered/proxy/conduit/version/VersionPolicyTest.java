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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.network.ProtocolVersion;
import org.junit.jupiter.api.Test;

class VersionPolicyTest {

  private static final String PING_NAME = "Conduit {versions}";
  private static final String KICK =
      "<red>This network only allows players to join on version <white>{versions}</white>.";
  private static final String KICK_RANGE =
      "<red>This network only allows players to join on versions <white>{versions}</white>.";

  private static VersionPolicy policy(ProtocolVersion min, ProtocolVersion max) {
    return new VersionPolicy(true, min, max, PING_NAME, KICK, KICK_RANGE);
  }

  @Test
  void disabledPolicyAllowsEverything() {
    assertFalse(VersionPolicy.DISABLED.isEnabled());
    assertTrue(VersionPolicy.DISABLED.allows(ProtocolVersion.MINECRAFT_1_8));
    assertTrue(VersionPolicy.DISABLED.allows(ProtocolVersion.MAXIMUM_VERSION));
  }

  @Test
  void policyWithNoBoundsIsInert() {
    assertFalse(policy(null, null).isEnabled());
    assertTrue(policy(null, null).allows(ProtocolVersion.MINECRAFT_1_8));
  }

  @Test
  void pinnedVersionAllowsOnlyThatProtocol() {
    VersionPolicy pinned =
        policy(ProtocolVersion.MINECRAFT_1_21_11, ProtocolVersion.MINECRAFT_1_21_11);

    assertTrue(pinned.isEnabled());
    assertTrue(pinned.isSingleVersion());
    assertTrue(pinned.allows(ProtocolVersion.MINECRAFT_1_21_11));
    assertFalse(pinned.allows(ProtocolVersion.MINECRAFT_1_21_7));
    assertFalse(pinned.allows(ProtocolVersion.MINECRAFT_1_8));
    assertEquals("1.21.11", pinned.getVersionsLabel());
  }

  @Test
  void rangeAllowsBothEndsAndEverythingBetween() {
    VersionPolicy range =
        policy(ProtocolVersion.MINECRAFT_1_21_4, ProtocolVersion.MINECRAFT_1_21_11);

    assertFalse(range.isSingleVersion());
    assertTrue(range.allows(ProtocolVersion.MINECRAFT_1_21_4));
    assertTrue(range.allows(ProtocolVersion.MINECRAFT_1_21_6));
    assertTrue(range.allows(ProtocolVersion.MINECRAFT_1_21_11));
    assertFalse(range.allows(ProtocolVersion.MINECRAFT_1_21_2));
    assertEquals("1.21.4–1.21.11", range.getVersionsLabel());
  }

  @Test
  void openEndedBoundsOnlyConstrainOneSide() {
    VersionPolicy floor = policy(ProtocolVersion.MINECRAFT_1_21_4, null);
    assertFalse(floor.allows(ProtocolVersion.MINECRAFT_1_21_2));
    assertTrue(floor.allows(ProtocolVersion.MAXIMUM_VERSION));
    assertEquals("1.21.4 or newer", floor.getVersionsLabel());

    VersionPolicy ceiling = policy(null, ProtocolVersion.MINECRAFT_1_21_4);
    assertTrue(ceiling.allows(ProtocolVersion.MINECRAFT_1_8));
    assertFalse(ceiling.allows(ProtocolVersion.MINECRAFT_1_21_5));
    assertEquals("1.21.4 or older", ceiling.getVersionsLabel());
  }

  @Test
  void unsupportedProtocolsAreLeftToVelocity() {
    VersionPolicy pinned =
        policy(ProtocolVersion.MINECRAFT_1_21_11, ProtocolVersion.MINECRAFT_1_21_11);

    assertTrue(pinned.allows(ProtocolVersion.UNKNOWN));
    assertTrue(pinned.allows(ProtocolVersion.LEGACY));
    assertTrue(pinned.allows(null));
  }

  @Test
  void sharedProtocolIsLabelledAsItsOwnRange() {
    // 1.21.9 and 1.21.10 share protocol 773 — both are genuinely accepted, so say so.
    VersionPolicy shared =
        policy(ProtocolVersion.MINECRAFT_1_21_9, ProtocolVersion.MINECRAFT_1_21_9);

    assertEquals("1.21.9–1.21.10", shared.getVersionsLabel());
  }

  @Test
  void pingNameAndKickMessagesRenderPlaceholders() {
    VersionPolicy pinned =
        policy(ProtocolVersion.MINECRAFT_1_21_11, ProtocolVersion.MINECRAFT_1_21_11);
    assertEquals("Conduit 1.21.11", pinned.pingVersionName());
    assertEquals("<red>This network only allows players to join on version"
        + " <white>1.21.11</white>.", pinned.renderKickMessage());

    VersionPolicy range =
        policy(ProtocolVersion.MINECRAFT_1_21_4, ProtocolVersion.MINECRAFT_1_21_11);
    assertEquals("<red>This network only allows players to join on versions"
        + " <white>1.21.4–1.21.11</white>.", range.renderKickMessage());
  }

  @Test
  void minMaxPlaceholdersAreSubstituted() {
    VersionPolicy range = new VersionPolicy(true, ProtocolVersion.MINECRAFT_1_21_4,
        ProtocolVersion.MINECRAFT_1_21_11, "{min} to {max}", "", "");

    assertEquals("1.21.4 to 1.21.11", range.pingVersionName());
  }

  @Test
  void blankKickTemplatesFallBackToTheShippedWording() {
    VersionPolicy pinned = new VersionPolicy(true, ProtocolVersion.MINECRAFT_1_21_11,
        ProtocolVersion.MINECRAFT_1_21_11, "", "", "");

    assertEquals("<red>This network only allows players to join on version"
        + " <white>1.21.11</white>.", pinned.renderKickMessage());
  }

  @Test
  void advertisedProtocolIsOneTheRejectedClientCannotBeSpeaking() {
    VersionPolicy range =
        policy(ProtocolVersion.MINECRAFT_1_21_4, ProtocolVersion.MINECRAFT_1_21_11);

    assertEquals(ProtocolVersion.MINECRAFT_1_21_11.getProtocol(), range.advertisedProtocol());
    assertEquals(ProtocolVersion.MINECRAFT_1_21_4.getProtocol(),
        policy(ProtocolVersion.MINECRAFT_1_21_4, null).advertisedProtocol());
  }

  @Test
  void parsesVersionNamesAndProtocolNumbers() {
    assertEquals(ProtocolVersion.MINECRAFT_1_21_11,
        VersionPolicy.parseVersion("versions.minimum", "1.21.11"));
    assertEquals(ProtocolVersion.MINECRAFT_1_21_11,
        VersionPolicy.parseVersion("versions.minimum", " 774 "));
    // A name covered by a multi-version protocol resolves to that protocol.
    assertEquals(ProtocolVersion.MINECRAFT_1_21_9,
        VersionPolicy.parseVersion("versions.minimum", "1.21.10"));
  }

  @Test
  void blankBoundMeansNoBound() {
    assertNull(VersionPolicy.parseVersion("versions.minimum", ""));
    assertNull(VersionPolicy.parseVersion("versions.minimum", "  "));
    assertNull(VersionPolicy.parseVersion("versions.minimum", null));
  }

  @Test
  void rejectsUnknownVersions() {
    assertThrows(IllegalArgumentException.class,
        () -> VersionPolicy.parseVersion("versions.minimum", "1.99"));
    assertThrows(IllegalArgumentException.class,
        () -> VersionPolicy.parseVersion("versions.minimum", "99999"));
  }
}
