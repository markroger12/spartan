# Configuration contract

All 24 YAML documents are generated under `plugins/AegisAC/`. Every document has
`config-version: 1`. The bundled defaults are also readable in
`aegis-common/src/main/resources/defaults/`.

| File(s) | Settings |
| --- | --- |
| `config.yml` | `default-profile`: strict/balanced/lenient; `world-profiles`: world-name to difficulty; `command-aliases`: additional restart-only names |
| `messages.yml` | Configurable command responses, prefix, & colors and named placeholders |
| `performance.yml` | `packet-metrics`: aggregate counts; per-session counters always run |
| `compatibility.yml` | Official provider polling and topology assertions; UNKNOWN requires the Bedrock policy |
| `profiles/{java,bedrock,strict,balanced,lenient}.yml` | `mode: observe-only`; edition profiles have per-check policies |
| `checks/movement.yml` | Live experimental movement policy, buffers, timing and grace |
| `checks/combat.yml` | Experimental combat rules, windows, target/history budgets and statistics |
| `checks/{world,player,inventory,protocol,exploit}.yml` | Phase 6 diagnostic limits, cooldowns, grace, evidence budgets; missing models remain disabled |
| `exemptions.yml` | Explicit permission/world/gamemode/flight exemptions |
| `alerts.yml`, `punishments.yml`, `setbacks.yml` | Alert delivery and opt-in action policies; punishments/setbacks default off |
| `storage.yml`, `webhooks.yml`, `logging.yml` | Optional evidence/HTTP output; defaults off |
| `gui.yml` | Validated inventory layout, titles, materials, names/lore, links and control slots |

Difficulty selection is per-world with a global fallback. Edition selection is
independent: JAVA selects `java`; BEDROCK and UNKNOWN select `bedrock`. Difficulty
and edition are returned together. Check rules are global; named difficulty profiles do not multiply their thresholds,
and global/per-check punishment policy remains independently configured.

## Reload

`/ac reload` admits one asynchronous request at a time. It reads and validates
**every** document, prepares an immutable snapshot, performs required migrations,
then atomically swaps the configuration reference. Packet callbacks only read the
current snapshot. Success reports its generation; a rejected reload preserves the
previous object and generation. Replies are delivered on the Bukkit scheduler.

All files are bounded to 256 KiB, YAML nesting is bounded, duplicate keys and
collection aliases are prohibited, and safe construction prevents YAML tags from
instantiating arbitrary classes. Unknown keys, wrong scalar types, null values,
unknown profiles, unsupported versions, blank/oversized messages and enabled
future subsystems fail validation. Invalid YAML includes line information where
available; semantic diagnostics include file, path and the expected type/domain.
Raw scalar values are not echoed into errors, to avoid exposing secrets.

Missing settings in version-1 documents inherit bundled defaults in memory;
normal reload never rewrites administrator files or their comments. Delete an
entire document only when intending the default to be regenerated. Do not use
symlinks for the configuration directory, subdirectories or files.

## Migration

The explicit development schema-zero importer changes `config.yml: profile` to
`default-profile` and adds missing defaults across documents. It rejects files
containing both old and new names. Before each migration, it creates a unique
adjacent `.v0-*.bak` file containing the original bytes. Migration writes use an
atomic same-directory replace; unsupported filesystems cause failure, not a
non-atomic fallback. Migrated YAML may be reformatted; original comments remain
in the backup. Second load is idempotent. Future versions are never downgraded.

The entire candidate validates before any migration starts. Filesystem migration
is atomic **per file**, not a multi-file transaction: an I/O failure can leave some
files migrated and backed up. Runtime settings still retain the old generation;
fix permissions and retry. Do not edit files concurrently with a migration.

## Message placeholders

Prefix applies to all responses. Version uses `%version%`; reload uses
`%generation%` or `%error%`; profile uses `%player%`, `%uuid%`, `%client%`,
`%bedrock%`, `%inbound%`, `%outbound%`; performance uses `%players%`, `%inbound%`,
`%outbound%`, `%untracked%`, `%generation%`. Unknown placeholders remain literal.
Text is never executed as a command. Startup and parser diagnostics use fixed
English strings because they must function even when configuration is invalid.


## Packet pipeline settings

`performance.yml: pipeline` validates the following startup settings. Changing any
of them via `/ac reload` rejects the candidate and keeps the active generation;
restart the server to apply the edited file. This prevents live queue replacement
or incompatible timing rules midway through a session. `packet-metrics` remains
hot-reloadable. Schema-1 files from Phase 1 inherit these defaults in memory.

