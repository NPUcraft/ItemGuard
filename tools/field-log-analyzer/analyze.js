#!/usr/bin/env node
'use strict';

/**
 * Dev-only field forensic analyzer. Must not ship in the product JAR.
 * Reads schemaVersion 1 or 2 JSONL from a zip (or extracted logs dir).
 *
 * Input:
 *   ITEMGUARD_FIELD_LOG_ZIP / -PFieldLogZip / default itemguard-log.zip
 *   ITEMGUARD_FIELD_LOG_DIR
 *
 * Output (anonymized):
 *   analysis/field-false-positive-report.json
 *   analysis/field-false-positive-report.md
 */

const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');
const zlib = require('zlib');

const ROOT = process.env.ITEMGUARD_ROOT || path.resolve(__dirname, '..', '..');
const DEFAULT_ZIP = path.join(ROOT, 'itemguard-log.zip');
const OUT_DIR = path.join(ROOT, 'analysis');
const EXTRACT_DIR = path.join(ROOT, 'build', 'field-log-extract');

const KNOWN_SOURCE_TOKENS = [
  'DOUBLE_CHEST',
  'CHEST',
  'BARREL',
  'SHULKER_BOX',
  'SHULKER',
  'HOPPER',
  'BLAST_FURNACE',
  'SMOKER',
  'FURNACE',
  'WORKBENCH',
  'CRAFTING',
  'STONECUTTER',
  'SMITHING',
  'GROUND_PICKUP',
  'CONTAINER',
  'MERCHANT',
  'BREWING',
  'CAMPFIRE',
  'COMPOSTER',
  'DROPPER',
  'DISPENSER',
  'LECTERN',
  'BUNDLE'
];

const WINDOWS_MS = [250, 500, 1000, 2000, 3000];
const DEFAULT_VALUES = {
  DIAMOND: 25,
  DIAMOND_BLOCK: 40,
  NETHERITE_INGOT: 55,
  NETHERITE_BLOCK: 80,
  ELYTRA: 90,
  TOTEM_OF_UNDYING: 70,
  ENCHANTED_GOLDEN_APPLE: 75,
  SHULKER_BOX: 35,
  DIRT: 1,
  STONE: 1,
  COBBLESTONE: 1,
  RAIL: 1
};

function main() {
  const zip = process.env.ITEMGUARD_FIELD_LOG_ZIP || process.argv[2] || DEFAULT_ZIP;
  const dirArg = process.env.ITEMGUARD_FIELD_LOG_DIR;
  let logsRoot;
  if (dirArg && fs.existsSync(dirArg)) {
    logsRoot = dirArg;
  } else if (fs.existsSync(zip)) {
    logsRoot = extractZip(zip);
  } else {
    console.error('No field logs found. Place itemguard-log.zip in the project root, or set ITEMGUARD_FIELD_LOG_ZIP / ITEMGUARD_FIELD_LOG_DIR.');
    process.exit(2);
  }

  const records = loadRecords(logsRoot);
  if (records.length === 0) {
    console.error('No JSONL records parsed from', logsRoot);
    process.exit(2);
  }

  const itemValues = loadItemValues(logsRoot);
  const report = analyze(records, itemValues, path.basename(zip));
  fs.mkdirSync(OUT_DIR, { recursive: true });
  fs.writeFileSync(path.join(OUT_DIR, 'field-false-positive-report.json'), JSON.stringify(report, null, 2));
  fs.writeFileSync(path.join(OUT_DIR, 'field-false-positive-report.md'), renderMarkdown(report));
  console.log('Wrote', path.join(OUT_DIR, 'field-false-positive-report.md'));
  console.log('alerts=', report.alerts.total, 'unknown=', report.unknownGain.total, 'players=', report.players);
}

function extractZip(zipPath) {
  fs.rmSync(EXTRACT_DIR, { recursive: true, force: true });
  fs.mkdirSync(EXTRACT_DIR, { recursive: true });
  const result = spawnSync('tar', ['-xf', zipPath, '-C', EXTRACT_DIR], { encoding: 'utf8' });
  if (result.status !== 0) {
    throw new Error('tar extract failed: ' + (result.stderr || result.stdout || result.status));
  }
  return EXTRACT_DIR;
}

function loadRecords(root) {
  const files = [];
  walk(root, files);
  const records = [];
  for (const file of files.sort()) {
    if (!file.endsWith('.jsonl')) {
      continue;
    }
    const text = fs.readFileSync(file, 'utf8');
    for (const line of text.split(/\r?\n/)) {
      if (!line.trim()) {
        continue;
      }
      try {
        records.push(JSON.parse(line));
      } catch (error) {
        // skip malformed
      }
    }
  }
  return records;
}

