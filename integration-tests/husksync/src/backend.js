'use strict';

const fs = require('fs');
const path = require('path');
const { copyDirIfExists, pathExists, tailLines } = require('../../bot/src/util');
const { startPaper, stopPaper } = require('../../bot/src/paper');
const { writeHuskSyncConfig, writePaperProxyConfig } = require('./config');

function classified(classification, message) {
  const error = new Error(message);
  error.classification = classification;
  return error;
}

async function startBackend(options) {
  const {
    root,
    runtimeDir,
    paperJar,
    itemGuardJar,
    harnessJar,
    huskSyncJar,
    reportsDir,
    gamePort,
    harnessPort,
    serverId,
    clusterId,
    database,
    redis,
    velocitySecret,
    librarySourceDir
  } = options;

  if (librarySourceDir && pathExists(librarySourceDir)) {
    copyDirIfExists(path.join(librarySourceDir, 'libraries'), path.join(runtimeDir, 'libraries'));
    copyDirIfExists(path.join(librarySourceDir, 'cache'), path.join(runtimeDir, 'cache'));
    copyDirIfExists(path.join(librarySourceDir, 'versions'), path.join(runtimeDir, 'versions'));
  }

  const paper = await startPaper({
    root,
    runtimeDir,
    paperJar,
    itemGuardJar,
    harnessJar,
    reportsDir,
    gamePort,
    harnessPort,
    extraPluginJars: [huskSyncJar],
    extraJvmArgs: [`-Ditemguard.server.id=${serverId}`],
    logFileName: `${serverId}.log`,
    stdoutPrefix: `[${serverId}] `,
    extraReadyCheck: (blob) => huskSyncReady(blob, serverId),
    beforeStart: (dir) => {
      writePaperProxyConfig({
        root,
        runtimeDir: dir,
        secret: velocitySecret,
        motd: `ItemGuard ${serverId}`
      });
      writeHuskSyncConfig({
        pluginDir: path.join(dir, 'plugins', 'HuskSync'),
        clusterId,
        serverName: serverId,
        database,
        redis
      });
      const written = fs.readFileSync(path.join(dir, 'plugins', 'HuskSync', 'server.yml'), 'utf8');
      if (!written.includes(`name: ${serverId}`)) {
        throw classified('TEST_HARNESS', `${serverId} HuskSync server.yml name mismatch`);
      }
      writeItemGuardLogging(dir, serverId);
    }
  });
  paper.serverId = serverId;
  paper.gamePort = gamePort;
  paper.harnessPort = harnessPort;
  paper.runtimeDir = runtimeDir;
  return paper;
}

function writeItemGuardLogging(runtimeDir, serverName) {
  const dir = path.join(runtimeDir, 'plugins', 'ItemGuard');
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, 'logging.yml'), [
    'logging:',
    '  enabled: true',
    '  format: JSONL',
    '  directory: logs',
    `  server-name: ${serverName}`,
    '  retention-days: 30',
    '  max-file-size-mb: 100',
    '  flush-interval-seconds: 1',
    '  timezone: system',
    '  queue:',
    '    capacity: 10000',
    '  categories:',
    '    alerts: true',
    '    unknown-gains: true',
    '    invalid-items: true',
    '    suspicious-items: true',
    '    husksync-data-apply: true',
    '    admin-actions: true',
    '    high-value-flows: true',
    '  high-value-flow:',
    '    minimum-item-value: 30',
    '  console-warning:',
    '    queue-drop-warning-interval-seconds: 60',
    ''
  ].join('\n'));
}

function huskSyncReady(blob, serverId) {
  if (/Error occurred while enabling HuskSync/i.test(blob)) {
    throw classified('HUSKSYNC_SETUP', `${serverId}: HuskSync failed to enable`);
  }
  if (/SQLException|Failed to initialize .*database|Could not connect to (the )?database|Unable to (initialise|initialize|connect to) (the )?(database|MariaDB|MySQL)/i.test(blob)) {
    throw classified('INFRASTRUCTURE', `${serverId}: HuskSync database connection failed`);
  }
  if (/Unable to connect to Redis|Redis connection (failed|refused)|Could not connect to Redis|Failed to connect to Redis/i.test(blob)) {
    throw classified('INFRASTRUCTURE', `${serverId}: HuskSync Redis connection failed`);
  }
  if (/HuskSync Integration: disabled/.test(blob)) {
    throw classified('HUSKSYNC_SETUP', `${serverId}: ItemGuard HuskSync integration is disabled`);
  }
  if (/NoSuchMethodError|NoClassDefFoundError|ClassCastException|LinkageError/.test(blob) && /HuskSync|husksync/i.test(blob)) {
    throw classified('HUSKSYNC_SETUP', `${serverId}: HuskSync API linkage error in logs`);
  }
  return /HuskSync Integration: enabled/.test(blob);
}

async function stopBackend(paper) {
  await stopPaper(paper);
}

function saveLogTail(logPath, count = 150) {
  try {
    return tailLines(fs.readFileSync(logPath, 'utf8'), count);
  } catch {
    return '';
  }
}

module.exports = { startBackend, stopBackend, huskSyncReady, saveLogTail };