| Key | Default | Valid range / meaning |
| --- | ---: | --- |
| `workers` | 2 | 1–8 fixed analysis workers |
| `executor-capacity` | 4096 | 1–16384 scheduled session batches |
| `session-capacity` | 256 | 8–4096 queued normalized frames per session |
| `batch-size` | 64 | 1–256 frames processed before yielding |
| `maximum-packet-bytes` | 8192 | 256–65536 bytes for selected decoded packets; larger ones create uncertainty |
| `pending-transactions` | 128 | 4–1024 outstanding and retired timing tokens per session |
| `history-capacity` | 32 | 1–256 recent normalized input frames |
| `transaction-timeout-ms` | 10000 | 1000–120000; observation-time token expiry |
| `stall-ms` | 250 | 50–10000; packet gap/worker delay health threshold |
| `uncertainty-ms` | 1000 | 100–60000; uncertainty grace after loss or timing anomalies |
| `smoothing` | 0.125 | Finite number greater than 0 and at most 1; RTT/jitter EWMA weight |
| `active-probes` | false | Opt-in ping probes for known modern clients only |
| `probe-interval-ticks` | 20 | 10–1200 server ticks between probe batches |

These are resource and observation settings, not cheat sensitivity or mutable
Minecraft physics constants. Memory per session is bounded by its queue, history,
and timing-token budgets; increasing all caps for many players has a real cost.

Connection messages additionally support `%transaction_rtt%`, `%keepalive_rtt%`,
`%jitter%`, `%pending%`, `%uncertain%`, `%loss_epoch%`, `%brand%`, `%player%`.
Performance adds `%processed%`, `%queued%`, `%dropped%`, `%decode_rejected%`,
`%failures%`, `%executor_rejected%`. RTT -1 means unavailable. Placeholders are
replaced once, and client brands are not interpreted as color codes or templates.

## Phase 3 physics budgets

The `physics` section in `performance.yml` is restart-bound, like `pipeline`.
Changing it through reload rejects the entire candidate generation. Earlier
version-1 administrator files inherit absent defaults without being rewritten.

| Key | Default | Accepted range / behavior |
| --- | --- | --- |
| `enabled` | `true` | Boolean; enables capture and diagnostic simulation |
| `captures-per-tick` | `2` | 1–16; round-robin players on the owning thread |
| `radius` | `2` | 1–4 blocks horizontally; outer block is a shape border |
| `maximum-blocks` | `512` | 32–4096 scanned cells per capture |
| `maximum-boxes` | `512` | 32–4096 collision pieces per capture |
| `maximum-entities` | `16` | 1–64 retained nearby entity AABBs |
| `maximum-age-ms` | `250` | 50–2000; old geometry cannot produce candidates |

Vertical capture covers six block layers around the player's feet, clipped to
world height. Default radius scans at most 150 cells per player (300 per tick).
Work and retained geometry are bounded; Bukkit's own spatial-query result
allocation is controlled by the server implementation, not this setting.
Insufficient budgets or unloaded chunks produce uncertainty, not air blocks.
At high player counts the round-robin cycle can exceed snapshot lifetime;
those players report unavailable predictions. Increase budgets only after profiling.
`messages.physics`, `messages.physics-usage` and the added capture metric
placeholders are reloadable. There are still no detection thresholds in this phase.

## Phase 4 live movement policy

`checks/movement.yml` now implements movement policy. It is reloadable as one
immutable generation; reload resets per-session evidence, buffers, timing credit
and candidate streaks before the next packet. Old version-1 `enabled: false` files
remain disabled and unchanged; absent fields inherit defaults. New installations
default to experimental observation. Other reserved check categories remain disabled.

| Section | Defaults and allowed range |
| --- | --- |
| `enabled` | true, boolean |
| `evidence-capacity` | 32, 1–256 records per session |
| `grace` | join 3000, teleport 2000, velocity 1500, lag 2000 ms; each 0–60000 |
| `health` | maximum-delay 250 ms (50–5000), maximum-jitter 100 ms (1–5000) |
| `safe-position` | minimum-samples 5 (2–200), maximum-age 2000 ms (50–10000) |
| `timing.window-ms` | 2000 (1000–10000) |
| `timing.nominal-tick-ms` | 50; only the nominal 20 TPS model is supported |
| `timing.bank-ms` | 250 (0–5000) credit for temporary bunching |
| `timing.silence-ms` | 1000 (250–10000) |
| `timing.burst-ms` | 200 (50–1000) |
| `timing.burst-packets` | 8 (3–100) |

Each stable ID under `checks` has `enabled`, `tolerance` (finite 0–10000),
`increment` (0.01–100), `decay-per-second` (0–100), `buffer-threshold`
(0.01–1000), `minimum-samples` (1–1000), `cooldown-ms` (1–60000), and
`bedrock-mode` (`disabled` or `diagnostic`). Default increments/thresholds are 1/4,
decay is 0.25 per second, samples 3, cooldown 1000 ms. Geometry tolerance is 0.03
blocks, except GroundA uses a 0/1 contact mismatch and NoFallA uses 3 blocks of
unsupported falling. TimerA uses 150 milliseconds of surplus; BlinkA uses 0.03
packets of surplus beyond the configured burst count. Units are not interchangeable.
NaN, infinity, unknown keys/types and enabling unavailable models are rejected.
There is no option to disable uncertainty gates. Phase 8 punishment policy is separate and cannot use diagnostic evidence.

