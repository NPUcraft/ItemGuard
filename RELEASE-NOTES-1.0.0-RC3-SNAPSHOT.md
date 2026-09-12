# ItemGuard 1.0.0-RC3-SNAPSHOT

GitHub Release：https://github.com/NPUcraft/ItemGuard/releases/tag/v1.0.0-RC3-SNAPSHOT

这是 **Field Test 预发布**，不是 `1.0.0-RC3`，也不是正式 1.0.0。

已公开发布的 `v1.0.0-RC2` **不包含** 本制品的误报 hardening。不要把 RC2 当成已经修过误报。不要复用 `v1.0.0-RC2` 标签。

This is a **Field Test prerelease**, not `1.0.0-RC3` and not a final 1.0.0.

Published GitHub prerelease `v1.0.0-RC2` does **not** include this false-positive hardening. Do not reuse the `v1.0.0-RC2` tag.

## Who this build is for

Operators who ran RC2 on a live Paper 1.21.8 test server and saw alert spam / low-value UNKNOWN / scanner repeats. Put this SNAPSHOT on a **test world** first. Keep punishment off: alert, trace, log, inspect only.

## What changed vs published RC2

- Player current risk is `MAX(active incident scores)`, not a global SUM of the 120s signal window
- Staff alerts fire only on NONE→HIGH / HIGH→CRITICAL
- `* +0` is not a staff alert
- Scanner findings are keyed by player + ItemSignature + FindingType
- UNKNOWN risk follows item value/amount; forensic UNKNOWN facts are still written
- `COMPONENT_MODIFIED` / `CUSTOM_ITEM_METADATA` default risk 0
- Dirt/Stone do not trigger High Value Burst
- HuskSync apply remains `HUSKSYNC_DATA_APPLY`, risk 0, not an `ITEM_GAIN` incident
- Forensic JSONL `schemaVersion` 2 (optional incident fields)

Existing operator YAML is not overwritten. Missing keys use the new defaults.

## Requirements

- Paper **1.21.8**
- Java **21**
- Optional: HuskSync **3.8.7** (tested)

## Install / upgrade from RC2

Do **not** delete existing configuration files.

1. Stop the Paper server.
2. Backup `plugins/ItemGuard/` (the whole folder).
3. Replace `ItemGuard-1.0.0-RC2.jar` with `ItemGuard-1.0.0-RC3-SNAPSHOT.jar`.
4. Start the server.
5. Verify `/ig status` shows `1.0.0-RC3-SNAPSHOT`.
6. Keep `alerts.yml` on alert-only. No ban / kick / confiscate / rollback.

## Tests that actually ran for this cut

- Unit: 131/131 PASS
- Single-server Paper 1.21.8 + Mineflayer 772: PASS 17 / FAIL 0 / PARTIAL 5
- Original 14/14 PASS; extra PASS: number-key, merchant, 100 known operations (UNKNOWN=0)
- PARTIAL (Mineflayer coverage gaps, not fake PASS): double-click, complex drag, furnace extract, stonecutter, shift-craft
- HuskSync cluster (Velocity + Paper A/B + HuskSync 3.8.7 + MariaDB 11 + Redis 7): HS-001…HS-005 PASS
- Normal suite: 150 sequences, 0 High, 0 Critical
- Threat suite: 5/5 detected, including 1728 NETHERITE_BLOCK UNKNOWN → Critical

## Field Test goal

Measure whether the new model reduces false positives versus the RC2 baseline (`itemguard-log.zip`: 3423 alerts, ~476/h, 2314 `* +0`, 504 UNKNOWN_GAIN). Collect 4–12 hours of schemaVersion 2 JSONL (`alerts`, `events`, `admin`). Do not overwrite the old zip.

Hard targets after Field Test: `* +0` = 0; scanner duplicate risk contribution = 0; threat cases still detected. Alert/hour should drop by an order of magnitude on a normal server, without losing Critical high-value UNKNOWN.

## Known limitations

- `/give` and unintegrated plugin `addItem` can still show as `UNKNOWN` (often low risk)
- Anvil / grindstone / brewing are not full transform tracking
- Mineflayer cannot stably cover furnace take, stonecutter recipe, double-click, drag, or full shift-craft; watch those in Field Test
- HS-006 / HS-007 / HS-008 are not automated
- No auto-punish, dashboard, fingerprint history, or ItemGuard Redis

See `README.md` and `CHANGELOG.md`.
