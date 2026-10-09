# Phase 2: packet processing and connection timing

This document records the Phase 2 packet contracts. The later collision and
physics layer is documented in [PHASE3.md](PHASE3.md).

Phase 2 is implemented on top of the Phase 1 bootstrap/configuration foundation.
It still has **zero cheat checks and no enforcement**. Raw observations and health
signals must not be mistaken for authoritative geometry or proof of cheating.

## Execution and ownership

`PacketEventsEngine` owns only its MONITOR listener. `PacketEventsNormalizer`
reads selected wrappers inside the callback, where the event buffer is valid.
`ClientVersionProvider` maps PacketEvents enum versions to cached immutable
capabilities (ping/pong, teleport confirmations, modern inventory state IDs).
UNKNOWN and out-of-library-range sentinels stay unknown. No reflection or Bukkit
world/entity access is used in packet callbacks.

The transport-bound `PacketRouter` assigns each observation to a session's
`SerialPacketQueue`. Queue insertion establishes a sequence across inbound and
outbound producers. A fixed-size worker pool has a bounded executor queue; each
session has one consumer and a bounded packet queue. Processing occurs outside
the ingress lock. Each consumer yields after a configurable batch so one player
cannot monopolize a worker indefinitely. It never falls back to caller-thread
analysis when the executor rejects work.

`PacketProcessor`/`PacketListener` run on workers. `PlayerData` serializes state
updates and immutable snapshot reads. Loss notification uses an atomic epoch
without waiting for its state lock. `PlayerRegistry` owns join/quit membership;
network callbacks never create sessions. Reconnecting uses a new session and
exact User-object binding, so old transport callbacks cannot feed the new one.

Disconnect/disable clears queues and bounded histories, closes player state and
shuts down owned workers. An in-flight callback cannot re-open a closed session.
Close is idempotent; restarting a closed engine is rejected. The shared PacketEvents
plugin is never loaded, initialized or terminated by AegisAC.

## Packet coverage

| Family | Values normalized now |
| --- | --- |
| Movement / rotation | Presence flags, position, yaw/pitch, ground and horizontal-collision claims |
| Keep-alive / modern ping-pong | Full signed IDs and callback timestamps, both directions |
| Legacy window confirmation | Window ID, signed action ID, accepted flag; no legacy probes injected |
| Entity interaction / attack / swing | Entity, action, hand, optional hit vector, swing hand |
| Dig / place / use item | Block coordinates, face, sequence, cursor/hand/action or use rotation |
| Held item / inventory | Slot, window, state ID, click type/button; open/close/content/slot notifications |
| Sprint / sneak / abilities | Client actions and flying claims; server flight permission remains distinct |
| Vehicle / input | Vehicle position and rotation; analog input and original modern directional mask |
| Teleport / rotation correction | ID, full relative mask, position/rotation/delta values; separate confirmation |
| Velocity / explosions | Entity ID or explosion marker and impulse; other entities do not update local velocity time |
| Effects / status | Entity, effect name/amplifier/duration/removal, entity status |
| Block acknowledgement | Server acknowledgement sequence, separate from client interaction sequences |
| Respawn / join-game reset | Lifecycle reset marker without decoding large dimension registries |
| Custom payload | Bounded channel name, byte count and optional sanitized 1.8+ brand; no raw body retained |
| Other PLAY traffic | Counters/history marker only; no body decoding |

Client `MovementSnapshot` values are reports, not legal movement predictions.
Position-only/rotation-only packets do not overwrite absent fields. Non-finite
movement marks state unknown and uncertain without any accusation. Server
teleports invalidate the current movement baseline; relative flags are retained,
not naively added to a possibly stale position. Raw impulse/effect/interactions
remain available in bounded recent input history for future engines.

Inventory contents and cursor-slot updates never imply that a client opened a
window. Client clicks do not authoritatively advance the server's inventory state.
No item stacks/NBT are retained. Wrappers can parse such data transiently inside
the configured packet-size budget; this is not an exploit-protection release.
Brand is client-controlled, bounded UTF-8 metadata, not an identity/cheat signal.

## Transactions and latency

`TransactionTracker` correlates observed requests with exact responses using
independent timing kinds and, for legacy windows, the window ID as well as the
signed action ID. The pending table and retired-token history have fixed caps.
IDs are opaque: negative values and wrapping integer IDs are supported.

