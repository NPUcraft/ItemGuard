'use strict';

const { failDump } = require('../report');
const { waitUntil, countByName, newFlows, unknownGains, diamondCredits, markFromDump, sleep, waitForForensic, readForensicLogs, allForensicRecords, assertForensicRecordSafe } = require('../util');
const {
  countItem,
  openBlock,
  shiftClickSlot,
  normalWithdrawSlot,
  firstInventorySlot,
  firstEmptyInventorySlot,
  swapInventorySlots,
  craftDiamonds,
  creativeGiveDiamond
} = require('../client');

const NOT_AUTOMATED = [
  { id: 'TEST-AUTO-015', name: 'Alert escalation HIGH then CRITICAL', reason: 'No stable real HIGH→CRITICAL sequence without fabricating risk; diagnostics observer is available later.' },
  { id: 'DOUBLE-CLICK', name: 'Double click', reason: 'Not in phase 1 automation set.' },
  { id: 'NUMBER-KEY', name: 'Number-key swap', reason: 'Not in phase 1 automation set.' },
  { id: 'COMPLEX-DRAG', name: 'Complex drag', reason: 'Not in phase 1 automation set.' },
  { id: 'SHIFT-CRAFT', name: 'Shift-crafting', reason: 'Phase 1 crafts once; shift-craft is pending.' },
  { id: 'HUSKSYNC-CLUSTER', name: 'HuskSync / Velocity cluster', reason: 'Run separately with gradlew.bat huskSyncIntegrationTest.' }
];

async function runTests(ctx) {
  const tests = [
    test('TEST-AUTO-001', 'Pickup one diamond', true, () => pickup(ctx, 1)),
    test('TEST-AUTO-002', 'Pickup stack of 64 diamonds', true, () => pickup(ctx, 64)),
    test('TEST-AUTO-003', 'Chest normal click withdraw', true, () => chest(ctx, 'normal')),
    test('TEST-AUTO-004', 'Chest shift-click', true, () => chest(ctx, 'shift')),
    test('TEST-AUTO-005', 'Partial shift-click then unrelated pickup', true, () => partial(ctx)),
    test('TEST-AUTO-006', 'Crafting table diamond block', true, () => craft(ctx)),
    test('TEST-AUTO-007', 'Death keepInventory false/true', true, () => death(ctx)),
    test('TEST-AUTO-008', 'Bundle economic movement', false, () => bundle(ctx)),
    test('TEST-AUTO-009', 'Filled shulker from chest', true, () => filledShulker(ctx)),
    test('TEST-AUTO-010', 'Placed shulker withdraw', true, () => placedShulker(ctx)),
    test('TEST-AUTO-011', 'Creative inventory diamond', false, () => creative(ctx)),
    test('TEST-AUTO-012', 'Illegal enchantment scanner', true, () => illegalScan(ctx)),
    test('TEST-AUTO-013', 'Legal custom item scanner', true, () => customItem(ctx)),
    test('TEST-AUTO-014', 'Risk signal expiration', true, () => riskExpiration(ctx))
  ];
  const results = [];
  for (const entry of tests) {
    results.push(await runOne(ctx, entry));
  }
  for (const item of NOT_AUTOMATED) {
    results.push({
      id: item.id,
      name: item.name,
      status: 'NOT AUTOMATED',
      required: false,
      note: item.reason
    });
  }
  return results;
}

function test(id, name, required, fn) {
  return { id, name, required, fn };
}

async function runOne(ctx, entry) {
  const started = Date.now();
  try {
    await ctx.harness.reset();
    await ctx.harness.waitSettled({ timeout: 4000 });
    const result = await entry.fn();
    return {
      id: entry.id,
      name: entry.name,
      status: result.status || 'PASS',
      required: entry.required,
      note: result.note || null,
      elapsedMs: Date.now() - started,
      dump: result.dump || null
    };
  } catch (error) {
    let dump = null;
    try {
      dump = await ctx.harness.diagnostic();
    } catch {
      dump = null;
    }
    console.error(`${entry.id} failed:`, error.message);
    return {
      id: entry.id,
      name: entry.name,
      status: 'FAIL',
      required: entry.required,
      error: error.message,
      elapsedMs: Date.now() - started,
      dump: failDump({ ctx, dump, extra: error.last || error.details || null })
    };
  }
}

