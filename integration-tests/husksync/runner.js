'use strict';

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');
const { createHarness } = require('../bot/src/harness');
const { findFreePort, pathExists, waitForTcp, resolveItemGuardJar, versionFromItemGuardJar } = require('../bot/src/util');
const { resolveArtifacts } = require('./src/artifacts');
const { dockerAvailable, composeAvailable, startDataPlane, stopDataPlane, composeLogs, externalDatabaseFromEnv } = require('./src/docker');
const { startBackend, stopBackend } = require('./src/backend');
const { startVelocity, stopVelocity } = require('./src/velocity');
const { runHuskSyncTests } = require('./src/tests');
const { writeHuskSyncReports } = require('./src/report');

const BOT_DIR = path.join(__dirname, '..', 'bot');
const ROOT = process.env.ITEMGUARD_ROOT || path.resolve(__dirname, '..', '..');

async function main() {
  const started = Date.now();
  const reportsDir = process.env.ITEMGUARD_REPORTS_DIR || path.join(ROOT, 'integration-tests', 'reports', 'husksync');
  const clusterRoot = process.env.ITEMGUARD_RUNTIME_DIR || path.join(ROOT, 'build', 'husksync-integration');
  const paperJar = process.env.ITEMGUARD_PAPER_JAR || path.join(ROOT, 'run', 'paper-1.21.8.jar');
  const itemGuardJar = resolveItemGuardJar(ROOT, process.env.ITEMGUARD_ITEMGUARD_JAR);
  const harnessJar = process.env.ITEMGUARD_HARNESS_JAR || path.join(ROOT, 'integration-tests', 'harness', 'build', 'libs', 'ItemGuard-TestHarness-1.0.0-test.jar');
  const botName = process.env.ITEMGUARD_BOT_NAME || 'ItemGuardBot';
  const runId = `igtest-${Date.now().toString(36)}`;
  const clusterId = runId;
  const composeProject = `ighs${runId.replace(/[^a-z0-9]/gi, '').slice(-16).toLowerCase()}`;

  fs.mkdirSync(reportsDir, { recursive: true });
  fs.mkdirSync(clusterRoot, { recursive: true });

  const environment = {
    paper: '1.21.8-60',
    itemguard: versionFromItemGuardJar(itemGuardJar),
    husksync: '3.8.7',
    velocity: '3.5.1-615',
    mineflayer: null,
    minecraftProtocol: 772,
    database: 'unavailable',
    redis: 'unavailable',
    clusterId,
    node: process.version,
    recommendRc1: false
  };

  const results = [];
  let dataPlane = null;
  let paperA = null;
  let paperB = null;
  let velocity = null;
  let bot = null;
  let botLog = null;
  let disconnectBot = async () => {};
  let exitCode = 0;

  try {
    checkNode();
    checkJava(environment);
    ensureNpm(environment);
    const client = require('../bot/src/client');
    disconnectBot = client.disconnectBot;
    assertFile(paperJar, 'Paper JAR not found. Set ITEMGUARD_PAPER_JAR or -PpaperJar=... Default: run/paper-1.21.8.jar');
    assertFile(itemGuardJar, 'ItemGuard JAR not found. Run gradle jar first.');
    assertFile(harnessJar, 'Test Harness JAR not found. Run gradle :harness:jar first.');

    const artifacts = await resolveArtifacts(ROOT);
    environment.husksync = artifacts.huskSyncVersion;
    environment.velocity = artifacts.velocityVersion;

    const ports = await allocatePorts({
      velocity: 25580,
      paperA: 25581,
      paperB: 25582,
      harnessA: 18781,
      harnessB: 18782,
      mariadb: 33067,
      redis: 63791
    });
    environment.velocityPort = ports.velocity;
    environment.paperAPort = ports.paperA;
    environment.paperBPort = ports.paperB;

    const db = await startDatabase({
      root: ROOT,
      project: composeProject,
      mariadbPort: ports.mariadb,
      redisPort: ports.redis,
      reportsDir
    });
    dataPlane = db.plane;
    environment.database = db.databaseLabel;
    environment.redis = db.redisLabel;
    environment.docker = db.docker ? composeProject : 'external';

    const forwardingSecret = crypto.randomBytes(16).toString('hex');
    const database = {
      host: db.host,
      port: db.port,
      database: db.database,
      user: db.user,
      password: db.password
    };
    const redis = {
      host: db.redisHost,
      port: db.redisPort,
      user: db.redisUser,
      password: db.redisPassword,
      database: 0
    };

    const serverADir = path.join(clusterRoot, 'server-a');
    const serverBDir = path.join(clusterRoot, 'server-b');
    const velocityDir = path.join(clusterRoot, 'velocity');

    paperA = await startBackend({
      root: ROOT,
      runtimeDir: serverADir,
      paperJar,
      itemGuardJar,
      harnessJar,
      huskSyncJar: artifacts.huskSyncJar,
      reportsDir,
      gamePort: ports.paperA,
      harnessPort: ports.harnessA,
      serverId: 'server-a',
      clusterId,
      database,
      redis,
      velocitySecret: forwardingSecret
    });
    paperB = await startBackend({
      root: ROOT,
      runtimeDir: serverBDir,
      paperJar,
      itemGuardJar,
      harnessJar,
      huskSyncJar: artifacts.huskSyncJar,
      reportsDir,
      gamePort: ports.paperB,
      harnessPort: ports.harnessB,
      serverId: 'server-b',
      clusterId,
      database,
      redis,
      velocitySecret: forwardingSecret,
      librarySourceDir: serverADir
    });
    if (paperA.paperVersion) {
      environment.paper = paperA.paperVersion;
    }

    velocity = await startVelocity({
      runtimeDir: velocityDir,
      velocityJar: artifacts.velocityJar,
      reportsDir,
      proxyPort: ports.velocity,
      serverAPort: ports.paperA,
      serverBPort: ports.paperB,
      forwardingSecret
    });

    const harnessA = createHarness({ port: ports.harnessA, player: botName, timeoutMs: 10000 });
    const harnessB = createHarness({ port: ports.harnessB, player: botName, timeoutMs: 10000 });
    await harnessA.waitUntilReady();
    await harnessB.waitUntilReady();
    const envA = await harnessA.environment();
    const envB = await harnessB.environment();
    assertReady(envA, 'server-a');
    assertReady(envB, 'server-b');
    if (envA.itemguard) {
      environment.itemguard = envA.itemguard;
    }
    if (envA.bukkitVersion) {
      environment.paper = `${envA.minecraft || '1.21.8'} / ${envA.bukkitVersion}`;
    }
    if (envA.huskSyncVersion) {
      environment.husksync = envA.huskSyncVersion;
    }

    botLog = fs.createWriteStream(path.join(reportsDir, 'bot.log'), { flags: 'w' });
    bot = await client.connectBot({
      host: '127.0.0.1',
      port: ports.velocity,
      username: botName,
      version: '1.21.8'
    });
    attachBotLog(bot, botLog);
    environment.mineflayer = require(path.join(BOT_DIR, 'node_modules', 'mineflayer', 'package.json')).version;
    environment.minecraftProtocol = bot.protocolVersion || 772;
    environment.botVersion = bot.version;
    environment.botUuid = bot.uuid;

    await harnessA.waitForPlayer(20000);
    const identify = await harnessA.identify();
    if (identify.serverId !== 'server-a' || !identify.online) {
      const error = new Error(`Bot connected through Velocity but did not appear on server-a (identify=${JSON.stringify(identify)})`);
      error.classification = 'MINEFLAYER';
      throw error;
    }

    const ctx = {
      bot,
      botName,
      harnessA,
      harnessB,
      paperA,
      paperB,
      velocity,
      dataPlane,
      reportsDir,
      environment,
      backend: 'server-a'
    };
    results.push(...await runHuskSyncTests(ctx));
  } catch (error) {
    exitCode = 1;
    results.push({
      id: 'HS-INFRA',
      name: 'HuskSync cluster infrastructure',
      status: 'FAIL',
      required: true,
      classification: error.classification || 'INFRASTRUCTURE',
      error: error.stack || String(error),
      details: error.details || (dataPlane && dataPlane.logs ? { docker: dataPlane.logs() } : null)
    });
    console.error(error);
    if (dataPlane && dataPlane.file) {
      try {
        fs.writeFileSync(path.join(reportsDir, 'mariadb.log'), composeLogs(dataPlane.file, dataPlane.project, dataPlane.env, 'mariadb'));
        fs.writeFileSync(path.join(reportsDir, 'redis.log'), composeLogs(dataPlane.file, dataPlane.project, dataPlane.env, 'redis'));
      } catch {
        // ignore
      }
    }
  } finally {
    try {
      await disconnectBot(bot);
    } catch (error) {
      console.error('Bot disconnect failed:', error);
    }
    try {
      if (botLog) {
        botLog.end();
      }
    } catch {
      // ignore
    }
    try {
      await stopVelocity(velocity);
    } catch (error) {
      console.error('Velocity shutdown failed:', error);
    }
    try {
      await stopBackend(paperA);
    } catch (error) {
      console.error('Paper A shutdown failed:', error);
    }
    try {
      await stopBackend(paperB);
    } catch (error) {
      console.error('Paper B shutdown failed:', error);
    }
    try {
      await stopDataPlane(dataPlane);
    } catch (error) {
      console.error('Docker teardown failed:', error);
    }
  }

  const requiredPass = results.filter((test) => ['HS-001', 'HS-002', 'HS-003', 'HS-004', 'HS-005'].includes(test.id) && test.status === 'PASS').length;
  environment.recommendRc1 = requiredPass >= 5 && results.every((test) => test.status !== 'FAIL');
  const report = writeHuskSyncReports({
    reportsDir,
    environment,
    results,
    elapsedMs: Date.now() - started
  });
  console.log(report.markdown);
  const requiredFailed = results.some((test) => test.required && test.status !== 'PASS' && test.status !== 'PARTIAL');
  const anyFail = results.some((test) => test.status === 'FAIL');
  if (requiredFailed || anyFail || exitCode !== 0) {
    process.exitCode = 1;
  }
}

