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
