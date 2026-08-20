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
 * Clientbound game-event packet (weather, gamemode, limited crafting, and similar).
 */
public class GameEventPacket implements MinecraftPacket {

  public static final int EVENT_START_RAINING = 1;
  public static final int EVENT_END_RAINING = 2;
  public static final int EVENT_CHANGE_GAMEMODE = 3;
  // The client keeps the rain and thunder gradients separate from the "is it raining" flag, and
  // only ever changes them when the server tells it to. Ending the rain sets the rain gradient to
  // full (the client expects the server to fade it out afterwards), so both levels have to be
  // zeroed explicitly to actually stop rendering precipitation.
  public static final int EVENT_RAIN_LEVEL_CHANGE = 7;
  public static final int EVENT_THUNDER_LEVEL_CHANGE = 8;
  public static final int EVENT_LIMITED_CRAFTING = 12;
  public static final int EVENT_START_WAITING_FOR_CHUNKS = 13;

  private int event;
  private float value;

  public GameEventPacket() {
  }

  public GameEventPacket(int event, float value) {
    this.event = event;
    this.value = value;
  }

  public int getEvent() {
    return event;
  }

  public float getValue() {
    return value;
  }

  public static GameEventPacket changeGamemode(int gamemode) {
    return new GameEventPacket(EVENT_CHANGE_GAMEMODE, gamemode);
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    this.event = buf.readUnsignedByte();
    this.value = buf.readFloat();
  }

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    buf.writeByte(event);
    buf.writeFloat(value);
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
