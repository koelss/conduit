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

package com.velocitypowered.proxy.conduit.version;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent.PreLoginComponentResult;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Enforces the operator's advertised Minecraft version range (the {@code [versions]} section of
 * {@code conduit.toml}).
 *
 * <h3>Server list</h3>
 * A client inside the range sees the network exactly as before: the normal MOTD, ping bars, and
 * player count. A client outside the range is answered with a ping whose {@code version.protocol}
 * is one it cannot be speaking, which is the vanilla signal for "incompatible": the client replaces
 * the player count with the ping's {@code version.name} and draws a red cross instead of the bars.
 * That name is the configured label — {@code Conduit 1.21.11} by default — so the entry states
 * plainly which versions the network runs, rather than implying it is offline.
 *
 * <p>The rewrite runs at {@link PostOrder#LAST}, after
 * {@link com.velocitypowered.proxy.conduit.motd.MotdCache} has stored the response at
 * {@link PostOrder#LATE}. The cache therefore only ever holds the unmodified MOTD, and the
 * per-client version swap is applied on top of a hit — two clients on different versions behind one
 * IP each get the right answer.
 *
 * <h3>Joining</h3>
 * A login attempt from outside the range is denied at {@link PreLoginEvent} — before any
 * authentication or backend work — with the configured message, which names the accepted versions.
 *
 * <p>The policy is held in a volatile field so {@code /conduit reload} can swap it without a
 * restart.
 */
public class VersionGate {

  private static final Logger logger = LogManager.getLogger(VersionGate.class);

  private volatile VersionPolicy policy;

  /**
   * Constructs a gate enforcing the given policy.
   *
   * @param policy the configured range; {@link VersionPolicy#DISABLED} enforces nothing
   */
  public VersionGate(VersionPolicy policy) {
    this.policy = policy == null ? VersionPolicy.DISABLED : policy;
  }

  /** Registers the ping and pre-login listeners on the proxy. */
  public void register(Object plugin, ProxyServer proxy) {
    proxy.getEventManager().register(plugin, this);
    if (policy.isEnabled()) {
      logger.info("[Conduit] VersionGate registered (allowing {}).", policy.getVersionsLabel());
    } else {
      logger.info("[Conduit] VersionGate registered (no version restriction).");
    }
  }

  /** Returns the policy currently being enforced. */
  public VersionPolicy getPolicy() {
    return policy;
  }

  /** Swaps in a freshly loaded policy; takes effect on the next ping or login. */
  public void setPolicy(VersionPolicy policy) {
    this.policy = policy == null ? VersionPolicy.DISABLED : policy;
  }

  /** Marks the server list entry as version-incompatible for clients outside the range. */
  @Subscribe(order = PostOrder.LAST)
  public void onProxyPing(ProxyPingEvent event) {
    VersionPolicy current = policy;
    if (!current.isEnabled()) {
      return;
    }
    ProtocolVersion clientVersion = event.getConnection().getProtocolVersion();
    if (current.allows(clientVersion)) {
      return;
    }
    ServerPing ping = event.getPing();
    event.setPing(ping.asBuilder()
        .version(new ServerPing.Version(current.advertisedProtocol(), current.pingVersionName()))
        .build());
  }

  /**
   * Denies logins from clients outside the range.
   *
   * <p>Runs at {@link PostOrder#FIRST} so the connection is dropped before authentication and other
   * per-login setup work that would only be discarded.
   */
  @Subscribe(order = PostOrder.FIRST)
  public void onPreLogin(PreLoginEvent event) {
    VersionPolicy current = policy;
    if (!current.isEnabled()) {
      return;
    }
    ProtocolVersion clientVersion = event.getConnection().getProtocolVersion();
    if (current.allows(clientVersion)) {
      return;
    }
    event.setResult(PreLoginComponentResult.denied(
        MiniMessage.miniMessage().deserialize(current.renderKickMessage())));
  }
}
