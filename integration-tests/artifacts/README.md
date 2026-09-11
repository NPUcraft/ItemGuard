# Upgrade-test artifacts

Do **not** commit large historical JARs unless the project explicitly decides to.

## After every release, keep locally

- `ItemGuard-<version>.jar`
- `ItemGuard-<version>.sha256`
- matching `RELEASE-NOTES-<version>.md` / `CHANGELOG.md`
- source identity: git tag (when git exists), for example `v1.0.0-RC2`
- build environment: Java 21, Paper target, Gradle wrapper version

Bind **artifact ↔ checksum ↔ git tag**. This workspace currently has **no git repository**, so tags cannot be created here yet.

## Original RC1

Expected checksum (also in `releases.json`):

```text
11581d2fa2a61198a491ef680181f1fc5041e413c54fdbb1f0c54c3d5eeda87b
```

Place a matching file at:

```text
integration-tests/artifacts/ItemGuard-1.0.0-RC1.jar
```

or pass `-PRc1Jar=` / `ITEMGUARD_RC1_JAR`.

A later workspace build that happens to still be named `ItemGuard-1.0.0-RC1.jar` is **not** RC1. Checksum mismatch fails as `ARTIFACT_MISMATCH` / `Wrong RC1 artifact.`

## Checksums file

`integration-tests/artifacts/releases.json` is the list of published hashes. `upgradeIntegrationTest` reads RC1 from that file. Do not scatter extra historical checksums through Java/JS/Gradle.
