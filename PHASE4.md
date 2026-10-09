# Phase 4: experimental movement checks

This iteration implements **15 experimental evaluators**, independent buffers,
bounded evidence, uncertainty/grace gates and safe-position candidates. Three
roadmap entries remain **unavailable**: NoSlowA needs acknowledged active-item
state, ElytraA needs a validated glide/firework model, and VehicleA needs
vehicle-specific state, dimensions and physics. Enabling them is rejected rather
than claiming working detection. Full Phase 4 special-movement coverage remains
unfinished until those model prerequisites and live trace validation exist.

There is **no enforcement**: no setback, cancellation, kick, ban, global risk score,
persistent violation log or punishment command. These belong to later phases.
No output from this release should be wired directly to automatic punishment.

## Check contracts and evidence

`Check<T>` defines pure evaluation. `MovementCheck` evaluates geometry/context;
`MovementTiming` evaluates movement-only packet arrivals. `MovementDispatcher`
owns each player's configuration generation, per-ID `CheckBuffer`, bounded evidence
ring and `SafePositionTracker`. `MovementMonitor` supplies lifecycle and health gates.
All this state is serialized by the existing per-session packet worker.

Geometry evaluation uses the **full candidate set** produced by Phase 3, with
horizontal distance, vertical interval and horizontal acceleration alternatives.
It does not substitute the closest candidate's residual for every possible legal
input. Uncertainty is unioned across alternatives. Collision checks use swept AABBs;
current and previous support use a thin foot volume, not the client ground bit.
Observed displacement outside captured coverage adds uncertainty and triggers no
additional world scan. The latest analysis frame may retain one snapshot in
addition to the latest captured snapshot; both are bounded and cleared on reset.

The check catalog and precise signals are in [CHECKS.md](CHECKS.md). Related IDs
share inputs; Speed/Sprint/Sneak/Acceleration and Fly/Gravity/AirJump are correlated
signals. Their counts are **not independent evidence multipliers**.

Each evaluation is one of: disabled, unavailable, suppressed/not applicable, pass,
buffering, diagnostic, or experimental finding. A mismatch must exceed that
check's configured tolerance, accumulate a bounded buffer, meet consecutive-sample
requirements and satisfy its cooldown before a finding is recorded. Buffer decay
uses elapsed monotonic seconds, not packet count. Time reversal resets the buffer;
ordinary nanoTime wrap is supported. Passing observations break the sample streak.

**Any uncertainty resets the check buffer to zero.** A raw mismatch can instead
produce a cooldown-limited diagnostic record containing the exact reason set.
Diagnostics cannot increase a buffer, finding count or trusted safe-position
streak. Explicit permission/world/gamemode exemptions suppress diagnostic records
as well. Per-check disablement leaves other check policies independent.

`MovementEvidence` records check ID, packet sequence, observation time, configuration
generation, numeric observed/expected/excess values, diagnostic classification and
reasons. The ring has a configurable fixed capacity (32 by default). This is current
session evidence, not complete trace storage or a database. Lifecycle/configuration
resets clear it. No chat/console alert is broadcast for each finding in this phase.

## What runtime geometry can establish

Phase 3 reanchors to reported positions and uses server-observed geometry. It has
no acknowledged client-world/input history. Every runtime geometry evaluation
therefore retains `INPUT_INFERRED` and `UNACKNOWLEDGED_WORLD`. Unknown edition also
retains `UNKNOWN_EDITION`; official identity adapters are still Phase 7.

Consequently, **current runtime geometry produces diagnostics, not trusted buffer
increments**. The trusted evaluation path is tested with explicit synthetic
complete-input fixtures, but no setting upgrades runtime observations into those
fixtures. No option disables these truthfulness gates. This is a concrete prerequisite
for useful enforcement-grade movement detection, not a detection-accuracy claim.
Climb/liquid/step and other partial mechanics retain their Phase 3 uncertainty.

## Timer and blink analysis

Only inbound PLAY movement packets count, including rotation-only and ground-only
reports. Unrelated traffic cannot become movement ticks. A nominal tick is 50 ms.
TimerA accumulates consumed tick time minus actual observed elapsed time, with
bounded negative credit for brief bunching and capped positive debt. It evaluates
completed windows (two seconds by default), not each short arrival gap. Default
buffer decay/sample requirements require sustained excess across multiple windows.

BlinkA looks for movement silence followed by a configured packet burst within a
short window. Silence alone is not evidence. This is an arrival-pattern observation,
not proof of deliberate packet withholding: network loss, transport batching and
translation can produce similar patterns. Impaired health clears timer debt but
preserves enough arrival history to describe a diagnostic burst; it cannot become
a trusted finding while uncertainty/grace is active.

