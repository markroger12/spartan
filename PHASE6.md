# Phase 6: world, player, inventory and protocol diagnostics

AegisAC 0.6.0 adds **31 experimental diagnostic evaluators** across the five
previously reserved world, player, inventory, protocol and exploit categories.
Eight advanced models remain explicitly unavailable. This implements the bounded
observation layer; it is not complete production coverage of every Phase 6 feature.
There is no cancellation, kick, ban, setback, automatic punishment or network firewall.
The earlier movement/combat engines remain intact.

## Ownership and processing

`GuardMonitor` runs on the existing serialized session worker. `PlayerData` passes
the previous reported movement and the immutable world view from immediately before
the current action invalidates it. The guard runs before `PacketState.apply`, so
malformed coordinates and slots can produce bounded numeric diagnostics before the
state layer rejects them. It never retains Bukkit objects, packet buffers, item NBT,
payload bodies or world/entity handles.

`WorldCaptureService` samples eye height, block interaction range and per-check
permissions on the owner thread under its existing round-robin budget. Missing,
stale or future attribute observations suppress geometry. Geometry never substitutes
a hardcoded reach attribute for unavailable owner state. A capture failure clears
the owner observations. Larger populations can outgrow the freshness budget; the
result is unavailable geometry, not worker-thread world access.

`GuardDispatcher` maintains independent counters/cooldowns and an evidence ring per
category. Every evidence record includes `OBSERVATION_ONLY`, and every runtime
observation carries client-state uncertainty. There is no trusted finding path in
this dispatcher: API buffers and finding counts remain zero. Phase 6 does not expose
unused buffer/punishment options as if they affected diagnostics.

## World signals

BlockReach compares the sampled eye against the closest point of the clicked unit
block, then subtracts the sampled block interaction range. Its configurable limit
is additional distance tolerance. Direction checks the clicked face's orientation
relative to the eye. RotationPlacement tests the reported look ray against a slightly
expanded clicked block. These are clicked-block approximations, not confirmed hit
results or placement outcomes.

GhostHand traces from eye to target block center against retained collision shapes,
excluding shapes in the target block. It requires a fresh capture covering the whole
segment and target block. Shapes, ray tracing, server captures and client delivery
are not authoritative client visibility; center targeting can disagree with legitimate
edge interactions. LiquidInteraction reports a dig completion naming a captured
water/lava block, subject to the same coverage and freshness gates.

Scaffold combines a rapid below-feet placement with a ray miss. Tower requires a
repeated vertical progression of rapid below-feet placements. These are reproducible
correlations, not full bridging/input models. FastPlace counts requests, not accepted
block placements. Nuker counts distinct completed-dig target coordinates in a bounded
one-second history, not actual broken blocks. InvalidDig matches a completion/cancel
to the latest observed start; item drops, release-use and other non-block actions do
not replace that start. Dig observations expire after 30 seconds. Creative/flight and
other configured exemptions suppress diagnostics.

ImpossiblePlace checks cursor components and face bounds. Legacy face 255 air-use
is handled separately when the protocol lacks modern inventory capability; it is
not treated as an invalid block placement. Modern interaction sequence checks begin
at protocol 759 (Java 1.19). Signed integer wrap is accepted; duplicate/reversed
observations are diagnostic. No acknowledgement or accepted block action is inferred.

## Player, inventory and protocol signals

UseOrder matches release-use to retained use observations, including legacy air-use;
slot changes clear the observation. Expired long-held uses do not produce a guessed
consumption result. FastUse counts use requests. Neither measures actual eating,
healing, bow charge, cooldown completion or authoritative active-item state.

InventoryMove/InventorySprint correlate movement or sprint start with an observed
server-opened menu. Matching closes clear it; unrelated closes do not. Client clicks
cannot open a server menu. ImpossibleInventory compares non-player-menu click IDs
with the observed menu. InvalidSlot bounds held slots to 0–8; SlotSpoof rejects
negative click slots except outside-click sentinel -999. Positive slot bounds require
menu-specific models and are not guessed. Plugin-opened menus, lag and delivery
remain unconfirmed; no inventory action is cancelled.

BadPackets checks nonfinite numbers in applicable normalized fields; evidence stores
a finite boolean indicator rather than NaN/Infinity. InvalidPosition uses the
30-million coordinate envelope. InvalidPitch reports excess outside [-90,90].
InvalidEntityInteraction checks self interaction and nonfinite target-local vectors.
Transaction reports unmatched/ambiguous timing outcomes from the existing exact
tracker; it is not proof of ping spoofing. ImpossibleClientState compares reported
flight to an observed server prohibition. PacketOrder notes position observations
while a known teleport awaits its exact sampled confirmation, expiring after ten
seconds. Plugin/proxy changes, delay and observation order remain uncertainty.

## Budgets, rates and lifecycle

