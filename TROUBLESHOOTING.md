# Troubleshooting

- **Gradle cannot find Java:** install a JDK 21, not a JRE. Set `JAVA_HOME`, ensure
  its `bin` is on PATH and run `javac -version`. The wrapper checks its distribution.
- **Dependency repository blocked:** permit `repo.maven.apache.org`,
  `plugins.gradle.org`, `plugins-artifacts.gradle.org`, `services.gradle.org`,
  `downloads.gradle.org`, `github.com`, `release-assets.githubusercontent.com`,
  `repo.papermc.io`, `artifactory.papermc.io` and `repo.codemc.io` as required by
  actual requests. Use the platform's proxy and trusted CA store; Java does not
  automatically honor `HTTPS_PROXY`. Never set insecure TLS flags or replace
  expected dependency hashes to silence failures.
- **Dependency verification fails:** inspect the changed artifact and official
  source. Upgrade reviewed locks and checksums deliberately; do not disable checks.
- **AegisAC fails to load:** confirm the shaded jar, Java 21 and PacketEvents
  2.14.0. Read the bootstrap error. Failure disables this plugin and unregisters
  its resources without terminating the shared PacketEvents API.
- **Unknown command:** the commands in README and PHASE9 are available in Phase 9.
  Permission defaults are operator-only. Another plugin may own `/ac`; try
  `/aegisac` or Bukkit's namespaced `/aegisac:ac` command.
- **Reload rejected:** the error identifies file/path, and a line for parse errors.
  Fix the value or typo and rerun. The last working generation remains active.
  Reserved subsystems cannot be enabled in this phase. A busy reload is bounded
  and will reject extra requests rather than accumulating work.
- **Player missing from profile:** no current joined session exists, or PacketEvents
  did not expose its transport at join (a warning records the UUID). Check the
  PacketEvents/server combination rather than interpreting zero counts as clean play.
- **Bedrock says UNKNOWN:** inspect `/ac edition <player>` for missing/failed/stale
  provider reasons. Non-positive queries require an explicit topology assertion to
  establish JAVA; UNKNOWN uses conservative Bedrock policy. See BEDROCK.md.
- **No detections or database:** expected. This foundation has zero detectors,
  optional Phase 8 storage/webhooks and guarded actions are configured separately. See PHASE8.md.
- **Folia refuses the jar:** Phase 10 is in progress; native lifecycle acceptance and independent live validation remain required. Do not add a support flag.

Tests write only temporary configurations. Production data is not used by the
suite. Each test suite's current XML report distinguishes executed, failed and
skipped tests. MockBukkit and mocked PacketEvents test API integration; they do
not simulate real network injection, world physics or Folia scheduling.


- **RTT is -1:** no valid sample in that stream yet. Modern probes default off;
  keep-alive timing is independent of ping/window transactions. Teleport replies
  do not count as ping samples.
- **Queued/dropped or decode-rejected increases:** inspect worker load and the
  bounded pipeline settings. Overflow and oversized selected packets mark state
  uncertain; they do not prove cheating. Check the supported PacketEvents/server
  combination and do not suppress these metrics to imply complete history.
- **Reload says restart required:** the `performance.yml: pipeline` section is
  lifetime-bound. Restore it to reload other settings, or restart to apply it.
- **Client brand differs from its real client:** expected; brand is client-reported
  metadata, not authentication or a Bedrock detector.

## Physics is unavailable or uncertain

`/ac physics <player>` is diagnostic only. `NO_WORLD`/`BASELINE_MISSING` is normal
before capture and two position reports. `STALE_WORLD` means the round-robin
capture cycle or packet queue exceeded the configured lifetime. `FUTURE_WORLD`
means the latest server capture is newer than the packet observation; that packet
is not compared. `INVALIDATED_WORLD` follows block/chunk changes or lifecycle resets.
`SCAN_BUDGET` and `UNLOADED_CHUNK` mean incomplete geometry. `PACKET_GAP` also
covers bursts and position-suppressed reports whose tick count is unknown.

