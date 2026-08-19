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
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import java.util.UUID;

/**
 * Clientbound spawn/add-entity packet used to track live entity IDs across backends.
 *
 * <p>Minecraft 1.21.9 moved velocity ahead of rotation and switched it to a variable-length
 * vector. That payload is stored as raw bytes so it is not decoded or re-encoded with the
 * obsolete short-vector format.
 *
 * @author Luna
 * @date 07/08/2026
 */
public class SpawnEntityPacket implements MinecraftPacket {

  private int entityId;
  private UUID uuid;
  private int type;
  private double posX;
  private double posY;
  private double posZ;
  private byte pitch;
  private byte yaw;
  private byte headYaw;
  private int data;
  private short velocityX;
  private short velocityY;
  private short velocityZ;
  private byte velB1;
  private byte velB2;
  private int velHigh;
  private int velScaleExtra;
  private boolean velHasExtra;

  public int getEntityId() {
    return entityId;
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    this.entityId = ProtocolUtils.readVarInt(buf);
    this.uuid = ProtocolUtils.readUuid(buf);
    this.type = ProtocolUtils.readVarInt(buf);
    this.posX = buf.readDouble();
    this.posY = buf.readDouble();
    this.posZ = buf.readDouble();
    if (version.lessThan(ProtocolVersion.MINECRAFT_1_21_9)) {
      this.pitch = buf.readByte();
      this.yaw = buf.readByte();
      this.headYaw = buf.readByte();
      this.data = ProtocolUtils.readVarInt(buf);
      this.velocityX = buf.readShort();
      this.velocityY = buf.readShort();
      this.velocityZ = buf.readShort();
    } else {
      this.velB1 = buf.readByte();
      if (this.velB1 != 0) {
        this.velB2 = buf.readByte();
        this.velHigh = buf.readInt();
        this.velHasExtra = (this.velB1 & 0x04) != 0;
        if (this.velHasExtra) {
          this.velScaleExtra = ProtocolUtils.readVarInt(buf);
        }
      } else {
        this.velHasExtra = false;
      }
      this.pitch = buf.readByte();
      this.yaw = buf.readByte();
      this.headYaw = buf.readByte();
      this.data = ProtocolUtils.readVarInt(buf);
    }
  }

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    ProtocolUtils.writeVarInt(buf, entityId);
    ProtocolUtils.writeUuid(buf, uuid);
    ProtocolUtils.writeVarInt(buf, type);
    buf.writeDouble(posX);
    buf.writeDouble(posY);
    buf.writeDouble(posZ);
    if (version.lessThan(ProtocolVersion.MINECRAFT_1_21_9)) {
      buf.writeByte(pitch);
      buf.writeByte(yaw);
      buf.writeByte(headYaw);
      ProtocolUtils.writeVarInt(buf, data);
      buf.writeShort(velocityX);
      buf.writeShort(velocityY);
      buf.writeShort(velocityZ);
    } else {
      buf.writeByte(velB1);
      if (velB1 != 0) {
        buf.writeByte(velB2);
        buf.writeInt(velHigh);
        if (velHasExtra) {
          ProtocolUtils.writeVarInt(buf, velScaleExtra);
        }
      }
      buf.writeByte(pitch);
      buf.writeByte(yaw);
      buf.writeByte(headYaw);
      ProtocolUtils.writeVarInt(buf, data);
    }
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
