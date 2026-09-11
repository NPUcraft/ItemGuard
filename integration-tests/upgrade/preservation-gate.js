'use strict';

const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');
const { startPaper, stopPaper } = require('../bot/src/paper');
const { createHarness } = require('../bot/src/harness');
const {
  pathExists,
  copyFile,
  fileFingerprint,
  waitUntil,
  tailLines,
  findFreePort
} = require('../bot/src/util');

const OLD_YAML = [
  'config.yml',
  'scanner.yml',
  'risk.yml',
  'items.yml',
  'alerts.yml',
  'integrations.yml',
  'messages.yml'
];
const ALL_YAML = [...OLD_YAML, 'logging.yml'];
const MANUAL_LOG = 'DO_NOT_DELETE';
const CUSTOM = {
  debug: true,
  traceRetentionMinutes: 47,
  reconcileIntervalTicks: 17,
  heartbeatIntervalTicks: 33,
  alertThreshold: 73,
  criticalThreshold: 91,
  diamondValue: 77,
  scannerAttributeMaxAbsolute: 42,
  huskSyncTimeoutMillis: 17000,
  alertAggregationWindowMillis: 4321,
  reloadNeedle: 'RC-upgrade-test'
};

const FIXTURE_HASHES = {
  'config.yml': 'a3df219080190fc626fa929871f5066d4911865d8dc3e2ca75c8ba4a2e63043b',
  'scanner.yml': '9e44118b8f738361d88545d9c3d1579c7bad3b066e6821859d793ccbede8b22b',
  'risk.yml': 'b9ab516630ac5c2057b5fbc41cb5f9e35aa284b29060daa76444434456cd039b',
  'items.yml': 'd16b515f6a22ef90f5b5d67096c125f382ed2ca842b4235fe5f6c7f4d5076477',
  'alerts.yml': '5d638b463b1171ee06f203f591a8c3236ed4f70e49d3f4962fede8412631f666',
  'integrations.yml': 'de51eb2b05af8fda2e0daeb352156fa4d90e8c43483dc66935163af5730876a2',
  'messages.yml': 'a6ef4ceaf0f9806cd350d4ca7db2dc7b955b3618d5cb5e483afb7b997cb9f6f3'
};

function loadReleases(root) {
  const file = path.join(root, 'integration-tests', 'artifacts', 'releases.json');
  if (!pathExists(file)) {
    throw upgradeError('TEST_INFRASTRUCTURE', 'Missing integration-tests/artifacts/releases.json');
  }
  return JSON.parse(fs.readFileSync(file, 'utf8'));
}

function expectedRc1Sha256(root) {
  const rc1 = loadReleases(root)['1.0.0-RC1'];
  if (!rc1 || !rc1.sha256) {
    throw upgradeError('TEST_INFRASTRUCTURE', 'releases.json is missing 1.0.0-RC1.sha256');
  }
  return String(rc1.sha256).toLowerCase();
}

function assertRc1FixtureHashes(fixtureDir) {
  if (pathExists(path.join(fixtureDir, 'logging.yml'))) {
    throw upgradeError('TEST_INFRASTRUCTURE', 'RC1 config fixture must not contain logging.yml');
  }
  for (const name of OLD_YAML) {
    const file = path.join(fixtureDir, name);
    if (!pathExists(file)) {
      throw upgradeError('TEST_INFRASTRUCTURE', 'Missing RC1 fixture: ' + name);
    }
    const actual = fileFingerprint(file).sha256;
    if (actual !== FIXTURE_HASHES[name]) {
      throw upgradeError(
        'TEST_INFRASTRUCTURE',
        name + ' fixture hash ' + actual + ' does not match PROVENANCE.md ' + FIXTURE_HASHES[name]
      );
    }
  }
}

function installRc1Fixtures(fixtureDir, configDir) {
  fs.mkdirSync(configDir, { recursive: true });
  for (const name of OLD_YAML) {
    copyFile(path.join(fixtureDir, name), path.join(configDir, name));
  }
}