`UNACKNOWLEDGED_WORLD`, `INPUT_INFERRED` and currently `UNKNOWN_EDITION` are
expected. They are not cheating accusations. Use `/ac performance` for successful
captures and capture failures. A platform capture error is logged once per enable
and leaves analysis uncertain; consult the server log and supported API baseline.
See [PHASE3.md](PHASE3.md) for all model limits.

## Movement check results

`/ac checks` distinguishes experimental, disabled and unavailable entries. If
movement remains disabled after upgrade, the old explicit `enabled: false` was
preserved. Enable experimental observation in `checks/movement.yml` and reload.
`/ac movement` separates diagnostic records from buffered experimental findings.
Neither leads to punishment. Uncertainty always keeps the relevant buffer at zero.

NoSlowA, ElytraA and VehicleA cannot be enabled: required models/context are missing.
`/ac safeposition` may report a collision-clear candidate with `verified=false`.
This is expected with inferred input and unacknowledged world state. Never use it
as an automatic teleport target. Tick cadence is not measured MSPT. Large player
counts can exceed the bounded owner/capture refresh cycle and suppress analysis.

- **Combat diagnostics absent:** check root/per-ID enablement, bypass/world/gamemode
  exemptions, reset grace and `bedrock-mode`. Unknown targets cannot supply reach geometry.
- **Combat target history unknown/expired:** tracking is per viewer and bounded.
  A move without a spawn, removal, teleport/reset, invalid position or eviction can
  invalidate it. Packet callback acknowledgement is never proof of client execution.
- **CPS differs from a click counter:** `/ac cps` measures observed main-hand swing
  intervals and expires after silence; it does not measure physical mouse presses.
- **Combat reload requests restart:** target, history, pending attack and statistics
  resource capacities are fixed for a running configuration. Other rules reload atomically.

## Phase 5 console report: Paper 26.2 on Java 25

The reported `UnknownDependencyException: Unknown/missing dependency plugins:
[packetevents]` is the reason AegisAC did not enable. Install the standalone
PacketEvents **Spigot** distribution in `plugins/` alongside the shaded AegisAC jar,
then restart fully. Check PacketEvents' own startup result. Keep the hard dependency;
removing it does not supply the packet API. The original Phase 5 jar is not shown to
have a code failure by this log because Paper rejected it before enablement.

The build/test baseline is Paper API 1.21.11 with PacketEvents 2.14.0 on Java 21.
The supplied Paper 26.2/Java 25 runtime remains unverified; a successful dependency
installation alone does not certify compatibility. The JOML `sun.misc.Unsafe`
warning shown in the report is not an AegisAC stack trace.

The `minecraft:potion` error names an obsolete entry under
`chunks.entity-per-chunk-save-limit` in Paper's world configuration. Back up the
configuration and remove/update that entry according to the current Paper entity
registry, checking `config/paper-world-defaults.yml` and per-world `paper-world.yml`
as applicable. Do not delete worlds to resolve a configuration key.

The `sell` alias references the missing command `economyshopgui:sellall`. Restore
its intended plugin or remove the obsolete alias from command configuration.
These two configuration issues are separate from AegisAC's dependency failure.

## Phase 6 observations

Use `/ac inspect <name> [world|player|inventory|protocol|exploit]`. If diagnostics are
absent, check category/per-ID enablement, grace, explicit bypasses and edition policy.
Geometry also needs fresh owner attributes and, for occlusion/liquids, captured
coverage. Rates need a full family-specific window. `UNAVAILABLE` means a required
model is not implemented; enabling it deliberately rejects reload. Guard diagnostics
never increase trusted findings and never cancel actions or punish a player.

## Phase 7 identity diagnostics

- `PROVIDER_UNAVAILABLE`: no usable positive provider observation. Verify the local
  official plugin and proxy topology; do not guess identity from a name prefix.
- `PROVIDER_FAILURE`: API binding or query failed. Check provider compatibility and
  startup warnings. Failure is never a negative identity result.
- `NEGATIVE_NOT_AUTHORITATIVE`: queries did not establish Bedrock and complete local
  coverage has not been established (including missing configured providers).
