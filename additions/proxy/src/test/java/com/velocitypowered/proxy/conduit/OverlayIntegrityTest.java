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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OverlayIntegrityTest {

  @Test
  void compressionOverlayContainsSmartCompressionHook() throws Exception {
    String source = Files.readString(Path.of("../overlays/proxy/src/main/java/com/velocitypowered/"
        + "proxy/protocol/netty/MinecraftCompressorAndLengthEncoder.java"));

    assertTrue(source.contains("SmartCompression"));
    assertTrue(source.contains("recordCompressionSkip"));
  }

  @Test
  void tabCompleteOverlayContainsCacheHook() throws Exception {
    String source = Files.readString(Path.of("../overlays/proxy/src/main/java/com/velocitypowered/"
        + "proxy/connection/client/ClientPlaySessionHandler.java"));

    assertTrue(source.contains("TabCompleteCache"));
    assertTrue(source.contains("storeTabCompleteResponse"));
    assertTrue(source.contains("canDoSeamlessPlaySwitch"));
    assertTrue(source.contains("doSeamlessPlaySwitch"));
    assertTrue(source.contains("ScoreboardObjectivePacket"));
    assertTrue(source.contains("trackedScoreboardObjectives"));
    assertTrue(source.contains("GameEventPacket.changeGamemode"));
    assertTrue(source.contains("trackedPlayerEffects"));
    assertTrue(source.contains("EntityEventPacket.clearOperator"));
    assertTrue(source.contains("isPlayStaySwitch"));
    assertTrue(source.contains("stripPreviousServerHud"));
    assertTrue(source.contains("EntityMetadataPacket.resetBreathAndSwim"));
    assertTrue(source.contains("getConnectionInFlightOrConnectedServer"));
    assertTrue(source.contains("ServerboundPlayerLoadedPacket"));
  }

  @Test
  void backendOverlayRewritesPlayerTargetedPackets() throws Exception {
    String source = Files.readString(Path.of("../overlays/proxy/src/main/java/com/velocitypowered/"
        + "proxy/connection/backend/BackendPlaySessionHandler.java"));

    assertTrue(source.contains("EVENT_START_WAITING_FOR_CHUNKS"));
    assertTrue(source.contains("ClientboundSoundEntityPacket"));
    assertTrue(source.contains("EntityIdPayloadPacket"));
  }

  @Test
  void loginOverlayUsesConduitSeamlessFlag() throws Exception {
    String source = Files.readString(Path.of("../overlays/proxy/src/main/java/com/velocitypowered/"
        + "proxy/connection/backend/LoginSessionHandler.java"));

    assertTrue(source.contains("isSeamlessServerSwitches"));
    assertTrue(source.contains("SeamlessConfigSessionHandler"));
  }

  @Test
  void transitionOverlayAppliesSeamlessJoinWithoutPluginStall() throws Exception {
    String source = Files.readString(Path.of("../overlays/proxy/src/main/java/com/velocitypowered/"
        + "proxy/connection/backend/TransitionSessionHandler.java"));

    assertTrue(source.contains("isPlayStaySwitch"));
    assertTrue(source.contains("TimeUnit.MILLISECONDS"));
    assertTrue(source.contains("packet.handle(handler)"));
  }
}