function customizeOldConfigs(configDir) {
  replaceOnce(path.join(configDir, 'config.yml'), 'debug: false', 'debug: true');
  replaceOnce(path.join(configDir, 'config.yml'), '  interval-ticks: 20', '  interval-ticks: 17');
  replaceOnce(path.join(configDir, 'config.yml'), '  heartbeat-interval-ticks: 40', '  heartbeat-interval-ticks: 33');
  replaceOnce(path.join(configDir, 'config.yml'), '  retention-minutes: 30', '  retention-minutes: 47');
  replaceOnce(path.join(configDir, 'risk.yml'), '  high: 60', '  high: 73');
  replaceOnce(path.join(configDir, 'risk.yml'), '  critical: 80', '  critical: 91');
  replaceOnce(path.join(configDir, 'items.yml'), '  DIAMOND: 25', '  DIAMOND: 77');
  replaceOnce(path.join(configDir, 'alerts.yml'), 'threshold: 60', 'threshold: 73');
  replaceOnce(path.join(configDir, 'alerts.yml'), 'critical-threshold: 80', 'critical-threshold: 91');
  replaceOnce(path.join(configDir, 'alerts.yml'), '  window-millis: 5000', '  window-millis: 4321');
  replaceOnce(path.join(configDir, 'scanner.yml'), '  maximum-absolute-amount: 50.0', '  maximum-absolute-amount: 42.0');
  replaceOnce(path.join(configDir, 'integrations.yml'), '  timeout-millis: 15000', '  timeout-millis: 17000');
  replaceOnce(
    path.join(configDir, 'messages.yml'),
    'reload-success: "<green>Configuration reloaded."',
    'reload-success: "<green>ItemGuard RC-upgrade-test reload OK."'
  );
}

function seedUserLogs(configDir) {
  const file = path.join(configDir, 'logs', 'events', 'manual-test.jsonl');
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, MANUAL_LOG, 'utf8');
}

