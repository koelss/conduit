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

package com.velocitypowered.proxy.protocol.netty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.AvailableCommandsPacket;
import com.velocitypowered.proxy.protocol.packet.KeepAlivePacket;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Queue;
import org.junit.jupiter.api.Test;

class PlayPacketQueueOutboundHandlerTest {

  @SuppressWarnings("unchecked")
  @Test
  void evictsOrdinaryPacketsWithoutEvictingCommandTree() throws Exception {
    PlayPacketQueueOutboundHandler handler = new PlayPacketQueueOutboundHandler(
        ProtocolVersion.MINECRAFT_1_20_2, ProtocolUtils.Direction.CLIENTBOUND);
    Field queueField = PlayPacketQueueOutboundHandler.class.getDeclaredField("queue");
    queueField.setAccessible(true);
    Queue<MinecraftPacket> queue = (Queue<MinecraftPacket>) queueField.get(handler);
    KeepAlivePacket keepAlive = new KeepAlivePacket();
    AvailableCommandsPacket commands = new AvailableCommandsPacket();
    queue.offer(keepAlive);
    queue.offer(commands);

    Method evict = PlayPacketQueueOutboundHandler.class.getDeclaredMethod("evictOldestNonCommandTree");
    evict.setAccessible(true);

    assertTrue((Boolean) evict.invoke(handler));
    assertFalse(queue.contains(keepAlive));
    assertSame(commands, queue.peek());
  }

  @SuppressWarnings("unchecked")
  @Test
  void collapsesQueuedCommandTreesToTheNewest() throws Exception {
    PlayPacketQueueOutboundHandler handler = new PlayPacketQueueOutboundHandler(
        ProtocolVersion.MINECRAFT_1_20_2, ProtocolUtils.Direction.CLIENTBOUND);
    Field queueField = PlayPacketQueueOutboundHandler.class.getDeclaredField("queue");
    queueField.setAccessible(true);
    Queue<MinecraftPacket> queue = (Queue<MinecraftPacket>) queueField.get(handler);

    // Simulate a stale tree already sitting in the CONFIG queue, then a fresh one arriving.
    AvailableCommandsPacket staleTree = new AvailableCommandsPacket();
    queue.offer(staleTree);

    Method release = PlayPacketQueueOutboundHandler.class.getDeclaredMethod("releaseQueuedCommandTrees");
    release.setAccessible(true);
    release.invoke(handler);
    AvailableCommandsPacket freshTree = new AvailableCommandsPacket();
    queue.offer(freshTree);

    // Only the newest tree survives, so the FIFO flush can never surface the stale one.
    assertFalse(queue.contains(staleTree));
    assertSame(freshTree, queue.peek());
    assertEquals(1, queue.size());
  }
}
