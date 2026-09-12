#!/usr/bin/env node
'use strict';

/**
 * Dev-only BEFORE/AFTER field validation. Must not ship in the product JAR.
 * Never overwrites analysis/field-false-positive-report.* (PRE-HARDENING BASELINE).
 *
 * Usage:
 *   node tools/field-log-analyzer/compare.js path\to\post-hardening-logs.zip
 *   gradlew.bat fieldValidationCompare -PFieldLogZip=...
 */

const fs = require('fs');
const path = require('path');
const {
  analyze,
  loadRecords,
  extractZip,
  loadItemValues,
  renderMarkdown,
  ROOT,
  OUT_DIR,
  DEFAULT_ZIP
} = require('./analyze');

const BASELINE_JSON = path.join(OUT_DIR, 'field-false-positive-report.json');
const AFTER_JSON = path.join(OUT_DIR, 'field-validation-after.json');
const AFTER_MD = path.join(OUT_DIR, 'field-validation-after.md');
const COMPARE_JSON = path.join(OUT_DIR, 'field-validation-compare.json');
const COMPARE_MD = path.join(OUT_DIR, 'field-validation-compare.md');

function main() {
  const afterZip = process.env.ITEMGUARD_FIELD_AFTER_ZIP
    || process.env.ITEMGUARD_FIELD_LOG_ZIP
    || process.argv[2];
  const afterDir = process.env.ITEMGUARD_FIELD_AFTER_DIR || process.env.ITEMGUARD_FIELD_LOG_DIR;
  if ((!afterZip || afterZip === DEFAULT_ZIP) && !afterDir) {
    console.error('Provide a NEW post-hardening zip/dir. Refusing to reuse itemguard-log.zip (PRE-HARDENING BASELINE).');
    console.error('Set ITEMGUARD_FIELD_AFTER_ZIP / -PFieldLogZip to the new dump.');
    process.exit(2);
  }
  if (afterZip && path.resolve(afterZip) === path.resolve(DEFAULT_ZIP) && !afterDir) {
    console.error('Refusing to treat itemguard-log.zip as AFTER. Keep it as PRE-HARDENING BASELINE.');
    process.exit(2);
  }
  if (!fs.existsSync(BASELINE_JSON)) {
    console.error('Missing baseline', BASELINE_JSON);
    process.exit(2);
  }

  let logsRoot;
  if (afterDir && fs.existsSync(afterDir)) {
    logsRoot = afterDir;
  } else if (afterZip && fs.existsSync(afterZip)) {
    logsRoot = extractZip(afterZip);
  } else {
    console.error('After logs not found:', afterZip || afterDir);
    process.exit(2);
  }

  const records = loadRecords(logsRoot);
  if (records.length === 0) {
    console.error('No JSONL records parsed from after logs');
    process.exit(2);
  }
  const after = analyze(records, loadItemValues(logsRoot), path.basename(afterZip || afterDir));
  const before = JSON.parse(fs.readFileSync(BASELINE_JSON, 'utf8'));
  const compare = compareReports(before, after);

  fs.mkdirSync(OUT_DIR, { recursive: true });
  fs.writeFileSync(AFTER_JSON, JSON.stringify(after, null, 2));
  fs.writeFileSync(AFTER_MD, renderMarkdown(after));
  fs.writeFileSync(COMPARE_JSON, JSON.stringify(compare, null, 2));
  fs.writeFileSync(COMPARE_MD, renderCompareMarkdown(compare));
  console.log('Wrote', COMPARE_MD);
  console.log('starPlusZero after=', after.alerts.starPlusZero, 'scannerDuplicateRisk=', after.scanner.keysWithDuplicateRiskContribution);
}

function metric(before, after, pathKeys, hoursKey) {
  let b = before;
  let a = after;
  for (const key of pathKeys) {
    b = b == null ? null : b[key];
    a = a == null ? null : a[key];
  }
  const beforeValue = Number(b) || 0;
  const afterValue = Number(a) || 0;
  const delta = afterValue - beforeValue;
  const ratio = beforeValue === 0 ? (afterValue === 0 ? 1 : null) : Number((afterValue / beforeValue).toFixed(4));
  return { before: beforeValue, after: afterValue, delta, ratio };
}

