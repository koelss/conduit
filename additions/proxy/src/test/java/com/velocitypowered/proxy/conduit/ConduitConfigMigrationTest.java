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
}
