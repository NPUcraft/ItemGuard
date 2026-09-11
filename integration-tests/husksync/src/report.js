'use strict';

const fs = require('fs');
const path = require('path');
const { tailLines } = require('../../bot/src/util');

function writeHuskSyncReports({ reportsDir, environment, results, elapsedMs }) {
  fs.mkdirSync(reportsDir, { recursive: true });
  const summary = {
    passed: results.filter((test) => test.status === 'PASS').length,
    failed: results.filter((test) => test.status === 'FAIL').length,
    partial: results.filter((test) => test.status === 'PARTIAL').length,
    notAutomated: results.filter((test) => test.status === 'NOT AUTOMATED').length
  };
  const json = {
    environment,
    summary,
    elapsedMs,
    tests: results
  };
  const jsonPath = path.join(reportsDir, 'results.json');
  const mdPath = path.join(reportsDir, 'results.md');
  fs.writeFileSync(jsonPath, JSON.stringify(json, null, 2));
  const lines = [
    '# ItemGuard HuskSync Integration Results',
    '',
    '## Environment',
    '',
    `- Paper: ${environment.paper}`,
    `- ItemGuard: ${environment.itemguard}`,
    `- HuskSync: ${environment.husksync}`,
    `- Velocity: ${environment.velocity}`,
    `- Mineflayer: ${environment.mineflayer}`,
    `- Minecraft protocol: ${environment.minecraftProtocol}`,
    `- Database: ${environment.database}`,
    `- Redis: ${environment.redis}`,
    `- Cluster id: ${environment.clusterId}`,
    `- Java: ${environment.java}`,
    `- Elapsed: ${elapsedMs}ms`,
    '',
    '## Infrastructure',
    '',
    `- Velocity: 127.0.0.1:${environment.velocityPort}`,
    `- Paper A: 127.0.0.1:${environment.paperAPort}`,
    `- Paper B: 127.0.0.1:${environment.paperBPort}`,
    `- Docker: ${environment.docker || 'external'}`,
    '',
    `PASS ${summary.passed} / FAIL ${summary.failed} / PARTIAL ${summary.partial} / NOT AUTOMATED ${summary.notAutomated}`,
    ''
  ];
  if (environment.recommendRc1) {
    lines.push('RECOMMEND: ' + (environment.itemguard || 'current build'), '');
  } else {
    lines.push('RECOMMEND: stay on the current build until HS-001..HS-005 are all real PASS.', '');
  }
  for (const test of results) {
    lines.push(`### ${test.status} ${test.id} ${test.name}`);
    if (test.classification) {
      lines.push(`- classification: ${test.classification}`);
    }
    if (test.error) {
      lines.push(`- error: ${String(test.error).split('\n')[0]}`);
    }
    if (test.note) {
      lines.push(`- note: ${test.note}`);
    }
    if (test.details && test.details.inventory) {
      lines.push(`- inventory: ${JSON.stringify(test.details.inventory)}`);
    }
    if (test.details && test.details.huskSyncState) {
      lines.push(`- HuskSync state: ${test.details.huskSyncState}`);
    }
    if (test.details && test.details.riskScore != null) {
      lines.push(`- risk: ${test.details.riskScore}`);
    }
    lines.push('');
  }
  appendLogTail(lines, reportsDir, 'server-a.log', 'Paper A');
  appendLogTail(lines, reportsDir, 'server-b.log', 'Paper B');
  appendLogTail(lines, reportsDir, 'velocity.log', 'Velocity');
  const markdown = lines.join('\n');
  fs.writeFileSync(mdPath, markdown);
  return { jsonPath, mdPath, markdown, summary };
}

function appendLogTail(lines, reportsDir, name, title) {
  const file = path.join(reportsDir, name);
  if (!fs.existsSync(file)) {
    return;
  }
  lines.push(`## ${title} log tail`, '', '```');
  lines.push(tailLines(fs.readFileSync(file, 'utf8'), 40));
  lines.push('```', '');
}

function failDump(ctx, extra) {
  const dump = {
    extra,
    botBackend: ctx.backend || null,
    botUuid: ctx.bot && ctx.bot.uuid,
    paperA: tailLines(readQuiet(path.join(ctx.reportsDir, 'server-a.log')), 150),
    paperB: tailLines(readQuiet(path.join(ctx.reportsDir, 'server-b.log')), 150),
    velocity: tailLines(readQuiet(path.join(ctx.reportsDir, 'velocity.log')), 80),
    docker: ctx.dataPlane && ctx.dataPlane.logs ? ctx.dataPlane.logs() : null
  };
  return dump;
}

function readQuiet(file) {
  try {
    return fs.readFileSync(file, 'utf8');
  } catch {
    return '';
  }
}

module.exports = { writeHuskSyncReports, failDump };
