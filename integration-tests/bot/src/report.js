'use strict';

const fs = require('fs');
const path = require('path');
const { tailLines } = require('./util');

function writeReports({ reportsDir, environment, results, elapsedMs, paperLog }) {
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
    '# ItemGuard Runtime Integration Results',
    '',
    `- Paper: ${environment.paper}`,
    `- Java: ${environment.java}`,
    `- ItemGuard: ${environment.itemguard}`,
    `- Node: ${environment.node}`,
    `- Mineflayer: ${environment.mineflayer}`,
    `- Bot protocol: ${environment.protocol || environment.botVersion}`,
    `- Elapsed: ${elapsedMs}ms`,
    '',
    `PASS ${summary.passed} / FAIL ${summary.failed} / PARTIAL ${summary.partial} / NOT AUTOMATED ${summary.notAutomated}`,
    ''
  ];
  for (const test of results) {
    lines.push(`${test.status} ${test.id} ${test.name}`);
    if (test.error) {
      lines.push(`- error: ${String(test.error).split('\n')[0]}`);
    }
    if (test.note) {
      lines.push(`- note: ${test.note}`);
    }
  }
  if (paperLog && fs.existsSync(paperLog)) {
    lines.push('', '## Paper log tail', '', '```');
    lines.push(tailLines(fs.readFileSync(paperLog, 'utf8'), 40));
    lines.push('```');
  }
  const markdown = lines.join('\n') + '\n';
  fs.writeFileSync(mdPath, markdown);
  return { jsonPath, mdPath, markdown, summary };
}

function failDump({ ctx, dump, extra }) {
  let paperTail = '';
  try {
    paperTail = tailLines(fs.readFileSync(path.join(ctx.reportsDir, 'paper.log'), 'utf8'), 100);
  } catch {
    paperTail = '';
  }
  return {
    inventory: dump && dump.inventory,
    slots: dump && dump.slots,
    health: dump && dump.health,
    gamemode: dump && dump.gamemode,
    x: dump && dump.x,
    y: dump && dump.y,
    z: dump && dump.z,
    snapshot: dump && dump.snapshot,
    scan: dump && dump.scan,
    extra,
    paperLogTail: paperTail
  };
}

module.exports = { writeReports, failDump };