async function mark(ctx, options = {}) {
  const dump = await ctx.harness.waitSettled(options);
  return { dump, ...markFromDump(dump) };
}

async function pickup(ctx, amount) {
  const prepared = await ctx.harness.prepare(amount === 1 ? 'pickup-one' : 'pickup-stack');
  const startedAt = prepared.startedAt;
  await waitUntil(() => countItem(ctx.bot, 'diamond') >= amount, {
    timeout: 4000,
    message: `Bot did not pick up ${amount} diamond(s)`
  });
  const dump = await waitUntil(async () => {
    const current = await ctx.harness.diagnostic();
    const snapshot = current.snapshot || {};
    const diamondFlows = newFlows(snapshot, { ids: new Set() }, (flow) => (
      flow.material === 'DIAMOND' && flow.amountDelta === amount && flow.source === 'GROUND_PICKUP' && flow.timestampEpochMilli >= startedAt
    ));
    if (countByName(current.inventory, 'DIAMOND') !== amount) {
      return null;
    }
    if (!diamondFlows.length) {
      return null;
    }
    return current;
  }, { timeout: 4000, message: 'ItemGuard did not record GROUND_PICKUP in time' });

  const snapshot = dump.snapshot;
  assert(countByName(dump.inventory, 'DIAMOND') === amount, `inventory DIAMOND=${countByName(dump.inventory, 'DIAMOND')}`);
  const pickupFlows = (snapshot.recentFlows || []).filter((flow) => (
    flow.timestampEpochMilli >= startedAt
    && flow.material === 'DIAMOND'
    && flow.amountDelta === amount
    && flow.source === 'GROUND_PICKUP'
    && flow.destination === 'PLAYER_INVENTORY'
    && flow.confidence === 'VERIFIED'
  ));
  assert(pickupFlows.length >= 1, 'missing VERIFIED GROUND_PICKUP flow');
  assert(unknownGains(snapshot, startedAt).length === 0, 'unexpected UNKNOWN gain');
  assert(snapshot.riskScore < ctx.alertThreshold, `risk ${snapshot.riskScore} reached alert threshold`);
  assert(diamondCredits(snapshot).length === 0, 'stale diamond expected credits remain');
  await sleep(1500);
  const forensic = readForensicLogs(ctx.runtimeDir);
  const recent = allForensicRecords(forensic).filter((record) => Number(record.epochMillis || 0) >= startedAt - 250);
  recent.forEach(assertForensicRecordSafe);
  assert(!recent.some((record) => record.type === 'UNKNOWN_GAIN' && record.material === 'DIAMOND'), 'diamond pickup wrote UNKNOWN_GAIN JSONL');
  assert(!recent.some((record) => record.type === 'HIGH_VALUE_FLOW' && record.material === 'DIAMOND'), 'low-value diamond pickup wrote HIGH_VALUE_FLOW JSONL');
  return { status: 'PASS', dump };
}

async function chest(ctx, mode) {
  const prepared = await ctx.harness.prepare(mode === 'shift' ? 'chest-shift' : 'chest-normal');
  const baseline = await mark(ctx, { after: prepared.startedAt, timeout: 4000 });
  const window = await openBlock(ctx.bot, prepared.x, prepared.y, prepared.z);
  if (mode === 'shift') {
    await shiftClickSlot(ctx.bot, 0);
  } else {
    await normalWithdrawSlot(ctx.bot, 0);
  }
  await window.close();
  const dump = await waitUntil(async () => {
    const current = await ctx.harness.diagnostic();
    if (countByName(current.inventory, 'DIAMOND') !== 64) {
      return null;
    }
    const flows = newFlows(current.snapshot, baseline, (flow) => (
      flow.material === 'DIAMOND' && flow.amountDelta === 64 && flow.source === 'CONTAINER'
    ));
    return flows.length ? current : null;
  }, { timeout: 4000, message: `Chest ${mode} did not produce CONTAINER +64` });
  assert(unknownGains(dump.snapshot, baseline).length === 0, 'UNKNOWN after chest withdraw');
  assert(diamondCredits(dump.snapshot).length === 0, 'leftover chest credits');
  return { status: 'PASS', dump };
}