`exemptions.yml` supports `enabled`, `permission-bypass`, `creative-or-spectator`,
`flight` (all true on new installations), and exact-name boolean `worlds` mappings.
For example `worlds: {lobby: true, arena: false}`. Existing disabled exemption files
stay disabled. Disabling exemptions does not disable physics/health uncertainty.
Global bypass is `aegisac.bypass`; individual permissions are
`aegisac.bypass.speeda`, `aegisac.bypass.timera`, etc. Defaults are false, including
for operators/admins. Changes are sampled by the bounded owner-thread scheduler;
stale or future-dated samples cannot establish eligibility.

## Phase 5 combat policy

`checks/combat.yml` enables experimental combat diagnostics on new installations.
Existing files with `enabled: false` stay disabled. The same version-1 default merge
and all-or-nothing reload applies; administrators' comments/files are preserved.

Resource keys `maximum-targets` (64), `history-size` (8), `pending-attacks` (32), and
`statistics-size` (40) require a restart. `evidence-capacity` (32) and all other
threshold/policy keys can reload. A reload resets history/statistics/evidence when
its generation reaches each worker; snapshot reads hide pending old generations.

Default windows: history 2000 ms, interpolation 150 ms, swing matching 200 ms,
multiple targets 100 ms, velocity response 400 ms, reset grace 2000 ms, and maximum
owner/processing delay 250 ms. Minimum statistics is 24 and cannot exceed capacity.
Nominal target boxes expand by 0.1 blocks. Clicking requires variation at most .025
and repeat ratio at least .85 (intervals within 1 ms of the mean). Snap angle is
60 degrees, alignment 1 degree, rotation variation .02, tiny descent .03 blocks,
and projected velocity response .1 of the observed impulse. These are diagnostic
parameters, not production punishment thresholds.

Each ID has `enabled`, `tolerance`, `increment`, `decay-per-second`,
`buffer-threshold`, `minimum-samples`, `cooldown-ms` and `bedrock-mode`. The latter
accepts `diagnostic` or `disabled`, including UNKNOWN edition. Buffers remain zero
for uncertain runtime combat observations regardless of configured thresholds.
`exemptions.yml` applies to combat too. Bypass nodes default false. New command
messages are `combat-summary` and `cps-summary`; usage/evidence reuse existing templates.

## Phase 6 action and protocol policy

The world, player, inventory, protocol and exploit category documents now contain
live diagnostic policy. Fresh files enable implemented checks; old `enabled: false`
remains false. Unknown keys or attempts to enable any unavailable model reject the
entire reload, including when the enclosing category is disabled.

Category options: `enabled`, `evidence-capacity` (1–256, default 32), `grace-ms`
(0–60000, default 2000) and `maximum-delay-ms` (50–2000, default 250). Each rule has
`enabled`, `limit` (finite 0–1000000), `cooldown-ms` (1–60000, default 1000) and
`bedrock-mode` (`diagnostic` or `disabled`, including UNKNOWN edition).

`limit` uses the signal's units: request counts per bounded one-second window,
normalized payload bytes, additional BlockReach distance, reported inventory
movement distance, InvalidPitch excess degrees, or a 0/1 predicate. Default 0 means
a true predicate can emit diagnostic evidence. Unavailable entries stay disabled.
There are no ineffective buffer or punishment options in these category rules.

All these settings reload atomically and clear observations/evidence by generation.
Fixed rate windows (20 buckets per family) and completed-dig history (128 entries)
are bounded implementation budgets. Limits do not cancel incoming packets or enable
automatic punishment. Existing `performance.yml` resource settings still require a
restart. See [PHASE6.md](PHASE6.md) for exact default limits and trust boundaries.

## Phase 7 edition policy

All 72 IDs have Java enable flags and independent Bedrock mode rules. See
[BEDROCK.md](BEDROCK.md) for defaults, examples and topology requirements.
`BEDROCK_SUPPORTED` reuses a diagnostic predicate; `BEDROCK_ADJUSTED` applies
`base limit / sensitivity-multiplier + extra-tolerance`; `BEDROCK_DISABLED`
suppresses it. Sensitivity is finite 0.1–10, extra tolerance 0–10000. SUPPORTED
requires 1/0. Unavailable models cannot be enabled. Global/category/rule disables
and legacy `bedrock-mode: disabled` remain vetoes. UNKNOWN uses Bedrock policy.

