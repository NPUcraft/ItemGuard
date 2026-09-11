'use strict';

const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');
const { createHarness } = require('./src/harness');
const { startPaper, stopPaper } = require('./src/paper');
const { writeReports } = require('./src/report');
const { findFreePort, pathExists, resolveItemGuardJar, versionFromItemGuardJar } = require('./src/util');

const BOT_DIR = __dirname;
const ROOT = process.env.ITEMGUARD_ROOT || path.resolve(BOT_DIR, '..', '..');

async function main() {
  const started = Date.now();
  const reportsDir = process.env.ITEMGUARD_REPORTS_DIR || path.join(ROOT, 'integration-tests', 'reports');
  const runtimeDir = process.env.ITEMGUARD_RUNTIME_DIR || path.join(ROOT, 'build', 'integration-runtime');
  const paperJar = process.env.ITEMGUARD_PAPER_JAR || path.join(ROOT, 'run', 'paper-1.21.8.jar');
  const itemGuardJar = resolveItemGuardJar(ROOT, process.env.ITEMGUARD_ITEMGUARD_JAR);
  const harnessJar = process.env.ITEMGUARD_HARNESS_JAR || path.join(ROOT, 'integration-tests', 'harness', 'build', 'libs', 'ItemGuard-TestHarness-1.0.0-test.jar');
  const botName = process.env.ITEMGUARD_BOT_NAME || 'ItemGuardBot';

  fs.mkdirSync(reportsDir, { recursive: true });

  const environment = {
    paper: 'unknown',
    java: process.versions ? undefined : undefined,
    itemguard: versionFromItemGuardJar(itemGuardJar),
    node: process.version,
    mineflayer: null,
    protocol: null,
    host: '127.0.0.1'
  };

  const results = [];
  let paper = null;
  let bot = null;
  let harness = null;
  let exitCode = 0;
  let disconnectBot = async () => {};

  try {
    checkNode();
    checkJava(environment);
    ensureNpm(environment);
    const client = require('./src/client');
    disconnectBot = client.disconnectBot;
    const { connectBot } = client;
    const { runTests } = require('./src/tests');
    assertFile(paperJar, 'Paper JAR not found. Set ITEMGUARD_PAPER_JAR or -PpaperJar=... Default: run/paper-1.21.8.jar');
    assertFile(itemGuardJar, 'ItemGuard JAR not found. Run gradle jar first.');
    assertFile(harnessJar, 'Test Harness JAR not found. Run gradle :harness:jar first.');

    const gamePort = await findFreePort(25576);
    const harnessPort = await findFreePort(18776);
    environment.port = gamePort;
    environment.harnessPort = harnessPort;

    paper = await startPaper({
      root: ROOT,
      runtimeDir,
      paperJar,
      itemGuardJar,
      harnessJar,
      reportsDir,
      gamePort,
      harnessPort
    });
    environment.paper = paper.paperVersion || '1.21.8';

    harness = createHarness({
      port: harnessPort,
      player: botName,
      timeoutMs: 8000
    });
    await harness.waitUntilReady();
    const envInfo = await harness.environment();
    if (envInfo.minecraft) {
      environment.paper = `${envInfo.minecraft}${envInfo.bukkitVersion ? ' / ' + envInfo.bukkitVersion : ''}`;
    }
    if (envInfo.itemguard) {
      environment.itemguard = envInfo.itemguard;
    }
    if (!envInfo.itemguardEnabled) {
      throw new Error('ItemGuard did not enable on the integration server');
    }

    bot = await connectBot({
      host: '127.0.0.1',
      port: gamePort,
      username: botName,
      version: '1.21.8'
    });
    environment.protocol = bot.protocolVersion || bot.version || '1.21.8';
    environment.mineflayer = require('mineflayer/package.json').version;
    environment.botVersion = bot.version;

    const ctx = {
      bot,
      harness,
      botName,
      paper,
      reportsDir,
      runtimeDir,
      environment,
      alertThreshold: 60
    };
    await harness.waitForPlayer();
    results.push(...await runTests(ctx));
  } catch (error) {
    exitCode = 1;
    results.push({
      id: 'TEST-INFRA',
      name: 'Integration infrastructure',
      status: 'FAIL',
      required: true,
      error: error.stack || String(error),
      details: error.details || null
    });
    console.error(error);
  } finally {
    try {
      await disconnectBot(bot);
    } catch (error) {
      console.error('Bot disconnect failed:', error);
    }
    try {
      await stopPaper(paper);
    } catch (error) {
      console.error('Paper shutdown failed:', error);
    }
  }

  const report = writeReports({
    reportsDir,
    environment,
    results,
    elapsedMs: Date.now() - started,
    paperLog: path.join(reportsDir, 'paper.log')
  });
  console.log(report.markdown);
  const requiredFailed = results.some((test) => test.required && test.status !== 'PASS' && test.status !== 'PARTIAL');
  const anyFail = results.some((test) => test.status === 'FAIL');
  if (requiredFailed || anyFail || exitCode !== 0) {
    process.exitCode = 1;
  }
}

function checkNode() {
  const major = Number(process.versions.node.split('.')[0]);
  if (major < 18) {
    throw new Error(`Node.js >= 18 is required for real client integration tests. Found ${process.version}`);
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
    throw new Error(`Java 21 is required. Found ${text.trim()}`);
  }
  if (probe.status !== 0 && probe.error) {
    throw new Error('Java 21 is required and was not found on PATH / JAVA_HOME.');
  }
}

function ensureNpm(environment) {
  const installed = path.join(BOT_DIR, 'node_modules', 'mineflayer');
  if (pathExists(installed)) {
    environment.mineflayer = require('mineflayer/package.json').version;
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
    throw new Error('npm install failed. Node.js/npm is required for real client integration tests.');
  }
  environment.mineflayer = require('mineflayer/package.json').version;
}

function assertFile(file, message) {
  if (!pathExists(file)) {
    throw new Error(`${message} Missing: ${file}`);
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
