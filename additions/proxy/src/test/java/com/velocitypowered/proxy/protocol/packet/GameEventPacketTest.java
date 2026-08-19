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

  @Test
  void entityEventRoundTripsOperatorClear() {
    EntityEventPacket packet = EntityEventPacket.clearOperator(42);
    ByteBuf buf = Unpooled.buffer();
    packet.encode(buf, ProtocolUtils.Direction.CLIENTBOUND, ProtocolVersion.MINECRAFT_1_20_2);

    EntityEventPacket decoded = new EntityEventPacket();
    decoded.decode(buf, ProtocolUtils.Direction.CLIENTBOUND, ProtocolVersion.MINECRAFT_1_20_2);
    buf.release();

    assertEquals(42, decoded.getEntityId());
    assertEquals(EntityEventPacket.STATUS_OP_PERMISSION_LEVEL_0, decoded.getStatus());
  }

  @Test
  void hurtAnimationPreservesYaw() {
    HurtAnimationPacket packet = new HurtAnimationPacket();
    packet.setEntityId(7);
    ByteBuf raw = Unpooled.buffer();
    ProtocolUtils.writeVarInt(raw, 7);
    raw.writeFloat(90.0f);

    HurtAnimationPacket decoded = new HurtAnimationPacket();
    decoded.decode(raw, ProtocolUtils.Direction.CLIENTBOUND, ProtocolVersion.MINECRAFT_1_20_2);
    raw.release();

    assertEquals(7, decoded.getEntityId());
    decoded.setEntityId(9);
    ByteBuf encoded = Unpooled.buffer();
    decoded.encode(encoded, ProtocolUtils.Direction.CLIENTBOUND, ProtocolVersion.MINECRAFT_1_20_2);
    assertEquals(9, ProtocolUtils.readVarInt(encoded));
    assertEquals(90.0f, encoded.readFloat());
    encoded.release();
  }

  @Test
  void resetBreathAndSwimWritesAirAndFlags() {
    EntityMetadataPacket packet = EntityMetadataPacket.resetBreathAndSwim(12);
    ByteBuf buf = Unpooled.buffer();
    packet.encode(buf, ProtocolUtils.Direction.CLIENTBOUND, ProtocolVersion.MINECRAFT_1_20_2);

    EntityMetadataPacket decoded = new EntityMetadataPacket();
    decoded.decode(buf, ProtocolUtils.Direction.CLIENTBOUND, ProtocolVersion.MINECRAFT_1_20_2);
    buf.release();

    assertEquals(12, decoded.getEntityId());
    ByteBuf extra = Unpooled.buffer();
    decoded.encode(extra, ProtocolUtils.Direction.CLIENTBOUND, ProtocolVersion.MINECRAFT_1_20_2);
    assertEquals(12, ProtocolUtils.readVarInt(extra));
    assertEquals(0, extra.readUnsignedByte());
    assertEquals(0, ProtocolUtils.readVarInt(extra));
    assertEquals(0, extra.readByte());
    assertEquals(1, extra.readUnsignedByte());
    assertEquals(1, ProtocolUtils.readVarInt(extra));
    assertEquals(300, ProtocolUtils.readVarInt(extra));
    assertEquals(0xFF, extra.readUnsignedByte());
    extra.release();
  }
}