function walk(dir, out) {
  if (!fs.existsSync(dir)) {
    return;
  }
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(full, out);
    } else {
      out.push(full);
    }
  }
}

function loadItemValues(root) {
  const values = { ...DEFAULT_VALUES };
  const yml = findFile(root, 'items.yml');
  if (!yml) {
    return values;
  }
  const text = fs.readFileSync(yml, 'utf8');
  let def = 1;
  const defMatch = text.match(/default-value:\s*(\d+)/);
  if (defMatch) {
    def = Number(defMatch[1]);
  }
  values.__default = def;
  for (const match of text.matchAll(/^\s{2}([A-Z0-9_]+):\s*(\d+)/gm)) {
    values[match[1]] = Number(match[2]);
  }
  return values;
}

function findFile(dir, name) {
  const files = [];
  walk(dir, files);
  return files.find((file) => path.basename(file) === name);
}

function itemValue(values, material) {
  if (!material || material === '*') {
    return values.__default || 1;
  }
  if (Object.prototype.hasOwnProperty.call(values, material)) {
    return values[material];
  }
  if (material.endsWith('_SHULKER_BOX')) {
    return values.SHULKER_BOX || 35;
  }
  return values.__default || 1;
}

function valueBucket(weight) {
  if (weight <= 5) {
    return 'low';
  }
  if (weight < 20) {
    return 'medium';
  }
  if (weight < 50) {
    return 'high';
  }
  return 'extreme';
}

function amountBucket(amount) {
  const n = Number(amount) || 0;
  if (n <= 1) {
    return '1';
  }
  if (n <= 16) {
    return '2-16';
  }
  if (n <= 64) {
    return '17-64';
  }
  if (n <= 256) {
    return '65-256';
  }
  return '257+';
}

function classifySource(source) {
  const text = String(source || '').toUpperCase();
  if (!text || text === 'UNKNOWN') {
    return 'UNKNOWN';
  }
  for (const token of KNOWN_SOURCE_TOKENS) {
    if (text.includes(token)) {
      return token === 'SHULKER' ? 'SHULKER_BOX' : token;
    }
  }
  return 'OTHER';
}

function parseReasons(record) {
  const raw = record.metadata && record.metadata.reasons;
  if (!raw) {
    return record.signalTypes || [];
  }
  return String(raw).split(';').map((part) => part.trim()).filter(Boolean);
}

function reasonCategory(reason) {
  const text = reason.toLowerCase();
  if (text.includes('unexplained') || text.includes('unknown source')) {
    return 'UNEXPLAINED_ITEM_GAIN';
  }
  if (text.includes('identical')) {
    return 'REPEATED_IDENTICAL_ITEMS';
  }
  if (text.includes('within') && text.includes('gained')) {
    return text.includes('items gained') ? 'RAPID_OR_HIGH_VALUE_BURST' : 'BURST';
  }
  if (text.includes('modified component')) {
    return 'COMPONENT_MODIFIED';
  }
  if (text.includes('custom display') || text.includes('custom lore') || text.includes('custommodeldata') || text.includes('pdc')) {
    return 'CUSTOM_ITEM_METADATA';
  }
  if (text.includes('shulker')) {
    return 'SHULKER';
  }
  if (text.includes('conflicting')) {
    return 'CONFLICTING_ENCHANTMENTS';
  }
  if (text.includes('over-level') || text.includes('over level') || text.includes('illegal enchant')) {
    return 'ILLEGAL_ENCHANTMENT';
  }
  if (text.includes('oversized') || text.includes('stack')) {
    return 'STACK';
  }
  if (text.includes('attribute')) {
    return 'SUSPICIOUS_ATTRIBUTE';
  }
  if (text.includes('risk threshold')) {
    return 'RISK_THRESHOLD_REACHED';
  }
  return 'OTHER';
}

function topN(map, n) {
  return [...map.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, n)
    .map(([key, count]) => ({ key, count }));
}

function inc(map, key, by) {
  map.set(key, (map.get(key) || 0) + (by || 1));
}

