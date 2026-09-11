# ItemGuard 1.0.0-RC2

GitHub Release：https://github.com/NPUcraft/ItemGuard/releases/tag/v1.0.0-RC2

这是 **RC2**，不是正式 1.0.0。相对 RC1 的主要增加是取证 JSONL 日志。JAR 由 GitHub Actions 从本 tag 编译。

**升级验证：** 配置兼容（`CONFIG_COMPATIBILITY_ONLY`）已通过。原始已发布 RC1 JAR（SHA-256 `11581d2fa2a61198a491ef680181f1fc5041e413c54fdbb1f0c54c3d5eeda87b`）仍未用于精确制品升级，因此 **不是** `EXACT_ARTIFACT_UPGRADE PASS`。

This is **RC2**, not a final 1.0.0. It is the RC1 detection build plus **forensic JSONL logs**.

ItemGuard still detects abnormal item flows, invalid or suspicious items, and duplication indicators. It does **not** block all dupes, and it does **not** guarantee exploit protection.

## Who this RC is for

Operators already trying **RC1** on a test or small Paper 1.21.8 server, or new operators installing ItemGuard for the first time. Watch staff alerts and, when you need a paper trail, the new files under `plugins/ItemGuard/logs/`.

Use a test world first. Pay extra attention to:

- false positives
- custom-item plugins (names, lore, PDC, custom model data)
- crate / shop / reward plugins that put items directly into inventories

## Requirements

- Paper **1.21.8**
- Java **21**
- Optional: HuskSync **3.8.7** (tested). Other HuskSync versions are not claimed.

## What changed vs RC1

The largest addition is **Forensic Logs**:

- Asynchronous JSONL under `plugins/ItemGuard/logs/`
- `alerts/`, `events/`, `admin/`
- Daily and size rotation, 30-day default retention, bounded queue
- New `logging.yml` (created only if that file is missing)

Detection thresholds, scanner rules, and HuskSync apply handling are the same as RC1.

`/ig status` now also shows forensic logger health and queue size.

There is still **no** auto-ban, auto-kick, auto-confiscate, rollback, web dashboard, `/ig logs` search, or ItemGuard-owned database.

## New install

1. Put `ItemGuard-1.0.0-RC2.jar` in `plugins/`.
2. Start Paper once. Defaults appear under `plugins/ItemGuard/`, including `logging.yml` and `logs/`.
3. Leave `alerts.yml` on alert-only unless you later decide to change thresholds.
4. If you use HuskSync 3.8.7, keep `integrations.yml` `husksync.enabled: true`. If HuskSync is not installed, ItemGuard simply skips that integration.

## Upgrading from RC1

Do **not** delete existing configuration files. ItemGuard will not overwrite them.

1. Stop the Paper server.
2. Backup `plugins/ItemGuard/` (the whole folder).
3. Remove `ItemGuard-1.0.0-RC1.jar` from `plugins/`.
4. Put `ItemGuard-1.0.0-RC2.jar` in `plugins/`.
5. Start the server.
6. Review the new `logging.yml`.
7. Verify `/ig status` (version RC2, forensic logging enabled).

ItemGuard adds `logging.yml` and `logs/` if they are missing. If you already created those paths yourself, they are left alone. Existing YAML is not rewritten and does not need to be deleted.

Automated **configuration** compatibility (RC1-shaped YAML + RC2 JAR) is not the same as starting the original published RC1 JAR and replacing it. Exact artifact upgrade still requires that original file (SHA-256 `11581d2fa2a61198a491ef680181f1fc5041e413c54fdbb1f0c54c3d5eeda87b`).

## Forensic log boundary

A normal server shutdown drains and flushes queued records. A JVM crash, forced process termination, or host failure may lose a small number of records that had not yet been flushed.

## How to report problems

Include Paper version, ItemGuard version (`/ig status`), whether HuskSync is installed, the player action, `/ig inspect` / `/ig trace` output, and the alert text. Do not send database passwords, Redis credentials, Velocity secrets, or raw forensic JSONL that might contain player identifiers you are not allowed to share.

## Known limitations

- `/give` and unintegrated plugin `addItem` can show as `UNKNOWN`
- Anvil / grindstone / brewing are not full transform tracking
- Only HuskSync 3.8.7 + Paper 1.21.8 has real automated cluster coverage
- HS-006 / HS-007 / HS-008 are not automated
- Detection is indicator-based. It does not guarantee every cross-server duplication issue is found
- Forensic logs are not a crash-proof WAL; see the flush boundary above

See `README.md` and `CHANGELOG.md` for the longer operator and testing notes.
