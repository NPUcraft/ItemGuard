'use strict';

const { spawnSync } = require('child_process');
const path = require('path');
const { waitUntil } = require('../../bot/src/util');

function dockerAvailable() {
  const probe = spawnSync('docker', ['info'], { encoding: 'utf8', windowsHide: true });
  return probe.status === 0;
}

function composeAvailable() {
  const probe = spawnSync('docker', ['compose', 'version'], { encoding: 'utf8', windowsHide: true });
  return probe.status === 0;
}

async function startDataPlane({ root, project, mariadbPort, redisPort }) {
  if (!dockerAvailable() || !composeAvailable()) {
    const error = new Error('Docker / docker compose is required for huskSyncIntegrationTest unless ITEMGUARD_TEST_DB_HOST and ITEMGUARD_TEST_REDIS_HOST are set.');
    error.classification = 'INFRASTRUCTURE';
    throw error;
  }
  const file = path.join(root, 'integration-tests', 'husksync', 'docker-compose.yml');
  const env = {
    ...process.env,
    MARIADB_PORT: String(mariadbPort),
    REDIS_PORT: String(redisPort)
  };
  const up = spawnSync('docker', ['compose', '-f', file, '-p', project, 'up', '-d', '--wait', '--wait-timeout', '90'], {
    encoding: 'utf8',
    env,
    windowsHide: true
  });
  if (up.status !== 0) {
    const logs = composeLogs(file, project, env);
    const error = new Error(`docker compose up failed:\n${up.stderr || up.stdout}\n${logs}`);
    error.classification = 'INFRASTRUCTURE';
    throw error;
  }
  await waitUntil(() => serviceHealthy(project, 'mariadb') && serviceHealthy(project, 'redis'), {
    timeout: 60000,
    interval: 500,
    message: 'MariaDB/Redis did not become healthy'
  });
  return {
    project,
    file,
    env,
    logs: () => composeLogs(file, project, env)
  };
}

function serviceHealthy(project, service) {
  const result = spawnSync(
    'docker',
    ['inspect', '--format', '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}', `${project}-${service}-1`],
    { encoding: 'utf8', windowsHide: true }
  );
  const status = (result.stdout || '').trim();
  return status === 'healthy' || status === 'running';
}

function composeLogs(file, project, env, service) {
  const args = ['compose', '-f', file, '-p', project, 'logs', '--no-color', '--tail', '120'];
  if (service) {
    args.push(service);
  }
  const result = spawnSync('docker', args, {
    encoding: 'utf8',
    env,
    windowsHide: true
  });
  return `${result.stdout || ''}\n${result.stderr || ''}`;
}

async function stopDataPlane(plane) {
  if (!plane) {
    return;
  }
  spawnSync('docker', ['compose', '-f', plane.file, '-p', plane.project, 'down', '-v', '--remove-orphans'], {
    encoding: 'utf8',
    env: plane.env,
    windowsHide: true
  });
}

function externalDatabaseFromEnv() {
  if (!process.env.ITEMGUARD_TEST_DB_HOST || !process.env.ITEMGUARD_TEST_REDIS_HOST) {
    return null;
  }
  return {
    host: process.env.ITEMGUARD_TEST_DB_HOST,
    port: Number(process.env.ITEMGUARD_TEST_DB_PORT || 3306),
    database: process.env.ITEMGUARD_TEST_DB_NAME || 'itemguard_husksync_test',
    user: process.env.ITEMGUARD_TEST_DB_USER || 'itemguard_test',
    password: process.env.ITEMGUARD_TEST_DB_PASSWORD || '',
    redisHost: process.env.ITEMGUARD_TEST_REDIS_HOST,
    redisPort: Number(process.env.ITEMGUARD_TEST_REDIS_PORT || 6379),
    redisUser: process.env.ITEMGUARD_TEST_REDIS_USER || '',
    redisPassword: process.env.ITEMGUARD_TEST_REDIS_PASSWORD || '',
    docker: false
  };
}

module.exports = {
  dockerAvailable,
  composeAvailable,
  startDataPlane,
  stopDataPlane,
  composeLogs,
  externalDatabaseFromEnv
};
