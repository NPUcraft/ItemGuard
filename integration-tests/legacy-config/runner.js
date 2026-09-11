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
  rmDirIfExists
} = require('../bot/src/util');
const gate = require('../upgrade/preservation-gate');

const ROOT = process.env.ITEMGUARD_ROOT || path.resolve(__dirname, '..', '..');

async function main() {
  const started = Date.now();
  const reportsDir = process.env.ITEMGUARD_REPORTS_DIR
    || path.join(ROOT, 'integration-tests', 'reports', 'legacy-config');
  const runtimeDir = process.env.ITEMGUARD_RUNTIME_DIR || path.join(ROOT, 'build', 'legacy-config-runtime');
  const preserveRuntimeDir = path.join(ROOT, 'build', 'legacy-config-logging-preserve');
  const paperJar = process.env.ITEMGUARD_PAPER_JAR || path.join(ROOT, 'run', 'paper-1.21.8.jar');
  const harnessJar = process.env.ITEMGUARD_HARNESS_JAR
    || path.join(ROOT, 'integration-tests', 'harness', 'build', 'libs', 'ItemGuard-TestHarness-1.0.0-test.jar');
  const candidateJar = resolveItemGuardJar(ROOT, process.env.ITEMGUARD_ITEMGUARD_JAR);
  const expectedVersion = process.env.ITEMGUARD_EXPECT_VERSION || versionFromItemGuardJar(candidateJar);
  const fixtureDir = path.join(ROOT, 'integration-tests', 'fixtures', 'rc1-config');

  fs.mkdirSync(reportsDir, { recursive: true });

  const report = {
    verificationKind: 'CONFIG_COMPATIBILITY_UPGRADE',
    status: 'FAIL',
    failureClass: null,
    paperVersion: null,
    javaVersion: gate.javaVersion(),
    fixture: {
      path: fixtureDir,
      provenance: 'integration-tests/fixtures/rc1-config/PROVENANCE.md'
    },
    rc2: {
      path: candidateJar,
      sha256: pathExists(candidateJar) ? sha256File(candidateJar) : null,
      version: expectedVersion
    },
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
    gate.assertRc1FixtureHashes(fixtureDir);
    if (!pathExists(paperJar)) {
      throw gate.upgradeError('TEST_INFRASTRUCTURE', 'Paper JAR not found: ' + paperJar);
    }
    if (!pathExists(candidateJar)) {
      throw gate.upgradeError('TEST_INFRASTRUCTURE', 'RC2 candidate JAR not found. Run gradle jar first.');
    }
    if (!pathExists(harnessJar)) {
      throw gate.upgradeError('TEST_INFRASTRUCTURE', 'Test Harness JAR not found. Run gradle :harness:jar first.');
    }

    const gamePort = await findFreePort(25594);
    const harnessPort = await findFreePort(18794);
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

    const configDir = path.join(runtimeDir, 'plugins', 'ItemGuard');
    paper = await startPaper({
      ...common,
      runtimeDir,
      itemGuardJar: candidateJar,
      wipeWorlds: true,
      motd: 'ItemGuard RC1 Config Compatibility',
      logFileName: 'paper-legacy-first.log',
      beforeStart() {
        gate.installRc1Fixtures(fixtureDir, configDir);
        if (pathExists(path.join(configDir, 'logging.yml'))) {
          throw gate.upgradeError('TEST_INFRASTRUCTURE', 'Fixture install must not create logging.yml');
        }
        gate.customizeOldConfigs(configDir);
        gate.seedUserLogs(configDir);
        report.oldConfigHashesBefore = gate.fingerprints(configDir, gate.OLD_YAML);
      }
    });
    report.paperVersion = paper.paperVersion || 'unknown';
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
      motd: 'ItemGuard RC1 Config Compatibility Restart',
      logFileName: 'paper-legacy-restart.log'
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
    gate.writeGateReport(
      reportsDir,
      'ItemGuard RC1 Configuration Compatibility (not exact artifact upgrade)',
      report,
      Date.now() - started
    );
    console.log('CONFIG_COMPATIBILITY_UPGRADE: PASS');
    console.log('This is not EXACT_ARTIFACT_UPGRADE.');
  } catch (error) {
    report.status = 'FAIL';
    report.failureClass = error.code || 'TEST_INFRASTRUCTURE';
    report.errors.push(String(error.message || error));
    gate.writeGateReport(
      reportsDir,
      'ItemGuard RC1 Configuration Compatibility (not exact artifact upgrade)',
      report,
      Date.now() - started
    );
    console.error('[' + report.failureClass + '] ' + (error.message || error));
    if (error.stack) {
      console.error(error.stack);
    }
    process.exitCode = 1;
  } finally {
    await stopPaper(paper);
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
