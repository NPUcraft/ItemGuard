'use strict';

const { sleep, waitUntil } = require('./util');

function createHarness({ port, player, timeoutMs }) {
  const base = `http://127.0.0.1:${port}`;

  async function request(pathname, body) {
    const method = body === undefined ? 'GET' : 'POST';
    const response = await fetch(base + pathname, {
      method,
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify({ player, ...body })
    });
    const text = await response.text();
    let json;
    try {
      json = JSON.parse(text);
    } catch {
      throw new Error(`Harness ${method} ${pathname} returned non-JSON (${response.status}): ${text.slice(0, 500)}`);
    }
    if (!response.ok || json.ok === false) {
      const error = new Error(`Harness ${method} ${pathname} failed: ${json.error || response.status}`);
      error.payload = json;
      throw error;
    }
    return json;
  }

  async function waitUntilReady() {
    return waitUntil(async () => {
      try {
        const health = await request('/health');
        return health.ok ? health : null;
      } catch {
        return null;
      }
    }, { timeout: 30000, interval: 200, message: 'Harness HTTP did not become ready' });
  }

  async function waitForPlayer(timeout = 20000) {
    return waitUntil(async () => {
      try {
        const env = await request('/environment');
        return (env.onlinePlayers || []).includes(player) ? env : null;
      } catch {
        return null;
      }
    }, { timeout, interval: 200, message: `${player} did not appear online` });
  }

  async function diagnostic() {
    return request('/diagnostic');
  }

  async function waitSettled(options = {}) {
    const timeout = options.timeout ?? 4000;
    return waitUntil(async () => {
      const dump = await diagnostic();
      const snapshot = dump.snapshot || {};
      if (snapshot.dirty || snapshot.reconcileScheduled) {
        return null;
      }
      if (options.after && Number(snapshot.lastReconcileEpochMilli || 0) < options.after) {
        return null;
      }
      if (options.requireInventory) {
        for (const [material, amount] of Object.entries(options.requireInventory)) {
          if (Number((dump.inventory || {})[material] || 0) !== amount) {
            return null;
          }
        }
      }
      return dump;
    }, { timeout, interval: 50, message: options.message || 'ItemGuard session did not settle' });
  }

  return {
    player,
    request,
    waitUntilReady,
    waitForPlayer,
    environment: () => request('/environment'),
    reset: () => request('/reset', {}),
    prepare: (scenario) => request('/prepare', { scenario }),
    drop: (amount, options = {}) => request('/drop', { amount, reset: options.reset === true }),
    kill: () => request('/kill', {}),
    scan: () => request('/scan', {}),
    gamemode: (mode) => request('/gamemode', { mode }),
    keepInventory: (keepInventory) => request('/gamerule', { keepInventory }),
    diagnostic,
    waitSettled,
    identify: () => request('/identify'),
    events: () => request('/events'),
    husksync: (action, extra = {}) => request('/husksync', { action, ...extra }),
    waitForPlayerGone: (timeout = 10000) => waitUntil(async () => {
      try {
        const env = await request('/environment');
        return (env.onlinePlayers || []).includes(player) ? null : env;
      } catch {
        return null;
      }
    }, { timeout, interval: 200, message: `${player} did not leave this backend` }),
    waitIdle: (timeout = 10000) => waitUntil(async () => {
      const dump = await diagnostic();
      const state = dump.snapshot && dump.snapshot.huskSyncState;
      return state === 'IDLE' || state === 'NONE' ? dump : null;
    }, { timeout, interval: 100, message: 'HuskSync transaction did not return to IDLE' }),
    timeoutMs
  };
}

module.exports = { createHarness, sleep };
