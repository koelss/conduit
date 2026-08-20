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

package com.velocitypowered.proxy.connection.backend;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.connection.util.ConnectionMessages;
import com.velocitypowered.proxy.connection.util.ConnectionRequestResults;
import com.velocitypowered.proxy.connection.util.ConnectionRequestResults.Impl;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.DisconnectPacket;
import com.velocitypowered.proxy.protocol.packet.KeepAlivePacket;
import com.velocitypowered.proxy.protocol.packet.PluginMessagePacket;
import com.velocitypowered.proxy.protocol.packet.config.CodeOfConductAcceptPacket;
import com.velocitypowered.proxy.protocol.packet.config.CodeOfConductPacket;
import com.velocitypowered.proxy.protocol.packet.config.FinishedUpdatePacket;
import com.velocitypowered.proxy.protocol.packet.config.KnownPacksPacket;
import com.velocitypowered.proxy.protocol.util.PluginMessageUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Absorbs the backend configuration phase during a seamless server switch while the client
 * stays in the play state.
 *
 * @author Luna
 * @date 07/08/2026
 */
class SeamlessConfigSessionHandler implements MinecraftSessionHandler {

  private final VelocityServer server;
  private final VelocityServerConnection serverConn;
  private final CompletableFuture<Impl> resultFuture;

  SeamlessConfigSessionHandler(VelocityServer server, VelocityServerConnection serverConn,
                               CompletableFuture<Impl> resultFuture) {
    this.server = server;
    this.serverConn = serverConn;
    this.resultFuture = resultFuture;
  }

  @Override
  public boolean beforeHandle() {
    if (!serverConn.isActive()) {
      serverConn.disconnect();
      return true;
    }
    return false;
  }

  @Override
  public boolean handle(KnownPacksPacket packet) {
    final List<KnownPacksPacket.KnownPack> clientPacks = serverConn.getPlayer()
        .getClientKnownPacks();
    final List<KnownPacksPacket.KnownPack> reply = new ArrayList<>();
    if (clientPacks != null) {
      for (final KnownPacksPacket.KnownPack pack : packet.getPacks()) {
        if (clientPacks.contains(pack)) {
          reply.add(pack);
        }
      }
    }
    serverConn.ensureConnected().write(new KnownPacksPacket(reply));
    return true;
  }

  @Override
  public boolean handle(CodeOfConductPacket packet) {
    serverConn.ensureConnected().write(CodeOfConductAcceptPacket.INSTANCE);
    return true;
  }

  @Override
  public boolean handle(KeepAlivePacket packet) {
    serverConn.ensureConnected().write(packet);
    return true;
  }

  @Override
  public boolean handle(PluginMessagePacket packet) {
    // Since 1.20.2 the backend announces its server brand during the configuration phase. Because a
    // seamless switch absorbs that phase, the brand would otherwise never reach the client and the
    // F3 debug screen would keep showing the previous server's brand. Rewrite it (appending the
    // proxy brand, exactly as BackendPlaySessionHandler does in Play) and forward it to the client,
    // which is still in the Play state and accepts a minecraft:brand message there.
    if (PluginMessageUtil.isMcBrand(packet)) {
      final MinecraftConnection playerConnection = serverConn.getPlayer().getConnection();
      final PluginMessagePacket rewritten = PluginMessageUtil.rewriteMinecraftBrand(packet,
          server.getVersion(),
          playerConnection.getProtocolVersion(),
          server.getConfiguration().getServerBrand(),
          server.getConfiguration().getProxyBrandCustom(),
          server.getConfiguration().getBackendBrandCustom(),
          serverConn.getServer().getServerInfo().getName(),
          ProtocolVersion.getVersionByName(server.getConfiguration().getMinimumVersion())
              .getVersionIntroducedIn());
      playerConnection.write(rewritten);
    }
    // Any other config-phase plugin message (resource packs, cookies, …) is intentionally dropped
    // during a seamless switch, matching the rest of this handler.
    return true;
  }

  @Override
  public boolean handle(FinishedUpdatePacket packet) {
    final MinecraftConnection smc = serverConn.ensureConnected();
    smc.write(FinishedUpdatePacket.INSTANCE);
    smc.setActiveSessionHandler(StateRegistry.PLAY,
        new TransitionSessionHandler(server, serverConn, resultFuture));
    return true;
  }

  @Override
  public boolean handle(DisconnectPacket packet) {
    serverConn.disconnect();
    resultFuture.complete(ConnectionRequestResults.forDisconnect(packet, serverConn.getServer()));
    return true;
  }

  @Override
  public void disconnected() {
    resultFuture.complete(ConnectionRequestResults.forDisconnect(
        ConnectionMessages.INTERNAL_SERVER_CONNECTION_ERROR, serverConn.getServer()));
  }
}
