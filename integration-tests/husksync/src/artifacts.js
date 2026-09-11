'use strict';

const crypto = require('crypto');
const fs = require('fs');
const https = require('https');
const path = require('path');
const { pathExists } = require('../../bot/src/util');

const VELOCITY_VERSION = '3.5.1';
const VELOCITY_BUILD = 615;
const VELOCITY_SHA256 = 'b4e3164df5377346854dc6cb9e6a78022b1946ff69e89676313f5f6f1c6f0fb3';
const VELOCITY_URL = 'https://fill-data.papermc.io/v1/objects/b4e3164df5377346854dc6cb9e6a78022b1946ff69e89676313f5f6f1c6f0fb3/velocity-3.5.1-615.jar';
const HUSKSYNC_URL = 'https://repo.william278.net/releases/net/william278/husksync/husksync-bukkit/3.8.7%2B1.21.8/husksync-bukkit-3.8.7%2B1.21.8.jar';

async function resolveArtifacts(root) {
  const cache = path.join(root, 'integration-tests', 'dependencies');
  fs.mkdirSync(cache, { recursive: true });
  const huskSync = firstExisting([
    process.env.ITEMGUARD_HUSKSYNC_JAR,
    path.join(cache, 'HuskSync-3.8.7+1.21.8.jar'),
    path.join(root, 'run', 'plugins', 'HuskSync-3.8.7+1.21.8.jar')
  ]);
  const velocity = firstExisting([
    process.env.ITEMGUARD_VELOCITY_JAR,
    path.join(cache, 'velocity-3.5.1-615.jar')
  ]);
  const resolved = {
    huskSyncJar: huskSync || path.join(cache, 'HuskSync-3.8.7+1.21.8.jar'),
    velocityJar: velocity || path.join(cache, 'velocity-3.5.1-615.jar'),
    velocityVersion: `${VELOCITY_VERSION}-${VELOCITY_BUILD}`,
    huskSyncVersion: '3.8.7'
  };
  if (!pathExists(resolved.huskSyncJar)) {
    console.log('Downloading HuskSync 3.8.7+1.21.8 from repo.william278.net ...');
    await download(HUSKSYNC_URL, resolved.huskSyncJar);
    if (fs.statSync(resolved.huskSyncJar).size < 1_000_000) {
      throw infra('Downloaded HuskSync JAR is too small to be the plugin');
    }
  }
  if (!pathExists(resolved.velocityJar)) {
    console.log(`Downloading Velocity ${VELOCITY_VERSION}-${VELOCITY_BUILD} from PaperMC ...`);
    await download(VELOCITY_URL, resolved.velocityJar);
    const hash = sha256(resolved.velocityJar);
    if (hash !== VELOCITY_SHA256) {
      throw infra(`Velocity SHA-256 mismatch: ${hash}`);
    }
  }
  return resolved;
}

function firstExisting(candidates) {
  for (const file of candidates) {
    if (file && pathExists(file)) {
      return file;
    }
  }
  return null;
}

function sha256(file) {
  return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
}

function download(url, dest) {
  return new Promise((resolve, reject) => {
    const tmp = dest + '.part';
    const request = (target) => {
      https.get(target, (response) => {
        if (response.statusCode >= 300 && response.statusCode < 400 && response.headers.location) {
          response.resume();
          request(response.headers.location);
          return;
        }
        if (response.statusCode !== 200) {
          reject(infra(`Download failed ${response.statusCode} for ${target}`));
          return;
        }
        const stream = fs.createWriteStream(tmp);
        response.pipe(stream);
        stream.on('finish', () => {
          stream.close(() => {
            fs.renameSync(tmp, dest);
            resolve();
          });
        });
      }).on('error', (error) => reject(infra(`Download failed: ${error.message}`)));
    };
    request(url);
  });
}

function infra(message) {
  const error = new Error(message);
  error.classification = 'INFRASTRUCTURE';
  return error;
}

module.exports = { resolveArtifacts, VELOCITY_VERSION, VELOCITY_BUILD };
