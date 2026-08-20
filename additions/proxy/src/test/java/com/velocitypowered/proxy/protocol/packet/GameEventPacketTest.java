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
  void resetPlayerStateClearsEveryVisualOnLegacyEffectColour() {
    ByteBuf buf = encodeReset(ProtocolVersion.MINECRAFT_1_20_2);

    assertEquals(12, ProtocolUtils.readVarInt(buf));
    assertEntry(buf, 0, 0);
    assertEquals(0, buf.readByte());
    assertEntry(buf, 1, 1);
    assertEquals(300, ProtocolUtils.readVarInt(buf));
    assertEntry(buf, 6, 20);
    assertEquals(0, ProtocolUtils.readVarInt(buf));
    assertEntry(buf, 7, 1);
    assertEquals(0, ProtocolUtils.readVarInt(buf));
    assertEntry(buf, 8, 0);
    assertEquals(0, buf.readByte());
    // Before 1.20.5 the effect particles field is the potion colour.
    assertEntry(buf, 10, 1);
    assertEquals(0, ProtocolUtils.readVarInt(buf));
    assertEntry(buf, 11, 8);
    assertEquals(false, buf.readBoolean());
    assertEntry(buf, 12, 1);
    assertEquals(0, ProtocolUtils.readVarInt(buf));
    assertEntry(buf, 13, 1);
    assertEquals(0, ProtocolUtils.readVarInt(buf));
    assertEntry(buf, 14, 11);
    assertEquals(false, buf.readBoolean());
    assertEquals(0xFF, buf.readUnsignedByte());
    assertEquals(0, buf.readableBytes());
    buf.release();
  }

  @Test
  void resetPlayerStateUsesParticleListSerializerFrom1205() {
    ByteBuf buf = encodeReset(ProtocolVersion.MINECRAFT_1_21_5);

    ProtocolUtils.readVarInt(buf);
    skipEntries(buf, 4);
    assertEntry(buf, 8, 0);
    assertEquals(0, buf.readByte());
    assertEntry(buf, 10, 18);
    assertEquals(0, ProtocolUtils.readVarInt(buf));
    buf.release();
  }

  @Test
  void resetPlayerStateFollowsSerializerShiftIn1219() {
    ByteBuf buf = encodeReset(ProtocolVersion.MINECRAFT_1_21_9);

    ProtocolUtils.readVarInt(buf);
    assertEntry(buf, 0, 0);
    assertEquals(0, buf.readByte());
    assertEntry(buf, 1, 1);
    assertEquals(300, ProtocolUtils.readVarInt(buf));
    assertEntry(buf, 6, 20);
    assertEquals(0, ProtocolUtils.readVarInt(buf));
    skipEntries(buf, 2);
    assertEntry(buf, 10, 17);
    assertEquals(0, ProtocolUtils.readVarInt(buf));
    buf.release();
  }

  private static ByteBuf encodeReset(ProtocolVersion version) {
    ByteBuf buf = Unpooled.buffer();
    EntityMetadataPacket.resetPlayerState(12, version)
        .encode(buf, ProtocolUtils.Direction.CLIENTBOUND, version);
    return buf;
  }

  private static void assertEntry(ByteBuf buf, int index, int serializer) {
    assertEquals(index, buf.readUnsignedByte());
    assertEquals(serializer, ProtocolUtils.readVarInt(buf));
  }

  /**
   * Skips whole entries whose value is a single byte or a VarInt, which covers every entry this test
   * does not assert on directly.
   */
  private static void skipEntries(ByteBuf buf, int count) {
    for (int i = 0; i < count; i++) {
      buf.readUnsignedByte();
      int serializer = ProtocolUtils.readVarInt(buf);
      if (serializer == 0) {
        buf.readByte();
      } else {
        ProtocolUtils.readVarInt(buf);
      }
    }
  }
}
