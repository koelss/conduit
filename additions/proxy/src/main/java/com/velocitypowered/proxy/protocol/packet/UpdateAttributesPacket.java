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
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Clientbound update-attributes packet.
 *
 * <p>The properties section is kept as raw bytes so the packet forwards unchanged, and is parsed
 * on a best-effort basis so the proxy can remember which attributes a server gave the player. The
 * parse is deliberately non-fatal: an attribute layout this proxy does not understand leaves the
 * snapshot empty instead of dropping the connection.
 */
public class UpdateAttributesPacket implements MinecraftPacket {

  /**
   * A single attribute as it appeared on the wire: the encoded registry key (a VarInt id from
   * 1.20.5, an identifier string before that) and the base value, without any modifiers.
   */
  public static final class AttributeSnapshot {

    private final String id;
    private final byte[] key;
    private final double base;

    AttributeSnapshot(String id, byte[] key, double base) {
      this.id = id;
      this.key = key;
      this.base = base;
    }

    /**
     * Returns a stable identity for this attribute, used to deduplicate tracked attributes.
     */
    public String getId() {
      return id;
    }

    public double getBase() {
      return base;
    }

    byte[] getKey() {
      return key;
    }
  }

  private int entityId;
  private byte[] properties = new byte[0];
  private List<AttributeSnapshot> attributes = List.of();

  public int getEntityId() {
    return entityId;
  }

  public void setEntityId(int entityId) {
    this.entityId = entityId;
  }

  public List<AttributeSnapshot> getAttributes() {
    return attributes;
  }

  /**
   * Builds a packet that restores the given attributes to their base values with no modifiers.
   *
   * <p>Creative mode grants its extended block and entity interaction range through attribute
   * modifiers on the player. The client keeps those modifiers until a server tells it otherwise,
   * and a server only sends attributes that differ from its own idea of a freshly joined player,
   * so a survival destination never clears them by itself.
   */
  public static UpdateAttributesPacket resetModifiers(
      int entityId, Collection<AttributeSnapshot> attributes) {
    ByteBuf properties = Unpooled.buffer();
    ProtocolUtils.writeVarInt(properties, attributes.size());
    for (AttributeSnapshot attribute : attributes) {
      properties.writeBytes(attribute.getKey());
      properties.writeDouble(attribute.getBase());
      ProtocolUtils.writeVarInt(properties, 0); // no modifiers
    }

    byte[] payload = new byte[properties.readableBytes()];
    properties.readBytes(payload);
    properties.release();

    UpdateAttributesPacket packet = new UpdateAttributesPacket();
    packet.entityId = entityId;
    packet.properties = payload;
    return packet;
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    this.entityId = ProtocolUtils.readVarInt(buf);
    if (buf.isReadable()) {
      this.properties = new byte[buf.readableBytes()];
      buf.readBytes(this.properties);
    } else {
      this.properties = new byte[0];
    }
    this.attributes = readAttributes(this.properties, version);
  }

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion version) {
    ProtocolUtils.writeVarInt(buf, entityId);
    if (properties.length > 0) {
      buf.writeBytes(properties);
    }
  }

  /**
   * Parses the properties section, returning an empty list if it does not have the expected shape.
   */
  static List<AttributeSnapshot> readAttributes(byte[] properties, ProtocolVersion version) {
    if (properties.length == 0) {
      return List.of();
    }

    ByteBuf buf = Unpooled.wrappedBuffer(properties);
    try {
      List<AttributeSnapshot> attributes = new ArrayList<>();
      int count = ProtocolUtils.readVarInt(buf);
      for (int i = 0; i < count; i++) {
        int keyStart = buf.readerIndex();
        String id = readKey(buf, version);
        byte[] key = new byte[buf.readerIndex() - keyStart];
        buf.getBytes(keyStart, key);

        double base = buf.readDouble();
        skipModifiers(buf, version);
        attributes.add(new AttributeSnapshot(id, key, base));
      }
      return List.copyOf(attributes);
    } catch (RuntimeException e) {
      // An attribute layout we do not understand must never break the connection: the packet is
      // still forwarded verbatim, we simply cannot remember what was in it.
      return List.of();
    } finally {
      buf.release();
    }
  }

  private static String readKey(ByteBuf buf, ProtocolVersion version) {
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
      // Registry ids, so the numeric value only identifies the attribute within this version.
      return Integer.toString(ProtocolUtils.readVarInt(buf));
    }
    return ProtocolUtils.readString(buf);
  }

  private static void skipModifiers(ByteBuf buf, ProtocolVersion version) {
    int modifiers = ProtocolUtils.readVarInt(buf);
    for (int i = 0; i < modifiers; i++) {
      if (version.noLessThan(ProtocolVersion.MINECRAFT_1_21)) {
        // Modifier ids became namespaced identifiers in 1.21; before that they were UUIDs.
        ProtocolUtils.readString(buf);
      } else {
        buf.skipBytes(Long.BYTES * 2);
      }
      buf.readDouble();
      buf.readByte();
    }
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
