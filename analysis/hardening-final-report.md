# ItemGuard false-positive hardening — final report

Date: 2026-09-12  
Workspace version **at the time this report was written:** `1.0.0-RC2` (unpublished hardening cut; no `packageRelease`).  
Later development identity moved to `1.0.0-RC3-SNAPSHOT` because GitHub already published prerelease `v1.0.0-RC2` **without** this hardening. This file is historical evidence, not an RC3 release note.

This is **not** a full server replay. Field numbers are from re-parsed `itemguard-log.zip`. GUI gaps are Mineflayer PARTIAL, not faked PASS.

## 1–8. Field log re-parse (ground truth)

Source: `itemguard-log.zip`, schemaVersion 1, 2026-09-12 09:15–16:27 +08, **7.193 h**, **7** player UUIDs. Details: `analysis/field-false-positive-report.md`.

| Metric | Value |
| --- | ---: |
| Total records | 4253 |
| Alerts | **3423** (~476/h) |
| Risk ≥ 60 | 3423 |
| Risk ≥ 80 (Critical share of alerts) | **3157 (92.2%)** |
| Risk = 100 | 2411 |
| `material="*"` amount=0 | 2314 |
| UNKNOWN_GAIN | **504** |
| UNKNOWN low item-value (≤5) | **461 (91.5%)** |
| UNKNOWN near known source ±3s | **360 / 504 (71.4%)** diagnostic |
| Scanner raw findings | 218 |
| Unique player+signature+findingType | **8** (27.25× repeat) |
| Players who hit Risk 100 | 6 / 6 with alerts |

Near-known mix at ±3s (same player+material): DOUBLE_CHEST 152, SHULKER_BOX 80, WORKBENCH 54, GROUND_PICKUP 14, STONECUTTER 14, BARREL 13, FURNACE 12, CHEST 9, CRAFTING 7.

Prior human estimates (~3423 alerts, ~3157 Critical, ~504 UNKNOWN) **match this parse**. Use these numbers, not the hypothesis list, as ground truth.

## 9. Attribution bugs found

**Confirmed design problem**

- Source hints and exact credits shared one ledger concept; long hint TTL could explain later unrelated gains. Split: exact numeric credits (partial consume, 2500 ms) vs one-shot source hints (tick-scale, default 250 ms).
- Hint matching is no longer FIFO: material / interaction / container / source / newest timestamp.
- Empty first reconcile with pending hints uses a **1-tick** attribution grace instead of immediately UNKNOWN.
- GUI hint must survive until post-event diff (do not discard one-shot hints on a zero-diff reconcile).

**Strong suspicion (not full replay)**

- 71.4% of UNKNOWN_GAIN sit within ±3s of a logged known-source flow. JSONL does not record every hint/snapshot/tick, so this is a lower bound, not proof each pair is the same gain.

**Insufficient evidence**

- Furnace / stonecutter / merchant field UNKNOWN cannot be fully reproduced without raw Paper events. Merchant trade **is** automated PASS. Furnace extract and stonecutter recipe select are Mineflayer PARTIAL.

## 10. Risk model bugs found

**Confirmed**

- Player-wide SUM of 120 s signals → sticky saturation (Risk 100 then thousands of alerts).
- Alert on `playerCurrentRisk >= 60` for every later flow, including `* +0`.
- Scanner re-scan added a new RiskSignal every time (8 unique findings × ~27).
- COMPONENT_MODIFIED / CUSTOM_ITEM_METADATA scored as if they were cheating.
- HIGH_VALUE_ITEM_BURST used `itemValue × amount` so dirt/stone bulk qualified.
- Verified chest/pickup diamonds could fill the burst/repeated window and then a later UNKNOWN of the same material crossed HIGH.

## 11–12. Incident architecture / player current risk

```
Player → active incidents
Player current risk = MAX(active incident scores)
```

Types (few): `ITEM_GAIN`, `ILLEGAL_ITEM`, `SUSPICIOUS_ITEM`, `SHULKER_ACTIVITY`.