`compatibility.yml: identity` exposes `floodgate`, `geyser`,
`negative-results-authoritative`, `java-only-server` (booleans),
`poll-interval-ms` (50–10000; default 1000), `queries-per-tick` (1–128; default 8
sessions, each querying at most two providers), and `maximum-age-ms` (100–30000;
default 3000, at least poll interval). Both topology assertions default false.

`profiles/bedrock.yml` exposes `capture-translated-history` (default true),
`history-size` (1–64 per lane, default 16), `history-max-age-ms` (100–30000, default
5000), and the complete `checks` map. All Phase 7 settings reload atomically,
invalidate old analysis and require no restart. Missing additions default-merge
without rewriting existing files. Invalid scalars/modes/bounds reject the entire
reload. Changing mode or sensitivity does not provide a missing native model.

New messages: `edition-summary` uses player, edition, source, device, input,
version and reasons; `bedrock-summary` uses player, status, histories and reasons.
All output remains plain informational chat.

## Phase 8 output and action configuration

All six existing documents remain version 1 and reload atomically. Old explicit
`enabled: false` values are preserved; missing fields merge without rewriting files.
See [PHASE8.md](PHASE8.md) for defaults and exact runtime contracts.

| Document | Settings |
| --- | --- |
| `alerts.yml` | enabled, chat, console, action-bar, hover, suggest-teleport, cooldown-ms (100–60000), server label, format and hover-format |
| `logging.yml` | enabled (default false), diagnostics, plain-text, maximum-file-bytes (4096–100000000) |
| `storage.yml` | enabled (default true; only used when logging is enabled), backend `sqlite`, maximum-rows (100–1000000) |
| `webhooks.yml` | enabled, URL, alerts/diagnostics/punishments/startup/errors switches, timeout-ms (250–10000), retry-limit (0–3), minimum-interval-ms (1000–60000) |
| `punishments.yml` | enabled, allow-experimental, window-ms/decay-ms (1000–300000), cooldown-ms (1000–3600000), maximum-evidence-age-ms (50–2000), rules for all 72 IDs |
| `setbacks.yml` | enabled, mode NONE/LAST_VALID_POSITION, allow-experimental, disable-in-panic, minimum-vl/confidence, cooldown-ms (1000–60000), maximum-age-ms (50–1000), maximum-distance (0.1–16), movement-check switches |

Each punishment rule contains enabled, minimum-vl (1–10000), minimum-category-risk
and minimum-total-risk (0–100000), minimum-confidence (0–100), minimum-findings
(1–256), risk-weight (0.1–100), confidence (0–100), and 1–8 console command strings.
Only commands support YAML sequences; nulls, object tags and mapping lists remain
rejected. Commands allow only player/uuid/check placeholders, no leading slash or
control characters. Every threshold must pass; experimental findings additionally
require explicit opt-in. No setting can score diagnostics or remove uncertainty.

Queues/history budgets are fixed: 1024 evidence records, 64 consumed/tick, 256 staff
deliveries, 128 delivered/tick, 128 staff subscriptions, 512 log jobs, 32 writes/batch,
64 webhook messages and 256 score entries/session. Output settings need no restart.
Changed policy invalidates old queued evidence and scores; already committed logs or
sent HTTP requests are not retracted. Old files can leave storage disabled, in which
case explicitly enable it to use SQLite. Panic is runtime state, preserved on reload.

Raw POSITION, VELOCITY_CANCEL, ACTION_CANCEL, BLOCK_CANCEL and ATTACK_CANCEL are
unavailable and rejected. Current uncertain physics does not yield a verified
LAST_VALID_POSITION; selecting that mode cannot manufacture one.

## Phase 9 administration settings

See [PHASE9.md](PHASE9.md) for the full GUI schema, permissions and transaction
contract. The enabled GUI has 2–6 rows, bounded `content-slots`, non-overlapping
`controls`, per-menu `titles`, per-style `items` (material/name/lore) and ordered
`dashboard-links`. Existing disabled GUI files remain disabled. Client values are
bounded and interpolated once, after template color processing.

`config.yml: command-aliases` is a map of at most eight lowercase names to booleans;
changing it requires restart. Base aliases cannot be replaced. Commands/menus can
persist a check flag, a punishment flag or a default/per-world difficulty label.
Edits require current generation, validated unchanged disk documents and a current
permission/session check. The exact prior edited file is retained in
`.last-admin-edit.bak`; its new form contains merged defaults. External edits require
reload before using the editor. No command writes arbitrary YAML paths or commands.

## Candidate preflight and upgrade

The offline tools command `preflight CONFIG_DIR --alert-only` validates a bounded
temporary copy, reports missing defaults/migrations and refuses enabled global
punishments/setbacks. It leaves operator files untouched. See UPGRADING.md for exact
schema preservation and rollback, and PHASE11.md for development-only JVM trace options.
