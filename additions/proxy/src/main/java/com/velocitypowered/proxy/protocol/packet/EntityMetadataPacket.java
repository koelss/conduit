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

import com.velocitypowered.api.network.ProtocolVersion;
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

  // Entity and Living Entity metadata indices. These have been unchanged since 1.17, so the same
  // indices apply to every client that can switch seamlessly (1.20.2+).
  private static final int ENTITY_FLAGS_INDEX = 0;
  private static final int ENTITY_AIR_INDEX = 1;
  private static final int ENTITY_POSE_INDEX = 6;
  private static final int ENTITY_FROZEN_TICKS_INDEX = 7;
  private static final int LIVING_HAND_STATES_INDEX = 8;
  private static final int LIVING_EFFECT_PARTICLES_INDEX = 10;
  private static final int LIVING_EFFECT_AMBIENT_INDEX = 11;
  private static final int LIVING_ARROW_COUNT_INDEX = 12;
  private static final int LIVING_STINGER_COUNT_INDEX = 13;
  private static final int LIVING_SLEEPING_POSITION_INDEX = 14;

  // Serializer ids. Byte, VarInt, Boolean, and Optional Position sit at the head of the serializer
  // registry and have kept their ids across every supported version; the ones after the removal of
  // the compound-tag serializer in 1.21.9 and the addition of the particle-list serializer in
  // 1.20.5 have not, so they are resolved per version.
  private static final int SERIALIZER_BYTE = 0;
  private static final int SERIALIZER_VARINT = 1;
  private static final int SERIALIZER_BOOLEAN = 8;
  private static final int SERIALIZER_OPTIONAL_POSITION = 11;

  private static final int FULL_AIR_TICKS = 300;
  private static final int POSE_STANDING = 0;
  private static final int METADATA_END = 0xFF;

  /**
   * Returns the player's own entity to the state it would have on a fresh join.
   *
   * <p>A seamless switch keeps the client's player entity alive, so every visual driven by entity
   * metadata is inherited from the server the player left: arrows and bee stingers stuck in them,
   * potion particles, being on fire, air bubbles, the powder-snow freeze overlay, an item-use or
   * riptide animation, and a sneaking, crawling, swimming, or sleeping pose. A destination server
   * only sends metadata that differs from its own idea of a freshly joined player, so it never
   * clears any of this.
   */
  public static EntityMetadataPacket resetPlayerState(int entityId, ProtocolVersion version) {
    ByteBuf extra = Unpooled.buffer();
    // Clears being on fire, sneaking, sprinting, swimming, invisible, glowing, and elytra flight.
    writeEntry(extra, ENTITY_FLAGS_INDEX, SERIALIZER_BYTE);
    extra.writeByte(0);
    writeEntry(extra, ENTITY_AIR_INDEX, SERIALIZER_VARINT);
    ProtocolUtils.writeVarInt(extra, FULL_AIR_TICKS);
    writeEntry(extra, ENTITY_POSE_INDEX, poseSerializer(version));
    ProtocolUtils.writeVarInt(extra, POSE_STANDING);
    writeEntry(extra, ENTITY_FROZEN_TICKS_INDEX, SERIALIZER_VARINT);
    ProtocolUtils.writeVarInt(extra, 0);
    // Clears the item-use (eating, drinking, blocking, bow pull) and riptide spin animations.
    writeEntry(extra, LIVING_HAND_STATES_INDEX, SERIALIZER_BYTE);
    extra.writeByte(0);
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
      // The potion colour became a list of particles to spawn.
      writeEntry(extra, LIVING_EFFECT_PARTICLES_INDEX, particlesSerializer(version));
      ProtocolUtils.writeVarInt(extra, 0);
    } else {
      writeEntry(extra, LIVING_EFFECT_PARTICLES_INDEX, SERIALIZER_VARINT);
      ProtocolUtils.writeVarInt(extra, 0);
    }
    writeEntry(extra, LIVING_EFFECT_AMBIENT_INDEX, SERIALIZER_BOOLEAN);
    extra.writeBoolean(false);
    writeEntry(extra, LIVING_ARROW_COUNT_INDEX, SERIALIZER_VARINT);
    ProtocolUtils.writeVarInt(extra, 0);
    writeEntry(extra, LIVING_STINGER_COUNT_INDEX, SERIALIZER_VARINT);
    ProtocolUtils.writeVarInt(extra, 0);
    writeEntry(extra, LIVING_SLEEPING_POSITION_INDEX, SERIALIZER_OPTIONAL_POSITION);
    extra.writeBoolean(false);
    extra.writeByte(METADATA_END);

    byte[] payload = new byte[extra.readableBytes()];
    extra.readBytes(payload);
    extra.release();

    EntityMetadataPacket packet = new EntityMetadataPacket();
    packet.setEntityId(entityId);
    packet.setExtra(payload);
    return packet;
  }

  private static void writeEntry(ByteBuf buf, int index, int serializer) {
    buf.writeByte(index);
    ProtocolUtils.writeVarInt(buf, serializer);
  }

  private static int poseSerializer(ProtocolVersion version) {
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_21_9)) {
      return 20;
    }
    return version.noLessThan(ProtocolVersion.MINECRAFT_1_20_5) ? 21 : 20;
  }

  private static int particlesSerializer(ProtocolVersion version) {
    return version.noLessThan(ProtocolVersion.MINECRAFT_1_21_9) ? 17 : 18;
  }
}
