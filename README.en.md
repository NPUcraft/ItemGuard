# ItemGuard

**中文（默认）:** [README.md](README.md) · **English:** this file

Repository: https://github.com/NPUcraft/ItemGuard

Item security, item-flow monitoring and economy anomaly detection for Paper servers.

ItemGuard detects abnormal item flows, invalid or suspicious items, and duplication indicators. It then gives staff explainable evidence. It does **not** block every dupe, does **not** guarantee exploit protection, and never auto-bans, kicks, confiscates, or rolls back inventories.

> ItemGuard detects abnormal item flows, invalid or suspicious items, and duplication indicators.  
> It does not guarantee prevention or detection of every duplication exploit.

## What ItemGuard does

- Scans inventories for illegal or suspicious item data that Paper actually exposes
- Watches item movement (pickup, containers, crafting, death, shulkers, bundles, creative)
- Attributes gains to expected sources when Bukkit events or integrations can explain them
- Scores remaining unexplained or illegal patterns and alerts staff
- Keeps a short in-memory inspect / trace timeline for operators

## What ItemGuard does NOT claim

- It does not prevent or detect every duplication exploit
- It is not an anti-cheat, anti-xray, or combat monitor
- It does not rewrite other plugins’ inventories
- It does not talk to MySQL, Redis, or a web dashboard
- HuskSync support means ItemGuard can recognize legitimate sync restores, not that every cross-server dupe is caught

## Features

- **Illegal Item Scanner** — over-level enchantments, oversized stacks, illegal durability, suspicious attributes/components, nested container contents
- **Item Flow Monitor** — expected-flow credits plus inventory reconciliation
- **Risk Engine** — explainable signals with a live 0–100 score
- **Trace / Inspect** — in-memory timeline and session summary for staff
- **Shulker monitoring** — high flow and rapid transfer signals
- **HuskSync-aware synchronization** — treats official inventory apply as an explainable source
- **Forensic logs** — long-lived JSONL files for post-incident review (separate from in-memory `/ig trace`)

## Requirements

- **Paper 1.21.8** (target; other 1.21.x versions are not guaranteed)
- **Java 21**
- Optional: **HuskSync 3.8.7** on the same Paper server

## Installation

1. Install Java 21 and Paper 1.21.8.
2. Copy `ItemGuard-1.0.0-RC2.jar` into the Paper `plugins/` folder.
3. Start the server once. ItemGuard copies default YAML files into `plugins/ItemGuard/` if they are missing.
4. Edit those files if needed, then run `/ig reload`.
5. Give operators `itemguard.admin` (default: OP).

This is a **Release Candidate**. Use a test server first. Watch false positives around custom items and economy/reward plugins.

## Upgrading from RC1

Do **not** delete existing configuration files.

1. Stop the server
2. Backup `plugins/ItemGuard/`
3. Replace the RC1 JAR with `ItemGuard-1.0.0-RC2.jar`
4. Start the server
5. Review new `logging.yml`
6. Verify `/ig status`

ItemGuard adds `logging.yml` and `logs/` only when they are missing. Your existing YAML stays byte-for-byte as you left it; missing keys use in-memory defaults. Do not delete the old config folder to “make a clean install” unless you intend to reset.

Before upgrading from this RC to a later version, back up `plugins/ItemGuard/` again.

Automated **configuration** compatibility (RC1-shaped YAML + RC2 JAR) is not the same as starting the original published RC1 JAR and replacing it. Exact artifact upgrade still requires that original file (SHA-256 `11581d2fa2a61198a491ef680181f1fc5041e413c54fdbb1f0c54c3d5eeda87b`).

## HuskSync Compatibility

HuskSync is optional (`softdepend`). If the plugin is installed and `integrations.yml` keeps `husksync.enabled: true`, ItemGuard loads the integration automatically. If HuskSync is absent, ItemGuard stays enabled and the integration is skipped.

**Tested with:** HuskSync **3.8.7** + Paper **1.21.8**. Other HuskSync versions are not claimed.

