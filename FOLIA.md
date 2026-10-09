# Folia support status and Phase 10 ownership audit

**Folia runtime support remains disabled in 0.12.0-SNAPSHOT. Phase 10 is unfinished.**
The scheduler and service migrations described in [PHASE10.md](PHASE10.md) have
fixture coverage. The descriptor has no `folia-supported` flag and bootstrap rejects
Folia before creating services. Adding the flag alone cannot enable this build.

The original request requires “Never access entities/world state from the wrong
region” and “Test Paper AND Folia independently.” Fixtures do not satisfy live testing.

## Source audit

| Component | Ownership and implemented behavior | Remaining validation |
| --- | --- | --- |
| Scheduler adapters | Separate native global/entity/region/async APIs; managed bounded tasks and cancellation | Native server migration/retirement/shutdown |
| SessionAudience | Exact player-session address or global console; permission checks on recipient | Real disconnect/reconnect while I/O completes |
| PlayerDirectory | Entity-owned immutable observations; region-local health and freshness | Region migration/cadence, viewer visibility API |
| WorldCaptureService / WorldCapture | Global budget, entity capture, whole-area ownership and per-entity bounds guards | Region borders, nearby foreign entities, unload and chunk-load absence |
| WorldInvalidationListener | Event-local metadata and shared atomic revisions | Cross-region event coverage and silent edits |
| PacketEvents / common target histories | Owner attach; normalized per-viewer scalar packet history; no foreign Bukkit target reads | Real PacketEvents, clients, proxy ordering and Folia API compatibility |
| AegisPlugin lifecycle | Entity startup reconciliation; UUID-striped attach/quit; stale address/session checks | Concurrent native disable, startup, retirement and dependency lifecycle |
| IdentityService | Concurrent session membership; global bindings/query budget; atomic invalidation and publication epoch checks | Actual public-provider API thread contracts and deployments |
| OutputService / events | Source entity effects; recipient entity messages; concurrent bounded queues; listeners outside ledger monitors | Native event dispatch and plugin integrations |
| Console punishments | Source-owner grant per command; global dispatch; 100 ms grant expiry and data-only fences | Actual command implementations and authorization-window acceptance |
| VerifiedSetback | Async native teleport with verified, loaded, already-owned destination geometry; foreign destination refused | Native completion, cancellation, region transfer, disconnect/world shutdown |
| Commands / profiles | Directory identities and immutable target observations; requester visibility API | Native command routing and visibility behavior |
| MenuService | Viewer-owned inventory access, concurrent bounded membership, snapshot target/world checks | Native shutdown cleanup and client inventory acceptance |
| AdminService | Target-owner mutation, requester-owner completion, exact-record freeze expiry and data-only close | Remote controls and permissions during migration/reload |
| Aliases / capabilities | Lifecycle/global metadata and registration | Native command-map cleanup |
| Async logging / webhooks | Dedicated bounded workers, immutable payloads | Shutdown ordering under actual region callbacks |

## Remaining release gates

1. Validate native lifecycle and inventory teardown on the selected Folia version.
   `MenuService.close()` closes only inventories owned by the current thread and
   invalidates all menu state. It deliberately does not perform foreign inventory
   access or submit supposedly guaranteed work after disable. The upstream native
   entity scheduler rejects disabled-plugin tasks and also suppresses disabled-plugin
   callbacks. Real server shutdown/hot-disable behavior therefore needs an explicit
   tested strategy before claiming complete GUI support.
2. Run the migrated services with the native backend, real PacketEvents and any enabled
   Floodgate/Geyser providers. Public API presence alone does not prove thread safety.
3. Validate command grants and async corrections under real concurrency. Console command
   implementations must route their own target operations correctly. Async corrections
   refuse foreign destination geometry and cannot be rolled back once initiated.
4. Pass independent Paper and Folia acceptance runs and record exact versions and logs.
   Only then select the native backend, add the descriptor flag and publish a supported
   runtime matrix. Production does not have a user-facing override for these gates.

## Independent live acceptance protocol

Use isolated operator-controlled Paper **and** Folia servers with compatible
PacketEvents builds and existing EULA agreements. Record Java, server, dependency,
client and optional provider versions; preserve logs independently.

Exercise two players in distinct ticking regions, region migration, cross-region
combat, adjacent foreign chunks/entities, reload while work is queued, GUI/profile
inspection and edits, remote freeze/teleport/unfreeze, provider loss, reconnect,
server/world shutdown and cancellation during callbacks. Verify ownership assertions,
no chunk loads from capture, bounded tasks/queues, stale-session rejection, retained
diagnostic gates and inventory/worker cleanup. Test actual third-party punishment
commands and native teleport outcomes separately from synthetic evidence eligibility.

No configured live test servers or existing EULA files were found in this workspace.
Test-server availability was requested and remains unresolved. No live certification
or EULA acceptance is implied by the build/test workflow.