async function partial(ctx) {
  const prepared = await ctx.harness.prepare('chest-partial');
  const baseline = await mark(ctx, {
    timeout: 4000,
    after: prepared.startedAt,
    requireInventory: { DIAMOND: 32 },
    message: 'Partial fixture inventory did not settle'
  });
  await waitUntil(() => countItem(ctx.bot, 'cobblestone') >= 64 && countItem(ctx.bot, 'diamond') >= 32, {
    timeout: 3000,
    message: 'Mineflayer inventory did not sync the partial-shift fixture'
  });
  const window = await openBlock(ctx.bot, prepared.x, prepared.y, prepared.z);
  await shiftClickSlot(ctx.bot, 0);
  await window.close();
  const afterShift = await waitUntil(async () => {
    const current = await ctx.harness.diagnostic();
    if (countByName(current.inventory, 'DIAMOND') !== 64) {
      return null;
    }
    const shiftFlows = newFlows(current.snapshot, baseline, (flow) => flow.material === 'DIAMOND' && flow.amountDelta > 0);
    return shiftFlows.length ? current : null;
  }, { timeout: 4000, message: 'Partial shift did not add 32 diamonds' });
  const shiftFlows = newFlows(afterShift.snapshot, baseline, (flow) => flow.material === 'DIAMOND' && flow.amountDelta > 0);
  const totalDelta = shiftFlows.reduce((sum, flow) => sum + flow.amountDelta, 0);
  assert(totalDelta === 32, `ItemGuard diamond delta ${totalDelta}, expected +32`);
  assert(!shiftFlows.some((flow) => flow.amountDelta === 64), 'ItemGuard recorded +64 on a +32 shift');
  assert(unknownGains(afterShift.snapshot, baseline).length === 0, 'UNKNOWN on partial shift');
  assert(diamondCredits(afterShift.snapshot).length === 0, 'leftover 32 expected credits after partial shift');

  const cleared = await ctx.harness.prepare('clear-filler');
  await mark(ctx, { timeout: 4000, after: cleared.startedAt });
  const afterChest = await mark(ctx, { timeout: 4000 });
  const dropped = await ctx.harness.drop(32, { reset: false });
  await ctx.harness.waitSettled({ timeout: 4000, after: dropped.startedAt });
  await waitUntil(async () => {
    const current = await ctx.harness.diagnostic();
    return countByName(current.inventory, 'DIAMOND') >= 96 ? current : null;
  }, {
    timeout: 5000,
    message: 'Bot did not pick up the second 32 diamonds'
  });
  const afterPickup = await waitUntil(async () => {
    const current = await ctx.harness.diagnostic();
    const flows = newFlows(current.snapshot, afterChest, (flow) => (
      flow.material === 'DIAMOND' && flow.amountDelta === 32 && flow.source === 'GROUND_PICKUP'
    ));
    return flows.length ? current : null;
  }, { timeout: 4000, message: 'Second gain was not GROUND_PICKUP (chest credit leak?)' });
  const leaked = newFlows(afterPickup.snapshot, afterChest, (flow) => (
    flow.material === 'DIAMOND' && flow.source === 'CONTAINER' && flow.amountDelta > 0
  ));
  assert(leaked.length === 0, 'previous chest credit consumed the unrelated pickup');
  return { status: 'PASS', dump: afterPickup };
}

async function craft(ctx) {
  const prepared = await ctx.harness.prepare('craft');
  const baseline = await mark(ctx, {
    timeout: 4000,
    after: prepared.startedAt,
    requireInventory: { DIAMOND_BLOCK: 1 },
    message: 'Craft fixture did not settle'
  });
  try {
    await craftDiamonds(ctx.bot, prepared.x, prepared.y, prepared.z);
  } catch (error) {
    return {
      status: 'PARTIAL',
      note: `Mineflayer crafting API failed: ${error.message}`,
      dump: await ctx.harness.diagnostic()
    };
  }
  const dump = await waitUntil(async () => {
    const current = await ctx.harness.diagnostic();
    if (countByName(current.inventory, 'DIAMOND') !== 9) {
      return null;
    }
    const flows = newFlows(current.snapshot, baseline, (flow) => (
      flow.material === 'DIAMOND' && flow.source === 'CRAFTING' && flow.amountDelta === 9
    ));
    return flows.length ? current : null;
  }, { timeout: 5000, message: 'Crafting did not produce CRAFTING +9 DIAMOND' });
  assert(unknownGains(dump.snapshot, baseline).length === 0, 'UNKNOWN during craft');
  const diamondFlows = newFlows(dump.snapshot, baseline, (flow) => flow.material === 'DIAMOND' && flow.amountDelta > 0);
  const crafted = diamondFlows.reduce((sum, flow) => sum + flow.amountDelta, 0);
  assert(crafted === 9, `craft double-count? diamond flows summed to ${crafted}`);
  return { status: 'PASS', dump };
}

