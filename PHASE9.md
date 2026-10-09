# Phase 9: staff commands, inventory menus and persistent edits

AegisAC 0.9.0 adds the remaining staff commands, thirteen paginated inventory menus,
validated persistent edits and additional command aliases. Existing alert chat,
action-bar, hover and teleport-command suggestions remain available. This phase
adds administration; detection models and their experimental eligibility limits
are unchanged. No new dependency is required.

## Commands and permissions

All commands retain `/ac` and `/aegisac`. Permissions default to operator and are
children of `aegisac.admin`, except actual player bypass permissions, which default
false. Giving someone command access does not exempt them from checks.

| Command | Permission | Behavior |
| --- | --- | --- |
| `debug <player>` | `aegisac.debug` | One-shot session/loss summary and at most 16 packet kind/direction/sequence/time rows; no raw payload |
| `toggle <check>` | `aegisac.toggle` | Persist one check's enabled flag; unavailable models cannot be enabled |
| `reset <player>` | `aegisac.reset` | Clear current VL/recent output, invalidate analysis and queued evidence; preserve durable logs and punishment cooldowns |
| `exempt <player> <check\|*> <time>` | `aegisac.exempt` | Temporary per-session check exemption |
| `bypass <player> <check\|*> <time>` | `aegisac.bypass.manage` | Same temporary exemption; distinct from the actual `aegisac.bypass` exemption permission |
| `freeze <player> [time]` | `aegisac.freeze` | Hold position while allowing rotation; default 60 seconds, maximum 10 minutes |
| `unfreeze <player>` | `aegisac.unfreeze` | End a staff freeze without removing an independent timed exemption |
| `top` | `aegisac.top` | Up to ten visible online players ordered by current experimental risk, not cheating verdicts |
| `gui [player]` | `aegisac.gui` plus relevant view permission | Dashboard or a player's profile |
| `checks [category]` | `aegisac.checks` | All checks or combat/movement/world/player/inventory/protocol/exploit |
| `profile <player>` | `aegisac.profile` | Edition/source, version, brand, server/transaction ping, jitter, tick TPS, lag uncertainty, observed CPS, scores, effects, environment, profile and last alert |

All previous commands remain; see README. Completion is permission-aware. Online
inspection resolves visible, active sessions. Durable log lookup can also use an
explicit UUID. Durations use `s`, `m` or `h` (for example `30s`, `5m`, `1h`);
exemptions allow 1 second to 1 hour. Check names are case-insensitive.

Manual exemptions feed all three monitor families independently of permission
exemption settings and block scoring/queued effects. Starting one invalidates old
analysis; expiry resets monitor buffers before new analysis. They end on reconnect.
At most the 72 known IDs plus `*` can be stored per session. Freeze state is separate,
capped at 128 players, suppresses checks while active and marks a packet gap on
start/end. It cancels movement translation, direct interaction, block actions,
direct player attacks and inventory clicks/drags. Server teleports, death, quit,
expiry or plugin disable release it. It is a temporary staff hold, not a jail or
anti-escape system; it does not block commands or already airborne projectiles.
Staff control actions are recorded in the server console.

## Menus

`gui.yml` configures enabled state, 2–6 inventory rows, ordered content slots,
navigation/action slots, titles, materials, names, lore, colors and dashboard links.
Defaults use 54 slots, 36 paginated content slots and distinct controls. Duplicate,
overlapping/out-of-range slots, invalid materials, unknown keys and excessive text
or lists reject the entire configuration reload. Text uses legacy `&` colors and
one-pass `%placeholder%` substitution; client-provided values remain literal.

Menus: Dashboard, Checks, Combat, Movement, World, Player, Inventory, Protocol,
Exploit, Players, Player Profile, Violation History and Settings. Check menus require
`aegisac.checks`; Players/Profile require `aegisac.profile`; history requires
`aegisac.violations`; Settings requires profile-edit or punishment permission.

- Left-click a check to toggle its flag (`aegisac.toggle`). Category and edition
  policy still apply; the GUI shows disabled categories and unavailable models.
