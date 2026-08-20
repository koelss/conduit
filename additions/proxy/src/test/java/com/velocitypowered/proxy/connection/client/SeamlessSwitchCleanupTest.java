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

package com.velocitypowered.proxy.connection.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.network.ProtocolVersion;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SeamlessSwitchCleanupTest {

  @Test
  void clearsRegistryEffectIdsFrom1205() {
    Set<Integer> effectIds =
        ClientPlaySessionHandler.effectIdsToClear(ProtocolVersion.MINECRAFT_1_20_5, Set.of());

    assertTrue(effectIds.contains(0));
    assertTrue(effectIds.contains(32));
    assertFalse(effectIds.contains(33));
  }

  @Test
  void clearsLegacyEffectIdsBefore1205() {
    Set<Integer> effectIds =
        ClientPlaySessionHandler.effectIdsToClear(ProtocolVersion.MINECRAFT_1_20_2, Set.of());

    assertFalse(effectIds.contains(0));
    assertTrue(effectIds.contains(1));
    assertTrue(effectIds.contains(33));
    assertFalse(effectIds.contains(34));
  }

  @Test
  void clearsTrackedEffectsOutsideTheVanillaRange() {
    Set<Integer> effectIds =
        ClientPlaySessionHandler.effectIdsToClear(ProtocolVersion.MINECRAFT_1_21_5, Set.of(41));

    assertTrue(effectIds.contains(41));
    assertTrue(effectIds.contains(0));
  }
}
