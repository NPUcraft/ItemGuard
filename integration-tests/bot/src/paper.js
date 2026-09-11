'use strict';

const fs = require('fs');
const path = require('path');
const { spawn, spawnSync } = require('child_process');
const { copyFile, copyDirIfExists, rmDirIfExists, pathExists, waitUntil, tailLines } = require('./util');

async function startPaper(options) {
  const {
    root,
    runtimeDir,
    paperJar,
    itemGuardJar,
    harnessJar,
    reportsDir,
    gamePort,
    harnessPort,
    extraPluginJars = [],
    extraJvmArgs = [],
    logFileName = 'paper.log',
    stdoutPrefix = '',
    extraReadyCheck = null,
    beforeStart = null,
    echoStdout = true,
    copyTestConfigs = true,
    wipeWorlds = true,
    clearPluginJars = true,
    motd = 'ItemGuard Integration Test'
  } = options;

  fs.mkdirSync(runtimeDir, { recursive: true });
  fs.mkdirSync(path.join(runtimeDir, 'plugins'), { recursive: true });
  if (copyTestConfigs) {
    fs.mkdirSync(path.join(runtimeDir, 'plugins', 'ItemGuard'), { recursive: true });
    rmDirIfExists(path.join(runtimeDir, 'plugins', 'ItemGuard', 'logs'));
  }
  if (clearPluginJars) {
    const pluginsDir = path.join(runtimeDir, 'plugins');
    for (const name of fs.readdirSync(pluginsDir)) {
      if (name.endsWith('.jar')) {
        fs.rmSync(path.join(pluginsDir, name), { force: true });
      }
    }
  }

  if (wipeWorlds) {
    for (const world of ['world', 'world_nether', 'world_the_end']) {
      rmDirIfExists(path.join(runtimeDir, world));
    }
  }
  const lock = path.join(runtimeDir, 'session.lock');
  if (pathExists(lock)) {
    fs.rmSync(lock, { force: true });
  }

  copyDirIfExists(path.join(root, 'run', 'cache'), path.join(runtimeDir, 'cache'));
  copyDirIfExists(path.join(root, 'run', 'libraries'), path.join(runtimeDir, 'libraries'));
  copyDirIfExists(path.join(root, 'run', 'versions'), path.join(runtimeDir, 'versions'));

  copyFile(itemGuardJar, path.join(runtimeDir, 'plugins', path.basename(itemGuardJar)));
  if (harnessJar) {
    copyFile(harnessJar, path.join(runtimeDir, 'plugins', path.basename(harnessJar)));
  }
  for (const extraJar of extraPluginJars) {
    if (extraJar) {
      copyFile(extraJar, path.join(runtimeDir, 'plugins', path.basename(extraJar)));
    }
  }
  if (copyTestConfigs) {
    copyFile(
      path.join(root, 'integration-tests', 'server-config', 'risk.yml'),
      path.join(runtimeDir, 'plugins', 'ItemGuard', 'risk.yml')
    );
    copyFile(
      path.join(root, 'integration-tests', 'server-config', 'config.yml'),
      path.join(runtimeDir, 'plugins', 'ItemGuard', 'config.yml')
    );
    copyFile(
      path.join(root, 'integration-tests', 'server-config', 'logging.yml'),
      path.join(runtimeDir, 'plugins', 'ItemGuard', 'logging.yml')
    );
  }

  fs.writeFileSync(path.join(runtimeDir, 'eula.txt'), 'eula=true\n');
  fs.writeFileSync(path.join(runtimeDir, 'server.properties'), serverProperties(gamePort, motd));
  fs.writeFileSync(path.join(runtimeDir, 'bukkit.yml'), bukkitYml());

  if (typeof beforeStart === 'function') {
    beforeStart(runtimeDir);
  }

  const logPath = path.join(reportsDir, logFileName);
  const logStream = fs.createWriteStream(logPath, { flags: 'w' });
  const lines = [];
  const java = process.env.JAVA_HOME
    ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
    : 'java';

  const args = [
    '-Xms512M',
    '-Xmx2G',
    `-Ditemguard.harness.port=${harnessPort}`,
    `-Ditemguard.reports.dir=${reportsDir}`,
    `-Ditemguard.runtime.dir=${runtimeDir}`,
    ...extraJvmArgs,
    '-jar',
    paperJar,
    '--nogui'
  ];
  const child = spawn(java, args, {
    cwd: runtimeDir,
    stdio: ['pipe', 'pipe', 'pipe'],
    windowsHide: true
  });

  const handleChunk = (chunk) => {
    const text = chunk.toString('utf8');
    logStream.write(text);
    if (echoStdout) {
      if (stdoutPrefix) {
        for (const line of text.split(/\r?\n/)) {
          if (line) {
            process.stdout.write(stdoutPrefix + line + '\n');
          }
        }
      } else {
        process.stdout.write(text);
      }
    }
    for (const line of text.split(/\r?\n/)) {
      if (line) {
        lines.push(line);
        if (lines.length > 500) {
          lines.shift();
        }
      }
    }
  };
  child.stdout.on('data', handleChunk);
  child.stderr.on('data', handleChunk);

  const state = {
    child,
    logPath,
    lines,
    paperVersion: null,
    stopped: false
  };

  child.on('exit', (code) => {
    state.exitCode = code;
    try {
      logStream.end();
    } catch {
      // ignore
    }
  });

  try {
    await waitUntil(() => {
      const blob = lines.join('\n');
      const version = blob.match(/This server is running Paper version ([^\n]+)/);
      if (version) {
        state.paperVersion = version[1].trim();
      }
      if (/Error occurred while enabling ItemGuard/i.test(blob)) {
        throw new Error('ItemGuard failed to enable. See paper.log');
      }
      return /Done \(/i.test(blob) ? blob : null;
    }, { timeout: 240000, interval: 250, message: 'Paper did not reach Done in time' });
    if (typeof extraReadyCheck === 'function') {
      await waitUntil(() => extraReadyCheck(lines.join('\n')), {
        timeout: 60000,
        interval: 250,
        message: 'Paper extra ready check did not pass'
      });
    }
  } catch (error) {
    error.details = { paperLogTail: tailLines(lines.join('\n'), 100) };
    await stopPaper(state);
    throw error;
  }
  return state;
}

async function stopPaper(paper) {
  if (!paper || paper.stopped) {
    return;
  }
  paper.stopped = true;
  const child = paper.child;
  if (!child || child.exitCode != null || child.killed) {
    return;
  }
  try {
    if (child.stdin && child.stdin.writable) {
      child.stdin.write('stop\n');
    }
  } catch {
    // ignore
  }
  const exited = await waitForExit(child, 20000);
  if (!exited) {
    killTree(child.pid);
    await waitForExit(child, 5000);
  }
}

function waitForExit(child, timeoutMs) {
  if (child.exitCode != null) {
    return Promise.resolve(true);
  }
  return new Promise((resolve) => {
    const timer = setTimeout(() => resolve(false), timeoutMs);
    child.once('exit', () => {
      clearTimeout(timer);
      resolve(true);
    });
  });
}

function killTree(pid) {
  if (!pid) {
    return;
  }
  if (process.platform === 'win32') {
    spawnSync('taskkill', ['/PID', String(pid), '/T', '/F'], { stdio: 'ignore' });
    return;
  }
  try {
    process.kill(pid, 'SIGKILL');
  } catch {
    // ignore
  }
}

function serverProperties(port, motd) {
  return [
    'online-mode=false',
    'server-ip=127.0.0.1',
    `server-port=${port}`,
    'spawn-protection=0',
    'difficulty=normal',
    'gamemode=survival',
    'enable-command-block=false',
    'view-distance=4',
    'simulation-distance=4',
    `motd=${motd}`,
    'enable-status=true',
    'enforce-secure-profile=false',
    'spawn-monsters=false',
    'spawn-animals=false',
    'spawn-npcs=false',
    'allow-nether=false',
    'white-list=false',
    'enable-rcon=false',
    'enable-query=false',
    'sync-chunk-writes=false',
    'max-players=8',
    'network-compression-threshold=-1',
    ''
  ].join('\n');
}

function bukkitYml() {
  return [
    'settings:',
    '  allow-end: false',
    '  connection-throttle: -1',
    'spawn-limits:',
    '  monsters: 0',
    '  animals: 0',
    '  water-animals: 0',
    '  water-ambient: 0',
    '  water-underground-creature: 0',
    '  axolotls: 0',
    '  ambient: 0',
    'ticks-per:',
    '  monster-spawns: 0',
    '  animal-spawns: 0',
    ''
  ].join('\n');
}

module.exports = { startPaper, stopPaper, waitForExit, killTree };
