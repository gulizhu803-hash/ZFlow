import test from 'node:test';
import assert from 'node:assert/strict';
import { RUN_STAGES, stageRoleOf, deriveRunStages, formatClock } from '../lib/run-stages.js';

const entry = (sectionType, at, content = '') => ({
  stage: { sectionType, clientType: sectionType.split('_')[0].toUpperCase(), sectionContent: content },
  at
});
const states = view => view.nodes.map(node => node.state);

test('stageRoleOf maps every backend role prefix to one of the four stages', () => {
  const expected = {
    inspector_mcp: 'check', analyzer_demand: 'check', observer_demand: 'check',
    planner_step: 'plan', reasoner_action: 'plan',
    runner_result: 'execute', performer_result: 'execute', actor_result: 'execute',
    replier_overview: 'reply', summarizer_overview: 'reply', evaluator_overview: 'reply'
  };
  for (const [sectionType, role] of Object.entries(expected)) {
    assert.equal(stageRoleOf(sectionType), role, sectionType);
  }
  for (const value of ['', 'stream', 'complete', 'history', 'unknownRole', null, undefined, 42]) {
    assert.equal(stageRoleOf(value), null, String(value));
  }
});

test('RUN_STAGES lists the four ordered stages', () => {
  assert.deepEqual(RUN_STAGES.map(item => item.role), ['check', 'plan', 'execute', 'reply']);
  assert.deepEqual(RUN_STAGES.map(item => item.label), ['检查', '规划', '执行', '回复']);
});

test('no events keep four pending nodes and never fake progress', () => {
  const view = deriveRunStages({ status: 'running', stages: [] });
  assert.equal(view.total, 4);
  assert.equal(view.reached, 0);
  assert.equal(view.inFlight, true);
  assert.equal(view.retryable, false);
  assert.deepEqual(states(view), ['pending', 'pending', 'pending', 'pending']);
});

test('only the check stage marks the first node active with its output', () => {
  const view = deriveRunStages({ status: 'running', stages: [entry('inspector_mcp', 1000, '检索能力确认')] });
  assert.deepEqual(states(view), ['active', 'pending', 'pending', 'pending']);
  assert.equal(view.reached, 1);
  assert.equal(view.activeIndex, 0);
  assert.equal(view.nodes[0].output, '检索能力确认');
  assert.equal(view.nodes[0].detail, '工具检查');
});

test('completed marks reached nodes done and keeps per-stage durations', () => {
  const view = deriveRunStages({
    status: 'completed',
    updatedAt: 4000,
    stages: [entry('inspector_mcp', 1000), entry('planner_step', 2000), entry('runner_result', 3000), entry('replier_overview', 3500)]
  });
  assert.deepEqual(states(view), ['done', 'done', 'done', 'done']);
  assert.equal(view.reached, 4);
  assert.equal(view.inFlight, false);
  assert.equal(view.failed, false);
  assert.equal(view.retryable, false);
  assert.equal(view.nodes[0].durationMs, 1000);
  assert.equal(view.nodes[0].duration, '00:01');
  assert.equal(view.nodes[3].durationMs, 500);
});

test('failed puts the last known node in the error state', () => {
  const view = deriveRunStages({ status: 'failed', updatedAt: 2500, stages: [entry('inspector_mcp', 1000), entry('planner_step', 2000)] });
  assert.deepEqual(states(view), ['done', 'error', 'pending', 'pending']);
  assert.equal(view.failed, true);
  assert.equal(view.retryable, true);
  assert.equal(view.reached, 2);
});

test('unknown keeps the last known node as error and never marks completion', () => {
  const view = deriveRunStages({ status: 'unknown', stages: [entry('runner_result', 1000, '部分输出')] });
  assert.deepEqual(states(view), ['pending', 'pending', 'error', 'pending']);
  assert.equal(view.failed, true);
  assert.equal(view.retryable, false);
  assert.equal(view.nodes[2].output, '部分输出');
  assert.equal(view.inFlight, false);
});

test('stopped keeps the last known node done without an active ring', () => {
  const view = deriveRunStages({ status: 'stopped', updatedAt: 1800, stages: [entry('inspector_mcp', 1000), entry('planner_step', 1500)] });
  assert.deepEqual(states(view), ['done', 'done', 'pending', 'pending']);
  assert.equal(view.failed, false);
  assert.equal(view.retryable, false);
  assert.equal(view.inFlight, false);
});

test('unmapped stages are ignored but real ones still derive', () => {
  const view = deriveRunStages({ status: 'running', stages: [entry('stream', 500), entry('planner_step', 900)] });
  assert.deepEqual(states(view), ['pending', 'active', 'pending', 'pending']);
  assert.equal(view.reached, 1);
});

test('formatClock renders mm:ss from milliseconds', () => {
  assert.equal(formatClock(84000), '01:24');
  assert.equal(formatClock(0), '00:00');
  assert.equal(formatClock(999), '00:00');
  assert.equal(formatClock(-5), '00:00');
  assert.equal(formatClock(undefined), '00:00');
  assert.equal(formatClock(600000), '10:00');
});