Related flow evidence (UNKNOWN + burst + repeated + rapid) **in the same 2 s join wave** combines inside one `ITEM_GAIN`. Unrelated families never sum. Later diamond chest after an expired/old wave starts a new incident.

## 13. Unknown gain scoring

Fact: `UNEXPLAINED_ITEM_GAIN` forensic still logs. Risk comes from `UnknownGainScorer` + `unknown-gain:` in `risk.yml` (value/amount/weighted). Examples: +1 STONE → 3; 64 DIAMOND → 25; netherite block → 35. Not flat +35.

## 14–16. Detector gates

- HIGH_VALUE_ITEM_BURST: `item-value >= 20` **and** unexplained volume/value in the window. Dirt/stone never qualify. Verified chest diamonds never qualify.
- REPEATED_IDENTICAL: `item-value >= 20`, detailed ItemSignature, **unexplained** signature totals only.
- RAPID_ITEM_GAIN: unexplained amount vs threshold; verified low-value building → 0; verified-only high-value → 0.
- Shulker rapid default score 0. Verified rapid alone does not alert. Rapid + UNKNOWN imbalance can still raise a short `SHULKER_ACTIVITY` incident.

## 17. Scanner dedupe

Key: `player UUID + ItemSignature + FindingType`. First INVALID/SUSPICIOUS finding → one RiskSignal. Repeats refresh lastSeen/slot. Full scan with the item gone → resolve. Owner transfer (Steve → Alex) is a new finding. INFO/CUSTOM never enter risk. COMPONENT_MODIFIED / CUSTOM_ITEM_METADATA default 0.

## 18. Alert threshold-crossing

Alerts only from incident escalations: NONE→HIGH at 60, NONE/HIGH→CRITICAL at 80. Already CRITICAL: forensic only, no staff-chat spam. Skip `* +0` with empty evidence. Copy is incident-centric (`#A82F`, evidence lines, Inspect/Trace).

## 19. Attribution ExactCredit / SourceHint

- Exact: material, amount, source, confidence, TTL 2500 ms, partial consume (pickup, furnace extract).
- Hint: optional material, source, container, interaction id, TTL 250 ms, one-shot.
- Debounce still collapses same-tick clicks; multiple hints can explain one diff; closest matching hint wins.
- Optional `attribution-debug` (default off, unknown-only) and UNKNOWN JSONL `attributionContext` (pendingHintsCount, nearestHintSource, nearestHintAgeMs). No inventory dumps.

## 20. New unit tests

About **+48** vs the pre-hardening ~82 set. Current `gradlew.bat clean test`: **130/130 PASS**. Named coverage includes incident MAX/join/escalation, scanner 50× scan, unknown value scoring, dirt/stone burst, attribution TTL/hint ranking, field-derived fixtures, 150 normal sequences, 5 threat samples.

## 21. Single-server integration

`gradlew.bat integrationTest` (this cut): **PASS 18 / FAIL 0 / PARTIAL 4 / NOT AUTOMATED 2**

Original 14/14: **PASS**. Extra PASS: NUMBER-KEY, SHIFT-CRAFT (count=3 produced CRAFTING diamonds on this run), MERCHANT-TRADE, KNOWN-OP-STRESS (**100 known ops, UNKNOWN=0**). No staff `Alert ItemGuardBot` in the last passing run.

## 22. HuskSync

Unit: HuskSync transaction tests PASS. `HUSKSYNC_DATA_APPLY` still risk 0 and is not ITEM_GAIN.  
`huskSyncIntegrationTest` **not re-run here**: Docker engine was not running (`dockerDesktopLinuxEngine` pipe missing). **HS-001…HS-005 must be re-run before recommending Field Test.** Previous workspace cut had them PASS.

## 23. New GUI integration

| Scenario | Result |
| --- | --- |
| Number-key swap | PASS, UNKNOWN=0 |
| Merchant trade | PASS, VILLAGER_TRADE, UNKNOWN=0 |
| Shift-craft ×3 | PASS on the last run |
| 100 chest/shulker/craft | PASS, UNKNOWN=0 |
| Double-click | PARTIAL — Mineflayer `unimplemented` |
| Complex drag | PARTIAL — protocol `invalid operation` |
| Furnace result | PARTIAL — click did not extract (IRON_INGOT=0) |
| Stonecutter | PARTIAL — no recipe result slot |

