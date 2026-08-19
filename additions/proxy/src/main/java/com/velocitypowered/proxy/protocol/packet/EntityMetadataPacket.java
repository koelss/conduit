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

import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Clientbound set-entity-data packet.
 *
 * @author Luna
 * @date 19/08/2026
 */
public class EntityMetadataPacket extends EntityIdPayloadPacket {

  private static final int ENTITY_FLAGS_INDEX = 0;
  private static final int ENTITY_AIR_INDEX = 1;
  private static final int SERIALIZER_BYTE = 0;
  private static final int SERIALIZER_VARINT = 1;
  private static final int FULL_AIR_TICKS = 300;
  private static final int METADATA_END = 0xFF;

  /**
   * Clears swimming and restores full air so the bubble HUD does not linger after a switch.
   */
  public static EntityMetadataPacket resetBreathAndSwim(int entityId) {
    ByteBuf extra = Unpooled.buffer();
    extra.writeByte(ENTITY_FLAGS_INDEX);
    ProtocolUtils.writeVarInt(extra, SERIALIZER_BYTE);
    extra.writeByte(0);
    extra.writeByte(ENTITY_AIR_INDEX);
    ProtocolUtils.writeVarInt(extra, SERIALIZER_VARINT);
    ProtocolUtils.writeVarInt(extra, FULL_AIR_TICKS);
    extra.writeByte(METADATA_END);
    byte[] payload = new byte[extra.readableBytes()];
    extra.readBytes(payload);
    extra.release();

    EntityMetadataPacket packet = new EntityMetadataPacket();
    packet.setEntityId(entityId);
    packet.setExtra(payload);
    return packet;
  }
}
