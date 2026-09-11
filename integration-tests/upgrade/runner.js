'use strict';

const fs = require('fs');
const path = require('path');
const { startPaper, stopPaper } = require('../bot/src/paper');
const {
  findFreePort,
  pathExists,
  resolveItemGuardJar,
  versionFromItemGuardJar,
  sha256File,
  rmDirIfExists,
  tailLines
} = require('../bot/src/util');
const gate = require('./preservation-gate');

const ROOT = process.env.ITEMGUARD_ROOT || path.resolve(__dirname, '..', '..');

async function main() {
  const started = Date.now();
  const reportsDir = process.env.ITEMGUARD_REPORTS_DIR || path.join(ROOT, 'integration-tests', 'reports', 'upgrade');
  const runtimeDir = process.env.ITEMGUARD_RUNTIME_DIR || path.join(ROOT, 'build', 'upgrade-runtime');
  const preserveRuntimeDir = process.env.ITEMGUARD_UPGRADE_PRESERVE_RUNTIME
    || path.join(ROOT, 'build', 'upgrade-runtime-logging-preserve');
  const paperJar = process.env.ITEMGUARD_PAPER_JAR || path.join(ROOT, 'run', 'paper-1.21.8.jar');
  const harnessJar = process.env.ITEMGUARD_HARNESS_JAR
    || path.join(ROOT, 'integration-tests', 'harness', 'build', 'libs', 'ItemGuard-TestHarness-1.0.0-test.jar');
  const candidateJar = resolveItemGuardJar(ROOT, process.env.ITEMGUARD_ITEMGUARD_JAR);
  const expectedVersion = process.env.ITEMGUARD_EXPECT_VERSION || versionFromItemGuardJar(candidateJar);
  const expectedRc1 = gate.expectedRc1Sha256(ROOT);

  fs.mkdirSync(reportsDir, { recursive: true });

  const report = {
    verificationKind: 'EXACT_ARTIFACT_UPGRADE',
    status: 'FAIL',
    failureClass: null,
    paperVersion: null,
    javaVersion: gate.javaVersion(),
    rc1: { path: null, sha256: null, verified: false },
    rc2: { path: candidateJar, sha256: pathExists(candidateJar) ? sha256File(candidateJar) : null, version: expectedVersion },
    oldConfigHashesBefore: {},
    oldConfigHashesAfter: {},
    loggingYmlCreated: false,
    loggingYmlPreserved: false,
    logsDirectoriesCreated: false,
    existingLogsPreserved: false,
    runtimeEffectiveValues: {},
    reload: 'FAIL',
    secondRestart: 'FAIL',
    errors: []
  };

  let paper = null;
  try {
    const rc1Jar = resolveOriginalRc1Jar(ROOT);
    report.rc1.path = rc1Jar;
    report.rc1.sha256 = sha256File(rc1Jar);
    if (report.rc1.sha256 !== expectedRc1) {
      throw gate.upgradeError(
        'ARTIFACT_MISMATCH',
        'Wrong RC1 artifact. Expected SHA-256 ' + expectedRc1 + ' but got ' + report.rc1.sha256
      );
    }
    report.rc1.verified = true;

    if (!pathExists(paperJar)) {
      throw gate.upgradeError('TEST_INFRASTRUCTURE', 'Paper JAR not found: ' + paperJar);
    }
    if (!pathExists(candidateJar)) {
      throw gate.upgradeError('TEST_INFRASTRUCTURE', 'RC2 candidate JAR not found. Run gradle jar first.');
    }
    if (!pathExists(harnessJar)) {
      throw gate.upgradeError('TEST_INFRASTRUCTURE', 'Test Harness JAR not found. Run gradle :harness:jar first.');
    }
    if (report.rc2.sha256 === expectedRc1) {
      throw gate.upgradeError(
        'ARTIFACT_MISMATCH',
        'Wrong RC1 artifact. Candidate JAR is the original RC1 checksum; do not treat the modified workspace build as RC1.'
      );
    }

    const gamePort = await findFreePort(25590);
    const harnessPort = await findFreePort(18790);
    rmDirIfExists(runtimeDir);
    rmDirIfExists(preserveRuntimeDir);

    const common = {
      root: ROOT,
      paperJar,
      harnessJar,
      reportsDir,
      gamePort,
      harnessPort,
      copyTestConfigs: false,
      clearPluginJars: true,
      extraReadyCheck: (blob) => /Enabling ItemGuard/i.test(blob) && /ITEMGUARD_HARNESS_READY/i.test(blob)
    };

    paper = await startPaper({
      ...common,
      runtimeDir,
      itemGuardJar: rc1Jar,
      wipeWorlds: true,
      motd: 'ItemGuard RC1 Upgrade Base',
      logFileName: 'paper-rc1.log'
    });
    report.paperVersion = paper.paperVersion || 'unknown';
    assertRc1FirstBoot(paper.lines.join('\n'), path.join(runtimeDir, 'plugins', 'ItemGuard'));
    await stopPaper(paper);
    paper = null;

    const configDir = path.join(runtimeDir, 'plugins', 'ItemGuard');
    gate.customizeOldConfigs(configDir);
    gate.seedUserLogs(configDir);
    report.oldConfigHashesBefore = gate.fingerprints(configDir, gate.OLD_YAML);

    paper = await startPaper({
      ...common,
      runtimeDir,
      itemGuardJar: candidateJar,
      wipeWorlds: false,
      motd: 'ItemGuard RC2 Upgrade',
      logFileName: 'paper-rc2-first.log'
    });
    report.paperVersion = paper.paperVersion || report.paperVersion;
    await gate.verifyRc2Upgrade({
      paper,
      runtimeDir,
      configDir,
      harnessPort,
      expectedVersion,
      before: report.oldConfigHashesBefore,
      report,
      phase: 'first-start'
    });
    await gate.reloadAndVerify({
      paper,
      harnessPort,
      expectedVersion,
      configDir,
      before: report.oldConfigHashesBefore,
      report
    });
    const hashesAfterReload = gate.fingerprints(configDir, gate.ALL_YAML);
    await stopPaper(paper);
    paper = null;

    paper = await startPaper({
      ...common,
      runtimeDir,
      itemGuardJar: candidateJar,
      wipeWorlds: false,
      motd: 'ItemGuard RC2 Upgrade Restart',
      logFileName: 'paper-rc2-restart.log'
    });
    await gate.verifyRc2Upgrade({
      paper,
      runtimeDir,
      configDir,
      harnessPort,
      expectedVersion,
      before: report.oldConfigHashesBefore,
      report,
      phase: 'second-start'
    });
    gate.fingerprints(configDir, gate.ALL_YAML);
    const afterRestart = gate.fingerprints(configDir, gate.ALL_YAML);
    for (const name of Object.keys(hashesAfterReload)) {
      if (hashesAfterReload[name].sha256 !== afterRestart[name].sha256) {
        throw gate.upgradeError('CONFIG_OVERWRITE', 'second restart YAML changed: ' + name);
      }
    }
    report.secondRestart = 'PASS';
    await stopPaper(paper);
    paper = null;

    await gate.runPrecreatedLoggingScenario({
      common,
      preserveRuntimeDir,
      candidateJar,
      sourceConfigDir: configDir,
      report
    });

    report.status = 'PASS';
    gate.writeGateReport(reportsDir, 'ItemGuard RC1 → RC2 Exact Artifact Upgrade', report, Date.now() - started);
    console.log('EXACT_ARTIFACT_UPGRADE: PASS');
  } catch (error) {
    report.status = 'FAIL';
    report.failureClass = error.code || 'TEST_INFRASTRUCTURE';
    report.errors.push(String(error.message || error));
    gate.writeGateReport(reportsDir, 'ItemGuard RC1 → RC2 Exact Artifact Upgrade', report, Date.now() - started);
    console.error('[' + report.failureClass + '] ' + (error.message || error));
    if (error.stack) {
      console.error(error.stack);
    }
    process.exitCode = 1;
  } finally {
    await stopPaper(paper);
  }
}

