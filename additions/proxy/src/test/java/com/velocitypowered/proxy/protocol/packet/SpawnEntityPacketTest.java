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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpawnEntityPacketTest {

  @Test
  void roundTripsPre1219VelocityAsShorts() {
    ByteBuf original = Unpooled.buffer();
    ProtocolUtils.writeVarInt(original, 42);
    ProtocolUtils.writeUuid(original, UUID.fromString("00000000-0000-0000-0000-000000000001"));
    ProtocolUtils.writeVarInt(original, 7);
    original.writeDouble(1.0);
    original.writeDouble(64.0);
    original.writeDouble(2.0);
    original.writeByte(10);
    original.writeByte(20);
    original.writeByte(30);
    ProtocolUtils.writeVarInt(original, 0);
    original.writeShort(123);
    original.writeShort(-456);
    original.writeShort(789);

    byte[] expected = toBytes(original);

    SpawnEntityPacket packet = new SpawnEntityPacket();
    packet.decode(original, ProtocolUtils.Direction.CLIENTBOUND, ProtocolVersion.MINECRAFT_1_21_5);
    original.release();

    assertEquals(42, packet.getEntityId());

    ByteBuf encoded = Unpooled.buffer();
    packet.encode(encoded, ProtocolUtils.Direction.CLIENTBOUND, ProtocolVersion.MINECRAFT_1_21_5);
    assertArrayEquals(expected, toBytes(encoded));
    encoded.release();
  }

  @Test
  void roundTrips1219PlusVelocityAsRawBytes() {
    ByteBuf original = Unpooled.buffer();
    ProtocolUtils.writeVarInt(original, 99);
    ProtocolUtils.writeUuid(original, UUID.fromString("00000000-0000-0000-0000-000000000002"));
    ProtocolUtils.writeVarInt(original, 3);
    original.writeDouble(8.0);
    original.writeDouble(16.0);
    original.writeDouble(24.0);
    original.writeByte(0x05);
    original.writeByte(0x11);
    original.writeInt(0x11223344);
    ProtocolUtils.writeVarInt(original, 17);
    original.writeByte(1);
    original.writeByte(2);
    original.writeByte(3);
    ProtocolUtils.writeVarInt(original, 5);

    byte[] expected = toBytes(original);

    SpawnEntityPacket packet = new SpawnEntityPacket();
    packet.decode(original, ProtocolUtils.Direction.CLIENTBOUND, ProtocolVersion.MINECRAFT_1_21_9);
    original.release();

    assertEquals(99, packet.getEntityId());

    ByteBuf encoded = Unpooled.buffer();
    packet.encode(encoded, ProtocolUtils.Direction.CLIENTBOUND, ProtocolVersion.MINECRAFT_1_21_9);
    assertArrayEquals(expected, toBytes(encoded));
    encoded.release();
  }

  @Test
  void roundTripsRemoveEntities() {
    RemoveEntitiesPacket packet = new RemoveEntitiesPacket(List.of(1, 2, 40));
    ByteBuf buf = Unpooled.buffer();
    packet.encode(buf, ProtocolUtils.Direction.CLIENTBOUND, ProtocolVersion.MINECRAFT_1_20_2);

    RemoveEntitiesPacket decoded = new RemoveEntitiesPacket();
    decoded.decode(buf, ProtocolUtils.Direction.CLIENTBOUND, ProtocolVersion.MINECRAFT_1_20_2);
    buf.release();

    assertTrue(decoded.getEntityIds().containsAll(List.of(1, 2, 40)));
    assertEquals(3, decoded.getEntityIds().size());
  }

  private static byte[] toBytes(ByteBuf buf) {
    byte[] bytes = new byte[buf.readableBytes()];
    buf.getBytes(buf.readerIndex(), bytes);
    return bytes;
  }
}