function analyze(records, itemValues, sourceName) {
  const alias = new Map();
  let playerSeq = 0;
  function anonPlayer(uuid, name) {
    const key = uuid || ('name:' + name);
    if (!alias.has(key)) {
      playerSeq += 1;
      alias.set(key, { id: 'player-' + playerSeq, uuid: uuid || null });
    }
    return alias.get(key).id;
  }

  const alerts = records.filter((r) => r.type === 'ALERT');
  const events = records.filter((r) => r.type !== 'ALERT' && r.type !== 'ADMIN_ACTION');
  const unknown = records.filter((r) => r.type === 'UNKNOWN_GAIN');
  const suspicious = records.filter((r) => r.type === 'SUSPICIOUS_ITEM');
  const invalid = records.filter((r) => r.type === 'INVALID_ITEM');
  const highValue = records.filter((r) => r.type === 'HIGH_VALUE_FLOW');
  const husksync = records.filter((r) => r.type === 'HUSKSYNC_DATA_APPLY');

  const times = records.map((r) => r.epochMillis).filter((n) => typeof n === 'number').sort((a, b) => a - b);
  const start = times[0];
  const end = times[times.length - 1];
  const hours = Math.max((end - start) / 3_600_000, 1 / 60);

  const players = new Set();
  for (const record of records) {
    if (record.playerUuid) {
      players.add(record.playerUuid);
      anonPlayer(record.playerUuid, record.playerName);
    }
  }

  const alertsByHour = new Map();
  const alertsByPlayer = new Map();
  const risk100ByPlayer = new Map();
  const reasonCats = new Map();
  const signalTypes = new Map();
  const materialsAlert = new Map();
  let riskGe60 = 0;
  let riskGe80 = 0;
  let riskEq100 = 0;
  let starZero = 0;
  let critical = 0;

  for (const alert of alerts) {
    const hour = new Date(alert.epochMillis).toISOString().slice(0, 13);
    inc(alertsByHour, hour);
    const pid = anonPlayer(alert.playerUuid, alert.playerName);
    inc(alertsByPlayer, pid);
    const score = Number(alert.riskScore) || 0;
    if (score >= 60) {
      riskGe60 += 1;
    }
    if (score >= 80) {
      riskGe80 += 1;
      critical += 1;
    }
    if (score >= 100) {
      riskEq100 += 1;
      inc(risk100ByPlayer, pid);
    }
    if ((alert.material === '*' || !alert.material) && (!alert.amount || alert.amount === 0)) {
      starZero += 1;
    }
    inc(materialsAlert, alert.material || '*');
    for (const reason of parseReasons(alert)) {
      inc(reasonCats, reasonCategory(reason));
      inc(signalTypes, reasonCategory(reason));
    }
    for (const type of alert.signalTypes || []) {
      inc(signalTypes, type);
    }
  }

  const unknownByMaterial = new Map();
  const unknownByValue = new Map();
  const unknownByAmount = new Map();
  const unknownByPlayer = new Map();
  let unknownLowValue = 0;
  for (const rec of unknown) {
    const mat = rec.material || '*';
    const weight = itemValue(itemValues, mat);
    inc(unknownByMaterial, mat);
    inc(unknownByValue, valueBucket(weight));
    inc(unknownByAmount, amountBucket(rec.amount));
    inc(unknownByPlayer, anonPlayer(rec.playerUuid, rec.playerName));
    if (weight <= 5) {
      unknownLowValue += 1;
    }
  }

  const knownFlows = records.filter((r) => {
    if (r.type === 'ADMIN_ACTION') {
      return false;
    }
    const cls = classifySource(r.source);
    return cls !== 'UNKNOWN' && cls !== 'OTHER';
  });

  const unknownNear = {};
  for (const windowMs of WINDOWS_MS) {
    unknownNear[windowMs] = correlateUnknown(unknown, knownFlows, windowMs);
  }

  const scannerRaw = [...suspicious, ...invalid];
  const uniqueFindings = new Map();
  for (const rec of scannerRaw) {
    const findings = rec.findingTypes && rec.findingTypes.length ? rec.findingTypes : [rec.classification || rec.type];
    for (const finding of findings) {
      const key = [rec.playerUuid || '', rec.itemSignature || rec.material || '', finding].join('|');
      const prev = uniqueFindings.get(key) || {
        count: 0,
        riskContributionCount: 0,
        type: rec.type,
        findingType: finding,
        material: rec.material || null,
        signature: rec.itemSignature ? 'present' : 'absent',
        player: anonPlayer(rec.playerUuid, rec.playerName)
      };
      prev.count += 1;
      const contribution = Number(rec.riskScore) || Number(rec.metadata && rec.metadata.riskContribution) || 0;
      if (contribution > 0) {
        prev.riskContributionCount += 1;
      }
      uniqueFindings.set(key, prev);
    }
  }
  const uniqueList = [...uniqueFindings.values()].sort((a, b) => b.count - a.count);
  const rawFindingRows = uniqueList.reduce((sum, row) => sum + row.count, 0);

  const saturation = riskSaturation(alerts, anonPlayer);

  const bursts = [];
  for (const alert of alerts) {
    const reasons = parseReasons(alert);
    const burst = reasons.some((reason) => /items gained within/i.test(reason));
    if (!burst) {
      continue;
    }
    bursts.push({
      player: anonPlayer(alert.playerUuid, alert.playerName),
      material: alert.material || '*',
      amount: alert.amount || 0,
      itemValue: itemValue(itemValues, alert.material),
      riskScore: alert.riskScore,
      sourceClass: classifySource(alert.source),
      reasons: reasons.filter((reason) => /gained within/i.test(reason)).slice(0, 3)
    });
  }
  const lowValueBursts = bursts.filter((row) => row.itemValue <= 5 && (row.amount || 0) >= 64);

  const shulkerAlerts = alerts.filter((alert) => {
    const text = JSON.stringify(alert).toLowerCase();
    return text.includes('shulker');
  });

  return {
    generatedAt: new Date().toISOString(),
    source: sourceName,
    schemaVersions: [...new Set(records.map((r) => r.schemaVersion || 1))],
    note: 'Player names/UUIDs are anonymized. This is field evidence analysis, not a full server replay.',
    timeRange: {
      startEpochMillis: start,
      endEpochMillis: end,
      startIso: records.find((r) => r.epochMillis === start)?.timestamp,
      endIso: [...records].reverse().find((r) => r.epochMillis === end)?.timestamp,
      spanHours: Number(hours.toFixed(3))
    },
    records: {
      total: records.length,
      alerts: alerts.length,
      events: events.length,
      unknownGain: unknown.length,
      suspiciousItem: suspicious.length,
      invalidItem: invalid.length,
      highValueFlow: highValue.length,
      huskSyncApply: husksync.length
    },
    players: players.size,
    alerts: {
      total: alerts.length,
      perHour: Number((alerts.length / hours).toFixed(2)),
      riskGe60,
      riskGe80,
      riskEq100,
      criticalPriorityOrScore80: critical,
      starPlusZero: starZero,
      byHour: Object.fromEntries([...alertsByHour.entries()].sort()),
      byPlayer: topN(alertsByPlayer, 20),
      risk100ByPlayer: topN(risk100ByPlayer, 20),
      reasonCategories: topN(reasonCats, 20),
      materials: topN(materialsAlert, 20)
    },
    unknownGain: {
      total: unknown.length,
      lowValueCount: unknownLowValue,
      lowValuePercent: unknown.length ? Number((unknownLowValue / unknown.length * 100).toFixed(1)) : 0,
      byMaterial: topN(unknownByMaterial, 25),
      byValueBucket: Object.fromEntries(unknownByValue),
      byAmountBucket: Object.fromEntries(unknownByAmount),
      byPlayer: topN(unknownByPlayer, 20),
      nearKnownSource: unknownNear
    },
    scanner: {
      rawRows: scannerRaw.length,
      rawFindingOccurrences: rawFindingRows,
      uniqueFindingKeys: uniqueList.length,
      repeatMultiplier: uniqueList.length ? Number((rawFindingRows / uniqueList.length).toFixed(2)) : 0,
      topRepeats: uniqueList.slice(0, 15).map((row) => ({
        player: row.player,
        material: row.material,
        findingType: row.findingType,
        type: row.type,
        count: row.count,
        riskContributionCount: row.riskContributionCount || 0,
        signature: row.signature
      })),
      keysWithDuplicateRiskContribution: uniqueList.filter((row) => (row.riskContributionCount || 0) > 1).length
    },
    saturation,
    highValueBurst: {
      alertRowsMentioningBurst: bursts.length,
      byMaterial: topN(bursts.reduce((map, row) => {
        inc(map, row.material || '*');
        return map;
      }, new Map()), 20),
      lowValueHighAmount: lowValueBursts.slice(0, 30),
      lowValueHighAmountCount: lowValueBursts.length
    },
    shulker: {
      alertsMentioningShulker: shulkerAlerts.length,
      eventRows: records.filter((r) => /shulker/i.test(JSON.stringify(r))).length
    },
    incidents: incidentMetrics(records, alerts),
    unknownSourceFocus: unknownSourceFocus(unknownNear[3000] || { bySource: {} }, unknown.length),
    possibleGuiAttributionGap: possibleGuiAttributionGap(unknown, knownFlows),
    findings: classifyFindings({
      alerts,
      unknown,
      unknownNear,
      uniqueList,
      rawFindingRows,
      saturation,
      lowValueBursts,
      starZero,
      reasonCats
    })
  };
}