function compareReports(before, after) {
  return {
    generatedAt: new Date().toISOString(),
    note: 'BEFORE is PRE-HARDENING BASELINE (field-false-positive-report). AFTER is a new post-hardening dump. Player mix may differ; do not require a fixed numeric drop except * +0 = 0 and scanner duplicate risk contribution = 0.',
    beforeSource: before.source,
    afterSource: after.source,
    kpis: {
      alertTotal: metric(before, after, ['alerts', 'total']),
      alertPerHour: metric(before, after, ['alerts', 'perHour']),
      criticalTotal: metric(before, after, ['alerts', 'riskGe80']),
      criticalPerHour: {
        before: before.timeRange && before.timeRange.spanHours ? Number((before.alerts.riskGe80 / before.timeRange.spanHours).toFixed(2)) : 0,
        after: after.timeRange && after.timeRange.spanHours ? Number((after.alerts.riskGe80 / after.timeRange.spanHours).toFixed(2)) : 0
      },
      risk100: metric(before, after, ['alerts', 'riskEq100']),
      starPlusZero: metric(before, after, ['alerts', 'starPlusZero']),
      unknownGain: metric(before, after, ['unknownGain', 'total']),
      unknownPerHour: {
        before: before.timeRange && before.timeRange.spanHours ? Number((before.unknownGain.total / before.timeRange.spanHours).toFixed(2)) : 0,
        after: after.timeRange && after.timeRange.spanHours ? Number((after.unknownGain.total / after.timeRange.spanHours).toFixed(2)) : 0
      },
      unknownLowValuePercent: metric(before, after, ['unknownGain', 'lowValuePercent']),
      unknownNearKnownPercent: {
        before: before.unknownGain && before.unknownGain.nearKnownSource && before.unknownGain.nearKnownSource['3000']
          ? before.unknownGain.nearKnownSource['3000'].percent : 0,
        after: after.unknownGain && after.unknownGain.nearKnownSource && after.unknownGain.nearKnownSource['3000']
          ? after.unknownGain.nearKnownSource['3000'].percent : 0
      },
      scannerRaw: metric(before, after, ['scanner', 'rawRows']),
      scannerUnique: metric(before, after, ['scanner', 'uniqueFindingKeys']),
      scannerRepeat: metric(before, after, ['scanner', 'repeatMultiplier']),
      scannerDuplicateRiskContribution: {
        before: before.scanner && before.scanner.keysWithDuplicateRiskContribution != null
          ? before.scanner.keysWithDuplicateRiskContribution : null,
        after: after.scanner.keysWithDuplicateRiskContribution
      },
      shulkerAlerts: metric(before, after, ['shulker', 'alertsMentioningShulker']),
      highIncidents: { before: null, after: after.incidents && after.incidents.highIncidents },
      criticalIncidents: { before: null, after: after.incidents && after.incidents.criticalIncidents },
      incidentCount: { before: null, after: after.incidents && after.incidents.totalWithIncidentId }
    },
    targets: {
      starPlusZeroMustBeZero: after.alerts.starPlusZero === 0,
      scannerDuplicateRiskContributionMustBeZero: after.scanner.keysWithDuplicateRiskContribution === 0,
      normalAlertRateShouldDropByOrderOfMagnitude: before.alerts.perHour > 0
        ? after.alerts.perHour <= before.alerts.perHour / 10
        : null
    },
    unknownSourceFocus: {
      before: before.unknownGain && before.unknownGain.nearKnownSource && before.unknownGain.nearKnownSource['3000']
        ? before.unknownGain.nearKnownSource['3000'].bySource : {},
      after: after.unknownSourceFocus
    },
    highValueBurstByMaterial: {
      before: null,
      after: after.highValueBurst && after.highValueBurst.byMaterial
    },
    incidents: after.incidents,
    possibleGuiAttributionGap: after.possibleGuiAttributionGap
  };
}

function renderCompareMarkdown(compare) {
  const lines = [];
  lines.push('# Field validation compare (BEFORE vs AFTER)');
  lines.push('');
  lines.push(compare.note);
  lines.push('');
  lines.push('- BEFORE source: `' + compare.beforeSource + '`');
  lines.push('- AFTER source: `' + compare.afterSource + '`');
  lines.push('');
  lines.push('## KPIs');
  lines.push('');
  lines.push('| Metric | Before | After | Delta |');
  lines.push('| --- | ---: | ---: | ---: |');
  for (const [name, row] of Object.entries(compare.kpis)) {
    if (!row || typeof row !== 'object') {
      continue;
    }
    lines.push('| ' + name + ' | ' + fmt(row.before) + ' | ' + fmt(row.after) + ' | ' + fmt(row.delta) + ' |');
  }
  lines.push('');
  lines.push('## Hard targets');
  lines.push('');
  lines.push('- `* +0` == 0: ' + (compare.targets.starPlusZeroMustBeZero ? 'PASS' : 'FAIL'));
  lines.push('- Scanner duplicate risk contribution == 0: ' + (compare.targets.scannerDuplicateRiskContributionMustBeZero ? 'PASS' : 'FAIL'));
  lines.push('- Alert/hour order-of-magnitude drop: ' + String(compare.targets.normalAlertRateShouldDropByOrderOfMagnitude));
  lines.push('');
  lines.push('Do not treat Alert count as the only success metric. Threat suite (5/5, 1728 Netherite Critical once) must still hold.');
  lines.push('');
  return lines.join('\n');
}

function fmt(value) {
  if (value == null || Number.isNaN(value)) {
    return 'n/a';
  }
  return value;
}

if (require.main === module) {
  main();
}

module.exports = { compareReports };
