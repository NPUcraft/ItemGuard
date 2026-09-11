'use strict';

const fs = require('fs');
const path = require('path');
const { startPaper, stopPaper } = require('../bot/src/paper');
const {
  findFreePort,
  pathExists,
  resolveItemGuardJar,
  versionFromItemGuardJar,
  waitUntil,
  rmDirIfExists,
  tailLines
} = require('../bot/src/util');

const ROOT = process.env.ITEMGUARD_ROOT || path.resolve(__dirname, '..', '..');
const REQUIRED_CONFIGS = [
  'config.yml',
  'scanner.yml',
  'risk.yml',
  'items.yml',
  'alerts.yml',
  'integrations.yml',
  'messages.yml',
  'logging.yml'
];

async function main() {
  const started = Date.now();
  const reportsDir = process.env.ITEMGUARD_REPORTS_DIR || path.join(ROOT, 'integration-tests', 'reports', 'smoke');
  const runtimeDir = process.env.ITEMGUARD_RUNTIME_DIR || path.join(ROOT, 'build', 'release-smoke');
  const paperJar = process.env.ITEMGUARD_PAPER_JAR || path.join(ROOT, 'run', 'paper-1.21.8.jar');
  const itemGuardJar = resolveItemGuardJar(ROOT, process.env.ITEMGUARD_ITEMGUARD_JAR);
  const version = versionFromItemGuardJar(itemGuardJar);

  fs.mkdirSync(reportsDir, { recursive: true });
  assertFile(paperJar, 'Paper JAR not found. Set ITEMGUARD_PAPER_JAR or place run/paper-1.21.8.jar');
  assertFile(itemGuardJar, 'ItemGuard JAR not found. Run gradle jar first.');

  if (!version || version === 'unknown') {
    throw new Error('Clean install smoke could not parse version from ' + path.basename(itemGuardJar));
  }

  rmDirIfExists(runtimeDir);
  const gamePort = await findFreePort(25586);
  const harnessPort = await findFreePort(18786);

  const common = {
    root: ROOT,
    runtimeDir,
    paperJar,
    itemGuardJar,
    reportsDir,
    gamePort,
    harnessPort,
    copyTestConfigs: false,
    motd: 'ItemGuard Clean Install Smoke'
  };

  let first = null;
  let second = null;
  const result = {
    version,
    jar: path.basename(itemGuardJar),
    firstStart: 'FAIL',
    configs: 'FAIL',
    status: 'FAIL',
    reload: 'FAIL',
    restart: 'FAIL',
    warnings: [],
    errors: []
  };

  try {
    first = await startPaper({
      ...common,
      wipeWorlds: true,
      logFileName: 'paper-first.log'
    });
    assertNoEnableFailure(first.lines.join('\n'), version);
    await sendCommand(first, 'itemguard status', /Active sessions:/);
    await sendCommand(first, 'itemguard reload', /Configuration reloaded/);
    await sendCommand(first, 'itemguard status', /HuskSync integration active: false/);
    assertGeneratedConfigs(path.join(runtimeDir, 'plugins', 'ItemGuard'));
    assertForensicLogDirectories(path.join(runtimeDir, 'plugins', 'ItemGuard'));
    assertCleanPlugins(path.join(runtimeDir, 'plugins'));
    result.warnings = itemGuardWarnings(first.lines.join('\n'));
    if (result.warnings.length > 0) {
      throw new Error('ItemGuard warning spam on clean install:\n' + result.warnings.join('\n'));
    }
    result.firstStart = 'PASS';
    result.configs = 'PASS';
    result.status = 'PASS';
    result.reload = 'PASS';
    await stopPaper(first);
    first = null;

    second = await startPaper({
      ...common,
      wipeWorlds: false,
      logFileName: 'paper-restart.log'
    });
    assertNoEnableFailure(second.lines.join('\n'), version);
    await sendCommand(second, 'itemguard status', new RegExp('Version: ' + version.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
    result.restart = 'PASS';
    await stopPaper(second);
    second = null;

    writeReport(reportsDir, result, Date.now() - started, 'PASS');
    console.log('Clean install smoke: PASS');
  } catch (error) {
    result.errors.push(String(error.message || error));
    writeReport(reportsDir, result, Date.now() - started, 'FAIL');
    console.error(error);
    process.exitCode = 1;
  } finally {
    await stopPaper(first);
    await stopPaper(second);
  }
}

async function sendCommand(paper, command, pattern) {
  if (!paper.child.stdin || !paper.child.stdin.writable) {
    throw new Error('Paper stdin is not writable for ' + command);
  }
  const before = paper.lines.length;
  paper.child.stdin.write(command + '\n');
  await waitUntil(() => {
    const blob = paper.lines.slice(before).join('\n');
    return pattern.test(blob) ? blob : null;
  }, {
    timeout: 15000,
    interval: 100,
    message: 'Timed out waiting for output of `' + command + '`'
  });
}

function assertGeneratedConfigs(configDir) {
  for (const name of REQUIRED_CONFIGS) {
    const file = path.join(configDir, name);
    if (!pathExists(file)) {
      throw new Error('Missing generated config: ' + name);
    }
    const text = fs.readFileSync(file, 'utf8');
    if (text.charCodeAt(0) === 0xFEFF) {
      throw new Error(name + ' has a UTF-8 BOM');
    }
    if (text.includes('\u0000')) {
      throw new Error(name + ' is not valid UTF-8 text');
    }
    if (/\$\{[A-Za-z0-9._-]+\}/.test(text)) {
      throw new Error(name + ' still contains an unexpanded placeholder');
    }
    if (/TODO|FIXME|ItemGuardBot|integration-runtime|TestHarness/i.test(text)) {
      throw new Error(name + ' contains test or placeholder text');
    }
  }
  const risk = fs.readFileSync(path.join(configDir, 'risk.yml'), 'utf8');
  if (!risk.includes('UNEXPLAINED_ITEM_GAIN: 35') || !risk.includes('high: 60')) {
    throw new Error('Generated risk.yml is not the bundled default');
  }
  const integrations = fs.readFileSync(path.join(configDir, 'integrations.yml'), 'utf8');
  if (!integrations.includes('enabled: true')) {
    throw new Error('Generated integrations.yml should keep HuskSync enabled: true');
  }
  const logging = fs.readFileSync(path.join(configDir, 'logging.yml'), 'utf8');
  if (!logging.includes('format: JSONL') || !logging.includes('directory: logs')) {
    throw new Error('Generated logging.yml is not the bundled default');
  }
}

function assertForensicLogDirectories(configDir) {
  for (const name of ['logs', path.join('logs', 'alerts'), path.join('logs', 'events'), path.join('logs', 'admin')]) {
    const dir = path.join(configDir, name);
    if (!pathExists(dir)) {
      throw new Error('Missing forensic log directory: ' + name);
    }
  }
}

function assertCleanPlugins(pluginsDir) {
  const jars = fs.readdirSync(pluginsDir).filter((name) => name.endsWith('.jar'));
  if (jars.length !== 1 || !jars[0].startsWith('ItemGuard-')) {
    throw new Error('Clean install plugins/ should contain only ItemGuard JAR, found: ' + jars.join(', '));
  }
  if (jars.some((name) => /Harness|HuskSync|Test/i.test(name))) {
    throw new Error('Clean install included a test or HuskSync JAR: ' + jars.join(', '));
  }
}

function assertNoEnableFailure(log, version) {
  if (/Error occurred while enabling ItemGuard/i.test(log)) {
    throw new Error('ItemGuard failed to enable. Tail:\n' + tailLines(log, 80));
  }
  if (/Could not load .*ItemGuard/i.test(log)) {
    throw new Error('Paper could not load ItemGuard. Tail:\n' + tailLines(log, 80));
  }
  const escaped = version.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  if (!new RegExp('Enabling ItemGuard v' + escaped).test(log) && !new RegExp('ItemGuard v' + escaped).test(log)) {
    throw new Error('ItemGuard did not log version ' + version + ' on enable');
  }
  if (/HuskSync Integration: enabled/.test(log)) {
    throw new Error('HuskSync integration became active on a server without HuskSync');
  }
}

function itemGuardWarnings(log) {
  return log.split(/\r?\n/).filter((line) =>
    /\[ItemGuard\]/.test(line) && /\b(WARN|WARNING|SEVERE|ERROR)\b/.test(line)
  );
}

function assertFile(file, message) {
  if (!pathExists(file)) {
    throw new Error(message + ' Missing: ' + file);
  }
}

function writeReport(reportsDir, result, elapsedMs, status) {
  const payload = { status, elapsedMs, result };
  fs.writeFileSync(path.join(reportsDir, 'results.json'), JSON.stringify(payload, null, 2));
  const lines = [
    '# ItemGuard Clean Install Smoke',
    '',
    `- Status: ${status}`,
    `- Version: ${result.version}`,
    `- JAR: ${result.jar}`,
    `- First start: ${result.firstStart}`,
    `- Configs: ${result.configs}`,
    `- /ig status: ${result.status}`,
    `- /ig reload: ${result.reload}`,
    `- Restart: ${result.restart}`,
    `- Elapsed: ${elapsedMs}ms`,
    ''
  ];
  if (result.errors.length) {
    lines.push('## Errors', '', ...result.errors.map((error) => `- ${error}`), '');
  }
  fs.writeFileSync(path.join(reportsDir, 'results.md'), lines.join('\n'));
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