- `BEDROCK_PROVIDER_LOST`: a previously positive session no longer has a positive
  observation. It remains UNKNOWN until the provider recovers or the player reconnects.
- `STALE_IDENTITY`, `FUTURE_IDENTITY`, `PROVIDER_CHANGED`, `CONFIGURATION_CHANGED`:
  old identity is unavailable; allow the bounded owner-thread poll to refresh it.
  With many players, adjust budgets only after measuring owner-thread cost.
- `BEDROCK_PHYSICS_UNAVAILABLE`: expected. Native Bedrock physics is not implemented.
- Empty `/ac bedrock` histories: identity is not confirmed, analysis has not received
  a current packet, capture is disabled, or observations expired/reset.

For a verified Java-only server, use the documented `java-only-server` assertion.
Never turn on complete-provider-coverage merely to remove UNKNOWN from diagnostics.
Java/Bedrock profile overrides cannot enable unavailable models or enforcement.

## Phase 8 outputs

- No staff messages: opt in with `/ac verbose` for diagnostics or `/ac alerts` for
  eligible findings, retain the corresponding permission, and check alerts.yml.
  Console/action-bar channels are independently configured; cooldowns and queue budgets apply.
- VL/risk stays zero: diagnostics and uncertain evidence never score, even when
  punishment rules are enabled. Current models are experimental; this is expected.
- No SQLite records: enable both logging.yml and storage.yml, check `log-failed` and
  `log-dropped` with `/ac output`, and inspect log-directory permissions. Queued old
  generations are discarded; an abrupt shutdown can lose buffered records.
- SQLite full: bounded row retention does not shrink the file; the page cap can be
  reached before the row cap for large records. Review retention offline with backups.
- Webhook failures: verify the private official URL locally, configured categories,
  timeout and Discord rate limits. Do not paste the URL into chat/console. Counters
  report failure without printing endpoint or response bodies. Queues/retries are bounded.
- Commands do not run: check panic, global and per-check opt-ins, experimental gate,
  all score thresholds, evidence age, Java identity, permissions/exemptions, session
  freshness and cancellable listeners. Diagnostic evidence cannot qualify.
- No setback: a fresh verified same-session/world/revision candidate and current
  clear loaded geometry are mandatory. Current inferred physics cannot produce such
  a verified candidate. Unsupported modes are rejected rather than guessed.
- After restart: subscriptions, session scores and panic state reset. Stored SQLite
  evidence remains; it is not reloaded as current violations.

## Phase 9 menus and staff controls

- **Menus disabled:** old `gui.yml: enabled: false` is preserved. Set true and `/ac reload`.
- **Menu expired / edit rejected:** reopen after a reload, permission change, target
  reconnect/world change or another saved edit. Reload externally edited files first.
  Reopen after a failed edit before retrying; rejected menus do not accept more writes.
- **Check says category disabled:** toggling the individual flag does not enable the
  parent category or change Java/Bedrock edition policy.
- **Permission to bypass a player:** use `aegisac.bypass.manage`. `aegisac.bypass`
  exempts its holder; it does not grant the management command.
- **Alias missing:** restart after editing `command-aliases`, check the collision
  warning and use `/ac` if the platform lacks public command-map accessors.
- **Freeze ends:** expiry, server teleport, death, reconnect and plugin shutdown
  release this temporary hold. Independent timed exemptions remain independent.
- **Formatting changed after GUI edit:** the edited file contains validated merged
  defaults. Its exact previous contents are in the adjacent `.last-admin-edit.bak`.

## Phase 10 scheduler diagnostics

- **REGION_NOT_OWNED:** capture skipped geometry owned by another region. It is missing
  evidence, never a detection. Do not suppress the reason or replace missing blocks with air.
- **Scheduler capacity reached:** at most 4096 managed tasks can be outstanding. A
  rejected capture invalidates its world view and increments capture failures.
- **Reply missing after reconnect:** async work is deliberately tied to the original
  player session. Run the command again from the current session.
- **Folia TPS unknown in adapter fixtures:** global cadence is not region-local TPS.
  PlayerDirectory now samples entity cadence; new or stale samples remain unknown.
  This is tick cadence, not MSPT.
