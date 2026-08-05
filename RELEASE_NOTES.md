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
