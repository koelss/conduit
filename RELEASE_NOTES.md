# Conduit 1.6.0

## Fixed

- Fixed a latent client-side command-synchronization defect where a *stale* Brigadier command tree
  could be flushed to the client after a server switch, leaving valid backend commands rendered
  red/"unknown". The previous fix protected the command tree from eviction but could still keep an
  older tree ahead of a newer one in the CONFIG-state queue.

## Changed

- Rebuilt the CONFIG-state outbound packet queue's command-tree handling: queued
  `AvailableCommandsPacket`s are now collapsed to the single most recent tree, so FIFO flush order
  can never surface a stale tree, and command trees no longer count against the queue depth cap.
- Added a regression test (`collapsesQueuedCommandTreesToTheNewest`) covering the stale-tree case in
  addition to the existing eviction-ordering test.

## Verified

- `max-known-packs` large-modpack support confirmed wired end-to-end (default 1024, `conduit.toml`
  configurable, `-Dvelocity.max-known-packs` JVM override precedence) — no 64-entry cap remains.
- Full Conduit test suite green on JDK 25.

## Technical

Only the latest serialized command tree is ever meaningful to the client. Rather than merely
avoiding eviction of `AvailableCommandsPacket`, `PlayPacketQueueOutboundHandler` now releases any
older queued command trees when a newer one is enqueued, guaranteeing the client ends the CONFIG→PLAY
transition on the correct tree. The change is confined to `PlayPacketQueueOutboundHandler`.

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
