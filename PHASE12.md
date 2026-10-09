# Phase 12 — documentation and release preparation

`0.12.0-SNAPSHOT` adds a truthful compatibility matrix, immutable historical upgrade
fixtures, a non-mutating offline configuration preflight, operator upgrade/rollback
and staged observation procedures, integrity-checked candidate packaging and a
GitHub OIDC/Sigstore provenance workflow.

The historical fixture contains all 24 exact Phase 11 defaults from commit
`e7a5ab9fe0a308d2aaf0c64c3ef7b5dd47294115`. Tests cover byte-preserving current-schema
upgrades, disabled categories/custom messages, legacy backups/idempotence,
future-schema rejection and restart-only settings retaining active generations.
Preflight copies only bounded known YAML files to a temporary directory, uses the
real loader, and removes its temporary files. Source configuration is never migrated
or defaulted by that command. `--alert-only` requires both global action switches off.

```sh
./gradlew :aegis-tools:installDist
./aegis-tools/build/install/aegis-tools/bin/aegis-tools preflight /path/to/config --alert-only
```

Release tooling uses Python standard library only; the ordinary Java runtime/dependency
locks are unchanged. Candidate integrity tests cover missing/extra/tampered files,
traversal, symlinks, duplicate entries and unsupported certification. CI compiles/tests,
checks docs/package integrity, uploads a complete candidate, and signs/verifies push
provenance in an isolated job with no PR signing privileges.

Documentation: COMPATIBILITY.md, UPGRADING.md, OPERATIONS.md and RELEASE.md. Current
README/API/check catalog/version references and PROJECT_TREE.md are audited. The
existing Folia rejection, experimental models, disabled actions and uncertainty
requirements remain explicit.

The user identified OuiPanel server `d5579107` for a live trial. The provided console
URL does not supply authentication, current PacketEvents version or completed trial
results. Staged live observation and platform/provider certification are therefore
pending evidence. This milestone prepares a development candidate; it does not certify
a production anticheat or complete every earlier unavailable model.