async function verifyRc2Upgrade(options) {
  const { paper, runtimeDir, configDir, harnessPort, expectedVersion, before, report, phase } = options;
  const log = paper.lines.join('\n');
  if (/Error occurred while enabling ItemGuard/i.test(log)) {
    throw upgradeError('PLUGIN_STARTUP_ERROR', 'RC2 ItemGuard failed to enable during ' + phase + '.\n' + tailLines(log, 80));
  }
  if (hasItemGuardException(log)) {
    throw upgradeError('PLUGIN_STARTUP_ERROR', 'ItemGuard exception during ' + phase + '.\n' + tailLines(log, 80));
  }
  if (hasYamlParseError(log)) {
    throw upgradeError('CONFIG_NOT_LOADED', 'YAML parse error during ' + phase + '.\n' + tailLines(log, 80));
  }
  if (hasOverwriteWarning(log)) {
    throw upgradeError('CONFIG_OVERWRITE', 'Config overwrite warning during ' + phase + '.\n' + tailLines(log, 80));
  }
  if (!new RegExp('Enabling ItemGuard v' + escapeRegex(expectedVersion)).test(log)) {
    throw upgradeError(
      'PLUGIN_STARTUP_ERROR',
      'Expected Enabling ItemGuard v' + expectedVersion + ' during ' + phase + '.\n' + tailLines(log, 80)
    );
  }
  assertSingleItemGuardJar(path.join(runtimeDir, 'plugins'), path.basename(report.rc2.path));
  report.oldConfigHashesAfter = fingerprints(configDir, OLD_YAML);
  assertFingerprintsMatch(before, report.oldConfigHashesAfter, 'old YAML after ' + phase);

  const loggingFile = path.join(configDir, 'logging.yml');
  if (!pathExists(loggingFile)) {
    throw upgradeError('NEW_RESOURCE_NOT_CREATED', 'logging.yml was not created on RC2 ' + phase);
  }
  report.loggingYmlCreated = true;
  const loggingText = fs.readFileSync(loggingFile, 'utf8');
  if (!loggingText.includes('format: JSONL') || !loggingText.includes('directory: logs')) {
    throw upgradeError('NEW_RESOURCE_NOT_CREATED', 'logging.yml is missing bundled forensic defaults');
  }

  for (const name of ['logs', path.join('logs', 'alerts'), path.join('logs', 'events'), path.join('logs', 'admin')]) {
    if (!pathExists(path.join(configDir, name))) {
      throw upgradeError('LOG_DIRECTORY_ERROR', 'Missing log directory after ' + phase + ': ' + name);
    }
  }
  report.logsDirectoriesCreated = true;
  const manual = path.join(configDir, 'logs', 'events', 'manual-test.jsonl');
  if (!pathExists(manual) || fs.readFileSync(manual, 'utf8') !== MANUAL_LOG) {
    throw upgradeError('LOG_DIRECTORY_ERROR', 'Existing logs/events/manual-test.jsonl was changed or deleted');
  }
  report.existingLogsPreserved = true;

  const harness = createHarness({ port: harnessPort, player: 'ItemGuardBot', timeoutMs: 8000 });
  await harness.waitUntilReady();
  const env = await harness.environment();
  if (!env.itemguardEnabled) {
    throw upgradeError('PLUGIN_STARTUP_ERROR', 'ItemGuard was not enabled according to harness during ' + phase);
  }
  if (env.itemguard !== expectedVersion) {
    throw upgradeError(
      'PLUGIN_STARTUP_ERROR',
      'Runtime version was ' + env.itemguard + ', expected ' + expectedVersion
    );
  }
  const status = await sendCommand(paper, 'itemguard status', /Forensic Logging:/);
  if (!status.includes('Version: ' + expectedVersion)) {
    throw upgradeError('PLUGIN_STARTUP_ERROR', '/ig status version mismatch during ' + phase + ':\n' + status);
  }
  if (!status.includes('Reconciliation interval: 17 ticks')) {
    throw upgradeError('CONFIG_NOT_LOADED', '/ig status did not show custom reconcile interval 17 during ' + phase);
  }
  if (!/Forensic Logging: enabled/.test(status)) {
    throw upgradeError('NEW_RESOURCE_NOT_CREATED', 'Forensic logger was not active during ' + phase);
  }
  assertRuntimeValues(env.runtimeSettings || {}, phase);
  report.runtimeEffectiveValues = env.runtimeSettings || {};
}

async function reloadAndVerify({ paper, harnessPort, expectedVersion, configDir, before, report }) {
  const output = await sendCommand(paper, 'itemguard reload', /RC-upgrade-test reload OK|Configuration reloaded/);
  if (!output.includes('RC-upgrade-test reload OK')) {
    throw upgradeError('CONFIG_NOT_LOADED', 'Custom reload-success message was not used:\n' + output);
  }
  const log = paper.lines.join('\n');
  if (hasOverwriteWarning(log) || hasYamlParseError(log) || hasItemGuardException(log)) {
    throw upgradeError('PLUGIN_STARTUP_ERROR', 'Reload produced ItemGuard errors.\n' + tailLines(log, 80));
  }
  assertFingerprintsMatch(before, fingerprints(configDir, OLD_YAML), 'old YAML after reload');
  const harness = createHarness({ port: harnessPort, player: 'ItemGuardBot', timeoutMs: 8000 });
  const env = await harness.environment();
  if (env.itemguard !== expectedVersion) {
    throw upgradeError('PLUGIN_STARTUP_ERROR', 'Version changed after reload: ' + env.itemguard);
  }
  assertRuntimeValues(env.runtimeSettings || {}, 'reload');
  const writers = Number((env.runtimeSettings || {}).forensicWriterThreads);
  if (writers !== 1) {
    throw upgradeError(
      'PLUGIN_STARTUP_ERROR',
      'Expected exactly one ItemGuard-ForensicLogWriter after reload, found ' + writers
    );
  }
  await sendCommand(paper, 'itemguard status', /Forensic Logging: enabled/);
  report.reload = 'PASS';
  report.runtimeEffectiveValues = env.runtimeSettings || {};
}

