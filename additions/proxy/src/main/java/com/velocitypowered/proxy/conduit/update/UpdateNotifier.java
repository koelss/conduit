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

package com.velocitypowered.proxy.conduit.update;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Notifies staff with the {@link #PERMISSION} permission, when they join, that a newer Conduit
 * release is available.
 *
 * <p>Only players who explicitly hold {@code conduit.update.notify} are told, so ordinary players
 * never see the message. The notice is derived entirely from {@link UpdateChecker}'s cached
 * result, so joining never triggers a network call.</p>
 */
public final class UpdateNotifier {

  /** Permission required to receive the in-game update notification. */
  public static final String PERMISSION = "conduit.update.notify";

  private final UpdateChecker updateChecker;

  public UpdateNotifier(UpdateChecker updateChecker) {
    this.updateChecker = updateChecker;
  }

  /** Registers this listener on the proxy event manager. */
  public void register(Object plugin, ProxyServer proxy) {
    proxy.getEventManager().register(plugin, this);
  }

  /**
   * Sends the update notice to eligible players once they have fully logged in.
   *
   * @param event the post-login event
   */
  @Subscribe
  public void onPostLogin(PostLoginEvent event) {
    // Cheap, O(1) cached read first: when there is no update available — the overwhelmingly common
    // case — return before doing a permission lookup, so ordinary logins pay nothing.
    UpdateStatus status = updateChecker.getStatus();
    if (!status.hasUpdate()) {
      return;
    }
    Player player = event.getPlayer();
    if (!player.hasPermission(PERMISSION)) {
      return;
    }
    player.sendMessage(buildMessage(status));
  }

  /** Builds the clickable update notice component. Package-visible for testing. */
  static Component buildMessage(UpdateStatus status) {
    String url = status.latestUrl() != null ? status.latestUrl()
        : "https://github.com/tame-gg/conduit/releases/latest";

    Component link = Component.text(status.latestVersion(), NamedTextColor.AQUA)
        .decorate(TextDecoration.UNDERLINED)
        .clickEvent(ClickEvent.openUrl(url))
        .hoverEvent(HoverEvent.showText(
            Component.text("Open the latest release on GitHub", NamedTextColor.GRAY)));

    return Component.text()
        .append(Component.text("[Conduit] ", NamedTextColor.GOLD))
        .append(Component.text("A new release is available: ", NamedTextColor.YELLOW))
        .append(link)
        .append(Component.text(". You are running ", NamedTextColor.YELLOW))
        .append(Component.text(status.currentVersion(), NamedTextColor.WHITE))
        .append(Component.text(" and are ", NamedTextColor.YELLOW))
        .append(Component.text(status.releasesBehind() + " release(s)", NamedTextColor.GOLD))
        .append(Component.text(" behind.", NamedTextColor.YELLOW))
        .build();
  }
}
