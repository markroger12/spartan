# Phase 10: scheduling and service ownership

**0.10.0-SNAPSHOT advances Phase 10; Folia runtime support remains gated.**
The native scheduler and service handoffs have fixture coverage. Independent Paper
and Folia runs, including native inventory teardown and dependency integration,
are still required. `plugin.yml` omits `folia-supported`, and bootstrap rejects Folia.
See [FOLIA.md](FOLIA.md) for the component audit and release gates.

## Scheduling and lifecycle

`PlatformScheduler` separates global, entity, region and asynchronous work.
`BukkitPlatformScheduler` maps tick work to the conventional primary thread;
`FoliaPlatformScheduler` uses native GlobalRegionScheduler, EntityScheduler,
RegionScheduler and AsyncScheduler APIs with no Bukkit fallback. Production uses
the conventional adapter until the release gate passes.

`ManagedTasks` caps outstanding tasks at 4096. It handles cancellation before native
handle publication, completion, retirement, exceptions, recurring tasks and shutdown.
Tick delays are at least one; asynchronous delays use milliseconds. Close rejects
new work and cancels only owned handles. Cancellation cannot undo an executing
callback. Retirement cleanup is data-only because Folia restricts retirement context.
Dedicated bounded packet, configuration, logging and webhook workers remain separate.

Startup reconciliation schedules foreign players onto their entity owners. Join and
quit operations serialize by UUID stripe, and delayed reconciliation cannot replace
an attached session. A quit from an older player address cannot detach a new session.
`SessionAudience` binds replies to the exact player session or console scheduler.
Completions check permissions on the recipient owner; command blocks are not promoted
to console audiences. Workers retain entity addresses solely for scheduling.

## Player observations, identity and world capture

`PlayerDirectory` retains scheduling addresses and publishes immutable world, ping,
effect and environment observations from each player's owner. Reads reject replaced
sessions, changed loss epochs and samples older than two seconds. A recurring entity
task samples region-local tick cadence; health older than 250 ms becomes unknown.
Global Folia cadence is never substituted for region TPS. This is cadence, not MSPT.

Provider bindings and queries stay on the explicit global coordinator. Identity
membership can change from entity callbacks using concurrent entries and short queue
locks. Provider lifecycle changes invalidate the atomic epoch immediately. A query
captures its epoch before reading providers and cannot publish under a newer epoch.
A positive Bedrock latch remains conservative across provider loss. Live compatibility
with particular Floodgate/Geyser deployments still needs verification.

`WorldCaptureService` schedules budgeted capture work from its global coordinator,
with one pending capture per session. Capture reads require entity ownership and
ownership of the entire geometry area, including collision-neighbour chunks. Foreign
areas produce empty geometry with `REGION_NOT_OWNED`, never trusted air. Nearby entity
bounds require individual ownership; skipped entities count toward the iteration cap.
World invalidation uses IDs and atomic revisions, without foreign live entity reads.

Combat continues to use per-viewer normalized outbound target histories. It does not
read opponents' live state from the attacker's region or assume client acknowledgement.
No Bukkit objects enter common packet/physics workers.

## Output and corrections

The global output coordinator drains bounded evidence/delivery queues. Flags, scoring
and cancellable effects execute on the source player's owner; alert permissions and
message delivery execute on each recipient's owner. Deliveries bind both the source
evidence and the recipient session. Reconnect, loss, policy change and expiry reject
old work. Subscriptions and delivery admission are concurrent and bounded. Plugin
listeners execute outside ledger monitors to avoid cross-region lock dependencies.

Native console punishment dispatch obtains a source-owner authorization for each
command, then submits it to the global scheduler. The grant expires after 100 ms;
session, evidence, policy, age and panic are rechecked without global player reads.
Subsequent commands return to the source owner for another live permission/state
check. A permission change after a grant is a bounded cross-region authorization
window, not an atomic cross-region transaction. Arbitrary command implementations
must themselves support Folia and pass the live acceptance matrix.

`VerifiedSetback` retains the conventional synchronous path and adds a native async
path. Both validate verified evidence, dimensions, distance, border and bounded loaded
collision geometry. Async correction accepts only destination geometry already owned
by the source region; foreign/unloaded geometry refuses correction. It rechecks the
source authorization before `teleportAsync`, never waits on the future, and allows
one in-flight correction per session. Completion uses data-only session checks.
Native teleport can continue after initiation; disabling the plugin cannot retract it.
Current speculative runtime physics still cannot manufacture verified candidates.

## Staff tools

Commands, completion, risk ranking and profiles use directory identities and immutable
observations. Visibility is checked by the viewer's `canSee` API; targets are identity
addresses, not foreign live inspection. Remote controls mutate on the target owner
and reply on the original requester's owner. Replaced requesters/targets reject queued
controls. GUI actions retain viewer/session/configuration/target-world validation.

Menu membership and freeze records use concurrent maps with bounded admission.
Freeze expiry dispatches to the target owner and compares the exact freeze record,
so an old expiry cannot release a replacement freeze. Shutdown clears shared freeze
state without foreign player reads. Conventional inventory cleanup remains immediate.
Native disable invalidates menu state but cannot assume entity callbacks remain
available after the plugin is disabled; that teardown behavior is a release gate.

## Validation

[VALIDATION.md](VALIDATION.md) records the clean build, current test counts and jar
checksum. Separate native API fixtures and distinct-owner service harnesses exercise
routing, retirement, cancellation, freshness, session replacement, provider invalidation,
remote freezes, recipient permissions, async teleport and global command grants.
MockBukkit retains the conventional command/menu/bootstrap regression suite.

These checks do not certify a live Minecraft runtime. No live test servers or existing
EULA files are configured in this workspace. The pending test-server availability
question must be resolved before independent Paper/Folia acceptance can proceed.