## 24–26. Regression suites

- Normal: 150 sequences, **0 High, 0 Critical** (`analysis/hardening-precision.md`)
- Threat: **5 detected, 0 missed** (Sharpness 255, oversized, 64 diamond UNKNOWN, netherite > diamond, 1728 netherite CRITICAL once)
- Field-derived: anonymized FIELD-DERIVED REGRESSION, not FULL SERVER REPLAY

## 27. Performance

Incident list capped (`max-active-per-player` 16). Scanner finding cache bounded (64/player, 2 min retention). No per-tick walk of all historical incidents. Quit clears hints, credits, flow incidents, scanner findings. Not a load-test; no measured tick regression in integration (~3.5 min including 100-op stress).

## 28. Config changes

`risk.yml` is incident-oriented: `incidents.*` TTLs, `item-gain-join-millis` 2000, `unknown-gain.*`, `detectors.*` minimum item values, COMPONENT/CUSTOM scores 0, shulker rapid 0. Existing operator files are **never overwritten**; missing keys use new defaults; leftover non-zero COMPONENT/CUSTOM scores warn once.

## 29. Forensic schema

Runtime writes **schemaVersion 2** with optional `incidentId`, `incidentType`, `incidentRisk`, `incidentAlertTransition`. Analyzer still reads schema 1. `/ig trace` remains in-memory.

## 30. Remaining false-positive risks

- Some real GUI (furnace take, stonecutter recipe, double-click, drag) still unproven on Mineflayer; field UNKNOWN near those sources can return.
- 1-tick grace cannot cover every plugin that mutates inventory several ticks later.
- `/give` and unintegrated `addItem` remain UNKNOWN (fact), now low/high risk by value instead of +35.
- Conflicting enchantments still SUSPICIOUS (score 10) once per signature — custom kits with protection+blast_protection will still create a low incident, not a spam storm.
- Anvil / grindstone / brewing still lack full transform provenance.

## 31. RC2 vs RC3

GitHub already published prerelease **v1.0.0-RC2** from an earlier commit **without** this hardening. **Do not silently reuse that tag.** After this report was written, the workspace development identity moved to `1.0.0-RC3-SNAPSHOT`. A public `1.0.0-RC3` cut is still not allowed until Field Test.

## 32. Re-enter Field Test?

**Not yet.** Re-run `huskSyncIntegrationTest` (HS-001…005) with Docker, then yes: this cut meets the product gates except that cluster re-run.

## 33. Release blockers

1. **HuskSync cluster suite not re-executed** (Docker down) — gate item, not a known product regression.
2. **No `packageRelease` / 1.0.0 / auto RC3** until you choose the tag.
3. Furnace/stonecutter Mineflayer PARTIAL — not a blocker for RC if documented; it is a remaining field-FP risk.

## Release gate checklist (§102)

| Gate | Status |
| --- | --- |
| Unrelated incidents do not sum | PASS (unit) |
| Scanner same item no repeat stack | PASS (50× unit + field fixture) |
| Component / custom metadata 0 risk | PASS |
| Dirt/stone not High Value Burst | PASS |
| Repeated low-value not rare-identical | PASS |
| Verified rapid shulker not high risk alone | PASS |
| Alert only on incident threshold transition | PASS |
| No high-risk `* +0` spam | PASS |
| Known container/craft deterministic UNKNOWN=0 | PASS (14/14 + 100-op stress) |
| Real strong anomaly still Critical | PASS (1728 netherite unit) |
| 14/14 old integration | PASS |
| HS-001…005 | **not re-run this session** |

Success standard: normal behavior no longer stacks into cheating; the same fact is not scored repeatedly; UNKNOWN risk matches item value; high-value unexplained anomalies still produce a clear Critical incident.
