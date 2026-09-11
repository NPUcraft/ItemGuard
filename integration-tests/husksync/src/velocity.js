'use strict';

const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const { waitUntil, tailLines } = require('../../bot/src/util');
const { waitForExit, killTree } = require('../../bot/src/paper');

async function startVelocity(options) {
  const { runtimeDir, velocityJar, reportsDir, proxyPort, serverAPort, serverBPort, forwardingSecret } = options;
  fs.mkdirSync(runtimeDir, { recursive: true });
  fs.writeFileSync(path.join(runtimeDir, 'forwarding.secret'), forwardingSecret, { encoding: 'utf8' });
  fs.writeFileSync(path.join(runtimeDir, 'velocity.toml'), velocityToml({
    proxyPort,
    serverAPort,
    serverBPort
  }));

  const logPath = path.join(reportsDir, 'velocity.log');
  const logStream = fs.createWriteStream(logPath, { flags: 'w' });
  const lines = [];
  const java = process.env.JAVA_HOME
    ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
    : 'java';
  const child = spawn(java, ['-Xms256M', '-Xmx512M', '-jar', velocityJar], {
    cwd: runtimeDir,
    stdio: ['pipe', 'pipe', 'pipe'],
    windowsHide: true
  });
  const handleChunk = (chunk) => {
    const text = chunk.toString('utf8');
    logStream.write(text);
    for (const line of text.split(/\r?\n/)) {
      if (line) {
        process.stdout.write(`[velocity] ${line}\n`);
        lines.push(line);
        if (lines.length > 400) {
          lines.shift();
        }
      }
    }
  };
  child.stdout.on('data', handleChunk);
  child.stderr.on('data', handleChunk);

  const state = { child, logPath, lines, stopped: false, proxyPort };
  child.on('exit', () => {
    try {
      logStream.end();
    } catch {
      // ignore
    }
  });

  try {
    await waitUntil(() => {
      const blob = lines.join('\n');
      if (/Exception in thread|Failed to start|Address already in use/i.test(blob) && !/Listening on/i.test(blob)) {
        const error = new Error(`Velocity failed to start. See velocity.log`);
        error.classification = 'INFRASTRUCTURE';
        error.details = { velocityLogTail: tailLines(blob, 80) };
        throw error;
      }
      return /Listening on/i.test(blob) ? blob : null;
    }, { timeout: 60000, interval: 200, message: 'Velocity did not start listening' });
  } catch (error) {
    error.classification = error.classification || 'INFRASTRUCTURE';
    error.details = Object.assign({ velocityLogTail: tailLines(lines.join('\n'), 80) }, error.details || {});
    await stopVelocity(state);
    throw error;
  }
  return state;
}

async function stopVelocity(velocity) {
  if (!velocity || velocity.stopped) {
    return;
  }
  velocity.stopped = true;
  const child = velocity.child;
  if (!child || child.exitCode != null || child.killed) {
    return;
  }
  try {
    if (child.stdin && child.stdin.writable) {
      child.stdin.write('end\n');
    }
  } catch {
    // ignore
  }
  const exited = await waitForExit(child, 15000);
  if (!exited) {
    killTree(child.pid);
    await waitForExit(child, 5000);
  }
}

function velocityToml({ proxyPort, serverAPort, serverBPort }) {
  return `config-version = "2.7"
bind = "127.0.0.1:${proxyPort}"
motd = "<#09add3>ItemGuard HuskSync Integration"
show-max-players = 8
online-mode = false
force-key-authentication = false
prevent-client-proxy-connections = false
player-info-forwarding-mode = "MODERN"
forwarding-secret-file = "forwarding.secret"
announce-forge = false
kick-existing-players = false
ping-passthrough = "DISABLED"
player-sample-limit = 7
enable-player-address-logging = false

[servers]
server-a = "127.0.0.1:${serverAPort}"
server-b = "127.0.0.1:${serverBPort}"
try = ["server-a"]

[forced-hosts]

[advanced]
compression-threshold = 256
compression-level = -1
login-ratelimit = 0
connection-timeout = 5000
read-timeout = 30000
haproxy-protocol = false
tcp-fast-open = false
bungee-plugin-message-channel = true
show-ping-requests = false
failover-on-unexpected-server-disconnect = true
announce-proxy-commands = true
log-command-executions = true
log-player-connections = true
accepts-transfers = false
enable-reuse-port = false

[query]
enabled = false
port = ${proxyPort}
map = "ItemGuard"
show-plugins = false
`;
}

module.exports = { startVelocity, stopVelocity };
