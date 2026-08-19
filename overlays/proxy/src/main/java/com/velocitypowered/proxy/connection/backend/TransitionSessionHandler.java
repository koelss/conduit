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

package com.velocitypowered.proxy.connection.backend;

import static com.velocitypowered.proxy.connection.backend.BackendConnectionPhases.IN_TRANSITION;
import static com.velocitypowered.proxy.connection.forge.legacy.LegacyForgeHandshakeBackendPhase.HELLO;

import com.velocityctd.proxy.queue.VelocityQueue;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.ConnectionTypes;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.connection.client.ClientPlaySessionHandler;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.connection.util.ConnectionMessages;
import com.velocitypowered.proxy.connection.util.ConnectionRequestResults;
import com.velocitypowered.proxy.connection.util.ConnectionRequestResults.Impl;
import com.velocitypowered.proxy.network.Connections;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.DisconnectPacket;
import com.velocitypowered.proxy.protocol.packet.JoinGamePacket;
import com.velocitypowered.proxy.protocol.packet.KeepAlivePacket;
import com.velocitypowered.proxy.protocol.packet.PluginMessagePacket;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.ReferenceCountUtil;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A special session handler that catches "last minute" disconnects.
 */
public class TransitionSessionHandler implements MinecraftSessionHandler {

  private static final Logger LOGGER = LogManager.getLogger(TransitionSessionHandler.class);

  private final VelocityServer server;

  private final VelocityServerConnection serverConn;

  private final CompletableFuture<Impl> resultFuture;

  private final BungeeCordMessageResponder bungeecordMessageResponder;

  // Backend packets arriving while JoinGame is processed (async) are held and replayed after it, so
  // the client never gets world data before JoinGame. Empty in the normal flow (autoReading off).
  private boolean joinGameProcessing;
  private final Queue<MinecraftPacket> deferredPackets = new ArrayDeque<>();

  /**
   * Creates the new transition handler.
   *
   * @param server       the Velocity server instance
   * @param serverConn   the server connection
   * @param resultFuture the result future
   */
  TransitionSessionHandler(VelocityServer server,
                           VelocityServerConnection serverConn,
                           CompletableFuture<Impl> resultFuture) {
    this.server = server;
    this.serverConn = serverConn;
    this.resultFuture = resultFuture;
    this.bungeecordMessageResponder = new BungeeCordMessageResponder(server, serverConn.getPlayer());
  }

  @Override
  public boolean beforeHandle() {
    if (!serverConn.isActive()) {
      // Obsolete connection
      serverConn.disconnect();
      return true;
    }

    return false;
  }

  @Override
  public boolean handle(KeepAlivePacket packet) {
    serverConn.ensureConnected().write(packet);
    return true;
  }

  @Override
  public boolean handle(JoinGamePacket packet) {
    // Hold packets that follow JoinGame until it's processed, then replay in order.
    this.joinGameProcessing = true;

    final MinecraftConnection smc = serverConn.ensureConnected();
    final VelocityRegisteredServer previousServer = serverConn.getPreviousServer().orElse(null);
    final ConnectedPlayer player = serverConn.getPlayer();
    final VelocityServerConnection existingConnection = player.getConnectedServer();

    if (existingConnection != null) {
      // Shut down the existing server connection.
      player.setConnectedServer(null);
      existingConnection.disconnect();

      // Send keep alive to try to avoid timeouts
      player.sendKeepAlive();
    }

    // Reset Tablist header and footer to prevent desync
    player.clearPlayerListHeaderAndFooter();

    // Override online mode
    packet.setOnlineMode(player.isOnlineMode());

    ClientPlaySessionHandler playHandler;
    if (player.getConnection().getActiveSessionHandler() instanceof ClientPlaySessionHandler sessionHandler) {
      playHandler = sessionHandler;
    } else {
      playHandler = new ClientPlaySessionHandler(server, player);
      player.getConnection().setActiveSessionHandler(StateRegistry.PLAY, playHandler);
    }
    final boolean seamless = playHandler.canDoSeamlessPlaySwitch(packet, serverConn);

    Runnable applyJoin = () -> {
      if (!serverConn.isActive()) {
        serverConn.disconnect();
        return;
      }

      playHandler.handleBackendJoinGame(packet, serverConn);

      smc.setActiveSessionHandler(StateRegistry.PLAY, new BackendPlaySessionHandler(server, serverConn));

      if (smc.getProtocolVersion().noLessThan(ProtocolVersion.MINECRAFT_1_20_2)) {
        smc.addSessionHandler(StateRegistry.CONFIG,
            new ConfigSessionHandler(server, serverConn, resultFuture));
      }

      final var backendPipeline = smc.getChannel().pipeline();
      if (backendPipeline.context(Connections.READ_TIMEOUT) != null) {
        backendPipeline.replace(Connections.READ_TIMEOUT, Connections.READ_TIMEOUT,
            new ReadTimeoutHandler(server.getConfiguration().getReadTimeout(), TimeUnit.MILLISECONDS));
      }

      serverConn.getPlayer().setConnectedServer(serverConn);
      if (seamless) {
        smc.eventLoop().schedule(() -> {
          if (!serverConn.isActive()) {
            return;
          }
          flushDeferredPackets();
          smc.setAutoReading(true);
        }, 300, TimeUnit.MILLISECONDS);
      } else {
        flushDeferredPackets();
        smc.setAutoReading(true);
      }

      if (smc.getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_20_2)
          && player.getClientSettingsPacket() != null) {
        serverConn.ensureConnected().write(player.getClientSettingsPacket());
      }
    };