async function death(ctx) {
  await ctx.harness.keepInventory(false);
  const prepared = await ctx.harness.prepare('death-diamonds');
  await mark(ctx, {
    timeout: 4000,
    after: prepared.startedAt,
    requireInventory: { DIAMOND: 64 },
    message: 'Death fixture diamonds did not settle'
  });
  const beforeKill = await mark(ctx, { timeout: 4000 });
  await ctx.harness.kill();
  await waitUntil(async () => {
    const current = await ctx.harness.diagnostic();
    return current.health > 0 && countByName(current.inventory, 'DIAMOND') === 0 ? current : null;
  }, {
    timeout: 5000,
    message: 'Bot did not respawn empty after keepInventory=false'
  });
  const postDeath = await ctx.harness.diagnostic();
  assert(unknownGains(postDeath.snapshot, beforeKill).length === 0, 'UNKNOWN gain on death/respawn');
  const afterRespawn = await mark(ctx, { timeout: 4000 });
  await ctx.harness.prepare('pickup-now');
  await waitUntil(async () => {
    const current = await ctx.harness.diagnostic();
    return countByName(current.inventory, 'DIAMOND') >= 64 ? current : null;
  }, {
    timeout: 5000,
    message: 'Bot did not pick up death drops'
  });
  const afterPickup = await waitUntil(async () => {
    const current = await ctx.harness.diagnostic();
    const flows = newFlows(current.snapshot, afterRespawn, (flow) => (
      flow.material === 'DIAMOND' && flow.source === 'GROUND_PICKUP' && flow.amountDelta > 0
    ));
    return flows.length ? current : null;
  }, { timeout: 4000, message: 'Death drops were not GROUND_PICKUP' });
  assert(unknownGains(afterPickup.snapshot, afterRespawn).length === 0, 'UNKNOWN after death pickup');

  await ctx.harness.keepInventory(true);
  const keepPrep = await ctx.harness.prepare('death-diamonds');
  await mark(ctx, {
    timeout: 4000,
    after: keepPrep.startedAt,
    requireInventory: { DIAMOND: 64 }
  });
  const keepGate = await mark(ctx, { timeout: 4000 });
  await ctx.harness.kill();
  await waitUntil(async () => {
    const current = await ctx.harness.diagnostic();
    return current.health > 0 && countByName(current.inventory, 'DIAMOND') === 64 ? current : null;
  }, {
    timeout: 5000,
    message: 'keepInventory=true did not restore diamonds'
  });
  const keepDump = await ctx.harness.diagnostic();
  assert(unknownGains(keepDump.snapshot, keepGate).length === 0, 'keepInventory produced loss+unknown gain cycle');
  await ctx.harness.keepInventory(false);
  return { status: 'PASS', dump: keepDump };
}