function resolveOriginalRc1Jar(root) {
  const expected = gate.expectedRc1Sha256(root);
  const candidates = [
    process.env.ITEMGUARD_RC1_JAR,
    path.join(root, 'integration-tests', 'artifacts', 'ItemGuard-1.0.0-RC1.jar'),
    path.join(root, 'build', 'release', 'ItemGuard-1.0.0-RC1.jar')
  ].filter(Boolean);
  for (const candidate of candidates) {
    if (pathExists(candidate)) {
      return path.resolve(candidate);
    }
  }
  throw gate.upgradeError(
    'ARTIFACT_MISMATCH',
    'Wrong RC1 artifact. Original ItemGuard-1.0.0-RC1.jar was not found. '
      + 'Set ITEMGUARD_RC1_JAR or -PRc1Jar= to the published RC1 JAR '
      + '(SHA-256 ' + expected + '). Do not use the current workspace build.'
  );
}

function assertRc1FirstBoot(log, configDir) {
  if (/Error occurred while enabling ItemGuard/i.test(log)) {
    throw gate.upgradeError('PLUGIN_STARTUP_ERROR', 'RC1 ItemGuard failed to enable.\n' + tailLines(log, 80));
  }
  if (!/Enabling ItemGuard v1\.0\.0-RC1/i.test(log)) {
    throw gate.upgradeError('PLUGIN_STARTUP_ERROR', 'RC1 did not enable as 1.0.0-RC1.\n' + tailLines(log, 80));
  }
  for (const name of gate.OLD_YAML) {
    if (!pathExists(path.join(configDir, name))) {
      throw gate.upgradeError('NEW_RESOURCE_NOT_CREATED', 'RC1 did not create ' + name);
    }
  }
  if (pathExists(path.join(configDir, 'logging.yml'))) {
    throw gate.upgradeError('TEST_INFRASTRUCTURE', 'RC1 unexpectedly created logging.yml');
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