async function runPrecreatedLoggingScenario({ common, preserveRuntimeDir, candidateJar, sourceConfigDir, report }) {
  const destConfig = path.join(preserveRuntimeDir, 'plugins', 'ItemGuard');
  fs.mkdirSync(destConfig, { recursive: true });
  for (const name of OLD_YAML) {
    copyFile(path.join(sourceConfigDir, name), path.join(destConfig, name));
  }
  const customLogging = [
    'logging:',
    '  enabled: true',
    '  format: JSONL',
    '  directory: logs',
    '  server-name: rc1-operator-precreated',
    '  retention-days: 21',
    '  max-file-size-mb: 100',
    '  flush-interval-seconds: 2',
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
  ].join('\n');
  fs.writeFileSync(path.join(destConfig, 'logging.yml'), customLogging, 'utf8');
  fs.mkdirSync(path.join(destConfig, 'logs', 'events'), { recursive: true });
  fs.writeFileSync(path.join(destConfig, 'logs', 'events', 'manual-test.jsonl'), MANUAL_LOG, 'utf8');
  const beforeLogging = fileFingerprint(path.join(destConfig, 'logging.yml'));
  const beforeOld = fingerprints(destConfig, OLD_YAML);

  const gamePort = await findFreePort(25592);
  const harnessPort = await findFreePort(18792);
  let paper = null;
  try {
    paper = await startPaper({
      ...common,
      runtimeDir: preserveRuntimeDir,
      itemGuardJar: candidateJar,
      wipeWorlds: true,
      motd: 'ItemGuard RC2 Precreated logging.yml',
      logFileName: 'paper-rc2-logging-preserve.log',
      gamePort,
      harnessPort
    });
    assertFingerprintsMatch(beforeOld, fingerprints(destConfig, OLD_YAML), 'old YAML with precreated logging.yml');
    const afterLogging = fileFingerprint(path.join(destConfig, 'logging.yml'));
    if (afterLogging.sha256 !== beforeLogging.sha256) {
      throw upgradeError('CONFIG_OVERWRITE', 'Precreated logging.yml was overwritten during RC2 upgrade');
    }
    const harness = createHarness({ port: harnessPort, player: 'ItemGuardBot', timeoutMs: 8000 });
    await harness.waitUntilReady();
    const settings = (await harness.environment()).runtimeSettings || {};
    if (settings.loggingServerName !== 'rc1-operator-precreated') {
      throw upgradeError(
        'CONFIG_NOT_LOADED',
        'Precreated logging.yml was not loaded; server-name=' + settings.loggingServerName
      );
    }
    if (Number(settings.loggingRetentionDays) !== 21) {
      throw upgradeError('CONFIG_NOT_LOADED', 'Precreated retention-days was not loaded: ' + settings.loggingRetentionDays);
    }
    const manual = path.join(destConfig, 'logs', 'events', 'manual-test.jsonl');
    if (!pathExists(manual) || fs.readFileSync(manual, 'utf8') !== MANUAL_LOG) {
      throw upgradeError('LOG_DIRECTORY_ERROR', 'Precreated logging scenario deleted user logs');
    }
    report.loggingYmlPreserved = true;
  } finally {
    await stopPaper(paper);
  }
}

