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

package com.velocitypowered.proxy.conduit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConduitConfigTest {

  @TempDir
  Path tempDir;

  @Test
  void loadsConfiguredKnownPackLimit() throws Exception {
    Files.writeString(tempDir.resolve("conduit.toml"),
        """
        [modded]
        max-known-packs = 2048
        """);

    ConduitConfig config = ConduitConfig.load(tempDir);

    assertEquals(2048, config.getMaxKnownPacks());
  }

  @Test
  void loadsPinnedVersionRange() throws Exception {
    Files.writeString(tempDir.resolve("conduit.toml"),
        """
        [versions]
        enabled = true
        minimum = "1.21.11"
        maximum = "1.21.11"
        """);

    ConduitConfig config = ConduitConfig.load(tempDir);

    assertTrue(config.getVersionPolicy().isEnabled());
    assertTrue(config.getVersionPolicy().isSingleVersion());
    assertEquals("1.21.11", config.getVersionPolicy().getVersionsLabel());
    assertEquals("Conduit 1.21.11", config.getVersionPolicy().pingVersionName());
  }

  @Test
  void versionRangeIsUnrestrictedByDefault() throws Exception {
    Files.writeString(tempDir.resolve("conduit.toml"),
        """
        [modded]
        max-known-packs = 2048
        """);

    assertFalse(ConduitConfig.load(tempDir).getVersionPolicy().isEnabled());
  }

  @Test
  void loadsExplicitVersionAllowList() throws Exception {
    Files.writeString(tempDir.resolve("conduit.toml"),
        """
        [versions]
        enabled = true
        allow = ["1.21.11", "1.8"]
        """);

    ConduitConfig config = ConduitConfig.load(tempDir);

    assertTrue(config.getVersionPolicy().isEnabled());
    assertEquals(2, config.getVersionPolicy().getAllowed().size());
    assertEquals("1.8–1.8.9, 1.21.11", config.getVersionPolicy().getVersionsLabel());
  }

  @Test
  void allowListTakesPrecedenceOverAnInvertedRange() throws Exception {
    // The range is ignored outright when a list is given, so it is not validated either.
    Files.writeString(tempDir.resolve("conduit.toml"),
        """
        [versions]
        enabled = true
        allow = ["1.21.11"]
        minimum = "1.21.11"
        maximum = "1.21.4"
        """);

    assertEquals("1.21.11", ConduitConfig.load(tempDir).getVersionPolicy().getVersionsLabel());
  }

  @Test
  void rejectsUnknownConfiguredVersion() throws Exception {
    Files.writeString(tempDir.resolve("conduit.toml"),
        """
        [versions]
        enabled = true
        minimum = "1.99"
        """);

    assertThrows(IllegalArgumentException.class, () -> ConduitConfig.load(tempDir));
  }

  @Test
  void rejectsInvertedVersionRange() throws Exception {
    Files.writeString(tempDir.resolve("conduit.toml"),
        """
        [versions]
        enabled = true
        minimum = "1.21.11"
        maximum = "1.21.4"
        """);

    assertThrows(IllegalArgumentException.class, () -> ConduitConfig.load(tempDir));
  }

  @Test
  void rejectsInvalidChannelGuardAction() throws Exception {
    Files.writeString(tempDir.resolve("conduit.toml"),
        """
        [security]
        channel-guard-action = "ban"
        """);

    assertThrows(IllegalArgumentException.class, () -> ConduitConfig.load(tempDir));
  }

  @Test
  void loadsSparkBundleDisabled() throws Exception {
    Files.writeString(tempDir.resolve("conduit.toml"),
        """
        [spark]
        bundle-enabled = false
        """);

    ConduitConfig config = ConduitConfig.load(tempDir);

    assertFalse(config.isSparkBundleEnabled());
  }

  @Test
  void luckPermsBundleEnabledByDefault() throws Exception {
    ConduitConfig config = ConduitConfig.load(tempDir);

    assertTrue(config.isLuckPermsBundleEnabled());
  }

  @Test
  void loadsLuckPermsBundleDisabled() throws Exception {
    Files.writeString(tempDir.resolve("conduit.toml"),
        """
        [luckperms]
        bundle-enabled = false
        """);

    ConduitConfig config = ConduitConfig.load(tempDir);

    assertFalse(config.isLuckPermsBundleEnabled());
  }

  @Test
  void seamlessServerSwitchesDisabledByDefault() throws Exception {
    ConduitConfig config = ConduitConfig.load(tempDir);

    assertFalse(config.isSeamlessServerSwitches());
  }

  @Test
  void loadsSeamlessServerSwitchesEnabled() throws Exception {
    Files.writeString(tempDir.resolve("conduit.toml"),
        """
        [advanced]
        seamless-server-switches = true
        """);

    ConduitConfig config = ConduitConfig.load(tempDir);

    assertTrue(config.isSeamlessServerSwitches());
  }

  @Test
  void seamlessSwitchDefaults() throws Exception {
    ConduitConfig config = ConduitConfig.load(tempDir);

    assertEquals(250, config.getSeamlessSwitchSettleMs());
    assertTrue(config.isSeamlessSwitchSoundEnabled());
    assertEquals("minecraft:entity.enderman.teleport", config.getSeamlessSwitchSound());
    assertEquals(1.0f, config.getSeamlessSwitchSoundVolume());
    assertEquals(1.0f, config.getSeamlessSwitchSoundPitch());
  }

  @Test
  void loadsSeamlessSwitchOverrides() throws Exception {
    Files.writeString(tempDir.resolve("conduit.toml"),
        """
        [advanced]
        seamless-switch-settle-ms = 500
        seamless-switch-sound-enabled = false
        seamless-switch-sound = "minecraft:block.beacon.activate"
        seamless-switch-sound-volume = 0.5
        seamless-switch-sound-pitch = 1.5
        """);

    ConduitConfig config = ConduitConfig.load(tempDir);

    assertEquals(500, config.getSeamlessSwitchSettleMs());
    assertFalse(config.isSeamlessSwitchSoundEnabled());
    assertEquals("minecraft:block.beacon.activate", config.getSeamlessSwitchSound());
    assertEquals(0.5f, config.getSeamlessSwitchSoundVolume());
    assertEquals(1.5f, config.getSeamlessSwitchSoundPitch());
  }
}
