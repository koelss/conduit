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

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class ScoreboardObjectivePacketTest {

  @Test
  void removePacketContainsOnlyNameAndMethod() {
    ScoreboardObjectivePacket packet = ScoreboardObjectivePacket.remove("sidebar");
    ByteBuf buf = Unpooled.buffer();
    packet.encode(buf, ProtocolUtils.Direction.CLIENTBOUND, ProtocolVersion.MINECRAFT_26_2);

    ScoreboardObjectivePacket decoded = new ScoreboardObjectivePacket();
    decoded.decode(buf, ProtocolUtils.Direction.CLIENTBOUND, ProtocolVersion.MINECRAFT_26_2);
    buf.release();

    assertEquals("sidebar", decoded.getObjectiveName());
    assertEquals(ScoreboardObjectivePacket.METHOD_REMOVE, decoded.getMethod());
  }

  @Test
  void createPayloadRoundTripsAsRawBytes() {
    ByteBuf original = Unpooled.buffer();
    ProtocolUtils.writeString(original, "sidebar");
    original.writeByte(ScoreboardObjectivePacket.METHOD_CREATE);
    original.writeBytes(new byte[] {1, 2, 3, 4, 5});
    byte[] expected = toBytes(original);

    ScoreboardObjectivePacket packet = new ScoreboardObjectivePacket();
    packet.decode(original, ProtocolUtils.Direction.CLIENTBOUND, ProtocolVersion.MINECRAFT_26_2);
    original.release();

    ByteBuf encoded = Unpooled.buffer();
    packet.encode(encoded, ProtocolUtils.Direction.CLIENTBOUND, ProtocolVersion.MINECRAFT_26_2);
    assertArrayEquals(expected, toBytes(encoded));
    encoded.release();
  }

  private static byte[] toBytes(ByteBuf buf) {
    byte[] bytes = new byte[buf.readableBytes()];
    buf.getBytes(buf.readerIndex(), bytes);
    return bytes;
  }
}
