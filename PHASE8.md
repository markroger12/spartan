# Phase 8: alerts, evidence storage and guarded actions

AegisAC 0.8.0 adds staff notifications, structured asynchronous evidence storage,
rolling violation/risk scores, bounded Discord delivery, cancellable events,
panic mode and explicit punishment rules. It also implements a verified
LAST_VALID_POSITION correction path. All automatic actions default off.

Current runtime evidence remains uncertain or experimental. Diagnostic evidence
never enters the score ledger and can never authorize an action, regardless of
configuration. Existing runtime physics cannot establish verified safe positions.
This phase does not certify production detection accuracy or complete all
setback modes; raw POSITION and velocity/action/block/attack cancellation are
rejected because their authoritative models are unavailable.

## Ownership and delivery

Movement, combat and guard dispatchers emit immutable `Detection` records only when
they add evidence. `PlayerData` adds scalar session, loss epoch, provider epoch, identity key,
configuration generation, timestamp, client, timing, world and last observed-position
context. No Bukkit object or packet buffer leaves its owner. Output hooks are rewired
when edition/configuration changes replace monitors. There is no evidence-ring polling
or whole-player snapshot allocation on every packet.

`OutputService` owns effects on the ordinary server thread. It consumes at most 64
records per tick from a 1024-entry queue, dropping overflow without blocking workers.
Records older than two seconds, from future observation times, replaced sessions,
changed identity/configuration/provider or loss epochs are discarded. A rapidly
recovered provider cannot revive evidence from its earlier epoch; an ingress loss
cannot relabel an in-progress observation with the newer loss epoch. Per-check sequences
deduplicate records. A separate 256-entry staff delivery queue sends at most 128
messages per tick. At most 128 distinct staff may subscribe. Permission, subscription,
session, identity and age are checked again at delivery. Counters expose drops,
staleness, written records, I/O failures and webhook outcomes through `/ac output`.
Folia remains unsupported.

## Scores and eligibility

Only non-diagnostic, finite, uncertainty-free findings enter `ViolationLedger`.
At most 256 findings are retained per session. Each contributes one per-check VL
unit and its configured risk weight to its category and total. Contributions decay
linearly to zero over `decay-ms` and expire at `window-ms`, whichever comes first.
Configuration, session, identity/loss boundaries and backwards time clear obsolete
scores. These are in-memory session scores; reconnect does not restore them from logs.

Confidence is a risk-weighted average of configured finding confidence, with a 5%
bonus per additional distinct check ID, capped at three bonuses and 100 overall.
Different IDs are not proof of statistical independence. The result is a policy score,
not a calibrated probability. A diagnostic record can display an existing session
score but cannot contribute to it. The detector's buffer is reported independently.

Punishment requires all configured thresholds simultaneously: triggering-check VL,
category risk, total risk, confidence and total finding count within the rolling
window. Global enablement, that check's enablement and experimental opt-in are
separate gates. Default confidence 50 and conservative thresholds do not constitute
a ready-made ban policy. Do not enable actions based on these synthetic tests.

Before each command, the owner thread rechecks evidence age (default 500 ms), active
session/configuration/identity, confirmed Java edition, current player name, online
state, world, check/Java-profile enablement, bypass permissions, configured exemptions,
death/vehicle/gliding state and panic. Names must match Java's bounded name syntax;
command substitutions permit only `%player%`, `%uuid%` and `%check%`. Other placeholders,
control characters, leading slashes and more than eight commands are rejected.
Commands are administrator-configured console commands, never client payloads.
Bedrock and UNKNOWN cannot receive automatic actions in this release.

Each session has a punishment cooldown (default 60 seconds). Eligibility reserves
it before the cancellable event, preventing cancelled-event floods. Every command
is rechecked; a failed dispatch stops the batch. Logs distinguish DISPATCHED from
DISPATCH_FAILED: command acceptance does not prove a downstream ban/kick succeeded.
There are no command retries or offline punishment waves.

## Verified position correction

`setbacks.yml` is independent of punishment enablement. Supported modes are NONE
and LAST_VALID_POSITION, with separate movement-check switches, VL/confidence,
experimental opt-in, age, distance, cooldown and panic policy. The candidate must
be verified, uncertainty-free, same-world/session/revision, finite and fresh.
The owner thread checks loaded chunks, border, height, distance and the current
player dimensions. It rescans at most 128 blocks and 256 collision shapes without
loading chunks, rejecting overlap and intersecting liquids/fire. The destination
preserves current orientation. A cancellable event precedes revalidation and teleport;
a successful teleport invalidates packet analysis. It does not then punish from the
same obsolete evidence.

The current inferred/unacknowledged physics cannot generate a verified runtime
candidate. Tests exercise a synthetic verified candidate; enabling this policy does
not promote uncertain positions to verified. Native client conformance, other
cancellation modes, moving-entity collision guarantees and full correction coverage
remain outstanding.

## Alerts and commands

Staff opt in using `/ac alerts` for eligible findings or `/ac verbose` for diagnostics
as well. Subscriptions end on quit. `alerts.yml` controls chat, console, action bar,
hover and optional teleport-command suggestion. Clicking only fills the command box;
it does not execute a teleport. Staff still need server permission to execute it.
Per-player/check cooldown defaults to one second. Console delivery is separately
opt-in. Templates are expanded once; client metadata is bounded and stripped of
control/color characters before interpolation.

