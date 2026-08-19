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

package com.velocitypowered.proxy.protocol.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class GameEventPacketTest {

  @Test
  void roundTripsChangeGamemode() {
    GameEventPacket packet = GameEventPacket.changeGamemode(0);
    ByteBuf buf = Unpooled.buffer();
    packet.encode(buf, ProtocolUtils.Direction.CLIENTBOUND, ProtocolVersion.MINECRAFT_26_2);

    GameEventPacket decoded = new GameEventPacket();
    decoded.decode(buf, ProtocolUtils.Direction.CLIENTBOUND, ProtocolVersion.MINECRAFT_26_2);
    buf.release();

    assertEquals(GameEventPacket.EVENT_CHANGE_GAMEMODE, decoded.getEvent());
    assertEquals(0.0f, decoded.getValue());
  }

  @Test
  void removeEffectRoundTripsOn26() {
    RemoveEntityEffectPacket packet = new RemoveEntityEffectPacket(12, 15);
    ByteBuf buf = Unpooled.buffer();
    packet.encode(buf, ProtocolUtils.Direction.CLIENTBOUND, ProtocolVersion.MINECRAFT_26_2);

    RemoveEntityEffectPacket decoded = new RemoveEntityEffectPacket();
    decoded.decode(buf, ProtocolUtils.Direction.CLIENTBOUND, ProtocolVersion.MINECRAFT_26_2);
    buf.release();

    assertEquals(12, decoded.getEntityId());
    assertEquals(15, decoded.getEffectId());
  }
}
