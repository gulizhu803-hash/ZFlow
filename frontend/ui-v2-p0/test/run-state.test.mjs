import test from 'node:test';
import assert from 'node:assert/strict';
import { STATUS, initialState, reduce, deriveUi, deriveHistoryStatus } from '../src/run-state.js';

// 构造一个标准 SUBMIT 事件；runId 与其余字段可覆盖。
function submit(runId, overrides = {}) {
  return { type: 'SUBMIT', runId, sessionId: 's1', prompt: 'hello', at: 100, ...overrides };
}

// 快速推进到 running：idle → SUBMIT → STAGE。
function toRunning(runId = 'r1') {
  let state = reduce(initialState(), submit(runId));
  state = reduce(state, { type: 'STAGE', runId, stage: 'planner_step', at: 110 });
  return state;
}

test('1. idle → preparing → running → completed', () => {
  let state = initialState();
  assert.equal(state.status, STATUS.IDLE);

  state = reduce(state, submit('r1'));
  assert.equal(state.status, STATUS.PREPARING);
  assert.equal(state.runId, 'r1');
  assert.equal(state.prompt, 'hello');
  assert.equal(state.revision, 1);

  state = reduce(state, { type: 'PREPARED', runId: 'r1', at: 105 });
  assert.equal(state.status, STATUS.PREPARING);

  state = reduce(state, { type: 'STAGE', runId: 'r1', stage: 'planner_step', at: 110 });
  assert.equal(state.status, STATUS.RUNNING);
  assert.equal(state.stage, 'planner_step');
  assert.equal(state.stages.length, 1);
  assert.equal(state.stages[0].stage, 'planner_step');
  assert.equal(state.stages[0].at, 110);

  state = reduce(state, { type: 'COMPLETE', runId: 'r1', at: 120 });
  assert.equal(state.status, STATUS.COMPLETED);
});

test('2. preparing → failed', () => {
  let state = reduce(initialState(), submit('r1'));
  state = reduce(state, { type: 'FAIL', runId: 'r1', error: '模型不可用', at: 110 });
  assert.equal(state.status, STATUS.FAILED);
  assert.equal(state.error, '模型不可用');
  assert.equal(state.prompt, 'hello'); // 失败后 prompt 仍保留，便于恢复草稿
});

test('3. running → stopping → stopped', () => {
  let state = toRunning('r1');
  state = reduce(state, { type: 'STOP_REQUEST', runId: 'r1', at: 120 });
  assert.equal(state.status, STATUS.STOPPING);
  assert.equal(state.stopRequested, true);
  assert.equal(state.stopFrom, STATUS.RUNNING);

  state = reduce(state, { type: 'STOPPED', runId: 'r1', at: 130 });
  assert.equal(state.status, STATUS.STOPPED);
});

test('4. preparing → stopping → stopped（文案「已取消准备」）', () => {
  let state = reduce(initialState(), submit('r1'));
  state = reduce(state, { type: 'STOP_REQUEST', runId: 'r1', at: 110 });
  assert.equal(state.status, STATUS.STOPPING);
  assert.equal(state.stopFrom, STATUS.PREPARING);

  state = reduce(state, { type: 'STOPPED', runId: 'r1', at: 120 });
  assert.equal(state.status, STATUS.STOPPED);

  const ui = deriveUi(state);
  assert.equal(ui.label, '已取消准备');
  assert.ok(!ui.detail.includes('后台可能继续'));
});

test('5. running → unknown（disconnect）', () => {
  let state = toRunning('r1');
  state = reduce(state, { type: 'DISCONNECT', runId: 'r1', error: 'EOF', at: 120 });
  assert.equal(state.status, STATUS.UNKNOWN);
  assert.equal(state.error, 'EOF');
});

test('6. stopRequested 后 disconnect 不覆盖 stopped', () => {
  let state = toRunning('r1');
  state = reduce(state, { type: 'STOP_REQUEST', runId: 'r1', at: 120 });
  // stopping 阶段收到 disconnect，stopRequested 为 true，不能覆盖为 unknown。
  state = reduce(state, { type: 'DISCONNECT', runId: 'r1', at: 130 });
  assert.equal(state.status, STATUS.STOPPING);

  state = reduce(state, { type: 'STOPPED', runId: 'r1', at: 140 });
  assert.equal(state.status, STATUS.STOPPED);

  // stopped 之后 disconnect 同样不能覆盖。
  state = reduce(state, { type: 'DISCONNECT', runId: 'r1', at: 150 });
  assert.equal(state.status, STATUS.STOPPED);
});

test('7. completed 后 stage/complete 不改变状态', () => {
  let state = toRunning('r1');
  state = reduce(state, { type: 'COMPLETE', runId: 'r1', at: 120 });
  assert.equal(state.status, STATUS.COMPLETED);

  state = reduce(state, { type: 'STAGE', runId: 'r1', stage: 'late', at: 130 });
  assert.equal(state.status, STATUS.COMPLETED);

  state = reduce(state, { type: 'COMPLETE', runId: 'r1', at: 140 });
  assert.equal(state.status, STATUS.COMPLETED);
});