function assertRuntimeValues(settings, phase) {
  if (!settings || Object.keys(settings).length === 0) {
    throw upgradeError('CONFIG_NOT_LOADED', 'Harness runtimeSettings were empty during ' + phase);
  }
  const checks = [
    ['debug', CUSTOM.debug],
    ['traceRetentionMinutes', CUSTOM.traceRetentionMinutes],
    ['reconcileIntervalTicks', CUSTOM.reconcileIntervalTicks],
    ['heartbeatIntervalTicks', CUSTOM.heartbeatIntervalTicks],
    ['alertThreshold', CUSTOM.alertThreshold],
    ['criticalThreshold', CUSTOM.criticalThreshold],
    ['alertAggregationWindowMillis', CUSTOM.alertAggregationWindowMillis],
    ['riskHighThreshold', CUSTOM.alertThreshold],
    ['riskCriticalThreshold', CUSTOM.criticalThreshold],
    ['diamondValue', CUSTOM.diamondValue],
    ['huskSyncTimeoutMillis', CUSTOM.huskSyncTimeoutMillis]
  ];
  for (const [key, expected] of checks) {
    if (!sameValue(settings[key], expected)) {
      throw upgradeError(
        'CONFIG_NOT_LOADED',
        'Runtime ' + key + ' was ' + settings[key] + ', expected ' + expected + ' during ' + phase
      );
    }
  }
  if (Number(settings.scannerAttributeMaxAbsolute) !== CUSTOM.scannerAttributeMaxAbsolute) {
    throw upgradeError(
      'CONFIG_NOT_LOADED',
      'Runtime scannerAttributeMaxAbsolute was ' + settings.scannerAttributeMaxAbsolute + ' during ' + phase
    );
  }
  if (!String(settings.reloadSuccessMessage || '').includes(CUSTOM.reloadNeedle)) {
    throw upgradeError(
      'CONFIG_NOT_LOADED',
      'Runtime reload-success message was not the customized value during ' + phase
    );
  }
}

function sameValue(actual, expected) {
  if (typeof expected === 'number') {
    return Number(actual) === expected;
  }
  return actual === expected;
}

function fingerprints(configDir, names) {
  const map = {};
  for (const name of names) {
    map[name] = fileFingerprint(path.join(configDir, name));
  }
  return map;
}

function assertFingerprintsMatch(before, after, label) {
  for (const name of Object.keys(before)) {
    if (!after[name]) {
      throw upgradeError('CONFIG_OVERWRITE', label + ': missing ' + name);
    }
    if (before[name].sha256 !== after[name].sha256 || before[name].bytes !== after[name].bytes) {
      throw upgradeError(
        'CONFIG_OVERWRITE',
        label + ': ' + name + ' changed (before ' + before[name].sha256 + ' after ' + after[name].sha256 + ')'
      );
    }
  }
}

function replaceOnce(file, search, replacement) {
  if (!pathExists(file)) {
    throw upgradeError('TEST_INFRASTRUCTURE', 'Cannot patch missing file ' + file);
  }
  const text = fs.readFileSync(file, 'utf8');
  const count = text.split(search).length - 1;
  if (count !== 1) {
    throw upgradeError(
      'TEST_INFRASTRUCTURE',
      file + ': expected 1 occurrence of ' + JSON.stringify(search) + ', found ' + count
    );
  }
  fs.writeFileSync(file, text.replace(search, replacement), 'utf8');
}

function assertSingleItemGuardJar(pluginsDir, expectedName) {
  const jars = fs.readdirSync(pluginsDir).filter((name) =>
    /^ItemGuard-.*\.jar$/.test(name) && !name.includes('TestHarness')
  );
  if (jars.length !== 1 || jars[0] !== expectedName) {
    throw upgradeError(
      'TEST_INFRASTRUCTURE',
      'plugins/ must contain exactly ' + expectedName + ', found: ' + jars.join(', ')
    );
  }
}

async function sendCommand(paper, command, pattern) {
  if (!paper.child.stdin || !paper.child.stdin.writable) {
    throw upgradeError('TEST_INFRASTRUCTURE', 'Paper stdin is not writable for ' + command);
  }
  const before = paper.lines.length;
  paper.child.stdin.write(command + '\n');
  return waitUntil(() => {
    const blob = paper.lines.slice(before).join('\n');
    return pattern.test(blob) ? blob : null;
  }, {
    timeout: 15000,
    interval: 100,
    message: 'Timed out waiting for output of `' + command + '`'
  });
}

function hasOverwriteWarning(log) {
  return log.split(/\r?\n/).some((line) =>
    /overwrite/i.test(line) && !/not overwritten/i.test(line) && /ItemGuard/i.test(line)
  );
}

