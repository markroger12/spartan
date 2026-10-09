# Java and Bedrock separation — Phase 7

AegisAC queries the official Floodgate and Geyser APIs and applies independent
edition profiles. It does not identify a client from names, UUID patterns, client
brand, protocol number or plugin presence. All runtime checks remain diagnostic.

## Identity and server topology

The optional adapters query enabled local plugins `floodgate` and `Geyser-Spigot`:

- `FloodgateApi.getInstance().isFloodgatePlayer(UUID)` includes linked accounts.
- `GeyserApi.api().connectionByUuid(UUID)` returning a connection establishes a
  positive Geyser observation.
- Floodgate's optional `getPlayer(UUID)` supplies device OS, input mode and version.
  These are bounded client-reported labels, not authentication or cheat evidence.
  Metadata failures preserve positive identity. XUIDs, addresses and usernames
  from these APIs are never requested or retained.

A positive result from either provider wins. Missing classes, incompatible API
signatures, null instances, invocation failures, stale observations and provider
changes produce explicit uncertainty. Losing a positive Bedrock result cannot
turn that session into Java; the positive latch lasts until disconnect.

**Defaults leave non-positive identity UNKNOWN.** A negative local query does not
prove the absence of a remote translator. Configure the actual topology in
`compatibility.yml`:

```yaml
identity:
  floodgate: true
  geyser: true
  poll-interval-ms: 1000
  queries-per-tick: 8
  maximum-age-ms: 3000
  negative-results-authoritative: false
  java-only-server: false
```

On a verified Java-only server, explicitly set `java-only-server: true`. With
translation, set `negative-results-authoritative: true` only when enabled local
providers cover **every** Bedrock path, including proxies. Every configured provider
must return negative; disable an unused provider explicitly. A missing configured
provider or API failure keeps identity unknown. A positive observation overrides
both assertions. Do not assert local coverage for a proxy-only installation that
cannot expose identities here. Install/configure the appropriate official provider
on the backend or retain UNKNOWN.

The owner thread scans at most eight sessions per tick by default, with at most two
API calls per due session. The queue rotates fairly; it does not scan all players
for queries every tick. Provider/configuration transitions also invalidate the
published epoch; readers hide old identity immediately. Maximum age is checked
on reads, not only on the next packet. Large populations or slow ticks can exceed
the freshness budget and temporarily become UNKNOWN. API calls are synchronous
public queries: a blocking provider still blocks the owner thread.

## Independent profiles and modes

`profiles/java.yml` has an enable flag for every catalog ID.
`profiles/bedrock.yml` has a rule for all 72 IDs. UNKNOWN uses the same conservative
check policy as BEDROCK, while only confirmed BEDROCK records translated histories.
Per-world difficulty selection is independent and does not multiply these limits.

| Mode | Meaning | Default IDs |
| --- | --- | --- |
| `BEDROCK_SUPPORTED` | Reuse the normalized predicate diagnostically | BadPacketsA, InvalidPositionA, InvalidPitchA, InvalidSlotA, InvalidEntityInteractionA, InvalidAttackA, ImpossibleInteractionA, PayloadSizeA |
| `BEDROCK_ADJUSTED` | Apply a separate diagnostic limit | PacketSpamA, MovementSpamA, InteractionSpamA, PayloadSpamA, FastPlaceA, FastUseA, NukerA |
| `BEDROCK_DISABLED` | Suppress the check | All other 57 IDs, including all movement, Java combat geometry/statistics, inventory timing and unavailable models |

SUPPORTED does not mean certified native-client accuracy. ADJUSTED uses
`effective limit = base limit / sensitivity-multiplier + extra-tolerance`.
Default adjusted sensitivity `0.5` doubles the limit. Values range from 0.1 to 10;
extra tolerance ranges from 0 to 10000 in the underlying check's units. For movement
and combat rules this changes numeric tolerance; guard rules use their `limit`.
It does not change predicate constants, windows, buffers, geometry or native inputs.
SUPPORTED requires multiplier 1 and extra tolerance 0; use ADJUSTED to change them.

```yaml
checks:
  PacketSpamA:
    mode: BEDROCK_ADJUSTED
    sensitivity-multiplier: 0.75
    extra-tolerance: 0.0
```

Implemented checks permit manual mode changes. Unavailable models cannot be enabled.
Global/category disable, per-check `enabled: false`, and older per-check
`bedrock-mode: disabled` remain vetoes. No profile setting enables enforcement.
Overrides cannot supply missing physics or authoritative client state; manually
turning on a Java movement predicate does not create a Bedrock movement model.
All profile settings reload atomically. Missing fields merge without rewriting
existing administrator files, preserving their disabled settings.

## Translated analysis

Each confirmed Bedrock session owns six separate bounded histories: movement,
rotation, input, inventory, combat and placement/use. The default is 16 observations
per lane (maximum 64), expiring after five seconds. These are immutable scalar copies
of the translated PacketEvents stream, not native Bedrock packets. Nonfinite numbers
are represented by a finite indicator; action labels are bounded. There is no raw
payload/NBT retention and no disk history or account identifier collection.

Java and unknown sessions do not populate these histories. Identity/source/metadata
changes, configuration generation, packet loss, teleport/world/protocol reset,
backwards time and disconnect discard them. Polling the same identity with a newer
timestamp does not reset analysis. Worker state never holds Bukkit objects.

Confirmed Bedrock sessions bypass the Java physics tracker and report
`BEDROCK_PHYSICS_UNAVAILABLE` with zero candidates. Unknown sessions can still expose
an explicitly uncertain Java hypothesis (`UNKNOWN_EDITION`); the default profile
disables its movement check evidence. No native movement/friction/rotation model is
invented. Translation timing, native input, attack cadence, inventory sequencing,
placement cadence and combat geometry require real client validation before stronger
models can be offered.

## Diagnostics and verification

- `/ac edition <player>`: identity, provenance, optional device/input/version and reasons.
- `/ac bedrock <player>`: translated lane counts, status and uncertainty.
- `/ac checks`: per-check Bedrock mode followed by the legacy diagnostic/disabled veto.
- `/ac physics <player>`: native Bedrock physics explicitly unavailable.

Permissions `aegisac.edition` and `aegisac.bedrock` default to operators and are
included in `aegisac.admin`. The read-only API exposes `EditionSnapshot` and
`BedrockSnapshot` through `PlayerSnapshot`.

Official contracts were checked against the public sources on 2026-10-06:
[FloodgateApi](https://github.com/GeyserMC/Floodgate/blob/master/api/src/main/java/org/geysermc/floodgate/api/FloodgateApi.java),
[FloodgatePlayer](https://github.com/GeyserMC/Floodgate/blob/master/api/src/main/java/org/geysermc/floodgate/api/player/FloodgatePlayer.java),
[GeyserApi](https://github.com/GeyserMC/Geyser/blob/master/api/src/main/java/org/geysermc/geyser/api/GeyserApi.java),
[GeyserConnection](https://github.com/GeyserMC/Geyser/blob/master/api/src/main/java/org/geysermc/geyser/api/connection/GeyserConnection.java).
Reflection is limited to these public methods and avoids mandatory provider jars.
No Floodgate or Geyser implementation is bundled or initialized by AegisAC.

Tests use API-shaped fixtures and synthetic translated traces. They verify binding,
negative/positive/failure paths, stale/epoch fences, budgets, reconnects, policy
separation and reset behavior. They do **not** certify a live provider release,
proxy forwarding, linked-account deployment, touchscreen/controller accuracy or
native Bedrock cheat detection. Those live validations remain outstanding.