test('8. deriveHistoryStatus：idle / history / unknown 三种情况，绝不返回 running', () => {
  assert.equal(deriveHistoryStatus({ messages: [] }), 'idle');

  assert.equal(
    deriveHistoryStatus({
      messages: [
        { messageRole: 'user', messageContent: 'hi' },
        { messageRole: 'assistant', messageContent: 'ok' }
      ]
    }),
    'history'
  );

  assert.equal(deriveHistoryStatus({ messages: [{ messageRole: 'user', messageContent: 'hi' }] }), 'unknown');

  // 最后一条 user 之后有 complete 阶段 → history。
  assert.equal(
    deriveHistoryStatus({
      messages: [{ messageRole: 'user', messageContent: 'hi' }],
      stages: [{ clientType: 'complete' }]
    }),
    'history'
  );

  // 任何情况下都不返回 running。
  assert.notEqual(deriveHistoryStatus({ messages: [{ messageRole: 'user' }] }), 'running');
  assert.notEqual(deriveHistoryStatus({ messages: [{ messageRole: 'user' }, { messageRole: 'assistant' }] }), 'running');
});

test('9. stopped（运行中停止）文案不包含「取消」', () => {
  let state = toRunning('r1');
  state = reduce(state, { type: 'STOP_REQUEST', runId: 'r1', at: 120 });
  state = reduce(state, { type: 'STOPPED', runId: 'r1', at: 130 });

  const ui = deriveUi(state);
  assert.ok(ui.label.includes('已停止接收'));
  assert.ok(!ui.label.includes('取消'));
  assert.ok(!ui.detail.includes('取消'));
  assert.ok(!ui.label.includes('已结束'));
  assert.ok(!ui.detail.includes('后台已停止'));
});

test('10. unknown 文案不包含「完成」', () => {
  let state = toRunning('r1');
  state = reduce(state, { type: 'DISCONNECT', runId: 'r1', at: 120 });

  const ui = deriveUi(state);
  assert.ok(ui.label.includes('连接已中断') || ui.label.includes('状态未知'));
  assert.ok(!ui.label.includes('完成'));
  assert.ok(!ui.detail.includes('完成'));
});

test('旧 runId 的迟到事件被忽略，不能修改新 run', () => {
  let state = toRunning('r1');
  state = reduce(state, { type: 'COMPLETE', runId: 'r1', at: 120 });
  // 从终态开始新 run。
  state = reduce(state, submit('r2', { prompt: 'second', at: 200 }));
  assert.equal(state.runId, 'r2');
  assert.equal(state.status, STATUS.PREPARING);

  // 旧 runId 的 STAGE/COMPLETE 不能修改新 run。
  state = reduce(state, { type: 'STAGE', runId: 'r1', stage: 'late', at: 210 });
  state = reduce(state, { type: 'COMPLETE', runId: 'r1', at: 220 });
  assert.equal(state.status, STATUS.PREPARING);
  assert.equal(state.runId, 'r2');
  assert.equal(state.stage, null);

  // 新 run 正常推进。
  state = reduce(state, { type: 'STAGE', runId: 'r2', stage: 'x', at: 230 });
  assert.equal(state.status, STATUS.RUNNING);
});

test('RESET 回到 idle，run 字段清空', () => {
  let state = toRunning('r1');
  state = reduce(state, { type: 'FAIL', runId: 'r1', error: 'boom', at: 120 });
  state = reduce(state, { type: 'RESET' });

  assert.equal(state.status, STATUS.IDLE);
  assert.equal(state.runId, null);
  assert.equal(state.sessionId, null);
  assert.equal(state.prompt, '');
  assert.deepEqual(state.stages, []);
  assert.equal(state.stopRequested, false);
  assert.equal(state.stopFrom, null);
});

test('11. deriveHistoryStatus：只有阶段、空 assistant、过期 complete 都不能误判', () => {
  // 只有阶段、没有消息：读到了历史，不是 idle。
  assert.equal(deriveHistoryStatus({ messages: [], stages: [{ clientType: 'complete' }] }), 'history');

  // 空 assistant 占位符不能算“回答了”。
  assert.equal(
    deriveHistoryStatus({
      messages: [
        { messageRole: 'user', messageContent: 'hi' },
        { messageRole: 'assistant', messageContent: '   ' }
      ]
    }),
    'unknown'
  );

  // complete 必须是最后一个阶段；之前的 complete 可能属于上一轮。
  assert.equal(
    deriveHistoryStatus({
      messages: [{ messageRole: 'user', messageContent: 'hi' }],
      stages: [{ clientType: 'complete' }, { sectionType: 'runner_result' }]
    }),
    'unknown'
  );
});

test('12. 用户 Stop 后 FAIL 不能覆盖 stopping', () => {
  let state = toRunning('r1');
  state = reduce(state, { type: 'STOP_REQUEST', runId: 'r1', at: 120 });
  assert.equal(state.status, STATUS.STOPPING);

  state = reduce(state, { type: 'FAIL', runId: 'r1', error: 'network reset', at: 130 });
  assert.equal(state.status, STATUS.STOPPING);
  assert.equal(state.error, null);

  state = reduce(state, { type: 'STOPPED', runId: 'r1', at: 140 });
  assert.equal(state.status, STATUS.STOPPED);
});

test('13. 0-token 回复：preparing -> completed', () => {
  let state = reduce(initialState(), submit('r1'));
  assert.equal(state.status, STATUS.PREPARING);

  state = reduce(state, { type: 'COMPLETE', runId: 'r1', at: 120 });
  assert.equal(state.status, STATUS.COMPLETED);
});

test('14. PREPARED 可以补写新会话 sessionId', () => {
  let state = reduce(initialState(), submit('r1', { sessionId: null }));
  assert.equal(state.sessionId, null);

  state = reduce(state, { type: 'PREPARED', runId: 'r1', sessionId: 's-new', at: 105 });
  assert.equal(state.status, STATUS.PREPARING);
  assert.equal(state.sessionId, 's-new');
});