async function startDatabase({ root, project, mariadbPort, redisPort, reportsDir }) {
  if (dockerAvailable() && composeAvailable()) {
    const plane = await startDataPlane({ root, project, mariadbPort, redisPort });
    await waitForTcp('127.0.0.1', mariadbPort, 30000);
    await waitForTcp('127.0.0.1', redisPort, 15000);
    try {
      fs.writeFileSync(path.join(reportsDir, 'mariadb.log'), composeLogs(plane.file, plane.project, plane.env, 'mariadb'));
      fs.writeFileSync(path.join(reportsDir, 'redis.log'), composeLogs(plane.file, plane.project, plane.env, 'redis'));
    } catch {
      // ignore
    }
    return {
      plane,
      docker: true,
      host: '127.0.0.1',
      port: mariadbPort,
      database: 'itemguard_husksync_test',
      user: 'itemguard_test',
      password: 'itemguard-test-pass',
      redisHost: '127.0.0.1',
      redisPort: redisPort,
      redisUser: '',
      redisPassword: '',
      databaseLabel: `MariaDB 11 @ 127.0.0.1:${mariadbPort}/itemguard_husksync_test`,
      redisLabel: `Redis 7 @ 127.0.0.1:${redisPort}`
    };
  }
  const external = externalDatabaseFromEnv();
  if (external) {
    await waitForTcp(external.host, external.port, 15000);
    await waitForTcp(external.redisHost, external.redisPort, 15000);
    return {
      plane: null,
      docker: false,
      host: external.host,
      port: external.port,
      database: external.database,
      user: external.user,
      password: external.password,
      redisHost: external.redisHost,
      redisPort: external.redisPort,
      redisUser: external.redisUser,
      redisPassword: external.redisPassword,
      databaseLabel: `external MariaDB ${external.host}:${external.port}/${external.database}`,
      redisLabel: `external Redis ${external.redisHost}:${external.redisPort}`
    };
  }
  const error = new Error(
    'huskSyncIntegrationTest needs Docker Compose for MariaDB+Redis, or ITEMGUARD_TEST_DB_HOST and ITEMGUARD_TEST_REDIS_HOST. Ordinary gradle build / integrationTest are unaffected.'
  );
  error.classification = 'INFRASTRUCTURE';
  throw error;
}