Six rate windows each use twenty fixed 50 ms buckets. Evaluation waits for a full
second of observations for that family. The oldest partial bucket is excluded,
which conservatively undercounts instead of overstating the one-second rate. Rates
are observation-based, independent of worker scheduling time. Counters saturate;
no packet list grows with flood size. Completed-dig history is capped at 128 entries,
with an explicit trimming reason. World geometry is bounded by the existing capture
budgets (at most 4096 block samples/shapes).

Default limits are 300 inbound packets/s, 40 movement/s, 80 entity interactions/s,
20 payloads/s, 4096 normalized payload bytes, 30 placements/s, 30 uses/s and 12
distinct completed-dig targets. These are diagnostic policies, not recommendations
for bans or rate-limiting a server. PacketSpam includes normalized `Other` packets
but excludes cancelled/pre-PLAY traffic. Decoder-rejected traffic never reaches this
engine; existing decode-rejection metrics and loss invalidation remain separate.
Packet byte budgets protect analysis allocations, not the entire server network stack.

Each category has 32 evidence records by default (160 total), bounded to 256 per
category by validation. All evidence is immutable, numeric and session-local.
Configuration changes, queue loss, world/protocol resets, player teleport, backwards
observation time and quit discard histories and evidence. API reads hide old state
immediately when a loss epoch or new configuration is pending, even before the next
packet. Ordinary block/chunk invalidation does not reset every action/rate window.

## Configuration and administration

The five `checks/{world,player,inventory,protocol,exploit}.yml` files keep config-version
1. Fresh files enable implemented diagnostics. Existing `enabled: false` is preserved,
and absent additions inherit defaults without rewriting administrator files.
Category settings are `enabled`, `evidence-capacity`, `grace-ms` and `maximum-delay-ms`.
Per-check settings are `enabled`, `limit`, `cooldown-ms` and `bedrock-mode`.
All Phase 6 policy reloads atomically; fixed internal window/history budgets are not
exposed as restart-sensitive settings. NaN/Infinity, unknown keys, out-of-range
settings, unsupported Bedrock modes and enabling unavailable models reject the whole
reload. `bedrock-mode` is `diagnostic` or `disabled` and applies to UNKNOWN identity too.

`/ac inspect <player> [all|world|player|inventory|protocol|exploit]` shows states and
latest evidence, guarded by `aegisac.inspect`. `/ac checks` lists 72 entries: 31 new
implemented and eight unavailable entries, plus the previous 33 movement/combat
entries. Implemented check bypass nodes use lowercase IDs and default false, even
for operators/admins. Shared world, gamemode, flight and permission exemptions apply.
Grace and special movement suppress diagnostic evidence. Tick cadence, connection,
owner state and processing delay remain explicit uncertainty; no setting turns an
uncertain observation into a trusted violation.

## Coverage still unavailable

| Entry | Missing prerequisite |
| --- | --- |
| FastBreakA | Hardness, tools, enchantments and acknowledged block state |
| ImpossibleBreakA | Authoritative break eligibility and server acceptance |
| FastEatA | Confirmed active item and consumption completion |
| FastBowA | Bow-specific charge and accepted projectile state |
| RegenA | Authoritative health, effects and healing causes |
| AutoRespawnA | Authoritative death and respawn lifecycle |
| PortalInventoryA | Authoritative portal transition and menu state |
| BookExploitA | Bounded item-component decoding and version-specific limits |

Enabling these is rejected. Full Phase 6 coverage remains unfinished until those
models and live traces exist. The later Bedrock, risk/enforcement and Folia phases
remain separate work. Do not interpret a named diagnostic as certified cheat detection.

## Build and verification

Use JDK 21 and `./gradlew clean build --no-build-cache --console=plain`.
Output: `aegis-paper/build/libs/AegisAC-0.6.0-SNAPSHOT.jar`.
Dependencies and verification locks are unchanged. Complete source/config/test paths
are in [PROJECT_TREE.md](PROJECT_TREE.md); exact counts and checksum are in
[VALIDATION.md](VALIDATION.md). Tests exercise implemented predicates, sequence wrap,
legacy sentinels, menu state, ray/capture gates, rate boundaries, finite evidence,
configuration rollback, session invalidation, permissions, PacketEvents wrappers
and owner-thread attribute publication. They do not certify live server accuracy.

The supplied Phase 5 Paper 26.2/Java 25 console log failed before AegisAC enable with
`UnknownDependencyException: [packetevents]`. Install the standalone PacketEvents
Spigot plugin and restart. Phase 5/6 use pinned PacketEvents 2.14.0 with the Paper
1.21.11 API; compatibility with the supplied newer server remains unverified.
The obsolete `potion` chunk-save-limit entry and missing EconomyShopGUI alias are
separate server configuration issues. [TROUBLESHOOTING.md](TROUBLESHOOTING.md) records
the diagnosis; the hard dependency was retained because the packet engines require it.
