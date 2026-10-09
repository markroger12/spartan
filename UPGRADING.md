# Upgrade and rollback

Use a maintenance window and a complete server stop for jar replacement. `/ac reload`
loads settings; it does not replace jars or reinject PacketEvents. Avoid server `/reload`
and plugin hot unloaders.

1. Record the current AegisAC, Paper/Minecraft, Java and PacketEvents versions.
   Check COMPATIBILITY.md. Back up the installed jar and the entire `plugins/AegisAC/`
   directory, including YAML, `.bak` files, logs and SQLite. Stop first so the database
   backup is consistent; preserve any WAL/SHM files present with the backup.
2. Download the candidate and verify its checksums/provenance as described in RELEASE.md.
   Install only `AegisAC-0.12.0-SNAPSHOT.jar`; do not install sources, unbundled or tools jars.
3. Run the offline tools against the existing configuration:

   ```sh
   ./aegis-tools/bin/aegis-tools preflight /path/to/plugins/AegisAC --alert-only
   ```

   This reads a bounded temporary copy. Missing defaults and version-0 migrations are
   reported; originals are not changed. A successful preflight checks configuration,
   not live platform compatibility. A failed check must be corrected before the trial.
4. Keep global `punishments.yml: enabled: false` and `setbacks.yml: enabled: false`.
   For a new alert-only trial leave webhooks disabled, then follow OPERATIONS.md.
5. Replace the plugin jar while stopped, retain compatible standalone PacketEvents,
   restart normally and confirm successful enablement. Record the startup configuration
   generation and exact versions. Inspect known Java/Bedrock identities, packet counts,
   diagnostics, reload rejection and reconnect/session cleanup.
6. Preserve the old installation until the observation period and rollback drill finish.

## Supported configuration upgrade behavior

All 24 bundled documents retain schema 1. Missing files are installed; missing current
keys merge in memory without rewriting existing files. Explicit disables, custom
messages, thresholds and comments are preserved. Old customized performance messages
need the new Phase 11 placeholders added manually to display those metrics.

Version 0 upgrades rename `config.yml: profile` to `default-profile`, merge defaults,
write schema 1 and retain an exact-byte adjacent `.bak`. Both names at once are ambiguous
and rejected. All documents validate before migrations are written. Unknown keys, nulls,
unsafe tags, aliases and future schemas fail explicitly. Failure retains the active
configuration generation; some new missing default files may already have been installed.
Filesystem migrations are not a whole-directory transaction: restore the backup if disk
I/O fails during migration.

Restart-only settings include aliases, packet/physics resource budgets and combat
history capacities. Editing those on disk and running `/ac reload` is rejected; the
next full restart can load them. GUI/admin edits preserve an adjacent `.last-admin-edit.bak`.
The tests include the exact Phase 11 default snapshot from commit
`e7a5ab9fe0a308d2aaf0c64c3ef7b5dd47294115`, custom disabled categories/messages, legacy
migration, future-schema rejection and restart-only publication fences.

## Rollback

Stop the server. Restore the old jar **and its matching complete configuration/data
backup**; a jar alone cannot undo schema or persisted policy changes. Preserve trial
logs separately for diagnosis. Restore the matching PacketEvents/server versions if
those changed as part of the trial. Restart, check enablement, identities and session
counts, and keep actions disabled. Do not downgrade a future schema by editing its
version number. Database/log retention may have removed old records; a backup is the
rollback source. No external Discord message or already executed action can be retracted.