async function bundle(ctx) {
  const prepared = await ctx.harness.prepare('bundle');
  const baseline = await mark(ctx, { timeout: 4000, after: prepared.startedAt });
  const from = firstInventorySlot(ctx.bot, 'bundle');
  const to = firstEmptyInventorySlot(ctx.bot);
  if (from == null || to == null) {
    return {
      status: 'PARTIAL',
      note: 'Mineflayer could not locate bundle/empty slot for inventory move',
      dump: await ctx.harness.diagnostic()
    };
  }
  try {
    await swapInventorySlots(ctx.bot, from, to);
  } catch (error) {
    return {
      status: 'PARTIAL',
      note: `Mineflayer could not move bundle: ${error.message}`,
      dump: await ctx.harness.diagnostic()
    };
  }
  const afterMove = await ctx.harness.waitSettled({ timeout: 4000 });
  const diamondGains = newFlows(afterMove.snapshot, baseline, (flow) => (
    flow.material === 'DIAMOND' && flow.amountDelta > 0
  ));
  assert(diamondGains.length === 0, 'moving a filled bundle created a DIAMOND gain');
  assert(unknownGains(afterMove.snapshot, baseline).length === 0, 'UNKNOWN while moving bundle');
  const newBursts = (afterMove.snapshot.recentSignals || []).filter((signal) => (
    flowType(signal) === 'HIGH_VALUE_ITEM_BURST'
    && !signal.expired
    && !(baseline.signalIds && baseline.signalIds.has(signal.signalId))
  ));
  assert(newBursts.length === 0, 'HIGH_VALUE_BURST while moving bundle');

  const chestPrep = await ctx.harness.prepare('bundle-chest');
  const chestBase = await mark(ctx, { timeout: 4000, after: chestPrep.startedAt });
  try {
    const window = await openBlock(ctx.bot, chestPrep.x, chestPrep.y, chestPrep.z);
    await shiftClickSlot(ctx.bot, 0);
    await window.close();
  } catch (error) {
    return {
      status: 'PARTIAL',
      note: `Bundle chest withdraw via Mineflayer failed: ${error.message}`,
      dump: await ctx.harness.diagnostic()
    };
  }
  const afterChest = await waitUntil(async () => {
    const current = await ctx.harness.diagnostic();
    if (countByName(current.inventory, 'BUNDLE') < 1) {
      return null;
    }
    const bundleFlows = newFlows(current.snapshot, chestBase, (flow) => flow.material === 'BUNDLE' && flow.amountDelta === 1);
    return bundleFlows.length ? current : null;
  }, { timeout: 4000, message: 'Bot did not receive bundle from chest' });
  const nestedDiamond = newFlows(afterChest.snapshot, chestBase, (flow) => (
    flow.material === 'DIAMOND' && flow.amountDelta > 0
  ));
  assert(nestedDiamond.length === 0, 'taking a filled bundle created +64 DIAMOND flow');
  const bundleFlows = newFlows(afterChest.snapshot, chestBase, (flow) => flow.material === 'BUNDLE' && flow.amountDelta === 1);
  assert(bundleFlows.length >= 1, 'missing +1 BUNDLE flow');
  return { status: 'PASS', dump: afterChest };
}

async function filledShulker(ctx) {
  const prepared = await ctx.harness.prepare('filled-shulker');
  const baseline = await mark(ctx, { timeout: 4000, after: prepared.startedAt });
  const window = await openBlock(ctx.bot, prepared.x, prepared.y, prepared.z);
  await shiftClickSlot(ctx.bot, 0);
  await window.close();
  const dump = await waitUntil(async () => {
    const current = await ctx.harness.diagnostic();
    if (countByName(current.inventory, 'SHULKER_BOX') < 1) {
      return null;
    }
    const boxFlows = newFlows(current.snapshot, baseline, (flow) => flow.material === 'SHULKER_BOX' && flow.amountDelta === 1);
    return boxFlows.length ? current : null;
  }, { timeout: 4000, message: 'Bot did not receive filled shulker' });
  const nested = newFlows(dump.snapshot, baseline, (flow) => flow.material === 'DIAMOND' && flow.amountDelta > 0);
  assert(nested.length === 0, 'filled shulker produced nested DIAMOND flows');
  assert(unknownGains(dump.snapshot, baseline).filter((flow) => flow.material === 'DIAMOND').length === 0, 'UNEXPLAINED nested diamonds');
  const forensic = await waitForForensic(ctx.runtimeDir, (logs) => (
    logs.events.some((record) => record.type === 'HIGH_VALUE_FLOW' && record.material === 'SHULKER_BOX')
  ), { timeout: 5000, message: 'HIGH_VALUE_FLOW JSONL missing for filled shulker' });
  const highValue = forensic.logs.events.find((record) => record.type === 'HIGH_VALUE_FLOW' && record.material === 'SHULKER_BOX');
  assert(highValue, 'missing HIGH_VALUE_FLOW SHULKER_BOX record');
  assert(highValue.playerUuid === dump.uuid, `HIGH_VALUE_FLOW playerUuid ${highValue.playerUuid}`);
  assert(highValue.amount === 1, `HIGH_VALUE_FLOW amount ${highValue.amount}`);

  const from = firstInventorySlot(ctx.bot, 'shulker_box');
  const to = firstEmptyInventorySlot(ctx.bot);
  assert(from != null && to != null, 'could not find shulker/empty slot');
  const moveBase = await mark(ctx, { timeout: 4000 });
  await swapInventorySlots(ctx.bot, from, to);
  const afterMove = await ctx.harness.waitSettled({ timeout: 4000 });
  const moveGains = newFlows(afterMove.snapshot, moveBase, (flow) => flow.amountDelta > 0);
  assert(moveGains.length === 0, `moving shulker created gains: ${JSON.stringify(moveGains)}`);
  return { status: 'PASS', dump: afterMove };
}

