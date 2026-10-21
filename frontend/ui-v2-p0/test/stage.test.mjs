import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeStage } from '../src/stage.js';

test('1. 完整 entity 的字段映射', () => {
  const entity = {
    id: 'stage-42',
    clientType: 'INSPECTOR',
    sectionType: 'inspector_mcp',
    sectionTitle: '工具检查',
    round: 2,
    step: 3,
    sectionContent: '识别任务所需的检索能力。'
  };

  const stage = normalizeStage(entity, 1234);

  assert.equal(stage.id, 'stage-42');
  assert.equal(stage.clientType, 'INSPECTOR');
  assert.equal(stage.sectionType, 'inspector_mcp');
  assert.equal(stage.label, '工具检查');
  assert.equal(stage.round, 2);
  assert.equal(stage.step, 3);
  assert.equal(stage.content, '识别任务所需的检索能力。');
  assert.equal(stage.at, 1234);
  assert.equal(stage.raw, entity);
});

test('2. 缺 sectionTitle 时 label 回退到 sectionType，再回退到 clientType', () => {
  const bySection = normalizeStage({ clientType: 'PLANNER', sectionType: 'planner_step' }, 0);
  assert.equal(bySection.label, 'planner_step');

  const byClient = normalizeStage({ clientType: 'RUNNER' }, 0);
  assert.equal(byClient.label, 'RUNNER');
});

test('3. 缺 round/step 不抛错，值为 undefined', () => {
  const stage = normalizeStage({ clientType: 'RUNNER', sectionType: 'runner_result' }, 0);
  assert.equal(stage.round, undefined);
  assert.equal(stage.step, undefined);
});

test('4. 空对象有安全默认值，不抛错', () => {
  const stage = normalizeStage({}, undefined);

  assert.equal(typeof stage.id, 'string');
  assert.ok(stage.id.length > 0);
  assert.equal(stage.clientType, '');
  assert.equal(stage.sectionType, '');
  assert.equal(stage.label, '');
  assert.equal(stage.round, undefined);
  assert.equal(stage.step, undefined);
  assert.equal(stage.content, '');
  assert.equal(stage.at, null);
  assert.deepEqual(stage.raw, {});
});

test('5. raw 不被修改（同引用且内容不变）', () => {
  const entity = { clientType: 'INSPECTOR', sectionType: 'inspector_mcp', sectionContent: '内容', round: 1 };
  const before = JSON.parse(JSON.stringify(entity));

  const stage = normalizeStage(entity, 100);

  assert.equal(stage.raw, entity);
  assert.deepEqual(entity, before);
});

test('6. 同一条 entity 多次 normalize 结果稳定（id 一致）', () => {
  const entity = { clientType: 'PLANNER', sectionType: 'planner_step', round: 1, step: 2 };

  const a = normalizeStage(entity, 1);
  const b = normalizeStage(entity, 2);

  assert.equal(a.id, b.id);
  assert.equal(a.clientType, b.clientType);
  assert.equal(a.sectionType, b.sectionType);
  assert.equal(a.label, b.label);
  assert.equal(a.round, b.round);
  assert.equal(a.step, b.step);
  // at 是调用参数，允许不同；其余稳定。
  assert.equal(a.at, 1);
  assert.equal(b.at, 2);
});

test('7. 非对象输入（null / undefined / 字符串）不抛错，给安全默认值', () => {
  for (const value of [null, undefined, 'text', 42]) {
    const stage = normalizeStage(value, 5);
    assert.equal(stage.clientType, '');
    assert.equal(stage.sectionType, '');
    assert.equal(stage.label, '');
    assert.equal(stage.content, '');
    assert.equal(stage.at, 5);
    assert.equal(stage.raw, value);
  }
});

test('8. round/step 字符串数字会被归一为 number，非数字回退 undefined', () => {
  const stage = normalizeStage({ clientType: 'A', round: '3', step: 'x' }, 0);
  assert.equal(stage.round, 3);
  assert.equal(stage.step, undefined);
});