Supported placeholders: player, uuid, check, type, category, description, vl, max_vl,
buffer, confidence, risk, ping, jitter, tps, mspt, client_version, client_brand, bedrock,
x, y, z, world, server, evidence, observed, expected, sequence, session, experimental,
timestamp. `%max_vl%` is the configured triggering-check punishment threshold.
MSPT is `unavailable`; measured tick cadence is not mislabeled as MSPT. Coordinates
are the last captured packet-state position, not proof of an accepted client location.

| Command | Permission | Purpose |
| --- | --- | --- |
| `/ac alerts` | `aegisac.alerts` | Toggle finding subscription |
| `/ac verbose` | `aegisac.verbose` | Toggle diagnostic subscription |
| `/ac violations <player>` | `aegisac.violations` | Current decaying session scores |
| `/ac logs <online-player-or-uuid>` | `aegisac.logs` | Asynchronously query ten recent SQLite records |
| `/ac panic` | `aegisac.panic` | Toggle automatic-punishment block |
| `/ac output` | `aegisac.output` | Panic status and output queue/I/O counters |

All default to operators, belong to `aegisac.admin`, and have permission-aware
completion. Log replies recheck the recipient's permission on the server thread.
Empty results can mean disabled/unavailable storage; inspect counters for failures.
Panic keeps detection, scoring and logging active. It disables setbacks by default,
with an explicit `disable-in-panic` override. Panic survives configuration reloads
but resets on full plugin/server restart. Its state is shown by the command,
`/ac output` and startup console summary. Full GUI/command management remains Phase 9.

## Storage and webhooks

Logging is optional and defaults off. When enabled, SQLite is the default backend;
no external database/server credential is needed. `logging.yml: plain-text` adds
JSON Lines. Evidence includes the alert fields, timestamp, UUID, classification and
configuration/session context. Data includes player identifiers and coordinates;
choose retention and file access appropriate to the server.

One daemon owns a bounded 512-job queue, at most 32 writes per transaction, prepared
statements and UUID-indexed queries. SQLite retains at most `maximum-rows` (default
100000), trims oldest rows in the same transaction, and caps the database at 65536
pages (256 MiB with the default 4096-byte pages). Page reuse bounds growth; deletion
does not shrink an existing file. JSONL rotates between two files, each bounded by
`maximum-file-bytes` (default 10 MiB). A row too large for that limit is dropped.
No tick/packet thread does disk or database I/O. Queued records are filtered against
current generation/policy when a batch starts; in-flight commits are not undone.
Failures increment counters and produce rate-limited, payload-free warnings.
Shutdown closes ingress and permits a two-second bounded drain on the daemon,
then discards remaining work. Abrupt process loss can lose queued writes; this is
not an exactly-once audit system. MySQL/MariaDB remain optional future work and are
not accepted as configured backends.

`webhooks.yml` independently enables alerts, diagnostic alerts, punishments/setbacks,
startup and generic local output-error notices. The URL must be an official HTTPS
Discord webhook with no query, fragment or alternate host. It is never printed in
normal output. A 64-message queue uses one daemon, request timeout, no redirects,
discarded response bodies, disabled mentions, bounded content, request spacing
(default 1000 ms) and at most three configured retries (default two). Only 429,
server errors and transport failures retry; Retry-After is capped at 60 seconds.
Configuration changes discard obsolete queued/retry work. Already sent requests
cannot be retracted. Shutdown interrupts work and closes the HTTP client.
Webhook failures do not recursively enqueue webhook error messages. Tests use a
fake transport; no external Discord message was sent during development.

## API, source and validation

`AntiCheatApi.violations(UUID)` returns an immutable current-session snapshot.
Owner-thread `PlayerFlagEvent` can cancel scoring/output; `ViolationChangeEvent`
reports accepted score changes; `PlayerPunishEvent` and `PlayerSetbackEvent` can
cancel effects. Cancellation never fabricates stronger evidence. Event records and
scores are immutable. Listener code must return promptly. Rebuild pre-release API
integrations for the added interface method. There is no public trusted-flag producer.

See PROJECT_TREE.md for complete source and VALIDATION.md for executed tests and
artifact SHA-256. Build with JDK 21 and `./gradlew clean build --no-build-cache`.
The deployable artifact is `aegis-paper/build/libs/AegisAC-0.8.0-SNAPSHOT.jar`.
PacketEvents 2.14.0 remains separate. SQLite JDBC 3.53.4.0 is bundled with its native
resources/licenses; its jar/POM and imported JUnit BOM metadata were verified against
Maven Central's HTTPS SHA-256 files. Existing checksums were preserved. The build
loads the actual shaded jar with only a JDK parent and opens SQLite to verify packaging.

Tests cover real SQLite retention/rotation, synthetic eligible and ineligible
findings, deduplication/decay/window/bounds, permissions, stale/reload/reconnect/panic
and cancellable-event fences, retries/backpressure, shutdown, endpoint redaction,
actual detector output hooks and verified-position collision checks. No live server,
client, Discord deployment, real ban plugin or native Bedrock physics was validated.