function incidentMetrics(records, alerts) {
  const byId = new Map();
  for (const rec of records) {
    if (!rec.incidentId) {
      continue;
    }
    const prev = byId.get(rec.incidentId) || {
      id: rec.incidentId,
      type: rec.incidentType || 'UNKNOWN',
      scoreSum: 0,
      scoreMax: 0,
      alerts: 0,
      highTransitions: 0,
      criticalTransitions: 0,
      rows: 0
    };
    prev.rows += 1;
    if (rec.incidentType) {
      prev.type = rec.incidentType;
    }
    const risk = Number(rec.incidentRisk != null ? rec.incidentRisk : rec.riskScore) || 0;
    prev.scoreMax = Math.max(prev.scoreMax, risk);
    prev.scoreSum += risk;
    if (rec.type === 'ALERT') {
      prev.alerts += 1;
    }
    const transition = String(rec.incidentAlertTransition || '');
    if (/HIGH/i.test(transition) && !/CRITICAL/i.test(transition)) {
      prev.highTransitions += 1;
    }
    if (/CRITICAL/i.test(transition)) {
      prev.criticalTransitions += 1;
    }
    byId.set(rec.incidentId, prev);
  }
  const list = [...byId.values()];
  const types = {};
  for (const row of list) {
    types[row.type] = (types[row.type] || 0) + 1;
  }
  const alertCounts = list.map((row) => row.alerts);
  const spam = list.filter((row) => row.alerts > 2 || row.highTransitions > 1 || row.criticalTransitions > 1);
  return {
    totalWithIncidentId: list.length,
    byType: types,
    itemGain: types.ITEM_GAIN || 0,
    illegalItem: types.ILLEGAL_ITEM || 0,
    suspiciousItem: types.SUSPICIOUS_ITEM || 0,
    shulkerActivity: types.SHULKER_ACTIVITY || 0,
    averageScore: list.length ? Number((list.reduce((sum, row) => sum + row.scoreMax, 0) / list.length).toFixed(2)) : 0,
    highIncidents: list.filter((row) => row.scoreMax >= 60 && row.scoreMax < 80).length,
    criticalIncidents: list.filter((row) => row.scoreMax >= 80).length,
    alertsPerIncidentMax: alertCounts.length ? Math.max(...alertCounts) : 0,
    alertsPerIncidentAverage: list.length ? Number((alertCounts.reduce((a, b) => a + b, 0) / list.length).toFixed(2)) : 0,
    incidentsExceedingOneHighPlusOneCritical: spam.length,
    schema2AlertRows: alerts.filter((row) => row.incidentId).length
  };
}

