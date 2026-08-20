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

/**
 * Clientbound set-entity-motion packet.
 *
 * @author Luna
 * @date 19/08/2026
 */
public class EntityVelocityPacket extends EntityIdPayloadPacket {

  /**
   * Returns a packet that brings an entity to a standstill, dropping any momentum it carries.
   *
   * <p>Only usable up to 1.21.1: the velocity is three shorts there, while 1.21.2 replaced the
   * player's position packet with one that carries velocity itself, so those clients have their
   * momentum reset by the destination's own spawn teleport.
   */
  public static EntityVelocityPacket stop(int entityId) {
    EntityVelocityPacket packet = new EntityVelocityPacket();
    packet.setEntityId(entityId);
    packet.setExtra(new byte[6]);
    return packet;
  }
}
