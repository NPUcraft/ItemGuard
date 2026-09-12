# Hardening precision (synthetic)

Not a full server replay. Counts come from unit suites that already assert these values.

## Normal behavior (`FalsePositiveRegressionSuiteTest`)

- Sequences: 150 verified building-block gains (10 materials × 5 sources × 3 amounts)
- High alerts: **0**
- Critical alerts: **0**
- Extra: dirt burst below 60; verified diamond history does not promote a later +32 UNKNOWN to High; `* +0` is not staff-alert worthy

## Threat (`ThreatRegressionSuiteTest`)

- Samples: 5 (Sharpness 255 INVALID, oversized stack, 64 diamond UNKNOWN, netherite > diamond, 1728 netherite UNKNOWN)
- Detected: **5**
- Missed: **0**
- Critical dupe-like sample (1728 NETHERITE_BLOCK UNKNOWN, identical signature): score ≥ 80, one CRITICAL escalation

## Field-derived (`FieldDerivedRegressionTest`)

- FIELD-DERIVED REGRESSION only (anonymized patterns)
- Scanner repeat boots: 50 observes → 1 risk evidence
- Low-value dirt burst is not HIGH_VALUE_ITEM_BURST
- Unrelated incidents do not sum
- Craft hint does not consume a later cobblestone gain
