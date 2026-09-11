# RC1 bundled config fixture

**This is not the original RC1 JAR.**  
Exact artifact upgrade still requires SHA-256 `11581d2fa2a61198a491ef680181f1fc5041e413c54fdbb1f0c54c3d5eeda87b`.

## Provenance

| Field | Value |
| --- | --- |
| Claimed product version | `1.0.0-RC1` bundled defaults |
| Git commit / tag | **none** — this workspace has no `.git` |
| Original RC1 binary | **not present** |
| Source paths | `src/main/resources/{config,scanner,risk,items,alerts,integrations,messages}.yml` |
| Extracted | 2026-09-11 |
| Why these files | Last write times are 2026-09-11 12:48–12:57, during RC1 packaging. `logging.yml` was added later (21:40) and is **not** in this fixture. `run/plugins/ItemGuard/` SNAPSHOT configs from 2026-09-10 were **not** used (different bytes). |

## File SHA-256 at extraction

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| config.yml | 1469 | `a3df219080190fc626fa929871f5066d4911865d8dc3e2ca75c8ba4a2e63043b` |
| scanner.yml | 2449 | `9e44118b8f738361d88545d9c3d1579c7bad3b066e6821859d793ccbede8b22b` |
| risk.yml | 2037 | `b9ab516630ac5c2057b5fbc41cb5f9e35aa284b29060daa76444434456cd039b` |
| items.yml | 1436 | `d16b515f6a22ef90f5b5d67096c125f382ed2ca842b4235fe5f6c7f4d5076477` |
| alerts.yml | 451 | `5d638b463b1171ee06f203f591a8c3236ed4f70e49d3f4962fede8412631f666` |
| integrations.yml | 685 | `de51eb2b05af8fda2e0daeb352156fa4d90e8c43483dc66935163af5730876a2` |
| messages.yml | 3373 | `a6ef4ceaf0f9806cd350d4ca7db2dc7b955b3618d5cb5e483afb7b997cb9f6f3` |

`legacyConfigCompatibilityTest` refuses to run if these hashes change.

## What this can prove

RC2 will not overwrite a **RC1-shaped** 7-file YAML folder, and it will create `logging.yml` / `logs/` when missing.

## What this cannot prove

`EXACT_ARTIFACT_UPGRADE` — the published RC1 plugin binary was never started.