function assertReady(env, serverId) {
  if (!env.itemguardEnabled) {
    const error = new Error(`${serverId}: ItemGuard did not enable`);
    error.classification = 'ITEMGUARD_BUG';
    throw error;
  }
  if (!env.huskSyncDetected || !env.huskSyncEnabled) {
    const error = new Error(`${serverId}: HuskSync plugin is not enabled`);
    error.classification = 'HUSKSYNC_SETUP';
    throw error;
  }
  if (!env.huskSyncIntegrationActive) {
    const error = new Error(`${serverId}: ItemGuard HuskSync integration is not active`);
    error.classification = 'HUSKSYNC_SETUP';
    throw error;
  }
}

function attachBotLog(bot, stream) {
  const write = (event, data) => {
    try {
      stream.write(`${new Date().toISOString()} ${event} ${data == null ? '' : data}\n`);
    } catch {
      // ignore
    }
  };
  bot.on('error', (error) => write('error', error && error.message));
  bot.on('kicked', (reason) => write('kicked', typeof reason === 'string' ? reason : JSON.stringify(reason)));
  bot.on('end', (reason) => write('end', reason));
  bot.on('spawn', () => write('spawn', bot.entity && bot.entity.position));
  bot.on('message', (message) => {
    try {
      write('message', message.toString());
    } catch {
      write('message', String(message));
    }
  });
}

