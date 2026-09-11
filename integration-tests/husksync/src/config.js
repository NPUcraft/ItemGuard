'use strict';

const fs = require('fs');
const path = require('path');
const { copyDirIfExists, pathExists } = require('../../bot/src/util');

function writeHuskSyncConfig({ pluginDir, clusterId, serverName, database, redis }) {
  fs.mkdirSync(pluginDir, { recursive: true });
  fs.writeFileSync(path.join(pluginDir, 'config.yml'), huskSyncConfigYml({ clusterId, database, redis }));
  fs.writeFileSync(path.join(pluginDir, 'server.yml'), huskSyncServerYml(serverName));
}

function huskSyncServerYml(serverName) {
  return [
    '# ItemGuard HuskSync integration-only server id. Must match the Velocity backend id.',
    `name: ${serverName}`,
    ''
  ].join('\n');
}

function huskSyncConfigYml({ clusterId, database, redis }) {
  const redisUser = redis.user == null ? '' : redis.user;
  const redisPassword = redis.password == null ? '' : redis.password;
  return `# ItemGuard integration-only HuskSync 3.8.7 config. Not for production.
language: en-gb
check_for_updates: false
cluster_id: '${clusterId}'
debug_logging: false
enable_plan_hook: false
cancel_packets: false
disabled_commands: []
database:
  type: MARIADB
  credentials:
    host: ${database.host}
    port: ${database.port}
    database: ${database.database}
    username: ${database.user}
    password: '${escapeYaml(database.password)}'
    parameters: ?autoReconnect=true&useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8
  connection_pool:
    maximum_pool_size: 8
    minimum_idle: 2
    maximum_lifetime: 1800000
    keepalive_time: 0
    connection_timeout: 5000
  mongo_settings:
    using_atlas: false
    parameters: ?retryWrites=true&w=majority&authSource=HuskSync
  table_names:
    users: husksync_users
    user_data: husksync_user_data
redis:
  credentials:
    host: ${redis.host}
    port: ${redis.port}
    database: ${redis.database == null ? 0 : redis.database}
    user: '${escapeYaml(redisUser)}'
    password: '${escapeYaml(redisPassword)}'
    use_ssl: false
  sentinel:
    master: ''
    nodes: []
    password: ''
synchronization:
  mode: LOCKSTEP
  max_user_data_snapshots: 16
  snapshot_backup_frequency: 4
  auto_pinned_save_causes:
    - INVENTORY_COMMAND
    - ENDERCHEST_COMMAND
    - BACKUP_RESTORE
    - LEGACY_MIGRATION
    - MPDB_MIGRATION
  save_on_world_save: false
  save_on_death:
    enabled: false
    items_to_save: ITEMS_TO_KEEP
    save_empty_items: true
    sync_dead_players_changing_server: true
  compress_data: true
  notification_display_slot: NONE
  persist_locked_maps: true
  network_latency_milliseconds: 500
  features:
    inventory: true
    ender_chest: true
    experience: true
    advancements: true
    game_mode: true
    flight_status: true
    potion_effects: true
    statistics: true
    health: true
    hunger: true
    attributes: true
    persistent_data: true
    location: false
  blacklisted_commands_while_locked:
    - '*'
  attributes:
    synced_attributes:
      - "minecraft:generic.max_health"
      - "minecraft:max_health"
      - "minecraft:generic.max_absorption"
      - "minecraft:max_absorption"
      - "minecraft:generic.luck"
      - "minecraft:luck"
      - "minecraft:generic.scale"
      - "minecraft:scale"
      - "minecraft:generic.step_height"
      - "minecraft:step_height"
      - "minecraft:generic.gravity"
      - "minecraft:gravity"
    ignored_modifiers: ['minecraft:effect.*', 'minecraft:creative_mode_*']
  event_priorities:
    quit_listener: LOWEST
    join_listener: LOWEST
    death_listener: NORMAL
`;
}

function writePaperProxyConfig({ root, runtimeDir, secret, motd }) {
  const configDir = path.join(runtimeDir, 'config');
  fs.mkdirSync(configDir, { recursive: true });
  copyDirIfExists(path.join(root, 'run', 'config'), configDir);
  const globalPath = path.join(configDir, 'paper-global.yml');
  let text;
  if (pathExists(globalPath)) {
    text = fs.readFileSync(globalPath, 'utf8');
  } else {
    text = minimalPaperGlobal();
  }
  if (/velocity:\r?\n[ \t]+enabled:/.test(text)) {
    text = text.replace(
      /velocity:\r?\n[ \t]+enabled:.*\r?\n[ \t]+online-mode:.*\r?\n[ \t]+secret:.*/,
      `velocity:\n    enabled: true\n    online-mode: false\n    secret: ${yamlQuote(secret)}`
    );
  } else {
    text += `\nproxies:\n  velocity:\n    enabled: true\n    online-mode: false\n    secret: ${yamlQuote(secret)}\n`;
  }
  fs.writeFileSync(globalPath, text);
  fs.writeFileSync(path.join(runtimeDir, 'spigot.yml'), [
    'settings:',
    '  bungeecord: false',
    '  timeout: 60',
    '  late-bind: false',
    ''
  ].join('\n'));
  const properties = fs.readFileSync(path.join(runtimeDir, 'server.properties'), 'utf8')
    .replace(/motd=.*/g, `motd=${motd}`)
    .replace(/max-players=.*/g, 'max-players=8');
  const extra = [
    properties.trimEnd(),
    'prevent-proxy-connections=false',
    'level-type=minecraft:flat',
    'generate-structures=false',
    ''
  ].join('\n');
  fs.writeFileSync(path.join(runtimeDir, 'server.properties'), extra);
}

function minimalPaperGlobal() {
  return [
    '_version: 30',
    'proxies:',
    '  bungee-cord:',
    '    online-mode: true',
    '  proxy-protocol: false',
    '  velocity:',
    '    enabled: false',
    '    online-mode: true',
    '    secret: \'\'',
    ''
  ].join('\n');
}

function yamlQuote(value) {
  return `'${String(value).replace(/'/g, "''")}'`;
}

function escapeYaml(value) {
  return String(value == null ? '' : value).replace(/'/g, "''");
}

module.exports = {
  writeHuskSyncConfig,
  writePaperProxyConfig,
  huskSyncConfigYml,
  huskSyncServerYml
};