function unknownSourceFocus(near3s, unknownTotal) {
  const by = near3s.bySource || {};
  function pick(keys) {
    return keys.reduce((sum, key) => sum + (Number(by[key]) || 0), 0);
  }
  const chest = pick(['CHEST', 'DOUBLE_CHEST', 'BARREL']);
  const shulker = pick(['SHULKER_BOX', 'SHULKER']);
  const craft = pick(['WORKBENCH', 'CRAFTING']);
  const furnace = pick(['FURNACE', 'BLAST_FURNACE', 'SMOKER']);
  const stonecutter = pick(['STONECUTTER']);
  return {
    unknownTotal,
    nearKnownAt3s: near3s.unknownNearKnownSource || 0,
    nearKnownPercent: near3s.percent || 0,
    chest,
    shulker,
    craft,
    furnace,
    stonecutter,
    bySource: by
  };
}

function possibleGuiAttributionGap(unknown, knownFlows) {
  const near500 = correlateUnknown(unknown, knownFlows, 500);
  let clustered = 0;
  const byPlayer = new Map();
  for (const rec of unknown) {
    const pid = rec.playerUuid || rec.playerName;
    if (!byPlayer.has(pid)) {
      byPlayer.set(pid, []);
    }
    byPlayer.get(pid).push(rec);
  }
  for (const list of byPlayer.values()) {
    list.sort((a, b) => a.epochMillis - b.epochMillis);
    for (let i = 0; i < list.length; i++) {
      const window = list.filter((other) => Math.abs(other.epochMillis - list[i].epochMillis) <= 2000);
      if (window.length >= 3) {
        clustered += 1;
        break;
      }
    }
  }
  return {
    label: 'possible GUI attribution gap',
    note: 'Logs cannot prove double-click or drag. Clusters of UNKNOWN without a nearby known source are only a suspicion.',
    unknownNotNearKnownAt500ms: Math.max(0, unknown.length - (near500.unknownNearKnownSource || 0)),
    playersWithUnknownClusters: clustered
  };
}