async function placedShulker(ctx) {
  const prepared = await ctx.harness.prepare('placed-shulker');
  const baseline = await mark(ctx, { timeout: 4000, after: prepared.startedAt });
  const window = await openBlock(ctx.bot, prepared.x, prepared.y, prepared.z);
  await shiftClickSlot(ctx.bot, 0);
  await window.close();
  const dump = await waitUntil(async () => {
    const current = await ctx.harness.diagnostic();
    const flows = newFlows(current.snapshot, baseline, (flow) => (
      flow.material === 'DIAMOND' && flow.amountDelta === 64 && flow.source === 'SHULKER_BOX'
    ));
    return countByName(current.inventory, 'DIAMOND') === 64 && flows.length ? current : null;
  }, { timeout: 4000, message: 'Placed shulker withdraw was not SHULKER_BOX +64' });
  assert(unknownGains(dump.snapshot, baseline).length === 0, 'UNKNOWN from placed shulker');
  return { status: 'PASS', dump };
}

async function creative(ctx) {
  await ctx.harness.gamemode('CREATIVE');
  await sleep(200);
  const baseline = await mark(ctx, { timeout: 4000 });
  try {
    await creativeGiveDiamond(ctx.bot);
  } catch (error) {
    const dump = await ctx.harness.diagnostic();
    return {
      status: 'PARTIAL',
      note: `Mineflayer creative packet path unavailable: ${error.message}`,
      dump
    };
  }
  const dump = await waitUntil(async () => {
    const current = await ctx.harness.diagnostic();
    const flows = newFlows(current.snapshot, baseline, (flow) => (
      flow.material === 'DIAMOND' && flow.source === 'CREATIVE_INVENTORY' && flow.amountDelta >= 1
    ));
    return countByName(current.inventory, 'DIAMOND') >= 1 && flows.length ? current : null;
  }, { timeout: 4000, message: 'Creative diamond was not CREATIVE_INVENTORY' });
  assert(unknownGains(dump.snapshot, baseline).length === 0, 'Creative creation tagged UNKNOWN');
  assert(Array.isArray(dump.scan), 'scanner did not run for creative player');
  await ctx.harness.gamemode('SURVIVAL');
  return { status: 'PASS', dump };
}

async function illegalScan(ctx) {
  const prepared = await ctx.harness.prepare('illegal-enchant');
  await ctx.harness.waitSettled({ timeout: 4000, after: prepared.startedAt });
  await ctx.harness.scan();
  const dump = await waitUntil(async () => {
    const current = await ctx.harness.diagnostic();
    const findings = current.scan || [];
    const hit = findings.find((finding) => finding.signalType === 'OVER_LEVEL_ENCHANTMENT');
    const signal = (current.snapshot.recentSignals || []).find((item) => item.type === 'OVER_LEVEL_ENCHANTMENT' && !item.expired);
    return hit && countByName(current.inventory, 'DIAMOND_SWORD') >= 1 && signal ? current : null;
  }, { timeout: 5000, message: 'Scanner did not report OVER_LEVEL_ENCHANTMENT' });
  const finding = (dump.scan || []).find((item) => item.signalType === 'OVER_LEVEL_ENCHANTMENT');
  assert(finding, 'missing OVER_LEVEL_ENCHANTMENT finding');
  assert(finding.classification === 'INVALID', `classification ${finding.classification}`);
  const signal = (dump.snapshot.recentSignals || []).find((item) => item.type === 'OVER_LEVEL_ENCHANTMENT');
  assert(signal, 'missing OVER_LEVEL_ENCHANTMENT risk signal');
  assert(countByName(dump.inventory, 'DIAMOND_SWORD') >= 1, 'illegal item was removed');
  assert(dump.kicked === false && dump.banned === false, 'bot was kicked/banned');
  const forensic = await waitForForensic(ctx.runtimeDir, (logs) => (
    logs.events.some((record) => record.type === 'INVALID_ITEM' && (record.findingTypes || []).includes('OVER_LEVEL_ENCHANTMENT'))
    && logs.admin.some((record) => record.type === 'ADMIN_ACTION' && record.metadata && record.metadata.action === 'SCAN' && record.metadata.result === 'SUCCESS')
  ), { timeout: 5000, message: 'Illegal scan did not write INVALID_ITEM / ADMIN_ACTION JSONL' });
  const invalid = forensic.logs.events.find((record) => record.type === 'INVALID_ITEM' && (record.findingTypes || []).includes('OVER_LEVEL_ENCHANTMENT'));
  assert(invalid, 'missing INVALID_ITEM OVER_LEVEL_ENCHANTMENT record');
  assert(invalid.playerUuid === dump.uuid, `INVALID_ITEM playerUuid ${invalid.playerUuid}`);
  assert(invalid.material === 'DIAMOND_SWORD', `INVALID_ITEM material ${invalid.material}`);
  const admin = forensic.logs.admin.find((record) => record.metadata && record.metadata.action === 'SCAN' && record.metadata.result === 'SUCCESS');
  assert(admin, 'missing ADMIN_ACTION SCAN SUCCESS');
  assert(admin.metadata.target === ctx.botName || admin.metadata.targetUuid === dump.uuid, `SCAN target ${JSON.stringify(admin.metadata)}`);
  const unknown = forensic.logs.events.find((record) => record.type === 'UNKNOWN_GAIN' && record.material === 'DIAMOND_SWORD');
  assert(unknown, 'illegal-enchant give did not write UNKNOWN_GAIN JSONL');
  assert(unknown.source === 'UNKNOWN', `UNKNOWN_GAIN source ${unknown.source}`);
  return { status: 'PASS', dump };
}