ItemGuard recognizes HuskSync inventory data application and avoids treating legitimate synchronized inventory restoration as unexplained item gain. It can still surface suspicious patterns around item flow, but does not guarantee detection of every cross-server duplication issue.

ItemGuard never reads the HuskSync database, Redis, or Velocity forwarding secret.

## Commands

Root command: `/itemguard`  
Alias: `/ig`

| Command | Description |
| --- | --- |
| `/ig help` | Commands the sender may use |
| `/ig status` | Plugin, Paper, scanner, flow, risk, HuskSync, and forensic-log status |
| `/ig inspect <player>` | Live risk / flow / HuskSync summary |
| `/ig trace <player> [duration] [material]` | In-memory timeline (default `5m`) |
| `/ig scan <player>` | Scan the current inventory |
| `/ig alerts` | Recent staff alerts |
| `/ig reload` | Reload YAML without resetting player baselines |
| `/ig debug <player>` | Diagnostic dump for admins (no NBT/PDC/secrets) |

Duration examples: `30s`, `5m`, `10m`, `30m`, `1h`.

`/ig debug` is admin-only diagnostic information. It does not print full PDC, NBT, database passwords, Redis credentials, or Velocity secrets.

## Permissions

All default to `op`. `itemguard.admin` inherits the rest.

- `itemguard.admin`
- `itemguard.status`
- `itemguard.inspect`
- `itemguard.trace`
- `itemguard.scan`
- `itemguard.alerts`
- `itemguard.reload`
- `itemguard.debug`

## Configuration

Created on first start (never overwritten later):

- `config.yml` — reconciliation interval, heartbeat (game ticks), expected-credit TTL, scan debounce, nested depth
- `scanner.yml` — illegal / suspicious / custom item rules and PDC namespaces
- `risk.yml` — signal scores, TTL, alert thresholds
- `items.yml` — **risk weights**, not currency prices
- `alerts.yml` — staff alert delivery (no automatic punishment)
- `integrations.yml` — HuskSync toggle
- `messages.yml` — MiniMessage strings (English)
- `logging.yml` — asynchronous forensic JSONL (alerts, important events, admin actions)

Invalid values log a warning and fall back to defaults. The file is left as the operator wrote it.

### Suggested server-type profiles (manual)

There is no GUI profile switcher. Copy the idea into YAML:

**Vanilla Survival** — keep bundled defaults. Alert at 60, no auto-punish.

**Semi-Vanilla** — keep scanner INVALID rules; if shops/crates grant items, those plugins should call `ItemGuardApi.recordExpectedGain` or unexplained gains will appear.

**RPG / custom-item server** — lower or zero `CUSTOM_ITEM_METADATA`, `COMPONENT_MODIFIED`, and `CUSTOM_MAX_STACK` in `risk.yml`, and/or add your item-plugin namespaces under `scanner.yml` `persistent-data.ignored-namespaces`. Do not disable `OVERSIZED_STACK` / `OVER_LEVEL_ENCHANTMENT` unless you truly allow those.

## How Risk Scoring Works

Each detector emits explainable signals. Active, unexpired signals are **summed** and clamped to 0–100.

- Default staff alert: **60** (HIGH)
- Critical: **80**
- Default bias: **alert first, never punish automatically**
- Operators can raise thresholds if their server is noisier

Defaults (not a complete list):

| Signal | Default |
| --- | --- |
| `OVERSIZED_STACK` | 45 |
| `OVER_LEVEL_ENCHANTMENT` | 40 |
| `UNEXPLAINED_ITEM_GAIN` | 35 |
| `HIGH_VALUE_ITEM_BURST` / `RARE_ITEM_BURST` | 20 |
| `CUSTOM_ITEM_METADATA` | 2 |
| `COMPONENT_MODIFIED` | 4 |
| `HUSKSYNC_DATA_APPLY` | 0 |
| Creative inventory (`CREATIVE_INVENTORY`) | 0 |
| Known container source | 0 |

