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
import java.util.Collection;
import java.util.List;

/**
 * @author Luna
 * @date 07/08/2026
 */
public class RemoveEntitiesPacket implements MinecraftPacket {

  private Collection<Integer> entityIds;

  public RemoveEntitiesPacket() {
    this.entityIds = List.of();
  }

  public RemoveEntitiesPacket(Collection<Integer> entityIds) {
    this.entityIds = entityIds;
  }

  public Collection<Integer> getEntityIds() {
    return entityIds;
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    final int[] read = ProtocolUtils.readVarIntArray(buf);
    final List<Integer> ids = ProtocolUtils.newList(read.length);
    for (final int id : read) {
      ids.add(id);
    }
    this.entityIds = ids;
  }

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    ProtocolUtils.writeVarInt(buf, entityIds.size());
    for (final int entityId : entityIds) {
      ProtocolUtils.writeVarInt(buf, entityId);
    }
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
