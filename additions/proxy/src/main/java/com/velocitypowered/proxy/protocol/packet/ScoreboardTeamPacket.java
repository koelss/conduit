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
 * Clientbound teams packet. Non-remove payloads are preserved as raw trailing bytes so
 * version-specific team fields are forwarded unchanged.
 */
public class ScoreboardTeamPacket implements MinecraftPacket {

  public static final int METHOD_CREATE = 0;
  public static final int METHOD_REMOVE = 1;
  public static final int METHOD_UPDATE = 2;
  public static final int METHOD_ADD_ENTITIES = 3;
  public static final int METHOD_REMOVE_ENTITIES = 4;

  private String teamName = "";
  private int method;
  private byte[] extra = new byte[0];

  public ScoreboardTeamPacket() {
  }

  public static ScoreboardTeamPacket remove(String teamName) {
    ScoreboardTeamPacket packet = new ScoreboardTeamPacket();
    packet.teamName = teamName;
    packet.method = METHOD_REMOVE;
    return packet;
  }

  public String getTeamName() {
    return teamName;
  }

  public int getMethod() {
    return method;
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    this.teamName = ProtocolUtils.readString(buf);
    this.method = buf.readUnsignedByte();
    if (buf.isReadable()) {
      this.extra = new byte[buf.readableBytes()];
      buf.readBytes(this.extra);
    } else {
      this.extra = new byte[0];
    }
  }

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    ProtocolUtils.writeString(buf, teamName);
    buf.writeByte(method);
    if (method != METHOD_REMOVE && extra.length > 0) {
      buf.writeBytes(extra);
    }
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