function hasYamlParseError(log) {
  return /could not be parsed/i.test(log) || /duplicated mapping key/i.test(log);
}

function hasItemGuardException(log) {
  return /\[ItemGuard\].*(Exception|SEVERE|ERROR)/i.test(log)
    || /Error occurred while enabling ItemGuard/i.test(log);
}

function javaVersion() {
  const result = spawnSync('java', ['-version'], { encoding: 'utf8' });
  const text = (result.stderr || result.stdout || '').trim();
  const match = text.match(/version "([^"]+)"/);
  return match ? match[1] : text.split(/\r?\n/)[0] || 'unknown';
}

function escapeRegex(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function upgradeError(code, message) {
  const error = new Error(message);
  error.code = code;
  return error;
}

function writeGateReport(reportsDir, title, report, elapsedMs) {
  fs.mkdirSync(reportsDir, { recursive: true });
  const payload = { elapsedMs, ...report };
  fs.writeFileSync(path.join(reportsDir, 'results.json'), JSON.stringify(payload, null, 2));
  const lines = [
    '# ' + title,
    '',
    `- Verification kind: ${report.verificationKind || 'unknown'}`,
    `- Status: ${report.status}`,
    `- Failure class: ${report.failureClass || 'none'}`,
    `- Paper: ${report.paperVersion || 'unknown'}`,
    `- Java: ${report.javaVersion}`,
    `- RC2 candidate: ${report.rc2 && report.rc2.path}`,
    `- RC2 SHA-256: ${report.rc2 && report.rc2.sha256}`,
    `- RC2 version: ${report.rc2 && report.rc2.version}`,
    `- logging.yml created: ${report.loggingYmlCreated}`,
    `- existing logging.yml preserved: ${report.loggingYmlPreserved}`,
    `- logs/ directories created: ${report.logsDirectoriesCreated}`,
    `- existing logs preserved: ${report.existingLogsPreserved}`,
    `- reload: ${report.reload}`,
    `- second restart: ${report.secondRestart}`,
    `- Elapsed: ${elapsedMs}ms`,
    ''
  ];
  if (report.rc1) {
    lines.push(
      `- RC1 JAR: ${report.rc1.path || 'missing'}`,
      `- RC1 SHA-256: ${report.rc1.sha256 || 'missing'}`,
      `- RC1 checksum verified: ${report.rc1.verified ? 'yes' : 'no'}`,
      ''
    );
  }
  if (report.fixture) {
    lines.push(
      `- Fixture dir: ${report.fixture.path}`,
      `- Fixture provenance: ${report.fixture.provenance}`,
      ''
    );
  }
  lines.push('## Old config hashes before', '');
  for (const [name, info] of Object.entries(report.oldConfigHashesBefore || {})) {
    lines.push(`- ${name}: ${info.sha256} (${info.bytes} bytes)`);
  }
  lines.push('', '## Old config hashes after', '');
  for (const [name, info] of Object.entries(report.oldConfigHashesAfter || {})) {
    lines.push(`- ${name}: ${info.sha256} (${info.bytes} bytes)`);
  }
  lines.push('', '## Runtime effective values', '');
  lines.push('```json', JSON.stringify(report.runtimeEffectiveValues || {}, null, 2), '```', '');
  if ((report.errors || []).length) {
    lines.push('## Errors', '', ...report.errors.map((error) => `- ${error}`), '');
  }
  fs.writeFileSync(path.join(reportsDir, 'results.md'), lines.join('\n'));
}

module.exports = {
  OLD_YAML,
  ALL_YAML,
  MANUAL_LOG,
  CUSTOM,
  FIXTURE_HASHES,
  expectedRc1Sha256,
  assertRc1FixtureHashes,
  installRc1Fixtures,
  customizeOldConfigs,
  seedUserLogs,
  verifyRc2Upgrade,
  reloadAndVerify,
  runPrecreatedLoggingScenario,
  fingerprints,
  javaVersion,
  upgradeError,
  writeGateReport
};
