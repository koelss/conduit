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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConduitConfigMigrationTest {

  @TempDir
  Path tempDir;

  @Test
  void addsMissingSectionsWhileKeepingUserValues() throws Exception {
    Path file = tempDir.resolve("conduit.toml");
    // An old file from before command forwarding existed, with one non-default value set.
    Files.writeString(file,
        """
        [modded]
        max-known-packs = 4096

        [commands]
        admin-enabled = false
        """);

    ConduitConfigMigrator.migrate(file);

    String migrated = Files.readString(file);
    // The user's explicit values are untouched.
    assertTrue(migrated.contains("max-known-packs = 4096"),
        "existing user value must be preserved");
    assertTrue(migrated.contains("admin-enabled = false"),
        "existing user value must be preserved");
    // The new forwarding section was added with its default.
    assertTrue(migrated.contains("[forwarding]"), "missing section should be added");
    assertTrue(migrated.contains("command-forwarding"), "missing key should be added");

    // And it round-trips through the real loader with the expected values.
    ConduitConfig config = ConduitConfig.load(tempDir);
    assertEquals(4096, config.getMaxKnownPacks());
    assertFalse(config.isAdminCommandsEnabled());
    assertFalse(config.isCommandForwardingEnabled());
    assertEquals("velocity_command_forward:main", config.getCommandForwardingChannel());
    assertFalse(config.isSeamlessServerSwitches(), "new advanced option defaults to false");
  }

  @Test
  void doesNotOverwriteAnExplicitlyEnabledForwarding() throws Exception {
    Path file = tempDir.resolve("conduit.toml");
    Files.writeString(file,
        """
        [forwarding]
        command-forwarding = true
        channel = "custom:chan"
        """);

    ConduitConfigMigrator.migrate(file);

    ConduitConfig config = ConduitConfig.load(tempDir);
    assertTrue(config.isCommandForwardingEnabled(), "user opt-in must be preserved");
    assertEquals("custom:chan", config.getCommandForwardingChannel());
  }

  @Test
  void leavesCompleteFileUnchanged() throws Exception {
    Path file = tempDir.resolve("conduit.toml");
    // Extract the shipped defaults verbatim, then confirm migration is a no-op on them.
    try (var in = ConduitConfig.class.getResourceAsStream(
        "/com/velocitypowered/proxy/conduit/conduit.toml")) {
      Files.copy(in, file);
    }
    byte[] before = Files.readAllBytes(file);

    ConduitConfigMigrator.migrate(file);

    assertEquals(new String(before), Files.readString(file),
        "a complete file must not be rewritten");
  }

  @Test
  void preservesExistingValuesAndLayoutVerbatim() throws Exception {
    Path file = tempDir.resolve("conduit.toml");
    String original = """
        # my hand-written header
        [luckperms]
        bundle-enabled = false

        [spark]
        bundle-enabled = false
        """;
    Files.writeString(file, original);

    ConduitConfigMigrator.migrate(file);
    String out = Files.readString(file);

    // Append-only: every original line survives verbatim and the header stays first.
    for (String line : original.split("\n")) {
      assertTrue(out.contains(line), "original line must be preserved verbatim: <" + line + ">");
    }
    assertTrue(out.startsWith("# my hand-written header"), "user layout must be preserved");
    // Missing sections are appended with their defaults.
    assertTrue(out.contains("[update]"), "missing section should be appended");

    // The operator's disabled values are never reset.
    ConduitConfig cfg = ConduitConfig.load(tempDir);
    assertFalse(cfg.isLuckPermsBundleEnabled(), "luckperms bundle-enabled must stay false");
    assertFalse(cfg.isSparkBundleEnabled(), "spark bundle-enabled must stay false");
  }

  @Test
  void refreshesSeamlessCommentBlocksButKeepsValues() throws Exception {
    Path file = tempDir.resolve("conduit.toml");
    // An existing file carrying the old, verbose 1.7.1 seamless comments and a custom value.
    Files.writeString(file,
        """
        [advanced]
        # Enables experimental seamless server switches for 1.20.2+ clients.
        # When the proxy switches a player between backend servers that run compatible
        # registries/datapacks (homogeneous backends using the same protocol/game
        # environment) and the destination reports the same dimension, the client skips
        # the configuration screen and keeps its world loaded. Cross-dimension switches
        # and mixed-version/modded environments fall back to the normal switch.
        # Resource packs and cookies sent during the absorbed config phase are dropped.
        # Leave this off unless every backend is a compatible match.
        seamless-server-switches = true

        # Settle delay (in milliseconds) applied to a seamless server switch.
        # The seamless switch is otherwise instantaneous, which can feel jarring and, more
        # importantly, causes a desync: if the player moves before the destination backend
        # has finished streaming in the world and their position, they can end up "stuck"
        # in place until they reconnect. Set to 0 to restore. Range: 0–5000. Default: 250.
        seamless-switch-settle-ms = 500
        """);

    ConduitConfigMigrator.migrate(file);

    String migrated = Files.readString(file);
    // Values are preserved exactly.
    assertTrue(migrated.contains("seamless-server-switches = true"),
        "operator value must be preserved");
    assertTrue(migrated.contains("seamless-switch-settle-ms = 500"),
        "operator value must be preserved");
    // The verbose comment wording is gone, replaced by the condensed shipped wording.
    assertFalse(migrated.contains("Enables experimental seamless server switches for 1.20.2+"),
        "old verbose comment should be replaced");
    assertTrue(migrated.contains("# Experimental seamless server switches for 1.20.2+ clients"),
        "condensed shipped comment should be present");

    // Running again is a no-op for the comments (idempotent) and still loads.
    long before = Files.getLastModifiedTime(file).toMillis();
    ConduitConfigMigrator.migrate(file);
    ConduitConfig cfg = ConduitConfig.load(tempDir);
    assertTrue(cfg.isSeamlessServerSwitches());
    assertEquals(500, cfg.getSeamlessSwitchSettleMs());
    assertEquals(before, Files.getLastModifiedTime(file).toMillis(),
        "second migrate must not rewrite an already-condensed file");
  }

  @Test
  void handlesCrlfFilesWithoutCorruption() throws Exception {
    Path file = tempDir.resolve("conduit.toml");
    // A Windows-edited file with CRLF endings and one non-default value.
    Files.writeString(file, "[modded]\r\nmax-known-packs = 2048\r\n");

    ConduitConfigMigrator.migrate(file);

    ConduitConfig cfg = ConduitConfig.load(tempDir);
    assertEquals(2048, cfg.getMaxKnownPacks(), "CRLF value must be preserved");
    assertTrue(cfg.isLuckPermsBundleEnabled(), "appended defaults must be readable (CRLF file)");
  }
}
