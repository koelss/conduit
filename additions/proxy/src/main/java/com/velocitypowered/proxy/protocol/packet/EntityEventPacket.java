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

/**
 * Clientbound entity-event / entity-status packet (Int entity ID + unsigned status byte).
 *
 * <p>Status values 24–28 set the client's operator permission level (0–4), which controls
 * the F3+F4 gamemode switcher.
 *
 * @author Luna
 * @date 19/08/2026
 */
public class EntityEventPacket implements MinecraftPacket {

  public static final byte STATUS_OP_PERMISSION_LEVEL_0 = 24;

  private int entityId;
  private byte status;

  public EntityEventPacket() {
  }

  public EntityEventPacket(int entityId, byte status) {
    this.entityId = entityId;
    this.status = status;
  }

  public int getEntityId() {
    return entityId;
  }

  public void setEntityId(int entityId) {
    this.entityId = entityId;
  }

  public byte getStatus() {
    return status;
  }

  public static EntityEventPacket clearOperator(int entityId) {
    return new EntityEventPacket(entityId, STATUS_OP_PERMISSION_LEVEL_0);
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    this.entityId = buf.readInt();
    this.status = buf.readByte();
  }

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    buf.writeInt(entityId);
    buf.writeByte(status);
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
