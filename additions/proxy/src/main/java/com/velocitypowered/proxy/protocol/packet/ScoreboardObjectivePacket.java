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
 * Clientbound set-objective packet. Create/update payloads are preserved as raw trailing bytes
 * so version-specific number-format fields are not re-encoded incorrectly.
 */
public class ScoreboardObjectivePacket implements MinecraftPacket {

  public static final int METHOD_CREATE = 0;
  public static final int METHOD_REMOVE = 1;
  public static final int METHOD_UPDATE = 2;

  private String objectiveName = "";
  private int method;
  private byte[] extra = new byte[0];

  public ScoreboardObjectivePacket() {
  }

  public static ScoreboardObjectivePacket remove(String objectiveName) {
    ScoreboardObjectivePacket packet = new ScoreboardObjectivePacket();
    packet.objectiveName = objectiveName;
    packet.method = METHOD_REMOVE;
    return packet;
  }

  public String getObjectiveName() {
    return objectiveName;
  }

  public int getMethod() {
    return method;
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    this.objectiveName = ProtocolUtils.readString(buf);
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
    ProtocolUtils.writeString(buf, objectiveName);
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
