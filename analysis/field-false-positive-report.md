# Field False Positive Report

Generated: 2026-09-12T08:40:51.681Z
Source: `itemguard-log.zip` (schema 1)

Player names/UUIDs are anonymized. This is field evidence analysis, not a full server replay.

This is **field evidence analysis**, not a full server replay. ItemGuard JSONL does not record every hint, snapshot, or reconciliation tick.

## Time range

- Start: 2026-09-12T09:15:37.464+08:00
- End: 2026-09-12T16:27:10.898+08:00
- Span hours: 7.193
- Players (UUID): 7

## Record counts

- total: 4253
- alerts: 3423
- events: 828
- unknownGain: 504
- suspiciousItem: 218
- invalidItem: 0
- highValueFlow: 57
- huskSyncApply: 49

## Alerts

- Total: **3423**
- Per hour: 475.9
- Risk >= 60: 3423
- Risk >= 80: 3157
- Risk = 100: 2411
- material=`*` amount=0: 2314

### Reason categories (top)

- RISK_THRESHOLD_REACHED: 3027
- COMPONENT_MODIFIED: 659
- UNEXPLAINED_ITEM_GAIN: 325
- CUSTOM_ITEM_METADATA: 162
- OTHER: 141
- RAPID_OR_HIGH_VALUE_BURST: 61
- BURST: 59
- SHULKER: 26

### Alerts by anonymized player (top)

- player-3: 803
- player-7: 722
- player-2: 678
- player-4: 650
- player-5: 456
- player-6: 114

## UNKNOWN_GAIN

- Total: **504**
- Low item-value (<=5): 461 (91.5%)

### Value / amount buckets

{"low":461,"high":32,"extreme":8,"medium":3}
{"1":186,"17-64":231,"2-16":85,"65-256":2}

### Materials (top)

- STONE_BRICKS: 27
- STONE: 25
- SHULKER_BOX: 19
- GRASS_BLOCK: 17
- POLISHED_DIORITE: 16
- CHORUS_FRUIT: 15
- SANDSTONE: 15
- COBBLESTONE: 14
- SMOOTH_STONE: 14
- DIRT: 14
- QUARTZ_BLOCK: 14
- COOKED_PORKCHOP: 13
- LEVER: 11
- STICK: 10
- COAL: 9
- IRON_INGOT: 9
- PALE_OAK_LOG: 9
- RAIL: 8
- WHITE_STAINED_GLASS_PANE: 8
- END_ROD: 8
- BUCKET: 8
- DIAMOND_SHOVEL: 8
- FIREWORK_ROCKET: 7
- NETHERITE_PICKAXE: 7
- BAKED_POTATO: 7

### Near known source (same player + material)

| Window | Near | Percent |
| --- | ---: | ---: |
| ±250ms | 252 | 50% |
| ±500ms | 267 | 53% |
| ±1000ms | 301 | 59.7% |
| ±2000ms | 333 | 66.1% |
| ±3000ms | 360 | 71.4% |

Source mix at ±3000ms:

{
  "DOUBLE_CHEST": 152,
  "SHULKER_BOX": 80,
  "WORKBENCH": 54,
  "GROUND_PICKUP": 14,
  "STONECUTTER": 14,
  "BARREL": 13,
  "FURNACE": 12,
  "CHEST": 9,
  "CRAFTING": 7,
  "DISPENSER": 3,
  "CONTAINER": 2
}

## Scanner repeats

- Event rows: 218
- Finding occurrences: 218
- Unique player+signature+findingType: 8
- Repeat multiplier: 27.25

- x51 SUSPICIOUS_ITEM CONFLICTING_ENCHANTMENTS NETHERITE_BOOTS player-3
- x40 SUSPICIOUS_ITEM CONFLICTING_ENCHANTMENTS CHAINMAIL_LEGGINGS player-4
- x40 SUSPICIOUS_ITEM SUSPICIOUS_CONTAINER_CONTENTS SHULKER_BOX player-4
- x39 SUSPICIOUS_ITEM CONFLICTING_ENCHANTMENTS NETHERITE_BOOTS player-3
- x21 SUSPICIOUS_ITEM CONFLICTING_ENCHANTMENTS TURTLE_HELMET player-4
- x21 SUSPICIOUS_ITEM SUSPICIOUS_CONTAINER_CONTENTS SHULKER_BOX player-4
- x3 SUSPICIOUS_ITEM CONFLICTING_ENCHANTMENTS TURTLE_HELMET player-4
- x3 SUSPICIOUS_ITEM SUSPICIOUS_CONTAINER_CONTENTS SHULKER_BOX player-4

## Risk saturation

- Players who hit 100: 6 / 6

- player-3 alerts=803 after100=802 spanMin=373.6
- player-7 alerts=722 after100=720 spanMin=197.7
- player-2 alerts=678 after100=675 spanMin=77
- player-4 alerts=650 after100=650 spanMin=251.7
- player-5 alerts=456 after100=452 spanMin=319.2
- player-6 alerts=114 after100=113 spanMin=21.9

## Low-value burst language

- Count: 33

## Findings (status)

### P0-ALERT-SPAM — Confirmed design problem

**Alert volume vs unique players**

3423 alerts. Star/+0 rows=2314. This matches threshold-reached spam if current risk stays high.

### P0-COMPONENT-RISK — Confirmed design problem

**COMPONENT_MODIFIED / custom metadata in alert reasons**

COMPONENT_MODIFIED category count=659. Field alerts cite enchanted/named/damaged components on ordinary chest loot.

### P0-SCANNER-REPEAT — Confirmed design problem

**Scanner finding repeats**

raw finding occurrences=218 unique keys=8

### P0-UNKNOWN-NEAR-KNOWN — Strong suspicion

**UNKNOWN_GAIN near logged known sources**

At ±3s: 71.4% of UNKNOWN share player+material with a logged known-source event. Forensic logs do not include every ordinary flow, so this is a lower bound.

### P0-DIRT-BURST — Confirmed design problem

**Low item-value HIGH_VALUE/RAPID burst language**

Alerts whose material item-value<=5 and amount>=64 while reasons mention gained-within: 33

### P0-SATURATION — Confirmed design problem

**Sticky risk saturation**

6 anonymized players reached risk 100; subsequent alerts continue while score stays high.

## Hypotheses vs this parse

Prior hypothesis of ~3423 alerts is checked against `alerts.total` in this file. Use these parsed numbers, not the hypothesis, as ground truth.
