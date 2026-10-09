# Development candidate release procedure

Current version: `0.12.0-SNAPSHOT`, stage **development-candidate**. This is preparation
for observation, not a stable/production release. Live Paper acceptance, Folia/native
lifecycle and staged production observation remain open in `release/status.json`.
No formal tag/release or merge is created by the packaging procedure.

## Prepare and verify

Use JDK 21, the verified Gradle 9.8 wrapper, and Python 3.11+ for release tooling.
No new Java runtime dependency is added in Phase 12.

```sh
./gradlew clean build :aegis-tools:installDist --no-build-cache --console=plain
python3 -m unittest discover -s scripts/tests -v
python3 scripts/release.py check-docs
python3 scripts/release.py prepare /tmp/aegis-candidate-new
python3 scripts/release.py verify /tmp/aegis-candidate-new
```

Preparation requires committed source and a new output directory. `--allow-dirty` exists
only for local development validation and records that state in the manifest; such a
package is not a signed CI candidate. The output contains the shaded plugin, separate
offline tools ZIP, all 24 packaged defaults, operator/developer documentation, license,
`release-manifest.json` and `SHA256SUMS`. It excludes operator configurations, credentials,
real traces and live logs. Checksum verification rejects missing/extra/changed files,
traversal, symlinks and false Folia certification. It does not replace the full build.

The manifest records source commit, version, dirty state, configuration schema, stage,
compile baseline, runtime dependency and acceptance flags. All payload files are
checksummed. A checksum proves integrity against a trusted expected digest; it does
not by itself authenticate the publisher.

## GitHub provenance

The build workflow publishes `AegisAC-Phase12` candidate files after tests/package checks.
Pushes within this repository additionally run a separate, narrowly permitted job to
sign provenance through GitHub OIDC and Sigstore using the pinned official
`actions/attest` action. It attests the plugin/tools/manifests/checksum file and publishes
`AegisAC-Phase12-provenance` containing the signed bundle. No long-lived signing key or
user API secret is generated. PR builds remain read-only and have no signing permission.
Push and PR concurrency groups are distinct so a PR run does not cancel signing.

Use a current GitHub CLI with artifact attestation support:

```sh
cd /path/to/extracted-candidate
sha256sum -c SHA256SUMS
# Use the exact workflow/repository and trusted source commit/ref from the run.
gh attestation verify AegisAC-0.12.0-SNAPSHOT.jar --repo markroger12/spartan --signer-workflow markroger12/spartan/.github/workflows/build.yml
```

Inspect verified provenance for the intended source commit/ref and workflow run.
For offline bundle verification use `--bundle /path/to/attestation.sigstore.json` with
a CLI version supporting that flag; trusted roots/certificates must still validate.
CI performs verification with its installed CLI. A signed bundle authenticates the
build's identity and digest; it does not certify detection accuracy or close live gates.
If signing/verification fails, the candidate stays unsigned and that failure must be
reported. Do not invent a signature or call a workspace-only checksum a signed release.

## Promotion gates

Require the full clean build, upgrade/preflight tests, package integrity and verified
provenance; exact supported-version/client/provider records; a completed staged
alert-only observation and rollback drill; and resolution of material findings.
Unavailable models remain disclosed and actions default off. Folia cannot be enabled
from passing mocks: complete FOLIA.md's independent acceptance gates first.
Formal release/tag/merge requires a separate operator decision after reviewing evidence.
