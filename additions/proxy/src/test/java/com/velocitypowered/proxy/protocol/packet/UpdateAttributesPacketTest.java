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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.UpdateAttributesPacket.AttributeSnapshot;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import org.junit.jupiter.api.Test;

class UpdateAttributesPacketTest {

  // The registry id of minecraft:player.block_interaction_range on 1.21.
  private static final int BLOCK_INTERACTION_RANGE_ID = 6;

  private static UpdateAttributesPacket decode(ByteBuf buf, ProtocolVersion version) {
    UpdateAttributesPacket packet = new UpdateAttributesPacket();
    packet.decode(buf, ProtocolUtils.Direction.CLIENTBOUND, version);
    buf.release();
    return packet;
  }

  private static ByteBuf encode(UpdateAttributesPacket packet, ProtocolVersion version) {
    ByteBuf buf = Unpooled.buffer();
    packet.encode(buf, ProtocolUtils.Direction.CLIENTBOUND, version);
    return buf;
  }

  private static ByteBuf creativeReach(ProtocolVersion version) {
    ByteBuf buf = Unpooled.buffer();
    ProtocolUtils.writeVarInt(buf, 7); // entity id
    ProtocolUtils.writeVarInt(buf, 1); // one property
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
      ProtocolUtils.writeVarInt(buf, BLOCK_INTERACTION_RANGE_ID);
    } else {
      ProtocolUtils.writeString(buf, "minecraft:player.block_interaction_range");
    }
    buf.writeDouble(4.5d); // base value
    ProtocolUtils.writeVarInt(buf, 1); // one modifier
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_21)) {
      ProtocolUtils.writeString(buf, "minecraft:creative_mode_block_range");
    } else {
      buf.writeLong(0L);
      buf.writeLong(1L);
    }
    buf.writeDouble(0.5d); // amount
    buf.writeByte(0); // operation: add
    return buf;
  }

  @Test
  void readsModernAttributes() {
    UpdateAttributesPacket packet = decode(creativeReach(ProtocolVersion.MINECRAFT_1_21),
        ProtocolVersion.MINECRAFT_1_21);

    assertEquals(7, packet.getEntityId());
    assertEquals(1, packet.getAttributes().size());
    AttributeSnapshot attribute = packet.getAttributes().get(0);
    assertEquals(Integer.toString(BLOCK_INTERACTION_RANGE_ID), attribute.getId());
    assertEquals(4.5d, attribute.getBase());
  }

  @Test
  void readsLegacyAttributes() {
    UpdateAttributesPacket packet = decode(creativeReach(ProtocolVersion.MINECRAFT_1_20_2),
        ProtocolVersion.MINECRAFT_1_20_2);

    assertEquals("minecraft:player.block_interaction_range", packet.getAttributes().get(0).getId());
    assertEquals(4.5d, packet.getAttributes().get(0).getBase());
  }

  @Test
  void forwardsUnknownAttributesUnchanged() {
    ByteBuf original = Unpooled.buffer();
    ProtocolUtils.writeVarInt(original, 7);
    original.writeBytes(new byte[] {0x7F, 0x7F, 0x7F}); // not a valid properties section
    byte[] expected = new byte[original.readableBytes()];
    original.getBytes(original.readerIndex(), expected);

    UpdateAttributesPacket packet = decode(original, ProtocolVersion.MINECRAFT_1_21);
    assertTrue(packet.getAttributes().isEmpty());

    ByteBuf reencoded = encode(packet, ProtocolVersion.MINECRAFT_1_21);
    byte[] actual = new byte[reencoded.readableBytes()];
    reencoded.readBytes(actual);
    reencoded.release();
    assertEquals(java.util.Arrays.toString(expected), java.util.Arrays.toString(actual));
  }

  @Test
  void resetKeepsBaseValueAndDropsModifiers() {
    UpdateAttributesPacket creative = decode(creativeReach(ProtocolVersion.MINECRAFT_1_21),
        ProtocolVersion.MINECRAFT_1_21);

    UpdateAttributesPacket reset =
        UpdateAttributesPacket.resetModifiers(42, List.copyOf(creative.getAttributes()));
    ByteBuf encoded = encode(reset, ProtocolVersion.MINECRAFT_1_21);
    // entity id + property count + attribute key + base value + a zero modifier count.
    assertEquals(1 + 1 + 1 + Double.BYTES + 1, encoded.readableBytes());
    encoded.release();

    UpdateAttributesPacket roundTripped =
        decode(encode(reset, ProtocolVersion.MINECRAFT_1_21), ProtocolVersion.MINECRAFT_1_21);

    assertEquals(42, roundTripped.getEntityId());
    assertEquals(1, roundTripped.getAttributes().size());
    AttributeSnapshot attribute = roundTripped.getAttributes().get(0);
    assertEquals(Integer.toString(BLOCK_INTERACTION_RANGE_ID), attribute.getId());
    assertEquals(4.5d, attribute.getBase());
  }
}
