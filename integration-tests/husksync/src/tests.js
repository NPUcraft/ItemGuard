'use strict';

const { waitUntil, countByName, unknownGains, markFromDump, newFlows, sleep, waitForForensic } = require('../../bot/src/util');
const { openBlock, shiftClickSlot, blockAtInfo } = require('../../bot/src/client');
const { failDump } = require('./report');

const NOT_AUTOMATED = [
  { id: 'HS-006', name: 'Repeated restore race', reason: 'Allowed PARTIAL / NOT AUTOMATED in 1.0.' },
  { id: 'HS-007', name: 'Disconnect during sync', reason: 'Allowed PARTIAL / NOT AUTOMATED in 1.0.' },
  { id: 'HS-008', name: 'Forced timeout', reason: 'Covered by unit tests; not a real HuskSync PASS.' }
];

async function runHuskSyncTests(ctx) {
  const tests = [
    { id: 'HS-001', name: 'Basic A → B sync', required: true, run: () => hs001(ctx) },
    { id: 'HS-002', name: 'Repeated A/B switching', required: true, run: () => hs002(ctx) },
    { id: 'HS-003', name: 'Legitimate gain then sync', required: true, run: () => hs003(ctx) },
    { id: 'HS-004', name: 'Post-sync heartbeat baseline', required: true, run: () => hs004(ctx) },
    { id: 'HS-005', name: 'Online snapshot restore', required: true, run: () => hs005(ctx) }
  ];
  const results = [];
  let blocked = false;
  for (const entry of tests) {
    if (blocked) {
      results.push({
        id: entry.id,
        name: entry.name,
        status: 'NOT AUTOMATED',
        required: entry.required,
        note: 'Skipped because an earlier required HS test failed.'
      });
      continue;
    }
    const result = await runOne(ctx, entry);
    results.push(result);
    if (entry.required && result.status === 'FAIL') {
      blocked = true;
    }
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

async function runOne(ctx, entry) {
  const started = Date.now();
  try {
    const result = await entry.run();
    return {
      id: entry.id,
      name: entry.name,
      status: result.status || 'PASS',
      required: entry.required,
      elapsedMs: Date.now() - started,
      details: result.details || null,
      note: result.note || null
    };
  } catch (error) {
    let dumpA = null;
    let dumpB = null;
    try {
      dumpA = await ctx.harnessA.diagnostic().catch(() => null);
    } catch {
      dumpA = null;
    }
    try {
      dumpB = await ctx.harnessB.diagnostic().catch(() => null);
    } catch {
      dumpB = null;
    }
    return {
      id: entry.id,
      name: entry.name,
      status: 'FAIL',
      required: entry.required,
      elapsedMs: Date.now() - started,
      classification: error.classification || classify(error),
      error: error.stack || String(error),
      details: Object.assign(failDump(ctx, {
        message: error.message,
        dumpA,
        dumpB,
        eventsA: await safeEvents(ctx.harnessA),
        eventsB: await safeEvents(ctx.harnessB)
      }), error.details || {})
    };
  }
}

async function hs001(ctx) {
  await ctx.harnessA.waitForPlayer(20000);
  await ctx.harnessA.waitIdle(10000);
  const identifyA = await ctx.harnessA.identify();
  assert(identifyA.serverId === 'server-a', 'Bot did not land on server-a after Velocity login', 'HUSKSYNC_SETUP');
  assert(identifyA.online === true, 'Bot is not online on server-a', 'MINEFLAYER');
  const uuidA = identifyA.uuid;
  const uuidProxy = ctx.bot.uuid;
  assert(!!uuidA, 'Server A UUID missing', 'HUSKSYNC_SETUP');
  if (uuidProxy && uuidA.replace(/-/g, '').toLowerCase() !== String(uuidProxy).replace(/-/g, '').toLowerCase()) {
    const error = new Error(`Proxy UUID ${uuidProxy} != server-a UUID ${uuidA}`);
    error.classification = 'HUSKSYNC_SETUP';
    throw error;
  }

  await ctx.harnessA.reset();
  await ctx.harnessA.waitSettled({
    timeout: 8000,
    requireInventory: {},
    message: 'Server A did not settle after reset'
  });
  await ctx.harnessA.waitIdle(5000);

  const kit = await ctx.harnessA.prepare('cross-server-kit');
  const afterKit = await ctx.harnessA.waitSettled({
    timeout: 8000,
    requireInventory: { DIAMOND: 64, ELYTRA: 1 },
    message: 'Server A did not receive the trusted HuskSync kit'
  });
  assertEq(countByName(afterKit.inventory, 'DIAMOND'), 64, 'A kit DIAMOND', 'TEST_HARNESS');
  assertEq(countByName(afterKit.inventory, 'ELYTRA'), 1, 'A kit ELYTRA', 'TEST_HARNESS');
  await ctx.harnessA.waitIdle(5000);
  const beforeRisk = Number((afterKit.snapshot || {}).riskScore || 0);
  const markA = markFromDump(afterKit);
  const switchAt = Date.now();

  await switchViaProxy(ctx, 'server-b');

  const identifyB = await ctx.harnessB.identify();
  assert(identifyB.serverId === 'server-b', 'Bot did not arrive on server-b', 'MINEFLAYER');
  assert(identifyB.online === true, 'Bot is not online on server-b', 'MINEFLAYER');
  const uuidB = identifyB.uuid;
  assert(uuidA === uuidB, `UUID changed across proxy switch A=${uuidA} B=${uuidB}`, 'HUSKSYNC_SETUP');

  const envB = await ctx.harnessB.environment();
  assert(envB.huskSyncDetected === true, 'HuskSync not detected on B', 'HUSKSYNC_SETUP');
  assert(envB.huskSyncEnabled === true, 'HuskSync plugin not enabled on B', 'HUSKSYNC_SETUP');
  assert(envB.huskSyncIntegrationActive === true, 'ItemGuard HuskSync integration not active on B', 'HUSKSYNC_SETUP');

  const pre = await waitForEvent(ctx.harnessB, 'BukkitPreSyncEvent', switchAt - 250, 10000);
  const complete = await waitForEvent(ctx.harnessB, 'BukkitSyncCompleteEvent', switchAt - 250, 10000);
  const saveA = await waitForEvent(ctx.harnessA, 'BukkitDataSaveEvent', switchAt - 250, 10000).catch(() => null);

  const afterSync = await ctx.harnessB.waitSettled({
    timeout: 10000,
    requireInventory: { DIAMOND: 64, ELYTRA: 1 },
    message: 'Server B inventory did not match the synced kit'
  });
  await ctx.harnessB.waitIdle(10000);
  const dump = await ctx.harnessB.diagnostic();
  const snapshot = dump.snapshot || {};
  const inventory = dump.inventory || {};
  assertEq(countByName(inventory, 'DIAMOND'), 64, 'B inventory DIAMOND', 'HUSKSYNC_SETUP');
  assertEq(countByName(inventory, 'ELYTRA'), 1, 'B inventory ELYTRA', 'HUSKSYNC_SETUP');

  const applyFlows = newFlows(snapshot, null, (flow) => flow.source === 'HUSKSYNC_DATA_APPLY' && flow.amountDelta > 0);
  const diamondApply = applyFlows.filter((flow) => flow.material === 'DIAMOND').reduce((sum, flow) => sum + flow.amountDelta, 0);
  const elytraApply = applyFlows.filter((flow) => flow.material === 'ELYTRA').reduce((sum, flow) => sum + flow.amountDelta, 0);
  assert(diamondApply >= 64, `Missing HUSKSYNC_DATA_APPLY DIAMOND +64 (saw ${diamondApply})`, 'ITEMGUARD_BUG');
  assert(elytraApply >= 1, `Missing HUSKSYNC_DATA_APPLY ELYTRA +1 (saw ${elytraApply})`, 'ITEMGUARD_BUG');

  const unknown = unknownGains(snapshot, null).filter((flow) => (
    (flow.material === 'DIAMOND' && flow.amountDelta >= 64) ||
    (flow.material === 'ELYTRA' && flow.amountDelta >= 1)
  ));
  assert(unknown.length === 0, `HuskSync restore produced UNKNOWN gain: ${JSON.stringify(unknown)}`, 'ITEMGUARD_BUG');

  const risk = Number(snapshot.riskScore || 0);
  assert(risk <= beforeRisk, `HuskSync apply increased risk from ${beforeRisk} to ${risk}`, 'ITEMGUARD_BUG');
  const burst = (snapshot.recentSignals || []).filter((signal) => (
    !signal.expired && (signal.type === 'HIGH_VALUE_ITEM_BURST' || signal.type === 'UNEXPLAINED_ITEM_GAIN')
  ));
  assert(burst.length === 0, `Unexpected risk signal after HuskSync apply: ${JSON.stringify(burst)}`, 'ITEMGUARD_BUG');

  const credits = snapshot.expectedCredits || [];
  assert(credits.length === 0, `Stale expected credits after HuskSync apply: ${JSON.stringify(credits)}`, 'ITEMGUARD_BUG');
  assert(snapshot.huskSyncState === 'IDLE' || snapshot.huskSyncState === 'NONE', `HuskSync transaction was ${snapshot.huskSyncState}`, 'ITEMGUARD_BUG');
  assert(snapshot.huskSyncActive === true, 'huskSyncActive is false after apply', 'HUSKSYNC_SETUP');

  const afterHeartbeats = await waitHeartbeats(ctx.harnessB, 3, 15000);
  const laterUnknown = unknownGains(afterHeartbeats.snapshot || {}, markFromDump(dump));
  assert(laterUnknown.length === 0, `Heartbeat after sync produced UNKNOWN: ${JSON.stringify(laterUnknown)}`, 'ITEMGUARD_BUG');
  const laterApply = newFlows(afterHeartbeats.snapshot || {}, markFromDump(dump), (flow) => flow.source === 'HUSKSYNC_DATA_APPLY');
  assert(laterApply.length === 0, `Duplicate HUSKSYNC_DATA_APPLY after heartbeat: ${JSON.stringify(laterApply)}`, 'ITEMGUARD_BUG');
  const lastDiff = afterHeartbeats.snapshot && afterHeartbeats.snapshot.lastDiff ? afterHeartbeats.snapshot.lastDiff : {};
  const leftover = Object.entries(lastDiff).filter(([, amount]) => Number(amount) !== 0);
  assert(leftover.length === 0, `lastDiff not empty after heartbeat: ${JSON.stringify(lastDiff)}`, 'ITEMGUARD_BUG');
  assertEq(countByName(afterHeartbeats.inventory, 'DIAMOND'), 64, 'heartbeat DIAMOND', 'ITEMGUARD_BUG');
  assertEq(countByName(afterHeartbeats.inventory, 'ELYTRA'), 1, 'heartbeat ELYTRA', 'ITEMGUARD_BUG');
  assert(afterHeartbeats.snapshot.huskSyncState === 'IDLE' || afterHeartbeats.snapshot.huskSyncState === 'NONE', 'Transaction leaked after heartbeat', 'ITEMGUARD_BUG');

  const forensic = await waitForForensic(ctx.paperB.runtimeDir, (logs) => (
    logs.events.some((record) => record.type === 'HUSKSYNC_DATA_APPLY' && record.playerUuid === uuidB)
  ), { timeout: 5000, message: 'Server B did not write HUSKSYNC_DATA_APPLY JSONL' });
  const applyLog = forensic.logs.events.find((record) => record.type === 'HUSKSYNC_DATA_APPLY' && record.playerUuid === uuidB);
  assert(applyLog, 'missing HUSKSYNC_DATA_APPLY forensic record', 'ITEMGUARD_BUG');
  assertEq(Number(applyLog.riskScore), 0, 'HUSKSYNC_DATA_APPLY riskScore', 'ITEMGUARD_BUG');
  assertEq(applyLog.serverName, 'server-b', 'HUSKSYNC_DATA_APPLY serverName', 'ITEMGUARD_BUG');
  assertHuskSyncForensicV2(applyLog, 'HS-001 forensic');
  assertNoItemGainIncident(snapshot, 'HS-001 after apply');
  assertNoItemGainIncident(afterHeartbeats.snapshot, 'HS-001 after heartbeat');
  assertNoStaleCredits(snapshot, 'HS-001 after apply');
  assertNoStaleCredits(afterHeartbeats.snapshot, 'HS-001 after heartbeat');
  assertNoAlerts(afterHeartbeats.snapshot, 'HS-001');
  const unknownLogs = forensic.logs.events.filter((record) => (
    record.type === 'UNKNOWN_GAIN'
    && record.playerUuid === uuidB
    && (!applyLog.correlationId || record.correlationId === applyLog.correlationId)
  ));
  assert(unknownLogs.length === 0, `HuskSync apply wrote UNKNOWN_GAIN JSONL: ${JSON.stringify(unknownLogs)}`, 'ITEMGUARD_BUG');

  return {
    status: 'PASS',
    details: {
      inventory: afterHeartbeats.inventory,
      flowSource: 'HUSKSYNC_DATA_APPLY',
      riskScore: afterHeartbeats.snapshot.riskScore,
      unknownSignalCount: (afterHeartbeats.snapshot.recentSignals || []).filter((signal) => signal.type === 'UNEXPLAINED_ITEM_GAIN' && !signal.expired).length,
      huskSyncState: afterHeartbeats.snapshot.huskSyncState,
      huskSyncTransactionId: afterHeartbeats.snapshot.huskSyncTransactionId,
      baseline: afterHeartbeats.snapshot.materialTotals,
      lastDiff: afterHeartbeats.snapshot.lastDiff,
      uuids: { proxy: uuidProxy, serverA: uuidA, serverB: uuidB },
      events: {
        preSync: pre,
        syncComplete: complete,
        dataSaveA: saveA
      },
      kit,
      markA: { at: markA.at }
    }
  };
}

async function hs002(ctx) {
  await ensureOn(ctx, 'server-a');
  await ensureKit(ctx);
  const start = await currentHarness(ctx).waitSettled({
    timeout: 8000,
    requireInventory: { DIAMOND: 64, ELYTRA: 1 }
  });
  const mark = markFromDump(start);
  const hops = [];
  for (const target of ['server-b', 'server-a', 'server-b', 'server-a']) {
    hops.push(await switchAndStable(ctx, target, { DIAMOND: 64, ELYTRA: 1 }, mark));
  }
  const last = hops[hops.length - 1];
  assertNoUnknown(last.snapshot, mark, 'HS-002');
  assertIdle(last.snapshot);
  assertNoBurst(last.snapshot);
  assertNoItemGainIncident(last.snapshot, 'HS-002');
  assertNoStaleCredits(last.snapshot, 'HS-002');
  assertNoAlerts(last.snapshot, 'HS-002');
  assert(Number(last.snapshot.riskScore || 0) === 0, `HS-002 risk ${last.snapshot.riskScore}`, 'ITEMGUARD_BUG');
  return {
    status: 'PASS',
    details: {
      inventory: last.inventory,
      huskSyncState: last.snapshot.huskSyncState,
      riskScore: last.snapshot.riskScore,
      hops: hops.map((hop) => hop.backend)
    }
  };
}

async function hs003(ctx) {
  await ensureOn(ctx, 'server-a');
  await ensureKit(ctx);
  await ctx.harnessA.prepare('teleport-spawn');
  const before = await ctx.harnessA.waitSettled({
    timeout: 8000,
    requireInventory: { DIAMOND: 64, ELYTRA: 1 }
  });
  assertEq(countByName(before.inventory, 'DIAMOND'), 64, 'HS-003 baseline diamonds', 'TEST_HARNESS');
  const mark = markFromDump(before);
  const chest = await ctx.harnessA.prepare('chest-add-32');
  if (typeof ctx.bot.waitForChunksToLoad === 'function') {
    try {
      await ctx.bot.waitForChunksToLoad();
    } catch {
      // continue; the chest-block wait below is the real gate
    }
  }
  await waitUntil(() => {
    const pos = ctx.bot.entity && ctx.bot.entity.position;
    const block = blockAtInfo(ctx.bot, chest.x, chest.y, chest.z);
    if (!pos || Math.abs(pos.x - chest.x) > 6 || Math.abs(pos.y - chest.y) > 4 || Math.abs(pos.z - chest.z) > 6) {
      return null;
    }
    return block && /chest/i.test(block.name) ? block : null;
  }, { timeout: 8000, interval: 100, message: 'Mineflayer did not load the chest fixture after teleport' });
  try {
    const window = await openBlock(ctx.bot, chest.x, chest.y, chest.z);
    try {
      await shiftClickSlot(ctx.bot, 0);
    } finally {
      try {
        window.close();
      } catch {
        // ignore
      }
    }
  } catch (error) {
    error.classification = error.classification || 'MINEFLAYER';
    throw error;
  }
  const afterChest = await ctx.harnessA.waitSettled({
    timeout: 8000,
    requireInventory: { DIAMOND: 96, ELYTRA: 1 },
    message: 'Chest +32 diamonds did not settle on server-a'
  });
  assertEq(countByName(afterChest.inventory, 'DIAMOND'), 96, 'A after chest', 'TEST_HARNESS');
  const container = newFlows(afterChest.snapshot, mark, (flow) => (
    flow.material === 'DIAMOND' && flow.amountDelta > 0 && (flow.source === 'CONTAINER' || flow.source === 'SHULKER_BOX')
  ));
  assert(container.reduce((sum, flow) => sum + flow.amountDelta, 0) >= 32, `Missing CONTAINER +32 on A: ${JSON.stringify(newFlows(afterChest.snapshot, mark))}`, 'ITEMGUARD_BUG');
  assertNoUnknown(afterChest.snapshot, mark, 'HS-003 A chest');
  await ctx.harnessA.waitIdle(5000);

  const afterB = await switchAndStable(ctx, 'server-b', { DIAMOND: 96, ELYTRA: 1 }, markFromDump(afterChest));
  const apply = newFlows(afterB.snapshot, null, (flow) => flow.source === 'HUSKSYNC_DATA_APPLY' && flow.material === 'DIAMOND' && flow.amountDelta > 0);
  const applied = apply.reduce((sum, flow) => sum + flow.amountDelta, 0);
  assert(applied >= 32, `B did not attribute restored diamonds as HUSKSYNC_DATA_APPLY (saw ${applied})`, 'ITEMGUARD_BUG');
  assertNoUnknown(afterB.snapshot, null, 'HS-003 B restore');
  assertIdle(afterB.snapshot);
  assertNoItemGainIncident(afterB.snapshot, 'HS-003 B restore');
  assertNoStaleCredits(afterB.snapshot, 'HS-003 B restore');
  assertNoAlerts(afterB.snapshot, 'HS-003 B restore');
  assert(Number(afterB.snapshot.riskScore || 0) === 0, `B risk ${afterB.snapshot.riskScore} after legitimate 96 restore`, 'ITEMGUARD_BUG');
  const restoreJoin = (afterB.snapshot.activeIncidents || []).filter((incident) => incident.type === 'ITEM_GAIN');
  assert(restoreJoin.length === 0, `HuskSync restore joined an ITEM_GAIN incident: ${JSON.stringify(restoreJoin)}`, 'ITEMGUARD_BUG');
  return {
    status: 'PASS',
    details: {
      inventory: afterB.inventory,
      huskSyncState: afterB.snapshot.huskSyncState,
      riskScore: afterB.snapshot.riskScore,
      applyDiamonds: applied
    }
  };
}

async function hs004(ctx) {
  await ensureOn(ctx, 'server-a');
  await ensureKit(ctx);
  const afterB = await switchAndStable(ctx, 'server-b', { DIAMOND: 64, ELYTRA: 1 });
  const mark = markFromDump(afterB);
  const afterHeartbeats = await waitHeartbeats(ctx.harnessB, 3, 15000);
  const laterUnknown = unknownGains(afterHeartbeats.snapshot || {}, mark);
  assert(laterUnknown.length === 0, `Heartbeat after sync produced UNKNOWN: ${JSON.stringify(laterUnknown)}`, 'ITEMGUARD_BUG');
  const laterApply = newFlows(afterHeartbeats.snapshot || {}, mark, (flow) => flow.source === 'HUSKSYNC_DATA_APPLY');
  assert(laterApply.length === 0, `Duplicate HUSKSYNC_DATA_APPLY after heartbeat: ${JSON.stringify(laterApply)}`, 'ITEMGUARD_BUG');
  assertNoBurst(afterHeartbeats.snapshot);
  assertNoItemGainIncident(afterHeartbeats.snapshot, 'HS-004');
  assertNoStaleCredits(afterHeartbeats.snapshot, 'HS-004');
  assertNoAlerts(afterHeartbeats.snapshot, 'HS-004');
  assert(Number(afterHeartbeats.snapshot.riskScore || 0) === 0, `HS-004 risk ${afterHeartbeats.snapshot.riskScore}`, 'ITEMGUARD_BUG');
  assertIdle(afterHeartbeats.snapshot);
  const leftover = Object.entries(afterHeartbeats.snapshot.lastDiff || {}).filter(([, amount]) => Number(amount) !== 0);
  assert(leftover.length === 0, `lastDiff not empty after heartbeat: ${JSON.stringify(afterHeartbeats.snapshot.lastDiff)}`, 'ITEMGUARD_BUG');
  assertEq(countByName(afterHeartbeats.inventory, 'DIAMOND'), 64, 'HS-004 diamonds', 'ITEMGUARD_BUG');
  return {
    status: 'PASS',
    details: {
      inventory: afterHeartbeats.inventory,
      lastDiff: afterHeartbeats.snapshot.lastDiff,
      huskSyncState: afterHeartbeats.snapshot.huskSyncState,
      riskScore: afterHeartbeats.snapshot.riskScore
    }
  };
}

async function hs005(ctx) {
  await ensureOn(ctx, 'server-b');
  const harness = ctx.harnessB;
  const current = await harness.diagnostic();
  if (countByName(current.inventory, 'DIAMOND') !== 64) {
    await ensureOn(ctx, 'server-a');
    await ensureKit(ctx);
    await switchAndStable(ctx, 'server-b', { DIAMOND: 64, ELYTRA: 1 });
  }
  await harness.waitIdle(8000);
  const saveAt = Date.now();
  const saved = await harness.husksync('save');
  await waitForEvent(harness, 'BukkitDataSaveEvent', saveAt - 50, 8000);
  const snapshot64 = await waitUntil(async () => {
    const listed = await harness.husksync('list');
    return pickSavedSnapshot(listed.snapshots || [], saveAt);
  }, { timeout: 8000, interval: 200, message: 'No HuskSync snapshot after official userdata save' });
  await harness.prepare('add-diamonds-32');
  const at96 = await harness.waitSettled({
    timeout: 8000,
    requireInventory: { DIAMOND: 96 },
    message: 'Could not create 96 diamond inventory before restore'
  });
  assertEq(countByName(at96.inventory, 'DIAMOND'), 96, 'HS-005 current before restore', 'TEST_HARNESS');
  const mark = markFromDump(at96);
  const restoreAt = Date.now();
  const restored = await harness.husksync('restore', { snapshotId: snapshot64.id });
  const complete = await waitForEvent(harness, 'BukkitSyncCompleteEvent', restoreAt - 250, 10000);
  const pre = await waitForEvent(harness, 'BukkitPreSyncEvent', restoreAt - 250, 2000).catch(() => null);
  const after = await harness.waitSettled({
    timeout: 10000,
    requireInventory: { DIAMOND: 64 },
    message: 'Online snapshot restore did not return inventory to 64 diamonds'
  });
  await harness.waitIdle(10000);
  assertEq(countByName(after.inventory, 'DIAMOND'), 64, 'HS-005 restored diamonds', 'HUSKSYNC_SETUP');
  const apply = newFlows(after.snapshot, mark, (flow) => flow.source === 'HUSKSYNC_DATA_APPLY');
  const joinLike = newFlows(after.snapshot, mark, (flow) => /CROSS_SERVER|JOIN/i.test(String(flow.source || '')));
  assert(joinLike.length === 0, `Online restore used join-like source: ${JSON.stringify(joinLike)}`, 'ITEMGUARD_BUG');
  const positive = newFlows(after.snapshot, mark, (flow) => flow.amountDelta > 0);
  if (positive.length > 0) {
    assert(apply.length > 0, 'Online restore inventory gain was not classified as HUSKSYNC_DATA_APPLY', 'ITEMGUARD_BUG');
  } else {
    assert(complete != null, 'Online restore did not fire BukkitSyncCompleteEvent', 'HUSKSYNC_SETUP');
  }
  assertNoUnknown(after.snapshot, mark, 'HS-005 restore');
  assertIdle(after.snapshot);
  assertNoItemGainIncident(after.snapshot, 'HS-005 restore');
  assertNoStaleCredits(after.snapshot, 'HS-005 restore');
  assertNoAlerts(after.snapshot, 'HS-005 restore');
  assert(Number(after.snapshot.riskScore || 0) === 0, `HS-005 risk ${after.snapshot.riskScore}`, 'ITEMGUARD_BUG');
  const forensic = await waitForForensic(ctx.paperB.runtimeDir, (logs) => (
    logs.events.some((record) => (
      record.type === 'HUSKSYNC_DATA_APPLY' && Number(record.epochMillis || 0) >= restoreAt - 250
    ))
  ), { timeout: 5000, message: 'HS-005 did not write HUSKSYNC_DATA_APPLY JSONL' });
  const applyLog = (forensic.logs.events || [])
    .filter((record) => record.type === 'HUSKSYNC_DATA_APPLY' && Number(record.epochMillis || 0) >= restoreAt - 250)
    .sort((a, b) => Number(b.epochMillis || 0) - Number(a.epochMillis || 0))[0];
  assert(applyLog, 'HS-005 missing HUSKSYNC_DATA_APPLY forensic record', 'ITEMGUARD_BUG');
  assertHuskSyncForensicV2(applyLog, 'HS-005 forensic');
  const unknownLogs = (forensic.logs.events || []).filter((record) => (
    record.type === 'UNKNOWN_GAIN'
    && Number(record.epochMillis || 0) >= restoreAt - 250
  ));
  assert(unknownLogs.length === 0, `HS-005 restore wrote UNKNOWN_GAIN JSONL: ${JSON.stringify(unknownLogs)}`, 'ITEMGUARD_BUG');
  const leftover = Object.entries(after.snapshot.lastDiff || {}).filter(([, amount]) => Number(amount) !== 0);
  const afterHb = await waitHeartbeats(harness, 2, 10000);
  assertNoUnknown(afterHb.snapshot, markFromDump(after), 'HS-005 heartbeat');
  assertIdle(afterHb.snapshot);
  const leftoverAfter = Object.entries(afterHb.snapshot.lastDiff || {}).filter(([, amount]) => Number(amount) !== 0);
  assert(leftoverAfter.length === 0, `lastDiff not empty after restore heartbeat: ${JSON.stringify(afterHb.snapshot.lastDiff)}`, 'ITEMGUARD_BUG');
  return {
    status: 'PASS',
    details: {
      inventory: afterHb.inventory,
      huskSyncState: afterHb.snapshot.huskSyncState,
      riskScore: afterHb.snapshot.riskScore,
      saved,
      restored,
      snapshotId: snapshot64.id,
      saveCause: snapshot64.saveCause,
      leftoverDiff: leftover,
      preSync: pre,
      syncComplete: complete,
      events: await harness.events()
    }
  };
}

async function ensureOn(ctx, serverId) {
  const identify = await currentHarness(ctx).identify().catch(() => ({ online: false }));
  if (identify.online && identify.serverId === serverId) {
    ctx.backend = serverId;
    return;
  }
  const already = await (serverId === 'server-b' ? ctx.harnessB : ctx.harnessA).identify();
  if (already.online && already.serverId === serverId) {
    ctx.backend = serverId;
    return;
  }
  await switchViaProxy(ctx, serverId);
  await currentHarness(ctx).waitIdle(10000);
}

async function ensureKit(ctx) {
  const harness = currentHarness(ctx);
  await harness.waitForPlayer(15000);
  await harness.waitIdle(8000);
  let dump = await harness.diagnostic();
  if (countByName(dump.inventory, 'DIAMOND') === 64 && countByName(dump.inventory, 'ELYTRA') === 1) {
    return dump;
  }
  const kit = await harness.prepare('cross-server-kit');
  dump = await harness.waitSettled({
    timeout: 8000,
    requireInventory: { DIAMOND: 64, ELYTRA: 1 },
    message: 'Trusted kit was not applied'
  });
  await harness.waitIdle(5000);
  dump.kit = kit;
  return dump;
}

function currentHarness(ctx) {
  return ctx.backend === 'server-b' ? ctx.harnessB : ctx.harnessA;
}

async function switchAndStable(ctx, target, inventory, mark) {
  const switchAt = Date.now();
  await switchViaProxy(ctx, target);
  const harness = currentHarness(ctx);
  await waitForEvent(harness, 'BukkitSyncCompleteEvent', switchAt - 250, 10000).catch(() => null);
  await harness.waitIdle(10000);
  const dump = await harness.waitSettled({
    timeout: 10000,
    requireInventory: inventory,
    message: `${target} inventory did not stabilize after proxy switch`
  });
  dump.backend = target;
  if (mark) {
    assertNoUnknown(dump.snapshot, mark, `${target} switch`);
  }
  assertIdle(dump.snapshot);
  assertNoBurst(dump.snapshot);
  return dump;
}

function assertNoUnknown(snapshot, mark, label) {
  const unknown = unknownGains(snapshot, mark);
  assert(unknown.length === 0, `${label}: UNKNOWN gain ${JSON.stringify(unknown)}`, 'ITEMGUARD_BUG');
}

function assertNoItemGainIncident(snapshot, label) {
  const incidents = (snapshot && snapshot.activeIncidents) || [];
  const itemGain = incidents.filter((incident) => String(incident.type || '') === 'ITEM_GAIN');
  assert(itemGain.length === 0, `${label}: restore/sync entered ITEM_GAIN incident ${JSON.stringify(itemGain)}`, 'ITEMGUARD_BUG');
}

function assertNoStaleCredits(snapshot, label) {
  const credits = (snapshot && snapshot.expectedCredits) || [];
  const stale = credits.filter((credit) => credit.oneShot === true || Number(credit.remainingAmount || 0) > 0);
  assert(credits.length === 0 && stale.length === 0, `${label}: stale SourceHint/ExactCredit ${JSON.stringify(credits)}`, 'ITEMGUARD_BUG');
}

function assertNoAlerts(snapshot, label) {
  const alerts = (snapshot && snapshot.recentAlerts) || [];
  assert(alerts.length === 0, `${label}: unexpected staff alert ${JSON.stringify(alerts)}`, 'ITEMGUARD_BUG');
}

function assertHuskSyncForensicV2(record, label) {
  assert(record && record.type === 'HUSKSYNC_DATA_APPLY', `${label}: type is ${record && record.type}`, 'ITEMGUARD_BUG');
  assertEq(Number(record.schemaVersion), 3, `${label} schemaVersion`, 'ITEMGUARD_BUG');
  assertEq(Number(record.riskScore), 0, `${label} riskScore`, 'ITEMGUARD_BUG');
  const incidentType = record.incidentType;
  assert(
    incidentType == null || incidentType === '' || incidentType !== 'ITEM_GAIN',
    `${label}: HUSKSYNC_DATA_APPLY attached to ITEM_GAIN incident (${incidentType})`,
    'ITEMGUARD_BUG'
  );
}

function assertIdle(snapshot) {
  assert(snapshot.huskSyncState === 'IDLE' || snapshot.huskSyncState === 'NONE', `HuskSync transaction was ${snapshot.huskSyncState}`, 'ITEMGUARD_BUG');
}

function assertNoBurst(snapshot) {
  const burst = (snapshot.recentSignals || []).filter((signal) => (
    !signal.expired && (signal.type === 'HIGH_VALUE_ITEM_BURST' || signal.type === 'UNEXPLAINED_ITEM_GAIN')
  ));
  assert(burst.length === 0, `Unexpected risk signal: ${JSON.stringify(burst)}`, 'ITEMGUARD_BUG');
}

async function switchViaProxy(ctx, targetId) {
  const target = targetId === 'server-b' ? ctx.harnessB : ctx.harnessA;
  const other = targetId === 'server-b' ? ctx.harnessA : ctx.harnessB;
  if (!ctx.bot || ctx.bot._client == null) {
    const error = new Error('Mineflayer bot is not connected');
    error.classification = 'MINEFLAYER';
    throw error;
  }
  let spawned = false;
  const onSpawn = () => {
    spawned = true;
  };
  ctx.bot.once('spawn', onSpawn);
  ctx.bot.chat(`/server ${targetId}`);
  try {
    await target.waitForPlayer(15000);
  } catch (error) {
    ctx.bot.off('spawn', onSpawn);
    const wrapped = new Error(`Proxy /server ${targetId} did not place the bot on that backend: ${error.message}`);
    wrapped.classification = 'MINEFLAYER';
    throw wrapped;
  }
  const started = Date.now();
  while (!spawned && Date.now() - started < 5000) {
    await sleep(50);
  }
  ctx.bot.off('spawn', onSpawn);
  if (typeof ctx.bot.waitForChunksToLoad === 'function') {
    try {
      await ctx.bot.waitForChunksToLoad();
    } catch {
      // The chest-block wait in HS-003 is the real interaction gate.
    }
  }
  await sleep(200);
  await other.waitForPlayerGone(10000).catch(() => {});
  ctx.backend = targetId;
}

async function waitForEvent(harness, type, afterTs, timeout) {
  return waitUntil(async () => {
    const dump = await harness.events();
    const match = (dump.events || []).find((event) => (
      event.type === type && Number(event.timestampEpochMilli || 0) >= afterTs
    ));
    return match || null;
  }, { timeout, interval: 100, message: `Did not observe ${type}` });
}

async function waitHeartbeats(harness, count, timeout) {
  const first = await harness.diagnostic();
  let last = Number((first.snapshot || {}).lastReconcileEpochMilli || 0);
  let lastNanos = Number((first.snapshot || {}).lastReconcileNanos || 0);
  let seen = 0;
  return waitUntil(async () => {
    const dump = await harness.diagnostic();
    const at = Number((dump.snapshot || {}).lastReconcileEpochMilli || 0);
    const nanos = Number((dump.snapshot || {}).lastReconcileNanos || 0);
    if (at > last || (at === last && nanos !== lastNanos && nanos > 0)) {
      seen += 1;
      last = at;
      lastNanos = nanos;
    }
    return seen >= count ? dump : null;
  }, { timeout, interval: 200, message: `Did not observe ${count} ItemGuard heartbeats` });
}

async function safeEvents(harness) {
  try {
    return await harness.events();
  } catch {
    return null;
  }
}

function assert(condition, message, classification) {
  if (!condition) {
    const error = new Error(message);
    error.classification = classification || 'ITEMGUARD_BUG';
    throw error;
  }
}

function assertEq(actual, expected, label, classification) {
  if (actual !== expected) {
    const error = new Error(`${label}: expected ${expected}, got ${actual}`);
    error.classification = classification || 'ITEMGUARD_BUG';
    throw error;
  }
}

function pickSavedSnapshot(snapshots, saveAt) {
  const rows = (snapshots || []).map((row) => ({
    ...row,
    ts: Date.parse(row.timestamp || 0) || 0
  }));
  const recent = rows.filter((row) => row.ts >= saveAt - 5000);
  const pool = recent.length ? recent : rows;
  const commanded = pool.filter((row) => /SAVE_COMMAND|API/i.test(String(row.saveCause || '')));
  const ranked = (commanded.length ? commanded : pool).slice().sort((a, b) => b.ts - a.ts);
  return ranked[0] || null;
}

function classify(error) {
  const message = String(error && error.message || error || '');
  if (/docker|MariaDB|Redis|INFRASTRUCTURE|free port|Velocity failed/i.test(message)) {
    return 'INFRASTRUCTURE';
  }
  if (/HuskSync|cluster|UUID|server.yml/i.test(message)) {
    return 'HUSKSYNC_SETUP';
  }
  if (/Mineflayer|spawn|kicked|proxy \/server/i.test(message)) {
    return 'MINEFLAYER';
  }
  if (/Harness|kit|scenario/i.test(message)) {
    return 'TEST_HARNESS';
  }
  return 'ITEMGUARD_BUG';
}

module.exports = { runHuskSyncTests };