function correlateUnknown(unknown, knownFlows, windowMs) {
  const byPlayer = new Map();
  for (const rec of knownFlows) {
    const pid = rec.playerUuid;
    if (!pid) {
      continue;
    }
    if (!byPlayer.has(pid)) {
      byPlayer.set(pid, []);
    }
    byPlayer.get(pid).push(rec);
  }
  for (const list of byPlayer.values()) {
    list.sort((a, b) => a.epochMillis - b.epochMillis);
  }
  let near = 0;
  const bySource = new Map();
  for (const rec of unknown) {
    const list = byPlayer.get(rec.playerUuid) || [];
    const t = rec.epochMillis;
    const mat = rec.material;
    let matched = null;
    for (const other of list) {
      const dt = Math.abs((other.epochMillis || 0) - t);
      if (dt > windowMs) {
        continue;
      }
      if (mat && other.material && other.material !== mat && other.material !== '*') {
        continue;
      }
      matched = classifySource(other.source);
      break;
    }
    if (matched) {
      near += 1;
      inc(bySource, matched);
    }
  }
  return {
    unknownTotal: unknown.length,
    unknownNearKnownSource: near,
    percent: unknown.length ? Number((near / unknown.length * 100).toFixed(1)) : 0,
    bySource: Object.fromEntries([...bySource.entries()].sort((a, b) => b[1] - a[1]))
  };
}

function riskSaturation(alerts, anonPlayer) {
  const byPlayer = new Map();
  for (const alert of alerts) {
    const pid = alert.playerUuid || alert.playerName;
    if (!byPlayer.has(pid)) {
      byPlayer.set(pid, []);
    }
    byPlayer.get(pid).push(alert);
  }
  const rows = [];
  for (const [pid, list] of byPlayer) {
    list.sort((a, b) => a.epochMillis - b.epochMillis);
    const first60 = list.find((a) => (a.riskScore || 0) >= 60);
    const first80 = list.find((a) => (a.riskScore || 0) >= 80);
    const first100 = list.find((a) => (a.riskScore || 0) >= 100);
    let after100 = 0;
    if (first100) {
      after100 = list.filter((a) => a.epochMillis >= first100.epochMillis).length;
    }
    const last = list[list.length - 1];
    rows.push({
      player: anonPlayer(list[0].playerUuid, list[0].playerName),
      alerts: list.length,
      firstGe60Ms: first60 ? first60.epochMillis : null,
      firstGe80Ms: first80 ? first80.epochMillis : null,
      firstEq100Ms: first100 ? first100.epochMillis : null,
      alertsAfterFirst100: after100,
      minutesFromFirst60ToLast: first60 ? Number(((last.epochMillis - first60.epochMillis) / 60000).toFixed(1)) : null
    });
  }
  rows.sort((a, b) => b.alerts - a.alerts);
  return {
    players: rows.length,
    playersWhoHit100: rows.filter((r) => r.firstEq100Ms).length,
    topPlayers: rows.slice(0, 15)
  };
}

function classifyFindings(ctx) {
  const findings = [];
  function add(id, status, title, detail) {
    findings.push({ id, status, title, detail });
  }
  add(
    'P0-ALERT-SPAM',
    ctx.alerts.length > 500 ? 'Confirmed design problem' : 'Strong suspicion',
    'Alert volume vs unique players',
    ctx.alerts.length + ' alerts. Star/+0 rows=' + ctx.starZero + '. This matches threshold-reached spam if current risk stays high.'
  );
  const burstCat = ctx.reasonCats.get('COMPONENT_MODIFIED') || 0;
  add(
    'P0-COMPONENT-RISK',
    burstCat > 50 ? 'Confirmed design problem' : 'Strong suspicion',
    'COMPONENT_MODIFIED / custom metadata in alert reasons',
    'COMPONENT_MODIFIED category count=' + burstCat + '. Field alerts cite enchanted/named/damaged components on ordinary chest loot.'
  );
  const unique = ctx.uniqueList.length;
  add(
    'P0-SCANNER-REPEAT',
    unique > 0 && ctx.rawFindingRows / Math.max(unique, 1) >= 5 ? 'Confirmed design problem' : 'Strong suspicion',
    'Scanner finding repeats',
    'raw finding occurrences=' + ctx.rawFindingRows + ' unique keys=' + unique
  );
  const near3s = ctx.unknownNear[3000];
  add(
    'P0-UNKNOWN-NEAR-KNOWN',
    near3s && near3s.percent >= 20 ? 'Strong suspicion' : 'Insufficient evidence',
    'UNKNOWN_GAIN near logged known sources',
    'At ±3s: ' + (near3s ? near3s.percent + '% of UNKNOWN share player+material with a logged known-source event. Forensic logs do not include every ordinary flow, so this is a lower bound.' : 'n/a')
  );
  add(
    'P0-DIRT-BURST',
    ctx.lowValueBursts.length > 0 ? 'Confirmed design problem' : 'Insufficient evidence',
    'Low item-value HIGH_VALUE/RAPID burst language',
    'Alerts whose material item-value<=5 and amount>=64 while reasons mention gained-within: ' + ctx.lowValueBursts.length
  );
  add(
    'P0-SATURATION',
    ctx.saturation.playersWhoHit100 > 0 ? 'Confirmed design problem' : 'Strong suspicion',
    'Sticky risk saturation',
    ctx.saturation.playersWhoHit100 + ' anonymized players reached risk 100; subsequent alerts continue while score stays high.'
  );
  return findings;
}

