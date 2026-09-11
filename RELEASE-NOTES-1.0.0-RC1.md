# ItemGuard 1.0.0-RC1

ItemGuard is a Paper plugin for item security, item-flow monitoring, and economy anomaly detection. It detects abnormal item flows, invalid or suspicious items, and duplication indicators. It does **not** block all dupes, and it does **not** guarantee exploit protection.

## Who this RC is for

Operators who can install a plugin on a **test or small Paper 1.21.8** server and watch staff alerts for a while. This is a Release Candidate, not a promise that every production economy will be quiet.

Use a test world first. Pay extra attention to:

- false positives
- custom-item plugins (names, lore, PDC, custom model data)
- crate / shop / reward plugins that put items directly into inventories

## Requirements

- Paper **1.21.8**
- Java **21**
- Optional: HuskSync **3.8.7** (tested). Other HuskSync versions are not claimed.

## What you get

- Illegal / suspicious item scanner
- Item flow monitor with expected-source credits
- Risk score and staff alerts (default alert 60, critical 80)
- `/ig inspect`, `/ig trace`, `/ig scan`, `/ig alerts`
- HuskSync-aware inventory apply: legitimate synchronized restores are not treated as unexplained item gain
- Public `ItemGuardApi` so other plugins can register legal rewards

There is **no** auto-ban, auto-kick, auto-confiscate, rollback, web dashboard, or ItemGuard-owned database.

## Install

1. Put `ItemGuard-1.0.0-RC1.jar` in `plugins/`.
2. Start Paper once. Defaults appear under `plugins/ItemGuard/`.
3. Leave `alerts.yml` on alert-only unless you later decide to change thresholds.
4. If you use HuskSync 3.8.7, keep `integrations.yml` `husksync.enabled: true`. If HuskSync is not installed, ItemGuard simply skips that integration.

## Upgrading / backups

This is the first RC. Before any later version, copy `plugins/ItemGuard/` somewhere safe. ItemGuard does not overwrite existing YAML; missing keys use defaults.

## How to report problems

Include Paper version, ItemGuard version (`/ig status`), whether HuskSync is installed, the player action, `/ig inspect` / `/ig trace` output, and the alert text. Do not send database passwords, Redis credentials, or Velocity secrets.

## Known limitations

- `/give` and unintegrated plugin `addItem` can show as `UNKNOWN`
- Anvil / grindstone / brewing are not full transform tracking
- Only HuskSync 3.8.7 + Paper 1.21.8 has real automated cluster coverage
- HS-006 / HS-007 / HS-008 are not automated
- Detection is indicator-based. It does not guarantee every cross-server duplication issue is found

See `README.md` and `CHANGELOG.md` for the longer operator and testing notes.