- Right-click a check to toggle its punishment rule (`aegisac.punishment`). This
  does not enable global punishment or bypass confidence/freshness/identity gates.
- Settings can toggle global punishment (`aegisac.punishment`) or cycle the default
  difficulty (`aegisac.profile.set`). Player Profile can cycle that player's current
  world's difficulty using the same permission. These named difficulty profiles
  remain observe-only labels; they do not multiply check thresholds or override edition.
- Player Profile can reset current VL (`aegisac.reset`) and open the last 32 accepted
  session output records. History is read-only and may include diagnostics. It is
  cleared with reset/session turnover/new output context; use `/ac logs` for durable
  SQLite history. A history entry alone does not establish current actionable risk.

Actions are held on the server and keyed by inventory identity/slot. Item labels
cannot authorize actions. All clicks, including bottom-inventory shift/number-key,
double-click, offhand and outside clicks, and all drags are cancelled. Ordinary
left/right actions run next tick, rechecking permissions and sessions. An earlier
listener's cancellation is respected. Menus are limited to 128 simultaneous viewers and one scheduled click action per
menu per tick.
Stale configuration, replaced viewer/target sessions, changed target worlds and
revoked access invalidate menus. Closing the menu before the final authorization
check rejects a queued edit. After a rejection, reopen it before retrying.

## Persistent transactions

One daemon processes at most eight waiting edits. The server thread performs no
configuration disk I/O. Only check enablement, per-check/global punishment enablement,
and default/per-world profile selection can be written through this editor.

1. Require the exact expected active generation.
2. Copy all 24 bounded documents to temporary staging without following file links.
3. Validate their semantic equality with active settings; external edits require reload.
4. Apply the single permitted scalar edit and validate every staged document,
   including GUI materials and unavailable-model/punishment constraints.
5. Recheck the requester's permission/session and menu state on the server thread
   with a bounded five-second wait on the worker. The server thread does not wait.
6. Compare source bytes again, save the previous target bytes to the adjacent
   `<file>.last-admin-edit.bak`, atomically replace the one file, then publish the
   complete immutable configuration generation.

The edited file is serialized with merged defaults. Original comments/formatting
are preserved in the exact-byte backup; only the latest admin-edit backup is kept.
Normal reload does not rewrite schema-1 files. Unrelated documents are not rewritten.
Invalid/stale/revoked edits do not publish or overwrite the target. A failed atomic
move rejects the edit. An external writer racing *after* the final byte comparison
is not coordinated by an OS compare-and-swap; coordinate manual edits with staff.
Backup and target are individual atomic moves, not a filesystem-wide transaction.
Existing pipeline/physics/combat resource changes still require restart.

## Additional aliases and upgrades

`config.yml` accepts up to eight additional lowercase alias names mapped to booleans:

```yaml
command-aliases:
  staffac: true
```

Aliases require a full restart. `/ac` and `/aegisac` are permanent. Names already
registered by another plugin are skipped with a warning. Alias execution delegates
to the same permission checks; disabling AegisAC unregisters only commands it owns.
Additional aliases require the platform's public command-map accessors. Older server
implementations without those accessors must leave the additional-alias map empty.

Fresh installs enable GUI menus. Existing `gui.yml` files with `enabled: false`
remain disabled: set `enabled: true` and `/ac reload` to opt in. Other new settings
merge in memory without rewriting old files. Keep one AegisAC jar plus the required
standalone PacketEvents plugin in `plugins/`, and restart fully when upgrading jars.

## Validation and limits

See VALIDATION.md for executed tests and artifact checksum. Core tests cover staged
writes, backup fidelity, restarts, generation/disk conflicts, revoked authorization,
symlinks, schema limits and timed exemption lifecycle. MockBukkit tests cover all
menus, normal edits, permissions, stale sessions, inventory-generated actions,
freeze behavior, aliases and shutdown. Output tests cover reset/queued evidence,
late exemptions and bounded history. Real client inventory behavior, live server
compatibility and production detection accuracy still require separate validation.
Automatic punishment/setbacks remain disabled by default. Phase 10 is the separate
Folia/platform scheduling gate; this release continues to reject Folia.