function renderMarkdown(report) {
  const lines = [];
  lines.push('# Field False Positive Report');
  lines.push('');
  lines.push('Generated: ' + report.generatedAt);
  lines.push('Source: `' + report.source + '` (schema ' + report.schemaVersions.join(',') + ')');
  lines.push('');
  lines.push(report.note);
  lines.push('');
  lines.push('This is **field evidence analysis**, not a full server replay. ItemGuard JSONL does not record every hint, snapshot, or reconciliation tick.');
  lines.push('');
  lines.push('## Time range');
  lines.push('');
  lines.push('- Start: ' + report.timeRange.startIso);
  lines.push('- End: ' + report.timeRange.endIso);
  lines.push('- Span hours: ' + report.timeRange.spanHours);
  lines.push('- Players (UUID): ' + report.players);
  lines.push('');
  lines.push('## Record counts');
  lines.push('');
  for (const [k, v] of Object.entries(report.records)) {
    lines.push('- ' + k + ': ' + v);
  }
  lines.push('');
  lines.push('## Alerts');
  lines.push('');
  lines.push('- Total: **' + report.alerts.total + '**');
  lines.push('- Per hour: ' + report.alerts.perHour);
  lines.push('- Risk >= 60: ' + report.alerts.riskGe60);
  lines.push('- Risk >= 80: ' + report.alerts.riskGe80);
  lines.push('- Risk = 100: ' + report.alerts.riskEq100);
  lines.push('- material=`*` amount=0: ' + report.alerts.starPlusZero);
  lines.push('');
  lines.push('### Reason categories (top)');
  lines.push('');
  for (const row of report.alerts.reasonCategories) {
    lines.push('- ' + row.key + ': ' + row.count);
  }
  lines.push('');
  lines.push('### Alerts by anonymized player (top)');
  lines.push('');
  for (const row of report.alerts.byPlayer) {
    lines.push('- ' + row.key + ': ' + row.count);
  }
  lines.push('');
  lines.push('## UNKNOWN_GAIN');
  lines.push('');
  lines.push('- Total: **' + report.unknownGain.total + '**');
  lines.push('- Low item-value (<=5): ' + report.unknownGain.lowValueCount + ' (' + report.unknownGain.lowValuePercent + '%)');
  lines.push('');
  lines.push('### Value / amount buckets');
  lines.push('');
  lines.push(JSON.stringify(report.unknownGain.byValueBucket));
  lines.push(JSON.stringify(report.unknownGain.byAmountBucket));
  lines.push('');
  lines.push('### Materials (top)');
  lines.push('');
  for (const row of report.unknownGain.byMaterial) {
    lines.push('- ' + row.key + ': ' + row.count);
  }
  lines.push('');
  lines.push('### Near known source (same player + material)');
  lines.push('');
  lines.push('| Window | Near | Percent |');
  lines.push('| --- | ---: | ---: |');
  for (const ms of Object.keys(report.unknownGain.nearKnownSource)) {
    const row = report.unknownGain.nearKnownSource[ms];
    lines.push('| ±' + ms + 'ms | ' + row.unknownNearKnownSource + ' | ' + row.percent + '% |');
  }
  lines.push('');
  lines.push('Source mix at ±3000ms:');
  lines.push('');
  lines.push(JSON.stringify(report.unknownGain.nearKnownSource['3000']?.bySource || {}, null, 2));
  lines.push('');
  lines.push('## Scanner repeats');
  lines.push('');
  lines.push('- Event rows: ' + report.scanner.rawRows);
  lines.push('- Finding occurrences: ' + report.scanner.rawFindingOccurrences);
  lines.push('- Unique player+signature+findingType: ' + report.scanner.uniqueFindingKeys);
  lines.push('- Repeat multiplier: ' + report.scanner.repeatMultiplier);
  lines.push('');
  for (const row of report.scanner.topRepeats) {
    lines.push('- x' + row.count + ' riskContribution=' + (row.riskContributionCount || 0) + ' ' + row.type + ' ' + row.findingType + ' ' + (row.material || '') + ' ' + row.player);
  }
  lines.push('');
  lines.push('- Keys with duplicate risk contribution: ' + (report.scanner.keysWithDuplicateRiskContribution || 0));
  lines.push('');
  if (report.incidents) {
    lines.push('## Incidents (schemaVersion 2)');
    lines.push('');
    lines.push('- Total with incidentId: ' + report.incidents.totalWithIncidentId);
    lines.push('- ITEM_GAIN: ' + report.incidents.itemGain);
    lines.push('- ILLEGAL_ITEM: ' + report.incidents.illegalItem);
    lines.push('- SUSPICIOUS_ITEM: ' + report.incidents.suspiciousItem);
    lines.push('- SHULKER_ACTIVITY: ' + report.incidents.shulkerActivity);
    lines.push('- Average incident score: ' + report.incidents.averageScore);
    lines.push('- High incidents: ' + report.incidents.highIncidents);
    lines.push('- Critical incidents: ' + report.incidents.criticalIncidents);
    lines.push('- Alerts per incident (avg/max): ' + report.incidents.alertsPerIncidentAverage + ' / ' + report.incidents.alertsPerIncidentMax);
    lines.push('- Incidents exceeding 1 HIGH + 1 CRITICAL transition: ' + report.incidents.incidentsExceedingOneHighPlusOneCritical);
    lines.push('');
  }
  if (report.unknownSourceFocus) {
    lines.push('## UNKNOWN near known sources (Field validation)');
    lines.push('');
    lines.push('- Chest/barrel: ' + report.unknownSourceFocus.chest);
    lines.push('- Shulker: ' + report.unknownSourceFocus.shulker);
    lines.push('- Craft: ' + report.unknownSourceFocus.craft);
    lines.push('- Furnace: ' + report.unknownSourceFocus.furnace);
    lines.push('- Stonecutter: ' + report.unknownSourceFocus.stonecutter);
    lines.push('');
  }
  if (report.possibleGuiAttributionGap) {
    lines.push('## Possible GUI attribution gap');
    lines.push('');
    lines.push(report.possibleGuiAttributionGap.note);
    lines.push('');
    lines.push('- UNKNOWN not near known source at ±500ms: ' + report.possibleGuiAttributionGap.unknownNotNearKnownAt500ms);
    lines.push('- Players with UNKNOWN clusters: ' + report.possibleGuiAttributionGap.playersWithUnknownClusters);
    lines.push('');
  }
  lines.push('## Risk saturation');
  lines.push('');
  lines.push('- Players who hit 100: ' + report.saturation.playersWhoHit100 + ' / ' + report.saturation.players);
  lines.push('');
  for (const row of report.saturation.topPlayers) {
    lines.push('- ' + row.player + ' alerts=' + row.alerts + ' after100=' + row.alertsAfterFirst100 + ' spanMin=' + row.minutesFromFirst60ToLast);
  }
  lines.push('');
  lines.push('## Low-value burst language');
  lines.push('');
  lines.push('- Count: ' + report.highValueBurst.lowValueHighAmountCount);
  lines.push('');
  lines.push('## Findings (status)');
  lines.push('');
  for (const finding of report.findings) {
    lines.push('### ' + finding.id + ' — ' + finding.status);
    lines.push('');
    lines.push('**' + finding.title + '**');
    lines.push('');
    lines.push(finding.detail);
    lines.push('');
  }
  lines.push('## Hypotheses vs this parse');
  lines.push('');
  lines.push('Prior hypothesis of ~3423 alerts is checked against `alerts.total` in this file. Use these parsed numbers, not the hypothesis, as ground truth.');
  lines.push('');
  return lines.join('\n');
}

if (require.main === module) {
  main();
}

module.exports = {
  analyze,
  loadRecords,
  extractZip,
  loadItemValues,
  renderMarkdown,
  ROOT,
  OUT_DIR,
  DEFAULT_ZIP
};