Custom name/lore/PDC/component/max-stack facts share a **family cap of 10** per correlation so a legal custom item cannot stack those signals to the alert threshold by itself.

Signals live for `signal-ttl-millis` (default 120s). This is a rolling window, not a separate “one incident” cluster: unrelated events in that window can add together (for example unexplained 35 + high-value burst 20 = 55, still below 60). Same type + material + correlation is deduplicated.

`items.yml` values are **risk weights for burst detection**, not an economy price list.

## Forensic Logs

ItemGuard writes selected high-value evidence to `plugins/ItemGuard/logs/` as **JSONL** (one JSON object per line). This is for later investigation. It does **not** replace `/ig trace`, which stays a short in-memory timeline.

Default layout:

```text
plugins/ItemGuard/logs/
├── alerts/   HIGH / CRITICAL staff alerts
├── events/   UNKNOWN_GAIN, scanner findings, HuskSync apply, high-value flows
└── admin/    ItemGuard admin commands such as /ig scan
```

Files rotate daily (`2026-09-11.jsonl`) and again if a file exceeds `max-file-size-mb` (`2026-09-11-1.jsonl`). Files older than `retention-days` (default 30) are deleted. `server-name` in `logging.yml` tags each line so you can merge logs from several HuskSync backends.

**Recorded by default:** staff alerts, unexplained gains, invalid items, mid/high suspicious scanner findings, HuskSync data-apply summaries, admin actions (`scan` / `inspect` / `trace` / `reload` / `debug` / `alerts`), and item flows whose `items.yml` weight is at least `high-value-flow.minimum-item-value` (default 30). Diamond is 25, so a normal diamond pickup is not written.

**Not recorded:** ordinary cobblestone/chest/crafting traffic, player chat, login IPs, full NBT or ItemStack bytes, PDC values, inventory dumps, database/Redis/Velocity secrets, `/ig help`, `/ig status`. There is no `/ig logs` search command in this version.

Writes are asynchronous: the Paper main thread only enqueues an immutable record. A single writer thread appends JSONL. If the queue is full, low-priority records are dropped and a rate-limited console warning is printed; detection continues. Disk errors degrade logging only.

A normal server shutdown drains and flushes queued records. A JVM crash, forced process termination, or host failure may lose a small number of records that had not yet been flushed. ItemGuard does not fsync every record.

Set `logging.enabled: false` to disable persistence. `directory` must stay inside the plugin data folder; path traversal falls back to `logs`.

## False Positive Philosophy

ItemGuard prefers catching unexplained high-value flow over staying silent. Staff alerts are the product; automatic punishment is out of scope.

Common expected noise:

- `/give` and plugins that call `addItem` without `ItemGuardApi` → `UNKNOWN`
- Custom items with names, lore, CustomModelData, or PDC → `CUSTOM` (low score, family-capped)
- Creative mode item creation → tagged creative, risk 0, scanner still runs

Tune `risk.yml` / `scanner.yml` per server type instead of disabling the plugin.

## Scanner classifications

| Class | Meaning |
| --- | --- |
| `INVALID` | Data that cannot be a legal vanilla/Paper item (over-level, oversized stack, illegal durability) |
| `SUSPICIOUS` | Unusual, not automatically cheating |
| `CUSTOM` | Legitimate custom-item markers (name, lore, CustomModelData, unknown PDC) |
| `INFO` | Informational only |

Unknown PDC namespaces are `CUSTOM`. Explicitly denied namespaces are `SUSPICIOUS`. Vanilla `minecraft` / `bukkit` / `paper` namespaces are ignored. ItemGuard does not ship a huge third-party whitelist.

## Third-party plugins / ItemGuard API

If another plugin puts items directly into a player inventory, register that gain **before** the items appear:

```java
if (ItemGuardApiProvider.isAvailable()) {
    ItemGuardApi api = ItemGuardApiProvider.get();
    api.recordExpectedGain(player.getUniqueId(), "DIAMOND", 16, "MyCratePlugin", rewardId);
}
```