- Unknown, duplicate, ambiguous reused, expired and out-of-order acknowledgements
  cannot add RTT samples. Reordered replies retire only their exact token.
- Accepted outbound legacy window confirmations do not await a client response;
  rejected outbound confirmations can be paired with an accepted client reply.
- Keep-alive and transaction RTT have separate EWMAs. Jitter uses absolute changes
  within the same stream, avoiding artificial jitter from different baseline RTTs.
- Teleport confirmations must match a pending ID and never feed ping estimates.
- Expiry is evaluated against **observed timing-event timestamps**, not worker
  execution time. UI reads do not mutate expiry and invalidate a timely reply that
  is already queued. Pending count can therefore include an old token until the
  next timing event; memory remains bounded.
- Retired IDs are remembered only within the configured bounded history/timeout.
  This is not cryptographic replay prevention or an arbitrary-duration guarantee.

RTT begins at the outgoing MONITOR callback, **not a confirmed socket write**. It
can include local buffering, and observing an outgoing packet does not prove
successful delivery. The tracker intentionally offers no cumulative world-update
acknowledgement fence. Collision/target-history reconciliation must add and test
its own delivery semantics before relying on these observations.

Optional `active-probes: true` sends modern ping packets at a configured interval
only for known 1.17+ clients. It defaults off, sends nothing to legacy/unknown
clients, and cancels no responses. With probes off, transaction RTT can remain -1
until the server/another plugin produces matching traffic. Keep-alive timing is
still observed. Live proxy/translation compatibility must be validated before
turning probes on.

## Loss and uncertainty

Overflow clears queued analysis, records discarded-frame metrics and advances the
session's loss epoch before retaining the newest frame. Executor rejection and
decoder failure also clear pending analysis and mark uncertainty. An in-flight
older frame can finish, but API snapshots show its processed epoch differs from
the ingress loss epoch. The next epoch resets correlation, movement/action state
and recent history before processing resumes.

Processing failures are counted, invalidate history and are reported once through
the configured error sink to avoid log flooding. Stalls, backwards observation
time and worker delay add a configurable uncertainty window. Silence is visible
as a stalled connection even without another incoming packet. These are health
signals; no universal high-ping bypass, violation score or punishment is added.

The pipeline does not measure server TPS/MSPT, predict physics, establish correct
collision state, or implement checks. Those mechanisms remain in later phases.

## Configuration, API and operations

See `performance.yml: pipeline` and [CONFIGURATION.md](CONFIGURATION.md).
Resource/timing settings are validated and restart-bound; metrics, messages and
profiles still reload atomically. Existing Phase 1 documents inherit new defaults
without rewriting administrator files. All 24 YAML files remain version 1 because
the additions are backward-compatible; no destructive migration is needed.

The API snapshot adds `connection`, `movement`, `activity` and client `brand`.
A connection snapshot may be null before transport binding. Packet history stays
an internal bounded core API; a persistent trace/replay facility remains Phase 11.
`/ac connection <player>` exposes RTT/jitter/pending/loss/uncertainty, while
`/ac performance` exposes observed global counts and queue/decoder/processing
health. Per-player packet counts represent **processed** frames; global inbound
and outbound counters represent accepted observations, including later drops.
Uncertainty, loss epochs and drop counters must be checked before using history.

## Validation and limits

The JUnit suite tests bounded queues, concurrency/order/fairness, overflow and
executor rejection, failure recovery, reconnect/close, exact timing/expiry and
signed IDs, jitter, backlog-safe RTT, configuration validation/reload rules,
partial movement, teleport confirmation, inventory authority and local velocity.
Adapter tests use real PacketEvents wrappers and real Netty buffers, including
representative 1.8, 1.16.4/1.16.5-protocol and 1.21.11 movement/timing fixtures,
cached-wrapper mutation, truncated/oversized data, brand bounds and probe policy.

These are representative protocol fixtures, **not** a live multi-version server
certification. Paper/Spigot/Purpur server smoke tests, real clients and proxies,
large-server performance benchmarks and Folia remain unrun. The initial server
API baseline remains Paper 1.21.11. No dependency upgrade or detection claim is
implied by decoding older-client fixtures.
