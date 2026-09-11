# Changelog

## 1.0.0-RC2

Forensic JSONL logging. Exact RC1-binary upgrade is still blocked without the original JAR. RC1 YAML compatibility is covered separately. Detection, risk, and scanner behavior are unchanged from RC1.

### Added

- Asynchronous forensic JSONL logging
- Dedicated `plugins/ItemGuard/logs/` with `alerts/`, `events/`, and `admin/`
- Persistence for staff alerts, unexplained gains, invalid items, HuskSync apply summaries, admin actions, and high-value flows
- Rotation, retention, and a bounded dual queue (urgent/bulk)
- Bundled `logging.yml` (created only when missing)
- Real Paper RC1 → RC2 upgrade integration test (`gradlew.bat upgradeIntegrationTest`) — requires the original RC1 JAR checksum
- RC1 YAML compatibility test (`gradlew.bat legacyConfigCompatibilityTest`) — does **not** replace exact artifact upgrade
- Reproducible JAR output (`preserveFileTimestamps = false`, `reproducibleFileOrder = true`)

### Changed

- First install now also creates `logging.yml`
- `/ig status` includes forensic logger health and queue info
- Bundled YAML install is centralized in `ConfigResourcePolicy`: copy from the JAR only when the destination file is missing

### Fixed

- No RC1 config-overwrite bug was observed in this workspace; the original published RC1 JAR is not present here to run the Paper upgrade gate

### Tested

- Unit tests: 82/82 PASS
- Real Paper 1.21.8 + Mineflayer protocol 772 (14/14 single-server)
- Velocity + Paper A/B + HuskSync 3.8.7 + MariaDB + Redis + Mineflayer (HS-001…HS-005)
- `legacyConfigCompatibilityTest`: PASS (`CONFIG_COMPATIBILITY_UPGRADE` only)
- `upgradeIntegrationTest`: FAIL `ARTIFACT_MISMATCH` — original RC1 JAR SHA-256 `11581d2fa2a61198a491ef680181f1fc5041e413c54fdbb1f0c54c3d5eeda87b` is still missing
- Two sequential `clean build` JARs matched SHA-256 `981ed2d26e75768c1bbf4c06fe568e22052c89b8131412c62ba7be69a8edc50f`

### Known Limitations

- `/give` and unintegrated `addItem` may be `UNKNOWN` unless the other plugin uses `ItemGuardApi`
- Anvil / grindstone / brewing have no full transform provenance
- Paper-invisible item data is not scanned
- HuskSync coverage is 3.8.7 + Paper 1.21.8 only
- HS-006 / HS-007 / HS-008 are not automated
- No auto-punish, dashboard, fingerprint history, or ItemGuard Redis
- A JVM crash, forced process kill, or host failure may lose a small number of forensic records that had not yet been flushed

## 1.0.0-RC1

First release candidate for Paper 1.21.8. Detection behavior matches the SNAPSHOT build that already passed real Paper and HuskSync automation; this cut is about defaults, docs, packaging, and install safety.

### Added

- Bundled default configs with operator-facing comments (heartbeat is game ticks, signal TTL, HuskSync tested version, item weights are not prices)
- Trial key risk weights (`TRIAL_KEY`, `OMINOUS_TRIAL_KEY`)
- `bukkit` / `paper` ignored PDC namespaces so platform keys are not treated as unknown custom data
- Lightweight tab completion (permission-filtered subcommands, durations, material prefix)
- `/ig trace` rejects unknown materials instead of silently returning an empty timeline
- Clean-install smoke test (`gradlew.bat releaseSmokeTest`)
- `packageRelease` task writing `build/release/` (JAR, SHA-256, notes)

### Fixed

- Admin `/ig scan` debounce could suppress findings
- High-value burst could repeat without a new incoming gain
- Partial shift-click could leave a stale expected-flow credit
- HuskSync baseline / timeout handling around apply
- Bundle and shulker nested totals treated as extra economy gains
- HuskSync heartbeat interval is game ticks, not “every 40 reconcile cycles”
- Snapshot list / HuskSync admin calls are not performed on the Bukkit main thread (cluster HS-005)

### Tested

- Unit tests
- Real Paper 1.21.8 + Mineflayer protocol 772 (14 single-server scenarios)
- Velocity + Paper A/B + HuskSync 3.8.7 + MariaDB + Redis + Mineflayer (HS-001…HS-005)
- Clean Paper install with ItemGuard only (no HuskSync)

### Known Limitations

- `/give` and unintegrated `addItem` may be `UNKNOWN` unless the other plugin uses `ItemGuardApi`
- Anvil / grindstone / brewing have no full transform provenance
- Paper-invisible item data is not scanned
- HuskSync coverage is 3.8.7 + Paper 1.21.8 only
- HS-006 / HS-007 / HS-008 are not automated
- No auto-punish, dashboard, fingerprint history, or ItemGuard Redis
