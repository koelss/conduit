package com.velocitypowered.proxy.protocol.netty;

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
}