function checkNode() {
  const major = Number(process.versions.node.split('.')[0]);
  if (major < 18) {
    throw classified('INFRASTRUCTURE', `Node.js >= 18 is required. Found ${process.version}`);
  }
}

function checkJava(environment) {
  const java = process.env.JAVA_HOME
    ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
    : 'java';
  const probe = spawnSync(java, ['-version'], { encoding: 'utf8' });
  const text = `${probe.stdout || ''}\n${probe.stderr || ''}`;
  const match = text.match(/version "(\d+)/);
  environment.java = match ? Number(match[1]) : text.trim().split('\n')[0];
  if (match && Number(match[1]) < 21) {
    throw classified('INFRASTRUCTURE', `Java 21 is required. Found ${text.trim()}`);
  }
  if (probe.status !== 0 && probe.error) {
    throw classified('INFRASTRUCTURE', 'Java 21 is required and was not found on PATH / JAVA_HOME.');
  }
}

function ensureNpm(environment) {
  const installed = path.join(BOT_DIR, 'node_modules', 'mineflayer');
  if (pathExists(installed)) {
    environment.mineflayer = require(path.join(BOT_DIR, 'node_modules', 'mineflayer', 'package.json')).version;
    return;
  }
  console.log('Installing Node dependencies for integration-tests/bot ...');
  const npm = process.platform === 'win32' ? 'npm.cmd' : 'npm';
  const result = spawnSync(npm, ['install'], {
    cwd: BOT_DIR,
    stdio: 'inherit',
    shell: process.platform === 'win32'
  });
  if (result.status !== 0) {
    throw classified('INFRASTRUCTURE', 'npm install failed. Node.js/npm is required for HuskSync integration tests.');
  }
  environment.mineflayer = require(path.join(BOT_DIR, 'node_modules', 'mineflayer', 'package.json')).version;
}

function assertFile(file, message) {
  if (!pathExists(file)) {
    throw classified('INFRASTRUCTURE', `${message} Missing: ${file}`);
  }
}

function classified(classification, message) {
  const error = new Error(message);
  error.classification = classification;
  return error;
}

async function allocatePorts(starts) {
  const used = new Set();
  const ports = {};
  for (const [name, start] of Object.entries(starts)) {
    let port = await findFreePort(start);
    while (used.has(port)) {
      port = await findFreePort(port + 1);
    }
    used.add(port);
    ports[name] = port;
  }
  return ports;
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