    Runnable afterPlugins = () -> {
      if (!serverConn.isActive()) {
        return;
      }
      server.getClusterPlayerService().onPlayerSwitchServer(
          player,
          previousServer != null ? previousServer.getServerInfo().getName() : null,
          serverConn.getServerInfo().getName());

      if (this.server.isQueueEnabled()) {
        VelocityQueue<?> queue = this.server.getQueueManager().getQueue(serverConn.getServer()
                .getServerInfo().getName());
        queue.dequeue(player.getUniqueId());
      }

      server.getEventManager().fireAndForget(new ServerPostConnectEvent(player, previousServer));
      resultFuture.complete(ConnectionRequestResults.successful(serverConn.getServer()));
    };

    if (seamless) {
      try {
        applyJoin.run();
      } catch (Throwable exc) {
        failJoin(player, exc);
        return true;
      }
      server.getEventManager()
          .fire(new ServerConnectedEvent(player, serverConn.getServer(), previousServer))
          .thenRunAsync(afterPlugins, smc.eventLoop())
          .exceptionally(exc -> {
            failJoin(player, exc);
            return null;
          });
    } else {
      smc.setAutoReading(false);
      server.getEventManager()
          .fire(new ServerConnectedEvent(player, serverConn.getServer(), previousServer))
          .thenRunAsync(() -> {
            applyJoin.run();
            afterPlugins.run();
          }, smc.eventLoop())
          .exceptionally(exc -> {
            failJoin(player, exc);
            return null;
          });
    }

    return true;
  }

  @Override
  public boolean handle(DisconnectPacket packet) {
    MinecraftConnection connection = serverConn.ensureConnected();
    serverConn.disconnect();

    // If we were in the middle of the Forge handshake, it is not safe to proceed. We must kick
    // the client.
    if (connection.getType() == ConnectionTypes.LEGACY_FORGE && !serverConn.getPhase().consideredComplete()) {
      resultFuture.complete(ConnectionRequestResults.forUnsafeDisconnect(packet, serverConn.getServer()));
    } else {
      resultFuture.complete(ConnectionRequestResults.forDisconnect(packet, serverConn.getServer()));
    }

    return true;
  }

  @Override
  public boolean handle(PluginMessagePacket packet) {
    // Hold plugin messages during JoinGame processing so they reach the client after it.
    if (joinGameProcessing) {
      ReferenceCountUtil.retain(packet);
      deferredPackets.add(packet);
      return true;
    }

    if (bungeecordMessageResponder.process(packet)) {
      return true;
    }

    // We always need to handle plugin messages, for Forge compatibility.
    if (serverConn.getPhase().handle(serverConn, serverConn.getPlayer(), packet)) {
      // Handled, but check the server connection phase.
      if (serverConn.getPhase() == HELLO) {
        VelocityServerConnection existingConnection = serverConn.getPlayer().getConnectedServer();
        if (existingConnection != null && existingConnection.getPhase() != IN_TRANSITION) {
          // Indicate that this connection is "in transition"
          existingConnection.setConnectionPhase(IN_TRANSITION);

          // Tell the player that we're leaving and we just aren't coming back.
          existingConnection.getPhase().onDepartForNewServer(existingConnection, serverConn.getPlayer());
        }
      }

      return true;
    }

    serverConn.getPlayer().getConnection().write(packet.retain());
    return true;
  }

  @Override
  public void handleGeneric(MinecraftPacket packet) {
    // Hold packets during JoinGame processing to replay in order; otherwise drop (the default).
    if (joinGameProcessing) {
      ReferenceCountUtil.retain(packet);
      deferredPackets.add(packet);
    }
  }

  private void flushDeferredPackets() {
    joinGameProcessing = false;
    if (deferredPackets.isEmpty()) {
      return;
    }

    final MinecraftSessionHandler handler = serverConn.ensureConnected().getActiveSessionHandler();
    MinecraftPacket packet;
    while ((packet = deferredPackets.poll()) != null) {
      if (handler != null && !packet.handle(handler)) {
        handler.handleGeneric(packet);
      }
    }
    serverConn.getPlayer().getConnection().flush();
  }

  private void releaseDeferredPackets() {
    joinGameProcessing = false;
    MinecraftPacket packet;
    while ((packet = deferredPackets.poll()) != null) {
      ReferenceCountUtil.release(packet);
    }
  }

  @Override
  public void disconnected() {
    releaseDeferredPackets();
    resultFuture.complete(ConnectionRequestResults.forDisconnect(
        ConnectionMessages.INTERNAL_SERVER_CONNECTION_ERROR, serverConn.getServer()));
  }

  private void failJoin(ConnectedPlayer player, Throwable exc) {
    LOGGER.error("Unable to switch to new server {} for {}",
        serverConn.getServerInfo().getName(),
        player.getUsername(), exc);
    releaseDeferredPackets();
    player.disconnect(ConnectionMessages.INTERNAL_SERVER_CONNECTION_ERROR);
    resultFuture.completeExceptionally(exc);
  }
}
