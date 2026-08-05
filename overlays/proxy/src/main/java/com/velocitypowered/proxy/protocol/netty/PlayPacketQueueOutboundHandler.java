/*
 * Copyright (C) 2018-2026 Velocity Contributors
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

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.conduit.Conduit;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.AvailableCommandsPacket;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;
import org.jetbrains.annotations.NotNull;

/**
 * Queues up any pending PLAY packets while the client is in the CONFIG state.
 *
 * <p>Much of the Velocity API (i.e., chat messages) utilize PLAY packets; however, the client is
 * incapable of receiving these packets during the CONFIG state. Certain events such as the
 * ServerPreConnectEvent may be called during this time, and we need to ensure that any API that
 * uses these packets will work as expected.
 *
 * <p>This handler will queue up any packets that are sent to the client during this time, and send
 * them once the client has (re)entered the PLAY state.
 */
public class PlayPacketQueueOutboundHandler extends ChannelDuplexHandler {

  private final StateRegistry.PacketRegistry.ProtocolRegistry registry;

  private final Queue<MinecraftPacket> queue = new ArrayDeque<>();

  /**
   * Provides registries for "client" &amp; server bound packets.
   *
   * @param version the protocol version
   * @param direction the direction of packet flow (typically {@code CLIENTBOUND})
   */
  public PlayPacketQueueOutboundHandler(ProtocolVersion version, ProtocolUtils.Direction direction) {
    this.registry = StateRegistry.CONFIG.getProtocolRegistry(direction, version);
  }

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
    if (!(msg instanceof MinecraftPacket packet)) {
      ctx.write(msg, promise);
      return;
    }

    // If the packet exists in the CONFIG state, we want to always
    // ensure that it gets sent out to the client
    if (this.registry.containsPacket(packet)) {
      ctx.write(msg, promise);
      return;
    }

    // The Brigadier command tree (AvailableCommandsPacket) is a PLAY packet that must survive the
    // CONFIG queue: dropping it, or replaying a stale one, leaves the client with an incomplete tree
    // so otherwise-executable commands render red/"unknown". Only the most recent tree is ever
    // meaningful, so collapse any queued trees down to this newest one. This both guarantees the
    // client ends on the correct tree (FIFO flush order can never surface a stale tree) and keeps
    // command trees from counting against the depth cap.
    if (packet instanceof AvailableCommandsPacket) {
      releaseQueuedCommandTrees();
      this.queue.offer(packet);
      return;
    }

    if (isConduitPacketQueueEnabled() && this.queue.size() >= Conduit.get().getConfig().getPacketQueueMaxDepth()) {
      // Evict the oldest ordinary packet to make room. Command trees are never evicted here; if the
      // queue somehow contains only a command tree, drop this ordinary packet instead.
      if (!evictOldestNonCommandTree()) {
        ReferenceCountUtil.release(packet);
        return;
      }
    }

    // Otherwise, queue the packet
    this.queue.offer(packet);
  }

  private boolean evictOldestNonCommandTree() {
    Iterator<MinecraftPacket> iterator = this.queue.iterator();
    while (iterator.hasNext()) {
      MinecraftPacket queued = iterator.next();
      if (!(queued instanceof AvailableCommandsPacket)) {
        iterator.remove();
        ReferenceCountUtil.release(queued);
        return true;
      }
    }
    return false;
  }

  /**
   * Removes and releases every {@link AvailableCommandsPacket} currently queued. Called before a
   * newer command tree is enqueued so that at most one — always the latest — is ever flushed to the
   * client.
   */
  private void releaseQueuedCommandTrees() {
    Iterator<MinecraftPacket> iterator = this.queue.iterator();
    while (iterator.hasNext()) {
      MinecraftPacket queued = iterator.next();
      if (queued instanceof AvailableCommandsPacket) {
        iterator.remove();
        ReferenceCountUtil.release(queued);
      }
    }
  }

  @Override
  public void channelInactive(@NotNull ChannelHandlerContext ctx) throws Exception {
    this.releaseQueue(ctx, false);

    super.channelInactive(ctx);
  }

  @Override
  public void handlerRemoved(ChannelHandlerContext ctx) {
    this.releaseQueue(ctx, ctx.channel().isActive());
  }

  private void releaseQueue(ChannelHandlerContext ctx, boolean active) {
    // Send out all the queued packets
    MinecraftPacket packet;
    int flushed = 0;
    while ((packet = this.queue.poll()) != null) {
      if (active) {
        ctx.write(packet, ctx.voidPromise());
        flushed++;
      } else {
        ReferenceCountUtil.release(packet);
      }
    }

    if (active) {
      if (isConduitPacketQueueEnabled()) {
        Conduit.get().getDiagnostics().recordPacketQueueFlush("clientbound", flushed);
      }
      ctx.flush();
    }
  }

  private boolean isConduitPacketQueueEnabled() {
    try {
      return Conduit.get().getConfig().isPacketQueueOptEnabled();
    } catch (IllegalStateException ignored) {
      return false;
    }
  }
}
