'use strict';

const fs = require('fs');
const net = require('net');
const path = require('path');
const crypto = require('crypto');

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function waitUntil(fn, options = {}) {
  const timeout = options.timeout ?? 5000;
  const interval = options.interval ?? 50;
  const started = Date.now();
  let last;
  while (Date.now() - started < timeout) {
    last = await fn();
    if (last) {
      return last;
    }
    await sleep(interval);
  }
  const error = new Error(options.message || `waitUntil timed out after ${timeout}ms`);
  error.last = last;
  throw error;
}

function sha256File(file) {
  return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
}

function fileFingerprint(file) {
  const stat = fs.statSync(file);
  return {
    path: file,
    bytes: stat.size,
    sha256: sha256File(file)
  };
}

function pathExists(file) {
  try {
    fs.accessSync(file);
    return true;
  } catch {
    return false;
  }
}

function copyFile(src, dest) {
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  fs.copyFileSync(src, dest);
}

function resolveItemGuardJar(root, explicit) {
  if (explicit && pathExists(explicit)) {
    return explicit;
  }
  const dir = path.join(root, 'build', 'libs');
  if (!pathExists(dir)) {
    return explicit || path.join(dir, 'ItemGuard-1.0.0-RC3-SNAPSHOT.jar');
  }
  const jars = fs.readdirSync(dir).filter((name) =>
    /^ItemGuard-.+\.jar$/.test(name)
      && !name.endsWith('-sources.jar')
      && !name.endsWith('-javadoc.jar')
  );
  if (jars.length === 0) {
    return explicit || path.join(dir, 'ItemGuard-1.0.0-RC3-SNAPSHOT.jar');
  }
  jars.sort((a, b) => fs.statSync(path.join(dir, b)).mtimeMs - fs.statSync(path.join(dir, a)).mtimeMs);
  return path.join(dir, jars[0]);
}

function versionFromItemGuardJar(jar) {
  const match = path.basename(jar || '').match(/^ItemGuard-(.+)\.jar$/);
  return match ? match[1] : 'unknown';
}

function copyDirIfExists(src, dest) {
  if (!pathExists(src)) {
    return;
  }
  fs.cpSync(src, dest, { recursive: true, force: true });
}

function rmDirIfExists(dir) {
  if (pathExists(dir)) {
    fs.rmSync(dir, { recursive: true, force: true });
  }
}

function findFreePort(start) {
  return new Promise((resolve, reject) => {
    const tryPort = (port, attemptsLeft) => {
      const server = net.createServer();
      server.unref();
      server.on('error', (error) => {
        if (attemptsLeft <= 0) {
          reject(new Error(`No free port starting at ${start}: ${error.message}`));
          return;
        }
        tryPort(port + 1, attemptsLeft - 1);
      });
      server.listen(port, '127.0.0.1', () => {
        const actual = server.address().port;
        server.close(() => resolve(actual));
      });
    };
    tryPort(start, 20);
  });
}

function waitForTcp(host, port, timeout = 60000) {
  return waitUntil(() => new Promise((resolve) => {
    const socket = net.connect({ host, port }, () => {
      socket.end();
      resolve(true);
    });
    socket.setTimeout(1500);
    socket.on('error', () => resolve(false));
    socket.on('timeout', () => {
      socket.destroy();
      resolve(false);
    });
  }), {
    timeout,
    interval: 400,
    message: `${host}:${port} did not accept TCP connections`
  });
}

function tailLines(text, count) {
  const lines = String(text || '').split(/\r?\n/);
  return lines.slice(Math.max(0, lines.length - count)).join('\n');
}

function countByName(counts, name) {
  if (!counts) {
    return 0;
  }
  return Number(counts[name] || 0);
}

function flowsSince(snapshot, startedAt, extraFilter) {
  const flows = snapshot && Array.isArray(snapshot.recentFlows) ? snapshot.recentFlows : [];
  return flows.filter((flow) => {
    if (startedAt && flow.timestampEpochMilli + 250 < startedAt) {
      return false;
    }
    return extraFilter ? extraFilter(flow) : true;
  });
}