There is also `recordExpectedGain(UUID, ItemStack, int, String, String)`, plus `beginTransaction` / `endTransaction` for grouped grants.

`ExternalItemSourceProvider` is reserved for a later first-party provider registry. ItemGuard 1.0 does not auto-discover shop/crate/ItemsAdder plugins.

## Performance

Defaults are not “scan every player every tick”:

- Dirty players reconcile every **20 game ticks** (1s)
- Silent-mutation heartbeat every **40 game ticks** (~2s)
- Inventory scan debounce **1500ms** per player
- Nested container / bundle scan depth **3**
- Trace retention **30 minutes**, 2000 events/player

## Testing

ItemGuard RC2 has automated tests covering:

- Unit tests (pure Java)
- Real Paper **1.21.8** + Mineflayer protocol **772**
- Chest, shift-click, partial shift-click, craft, death, bundle, shulker, creative
- Illegal item scanner
- HuskSync cross-server restore on Velocity + two Paper servers
- HuskSync **3.8.7**, MariaDB, Redis

These are the configurations that actually ran. ItemGuard is **not** fully tested on every plugin mix or Paper version.

A normal `gradlew.bat clean build` compiles the plugin and runs unit tests only. It does **not** need Docker, Node, HuskSync, or Velocity.

```bat
gradlew.bat clean test
gradlew.bat integrationTest
gradlew.bat huskSyncIntegrationTest
gradlew.bat upgradeIntegrationTest
gradlew.bat legacyConfigCompatibilityTest
gradlew.bat releaseSmokeTest
```

`upgradeIntegrationTest` is **EXACT_ARTIFACT_UPGRADE**. It needs the original published RC1 JAR (`ITEMGUARD_RC1_JAR` or `-PRc1Jar=`), checksum `11581d2fa2a61198a491ef680181f1fc5041e413c54fdbb1f0c54c3d5eeda87b`. It is not part of `build`.

`legacyConfigCompatibilityTest` is **CONFIG_COMPATIBILITY_UPGRADE** only: it starts RC2 against RC1-shaped YAML fixtures. Passing it does **not** mean the original RC1 JAR was upgraded.

`integrationTest` needs Node.js and a Paper 1.21.8 JAR. `huskSyncIntegrationTest` also needs the cluster dependencies. Reports from those runs keep the version that actually ran; historical SNAPSHOT reports are not rewritten.

The Test Harness HTTP server binds `127.0.0.1` only and is **not** part of the product JAR.

## Known Limitations

1. `/give` and unintegrated plugins that call `addItem` can produce `UNKNOWN`. Use `ItemGuardApi`.
2. Anvil / grindstone / brewing do not get full transform provenance. Same-material metadata changes are not treated as economy gains.
3. Paper API data that is not visible or not reliable is not pretended to be scanned.
4. HuskSync is formally tested only as **3.8.7 + Paper 1.21.8**.
5. HS-006 / HS-007 / HS-008 are **not** covered by the automated HuskSync suite.
6. Risk signals in the 120s TTL window are additive; two unrelated legal-but-noisy events can still approach the alert threshold.
7. No SQLite, web dashboard, log search command, auto-ban, rollback, or ItemGuard-owned Redis. Important events are written asynchronously to local JSONL files under `plugins/ItemGuard/logs/`.

## Building

```bat
gradlew.bat clean build
```

The product JAR is:

```text
build/libs/ItemGuard-1.0.0-RC2.jar
```

GitHub Actions builds the plugin JAR on `main` and publishes a GitHub Release when a `v*` tag is pushed. Downloads: https://github.com/NPUcraft/ItemGuard/releases

`gradlew.bat packageRelease` still writes JAR + SHA-256 + notes into `build/release/` locally.

Version is defined once in `gradle.properties` and expanded into `plugin.yml` as `${version}`. Runtime status reads `plugin.getPluginMeta().getVersion()`.

## License

License: TBD
