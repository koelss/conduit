# Conduit 1.7.1

## Fixed

- **Seamless switches no longer strand players who move too early.** Going back to the hub (or any
  seamless switch) briefly held the player "stuck in place until you reconnect" if you moved before
  the destination server finished streaming you in — the movement raced ahead of the not-yet-loaded
  world. The switch now applies a short **settle delay** during which the player's own input is held
  and buffered while the destination loads them in, then released. This removes the desync and, as a
  bonus, stops the switch from feeling jarringly instantaneous.

- **Copyright line updates on existing installs.** Velocity copies `messages.properties` into an
  on-disk `lang/` folder and only ever *adds* missing keys, so the `/velocity` copyright stayed frozen
  at the old `Copyright 2018-2026 …` even after the jar was updated. Conduit now force-refreshes that
  one branding line from the shipped default, so existing installs show `Copyright 2026 tame.gg`.
- **Old `conduit.toml` files pick up the condensed `[advanced]` comments.** The config migrator now
  re-syncs the comment wording above the seamless-switch options to the shipped (shortened) text,
  while preserving every value exactly. No other key is touched.
- **Stale title/subtitle no longer sticks across a seamless switch.** A title banner set by the
  previous server (e.g. a hub's `"<server>" server` banner) stayed on screen after a same-dimension
  seamless switch, so it looked like every server had the same name. The title/subtitle/action bar
  is now reset as part of the seamless HUD cleanup, matching the non-seamless switch path.
- **Seamless-switch teleport sound now actually plays.** The cue was emitted against the player's
  previous server entity (the connected-server reassignment happens after the switch packets are
  built), so the client heard nothing. It is now sent directly against the player's own client-side
  entity id, so the ender pearl teleport sound plays on every seamless switch.

## Added

- **Configurable seamless-switch settle delay.** New `conduit.toml` →
  `[advanced] seamless-switch-settle-ms` (default **250**, range **0–5000**). Set to `0` to restore
  the previous instantaneous behaviour. This directly softens the "too quick" seamless switch.
- **Ender pearl teleport sound on server switch.** New `conduit.toml` →
  `[advanced] seamless-switch-sound-enabled` (default **true**), `seamless-switch-sound` (default
  `minecraft:entity.enderman.teleport` — the ender pearl teleport sound), `seamless-switch-sound-volume`,
  and `seamless-switch-sound-pitch`. Plays a short cue to the player whenever they are seamlessly
  moved between servers. All of these settings are live-reloadable.

---

# Conduit 1.7.0

## Added

- **Experimental seamless server switches** for Minecraft 1.20.2+ clients, controlled by
  `conduit.toml` → `[advanced] seamless-server-switches` (default **false**). When enabled, eligible
  same-dimension switches between homogeneous backends skip the client configuration screen and keep
  the world loaded. Cross-dimension switches, pre-1.20.2 clients, and Legacy Forge still use the
  existing switch path.

## Fixed

- **Seamless switches now clear leftover scoreboards.** A destination that recreates a common
  objective such as `sidebar` no longer crashes 1.20.2+ clients with
  `An objective with the name 'sidebar' already exists!`. The proxy tracks backend scoreboard
  objectives and teams and removes them from the client before the new server's packets arrive.

- **Seamless switches now apply destination player state.** Hub leftover **boss bars** (TAB RAM/TPS
  bars, including bars that only receive later UPDATE packets), **gamemode**, **operator permission
  level** (F3+F4), and **status effects** are cleared or rewritten from the destination. Packets that
  target the local player (damage, hurt animation, entity sounds, knockback, metadata) are rewritten
  to the client's original entity ID so hurt sounds play. The vanilla world-generation overlay is
  not forwarded after a seamless switch. Leftover boss bars are tracked through Join Game replay and
  removed again after a short delay; air/swim metadata is reset. Seamless joins no longer stall on
  Velocity `ServerConnectedEvent` (that wait dropped movement for about a second). Destination world
  packets follow HUD clear by 300ms, and 1.21.4+ backends are sent `player_loaded` so they accept
  input immediately. Cross-dimension switches (such as Overworld → End limbo) still send Join Game
  and Respawn, but leftover boss bars are stripped first because the client stayed in Play.

### Credits

- **Seamless server switching:** Based on the seamless server switching patch by [@ohemilyy](https://github.com/ohemilyy), originally provided in `b5a97c65eea43a1a3d5e21589b67e2888729e1e4` (`feat: seamless server switches`).

# Conduit 1.6.3

## Fixed

- **LuckPerms now autocompletes Conduit permissions (including maintenance bypass).** Velocity has
  no permission registry, so nodes that Conduit only checks rarely — especially
  `conduit.maintenance.bypass`, which is evaluated only while maintenance mode is active — never
  appeared in `/lpv` tab-complete or the LuckPerms web editor. Conduit now seeds its known
  `conduit.*` nodes into LuckPerms' suggestion tree at startup when LuckPerms is present
  (`conduit.admin`, `conduit.modlist`, `conduit.maintenance.bypass`, `conduit.channelguard.bypass`,
  `conduit.update.notify`, `conduit.forward.execute`).

## Fixed (build)

- **Refreshed the bundled LuckPerms Velocity jar to `5.5.71`.** The LuckPerms download server prunes
  old build numbers, so the previously pinned build `1643` (`5.5.55`) started returning HTTP 404. That
  broke the `:velocity-proxy:downloadBundledLuckPerms` task and therefore the entire build and CI. The
  pinned URL and SHA-256 now point at the current build `1658` (`5.5.71`), sourced from the LuckPerms
  metadata API.

> **Note:** because LuckPerms only retains recent builds, a pinned build can disappear again in the
> future. If `downloadBundledLuckPerms` fails with a 404, refresh `conduit.luckperms.velocity.url` /
> `.sha256` (and `.version`) in `gradle.properties` from `downloads.velocity` at
> <https://metadata.luckperms.net/data/all>.

# Conduit 1.6.2

## Fixed (security)

- **Restored the command-forwarding permission gate.** 1.6.1-hotfix1 removed the
  `conduit.forward.execute` check, which meant any player who could invoke the backend `/proxyexec`
  could run proxy commands (e.g. `/sparkv`) even with no permissions. The gate is back: with
  `[forwarding] require-permission = true`, a forwarded player command runs only if the player holds
  `conduit.forward.execute`. Forgery is still prevented (the message must come from a genuine backend
  connection), and console-originated commands are always allowed.

## Changed

- When a forwarded command is blocked, the log now spells out exactly how to authorise the player —
  e.g. `/lp user <name> permission set conduit.forward.execute true` — because Velocity has no
  permission registry, so the node does not autocomplete in the LuckPerms web editor. It must be
  granted explicitly (to a user or a staff group).
- Supersedes 1.6.1 and 1.6.1-hotfix1; version `1.6.2` sorts above both, so the update checker no
  longer reports a false "behind".

> **Known issue (upstream):** the `libdeflate` base bundles **Adventure 5**, so plugins compiled
> against Adventure 4 (e.g. LibertyBans 1.1.4) fail to load with
> `NoSuchMethodError: TextComponent.ofChildren`. This predates 1.6.x and is tracked separately.

# Conduit 1.6.1-hotfix1

## Fixed

- **Forwarded player commands are no longer blocked by an ungrantable permission.** With
  `[forwarding] require-permission = true`, Conduit gated forwarded player commands on a synthetic
  `conduit.forward.execute` node. Because Velocity has no permission registry, that node never
  appeared in the LuckPerms web editor and could not be granted there, so legitimate players were
  blocked with `Blocked forwarded command from … — missing permission 'conduit.forward.execute'`.

  Forwarded player commands now run **as the player**, so the command manager authorises them against
  each command's **own** permission — a real, editor-visible node — exactly like normal command
  execution. Forgery is still prevented: the message must originate from a genuine backend
  `ServerConnection`, never a client.

## Changed

- `[forwarding] require-permission` is deprecated and no longer enforced (kept so existing
  `conduit.toml` files still parse; a one-line note is logged if it is set true). `EXECUTE_PERMISSION`
  / `conduit.forward.execute` is retained only for source compatibility.

This hotfix supersedes 1.6.1 and includes all of its fixes below.

# Conduit 1.6.1

## Fixed

- **Reported version now matches the release.** The internal version, jar manifest
  (`Implementation-Version` / `Conduit-Version`), and update checker all read a single build-time
  value, so a `conduit-1.6.1.jar` reports `1.6.1` everywhere. Previously the version metadata was
  written only by the setup script and could go stale (a 1.6.0 jar still reported 1.5.2), which made
  the update checker wrongly claim you were behind.
- **Upgrades never reset your configuration — including LuckPerms.** The `conduit.toml` migrator is
  now strictly **append-only** and edits the file text in place: existing sections, keys, values,
  comments, ordering, and blank lines are preserved byte-for-byte, and only genuinely missing options
  are inserted. Previously the migrator round-tripped the whole file through a TOML writer, which
  reordered/reformatted it and made an operator-set value (e.g. `[luckperms] bundle-enabled = false`)
  look like it had been reset.
- Fixed the migrator failing to detect keys in `conduit.toml` files with Windows (CRLF) line
  endings; line-ending style is now preserved on write.

## Changed

- `conduit-build.properties` (version, build time, git hash) is generated by the Gradle build from
  `conduit.version`, making version reporting authoritative and automatic.
- The manifest now carries the real Conduit version instead of the upstream `-SNAPSHOT` coordinate.
- Rewrote `ConduitConfigMigrator` as an append-only, formatting-preserving text migrator:
  - New sections are appended verbatim from the shipped defaults (header + comments + defaults).
  - New keys are inserted under their existing section header, copied from the shipped defaults.
  - Renamed options carry the operator's value to the new key (via the `RENAMES` table); removed
    options are simply ignored by the loader — no manual cleanup, no reset.
  - Added regression tests for verbatim preservation, LuckPerms/Spark value retention, and CRLF files.

## Migration behaviour

Dropping in a newer Conduit jar now migrates `conduit.toml` automatically on startup, regardless of
the version upgraded from: new options appear with documented defaults, existing settings are left
exactly as written, and users never need to delete or recreate the file.

# Conduit 1.6.0

## Fixed

- **Client-side command synchronization / tab-completion is fixed.** Since 1.4.0, commands sent
  through the proxy rendered red ("unknown"), would not autofill, and produced no Brigadier
  suggestions — even though they still executed on the backend. The command tree is now serialized
  and applied by the client correctly again.
- Fixed a secondary latent defect where a *stale* Brigadier command tree could be flushed to the
  client after a server switch; queued command trees are now collapsed to the newest one.

## Root cause (the tab-completion regression)

Conduit overlays `proxy/build.gradle.kts`, and that overlay carried a shadow-jar exclude,
`exclude("it/unimi/dsi/fastutil/objects/*ObjectArray*")`, that upstream Velocity-CTD removed in
commit `082dd9fb` ("fix: fix tab completion", #1028). When the 1.4.0 rebrand re-synced Conduit onto a
newer upstream base, that base began using fastutil's `ObjectArrayList` in the command-graph /
tab-completion path — but Conduit's overlay was still stripping `ObjectArrayList` out of the release
jar. The `AvailableCommandsPacket` therefore failed to encode (a `NoClassDefFoundError` deep in the
packet encoder, with no error surfaced in the console), so the client silently received an unusable
command tree. Version 1.3.5 was unaffected because its older upstream base did not yet reference that
class, which is why the break appeared to start at 1.4.0.

The exclude has been removed from the overlay to match upstream #1028; `ObjectArrayList` is now
present in the jar and the command tree encodes correctly.

## Changed

- Removed the stale `*ObjectArray*` shadow-jar exclude from the `proxy/build.gradle.kts` overlay.
- Rebuilt the CONFIG-state outbound packet queue's command-tree handling: queued
  `AvailableCommandsPacket`s are collapsed to the single most recent tree, so FIFO flush order can
  never surface a stale tree, and command trees no longer count against the queue depth cap. Covered
  by a new regression test (`collapsesQueuedCommandTreesToTheNewest`).
- The release artifact is now named `conduit-<version>.jar` instead of the upstream
  `velocity-proxy-<...>-all.jar` coordinate.

## Verified

- Command autofill / tab-completion confirmed working again on Minecraft 26.2 through the proxy.
- Bundled Spark, native LuckPerms (plus the LuckPerms permission-integration resolver), and the
  optional VelocityCommandForward integration remain embedded in the jar.
- `max-known-packs` large-modpack support wired end-to-end (default 1024, `conduit.toml`
  configurable, `-Dvelocity.max-known-packs` JVM override precedence) — no 64-entry cap remains.
- Full Conduit test suite green on JDK 25.

# Conduit 1.5.2

## Fixed

- Fixed CI formatting failure by adding the required project license header to the command-tree queue regression test.

## Technical

- No production behavior changed from 1.5.1; this patch release makes the verified regression test pass repository formatting checks.

# Conduit 1.5.1

## Fixed

- Fixed command synchronization through the proxy causing valid backend commands to appear red or unknown in the client.
- Preserved the `AvailableCommandsPacket` Brigadier tree while the client connection transitions from CONFIG to PLAY.
- Kept command execution, permissions, aliases, forwarding, and tab completion behavior unchanged.

## Changed

- The bounded packet-queue optimization now evicts ordinary packets before evicting a command tree.
- Added a regression test covering command-tree preservation under queue pressure.

## Technical

Conduit’s queue optimization previously dropped the oldest queued PLAY packet whenever the queue
reached its cap. `AvailableCommandsPacket` is a PLAY packet, so a busy server switch could evict
the complete serialized Brigadier tree before it was flushed to the client. The client then had no
matching command nodes and rendered commands red, even though the backend dispatcher could execute
them. The outbound queue now identifies `AvailableCommandsPacket` and evicts the oldest ordinary
packet instead; redundant command-tree updates are dropped only when no ordinary packet is
available. The change is confined to `PlayPacketQueueOutboundHandler`.