function markFromDump(dump) {
  const snapshot = dump.snapshot || {};
  return {
    at: snapshot.lastReconcileEpochMilli || 0,
    ids: new Set((snapshot.recentFlows || []).map((flow) => flow.eventId)),
    signalIds: new Set((snapshot.recentSignals || []).map((signal) => signal.signalId))
  };
}

function newFlows(snapshot, mark, extraFilter) {
  const flows = snapshot && Array.isArray(snapshot.recentFlows) ? snapshot.recentFlows : [];
  return flows.filter((flow) => {
    if (mark && mark.ids && mark.ids.has(flow.eventId)) {
      return false;
    }
    return extraFilter ? extraFilter(flow) : true;
  });
}

function unknownGains(snapshot, markOrStartedAt) {
  const filter = (flow) => flow.amountDelta > 0 && (flow.source === 'UNKNOWN' || flow.confidence === 'UNKNOWN');
  if (markOrStartedAt && typeof markOrStartedAt === 'object' && markOrStartedAt.ids) {
    return newFlows(snapshot, markOrStartedAt, filter);
  }
  return flowsSince(snapshot, markOrStartedAt, filter);
}

function diamondCredits(snapshot) {
  return (snapshot.expectedCredits || []).filter((credit) => credit.material === 'DIAMOND' && credit.remainingAmount > 0);
}

function forensicLogsRoot(runtimeDir) {
  return path.join(runtimeDir, 'plugins', 'ItemGuard', 'logs');
}

function readJsonlDirectory(dir) {
  if (!pathExists(dir)) {
    return [];
  }
  const records = [];
  for (const name of fs.readdirSync(dir)) {
    if (!name.endsWith('.jsonl')) {
      continue;
    }
    const text = fs.readFileSync(path.join(dir, name), 'utf8');
    const lines = text.split(/\n/);
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i].trim();
      if (!line) {
        continue;
      }
      try {
        records.push(JSON.parse(line));
      } catch (error) {
        if (i === lines.length - 1) {
          continue;
        }
        throw new Error(`Invalid JSONL in ${name}: ${error.message}`);
      }
    }
  }
  return records;
}

function readForensicLogs(runtimeDir) {
  const root = forensicLogsRoot(runtimeDir);
  return {
    root,
    alerts: readJsonlDirectory(path.join(root, 'alerts')),
    events: readJsonlDirectory(path.join(root, 'events')),
    admin: readJsonlDirectory(path.join(root, 'admin'))
  };
}

function allForensicRecords(logs) {
  return [...logs.alerts, ...logs.events, ...logs.admin];
}

function assertForensicRecordSafe(record) {
  const sensitive = /^(ip|password|secret|token|nbt|serialized|pdcvalue|pdc-value)$/i;
  const walk = (value, key) => {
    if (key && sensitive.test(String(key))) {
      throw new Error('Forensic JSONL contains sensitive key: ' + key);
    }
    if (value && typeof value === 'object') {
      for (const [childKey, child] of Object.entries(value)) {
        walk(child, childKey);
      }
    }
  };
  walk(record, null);
  if (record.schemaVersion !== 1 && record.schemaVersion !== 2 && record.schemaVersion !== 3) {
    throw new Error('Forensic JSONL schemaVersion is not 1, 2 or 3: ' + JSON.stringify(record));
  }
}

async function waitForForensic(runtimeDir, predicate, options = {}) {
  return waitUntil(() => {
    const logs = readForensicLogs(runtimeDir);
    const records = allForensicRecords(logs);
    records.forEach(assertForensicRecordSafe);
    return predicate(logs, records) ? { logs, records } : null;
  }, {
    timeout: options.timeout ?? 5000,
    interval: options.interval ?? 100,
    message: options.message || 'Forensic JSONL did not appear in time'
  });
}

module.exports = {
  sleep,
  waitUntil,
  pathExists,
  copyFile,
  copyDirIfExists,
  rmDirIfExists,
  findFreePort,
  waitForTcp,
  tailLines,
  countByName,
  flowsSince,
  markFromDump,
  newFlows,
  unknownGains,
  diamondCredits,
  forensicLogsRoot,
  readForensicLogs,
  allForensicRecords,
  assertForensicRecordSafe,
  waitForForensic,
  resolveItemGuardJar,
  versionFromItemGuardJar,
  sha256File,
  fileFingerprint
};
