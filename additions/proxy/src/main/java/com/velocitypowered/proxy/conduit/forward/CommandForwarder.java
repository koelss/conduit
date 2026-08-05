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

package com.velocitypowered.proxy.conduit.forward;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Executes commands that backend servers forward to the proxy over a plugin-messaging channel.
 *
 * <p>This is a native, opt-in re-implementation of the proxy half of the
 * <a href="https://github.com/ItsTauTvyDas/VelocityCommandForward">VelocityCommandForward</a>
 * plugin, so operators no longer have to install a separate Velocity plugin for it. The backend
 * (Paper/Spigot) half of that plugin is unchanged and still required — Conduit only replaces the
 * proxy-side listener.
 *
 * <h3>Wire format</h3>
 * The message payload, written by the backend plugin with Guava's {@code ByteArrayDataOutput}, is:
 * <ol>
 *   <li>{@code UTF} — the sender UUID, or the empty string for a console-originated command;</li>
 *   <li>{@code UTF} — the command line to execute (without a leading slash);</li>
 *   <li>{@code byte} — flag bits; bit {@code 0x01} marks the command as "filtered" (silent);</li>
 *   <li>{@code UTF} — a human-readable log line the backend supplies.</li>
 * </ol>
 * This layout is kept byte-for-byte compatible with the upstream plugin so existing backend
 * installations keep working after switching to Conduit's built-in forwarder.
 *
 * <h3>Execution model</h3>
 * <ul>
 *   <li>An empty UUID runs the command as the {@linkplain ProxyServer#getConsoleCommandSource()
 *       proxy console}.</li>
 *   <li>A non-empty UUID runs the command as that player, if they are still online.</li>
 * </ul>
 * Commands are dispatched through the command manager's {@code executeAsync} so the netty thread
 * is never blocked.
 *
 * <h3>Security</h3>
 * Because a forwarded command executes with the authority of the console or a player, only
 * messages that genuinely originate from a backend server ({@link ServerConnection}) are honoured.
 * When {@code require-permission} is enabled, player-context commands additionally require the
 * player to hold {@link #EXECUTE_PERMISSION}; console-context commands are always allowed because
 * they can only be produced by a trusted backend console.
 *
 * <p>The singleton {@link #DISABLED} instance registers no listener and forwards nothing.
 */
public class CommandForwarder {

  /**
   * Permission a player must hold for a player-context forwarded command when {@code
   * require-permission} is enabled. Note: Velocity has no permission registry, so this node will not
   * autocomplete in the LuckPerms web editor — grant it explicitly with
   * {@code /lp user <name> permission set conduit.forward.execute true} (or to a group).
   */
  public static final String EXECUTE_PERMISSION = "conduit.forward.execute";

  /** Filter flag: the backend marked this command as silent (no log / feedback). */
  private static final int FLAG_FILTERED = 0x01;

  /**
   * Sentinel instance used when command forwarding is disabled. Registers nothing and ignores
   * every plugin message.
   */
  public static final CommandForwarder DISABLED =
      new CommandForwarder("velocity_command_forward:main", false, true) {
        @Override
        public void register(Object plugin, ProxyServer proxy) {
          // no-op
        }

        @Override
        public void onPluginMessage(PluginMessageEvent event) {
          // no-op
        }
      };

  private static final Logger logger = LogManager.getLogger(CommandForwarder.class);

  private final String channelName;
  private final ChannelIdentifier channel;
  private final boolean requirePermission;
  private final boolean logForwardedCommands;
  private volatile ProxyServer proxy;

  /**
   * Constructs a {@code CommandForwarder}.
   *
   * @param channelName          the plugin-messaging channel the backend sends on
   *                             ({@code namespace:path}); must match the backend plugin
   * @param requirePermission    whether player-context commands require {@link #EXECUTE_PERMISSION}
   * @param logForwardedCommands whether the backend-supplied log line is echoed to the console
   */
  public CommandForwarder(String channelName, boolean requirePermission,
      boolean logForwardedCommands) {
    this.channelName = channelName;
    this.channel = parseChannel(channelName);
    this.requirePermission = requirePermission;
    this.logForwardedCommands = logForwardedCommands;
  }

  private static ChannelIdentifier parseChannel(String id) {
    int colon = id == null ? -1 : id.indexOf(':');
    if (colon <= 0 || colon == id.length() - 1) {
      throw new IllegalArgumentException(
          "conduit.toml: forwarding.channel must be 'namespace:path', got '" + id + "'");
    }
    return MinecraftChannelIdentifier.create(id.substring(0, colon), id.substring(colon + 1));
  }

  /** Registers the forwarding channel and this listener on the given proxy. */
  public void register(Object plugin, ProxyServer proxy) {
    this.proxy = proxy;
    proxy.getChannelRegistrar().register(channel);
    proxy.getEventManager().register(plugin, this);
    logger.info("[Conduit] Command forwarding enabled on channel '{}' (require-permission={}).",
        channelName, requirePermission);
  }

  /**
   * Handles a forwarded-command message. Runs at {@link PostOrder#EARLY} and marks the event
   * {@link PluginMessageEvent.ForwardResult#handled() handled} so the payload is consumed at the
   * proxy instead of being relayed onward, exactly as the upstream plugin does.
   */
  @Subscribe(order = PostOrder.EARLY)
  public void onPluginMessage(PluginMessageEvent event) {
    if (!channel.equals(event.getIdentifier())) {
      return;
    }
    // The command is executed here; never relay the raw payload to another backend.
    event.setResult(PluginMessageEvent.ForwardResult.handled());

    // Only genuine backend connections may forward commands — never a player's own channel data.
    if (!(event.getSource() instanceof ServerConnection source)) {
      return;
    }
    ProxyServer proxy = this.proxy;
    if (proxy == null) {
      return;
    }

    final String uuidRaw;
    final String command;
    final int flags;
    final String log;
    try {
      ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
      uuidRaw = in.readUTF();
      command = in.readUTF();
      flags = in.readByte();
      log = in.readUTF();
    } catch (IllegalStateException | IndexOutOfBoundsException malformed) {
      logger.warn("[Conduit] Discarded a malformed forwarded-command message from backend '{}'.",
          source.getServerInfo().getName());
      return;
    }

    if (command.isBlank()) {
      return;
    }
    boolean filtered = (flags & FLAG_FILTERED) != 0;

    if (uuidRaw.isEmpty()) {
      maybeLog(filtered, log);
      proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), command);
    } else {
      executeAsPlayer(proxy, uuidRaw, command, filtered, log);
    }
  }

  private void executeAsPlayer(ProxyServer proxy, String uuidRaw, String command,
      boolean filtered, String log) {
    final UUID uuid;
    try {
      uuid = UUID.fromString(uuidRaw);
    } catch (IllegalArgumentException badUuid) {
      logger.warn("[Conduit] Ignored forwarded command with an invalid sender UUID '{}'.", uuidRaw);
      return;
    }
    proxy.getPlayer(uuid).ifPresent(player -> {
      // Gate player-context forwarded commands: without this, any player who can invoke the backend
      // /proxyexec could run proxy commands, and relying on each command's own permission is not
      // enough (e.g. /sparkv). The node is a plain Velocity permission — LuckPerms will not
      // autocomplete it in the web editor (Velocity has no permission registry), but it can still be
      // granted explicitly, so the block message spells out exactly how.
      if (requirePermission && !player.hasPermission(EXECUTE_PERMISSION)) {
        logger.warn("[Conduit] Blocked forwarded command '/{}' from {} — missing permission '{}'. "
                + "Grant it with '/lp user {} permission set {} true' (or to a group), or set "
                + "[forwarding] require-permission = false in conduit.toml to disable this gate.",
            command, player.getUsername(), EXECUTE_PERMISSION, player.getUsername(),
            EXECUTE_PERMISSION);
        return;
      }
      maybeLog(filtered, log);
      proxy.getCommandManager().executeAsync(player, command);
    });
  }

  private void maybeLog(boolean filtered, String log) {
    if (!filtered && logForwardedCommands && log != null && !log.isEmpty()) {
      logger.info(log);
    }
  }

  /** Returns the channel id this forwarder listens on. */
  public String getChannelName() {
    return channelName;
  }

  /** Returns whether player-context forwarded commands require {@link #EXECUTE_PERMISSION}. */
  public boolean isRequirePermission() {
    return requirePermission;
  }

  /** Returns whether backend-supplied log lines are echoed to the console. */
  public boolean isLogForwardedCommands() {
    return logForwardedCommands;
  }
}