async function customItem(ctx) {
  const prepared = await ctx.harness.prepare('custom-item');
  const dump = await waitUntil(async () => {
    const current = await ctx.harness.diagnostic();
    return (current.scan || []).length ? current : null;
  }, { timeout: 5000, message: 'Custom item scan produced no findings' });
  const findings = dump.scan || [];
  assert(findings.every((finding) => finding.classification !== 'INVALID'), `custom item marked INVALID: ${JSON.stringify(findings)}`);
  assert(findings.some((finding) => finding.classification === 'CUSTOM' || finding.classification === 'SUSPICIOUS'), 'expected CUSTOM/SUSPICIOUS findings');
  const customScore = (dump.snapshot.recentSignals || [])
    .filter((signal) => signal.type === 'CUSTOM_ITEM_METADATA' && !signal.expired)
    .reduce((sum, signal) => sum + signal.score, 0);
  assert(customScore <= 10, `CUSTOM_METADATA family cap failed, score ${customScore}`);
  return { status: 'PASS', dump };
}

async function riskExpiration(ctx) {
  const prepared = await ctx.harness.prepare('illegal-enchant');
  await ctx.harness.waitSettled({ timeout: 4000, after: prepared.startedAt });
  await ctx.harness.scan();
  const armed = await waitUntil(async () => {
    const current = await ctx.harness.diagnostic();
    const finding = (current.scan || []).find((item) => item.signalType === 'OVER_LEVEL_ENCHANTMENT');
    const signal = (current.snapshot.recentSignals || []).find((item) => item.type === 'OVER_LEVEL_ENCHANTMENT' && !item.expired);
    return finding && signal ? current : null;
  }, { timeout: 5000, message: 'Could not arm a real risk signal' });
  const cleared = await ctx.harness.reset();
  await ctx.harness.waitSettled({ timeout: 4000, after: cleared.startedAt });
  await sleep(5000);
  const dump = await waitUntil(async () => {
    const current = await ctx.harness.diagnostic();
    const activeOverLevel = (current.snapshot.recentSignals || []).filter((signal) => (
      signal.type === 'OVER_LEVEL_ENCHANTMENT' && !signal.expired
    ));
    return activeOverLevel.length === 0 ? current : null;
  }, { timeout: 5000, message: 'Expired OVER_LEVEL_ENCHANTMENT still contributed to risk' });
  const activeOverLevel = (dump.snapshot.recentSignals || []).filter((signal) => (
    signal.type === 'OVER_LEVEL_ENCHANTMENT' && !signal.expired
  ));
  assert(activeOverLevel.length === 0, 'OVER_LEVEL_ENCHANTMENT still active after TTL');
  return { status: 'PASS', dump };
}

function flowType(signal) {
  return signal.type || signal.signalType;
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

module.exports = { runTests };