Timing checks do not require a collision snapshot, but do require known protocol,
usable owner/health observations and non-impaired connection state for trusted
buffering. Bedrock/UNKNOWN identities are diagnostic or disabled per rule. Constant
high RTT alone is not a universal exemption; actual jitter, delay, loss and stalls
are gated. RTT does not turn an observation timestamp into a physical write timestamp.

## Health, lifecycle and exemptions

An owning-thread timer publishes measured **tick cadence**, not MSPT. This measures
intervals between scheduled callbacks, not CPU time spent in a server tick.
Workers receive immutable tick and owner-policy observations. Bounded round-robin
sampling reads permissions, world, gamemode, flight, vehicle and glide state; it
continues even when collision capture is disabled. No Bukkit access occurs inside
checks, packet callbacks or workers.

Unknown, expired or newer-than-packet owner/health samples cannot establish
eligibility. Excessive worker/tick delay, connection uncertainty and jitter suppress
buffering and extend lag grace. Join, local velocity/explosion, teleport, world
reset, queue loss and configuration changes reset the appropriate state. Other
entities' velocity cannot clear this session's check evidence. Phase 3 may still
conservatively reset its predictor for such a packet, leaving geometry unavailable.

`exemptions.yml` controls explicit world, permission, creative/spectator and flight
exemptions. Global bypass is `aegisac.bypass`, with individual permissions such as
`aegisac.bypass.speeda`. They default **false**, including for operators/admins.
Permission changes take effect when owner state is next sampled. Even with explicit
exemptions disabled, missing models, stale observations and physical uncertainty
still prevent trusted analysis. No universal high-ping bypass is introduced.

## Safe-position candidates

The tracker retains one immutable candidate with world/session identity, world
revision, observation time, coordinates, reasons and consecutive trusted samples.
A candidate requires a collision-clear supported position, no liquid/climbable
contact and no evaluator mismatch. Missing/disabled checks and explicit bypasses
prevent trusted promotion. A mismatch, loss, teleport, reload or unavailable
movement frame resets the candidate/streak. Revision mismatch or age expiry hides
it from reads; it is not blindly reused after a block changes.

Presence is not trust: `verified=false` means merely a geometric candidate.
**Current runtime candidates cannot become verified**, because their world and
input remain unacknowledged. Complete trusted fixtures test the promotion path.
No candidate is teleported to, and no safe-position API implies that a setback
has already been validated or performed. Future setbacks must revalidate session,
world, loaded chunks, collision and policy on the owning thread.

## Configuration and operations

`checks/movement.yml` is a complete, live-reloadable immutable policy. Each packet
reads one generation. A changed generation clears check buffers, timing debt,
evidence and safe-position streaks before subsequent evaluation. API reads hide
an older generation while its worker has not yet processed the change. Invalid
reloads retain the entire old generation. Existing version-1 files inherit missing
keys without rewrite; explicit old `enabled: false` remains false.

New installs enable the 15 implemented evaluators in experimental observation mode.
The three missing models remain disabled and cannot be enabled. Per-check tolerance,
buffer increment/decay/threshold, minimum samples, cooldown and Bedrock policy are
configurable. Grace, timing windows, evidence capacity and health limits are also
configurable; see [CONFIGURATION.md](CONFIGURATION.md). Difficulty profile names
remain metadata/observe-only policies and do not secretly multiply these rules.

- `/ac checks`: catalog with experimental/disabled/unavailable status.
- `/ac movement <player>`: per-check status, experimental finding/diagnostic counts,
  and the latest retained evidence. Counts can reset on lifecycle/policy changes.
- `/ac safeposition <player>`: candidate coordinates and explicit verification status.

Permissions follow command names; admin grants these commands but not bypass.
Messages are editable and reloadable. The read-only API adds
`PlayerSnapshot.movementChecks()`; pre-release integrations must rebuild for 0.4.0.
No new dependencies, runtime credentials or external services are required.

## Validation limits

Tests exercise legitimate and injected-mismatch numeric fixtures for all 13 geometry
evaluators; nominal, fast and bunched timing; repeated/isolated evidence; decay,
cooldown and monotonic wrap; per-check independence; uncertain/bypassed suppression;
config preservation; candidate eligibility/expiry/revision; tick cadence; local vs
remote impulses; raw movement pipeline replay; losses, reloads, disconnects and
commands. The existing Phase 1–3 suites still run.

These are synthetic regressions, including a Phase 3-generated jump replay, not
an independent live-client corpus. Paper/Spigot/Purpur, proxies, Geyser, Folia,
large-server load and enforcement-grade accuracy remain unverified. Three special
models remain unavailable. See [VALIDATION.md](VALIDATION.md) for exact execution
results and the packaged artifact checksum.
