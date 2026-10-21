import test from 'node:test';
import assert from 'node:assert/strict';
import { createRunStore } from '../src/run-store.js';
import { STATUS, deriveUi } from '../src/run-state.js';

// 构建一个完全注入的 store：固定/可控时钟、可控 idFactory、可观察的 AbortController。
// 不依赖真实计时器（setTimeout/Date.now），也不依赖 DOM（window/document/AbortController 均被注入）。
function makeHarness() {
  const controllers = [];
  let clock = 0;
  let seq = 0;

  const store = createRunStore({
    now: () => clock,
    createAbortController: () => {
      const controller = {
        aborted: false,
        signal: {
          get aborted() {
            return controller.aborted;
          },
          addEventListener() {},
          removeEventListener() {}
        },
        abort() {
          controller.aborted = true;
        }
      };
      controllers.push(controller);
      return controller;
    },
    idFactory: () => 'run-' + (++seq)
  });

  return {
    store,
    controllers,
    tick(n = 1) {
      clock += n;
    },
    now() {
      return clock;
    }
  };
}

// 推进到 running：begin → stage。
function beginRunning(harness, prompt = 'hello') {
  const { runId, signal } = harness.store.begin({ sessionId: 's1', prompt });
  harness.store.stage(runId, 'planner_step');
  return { runId, signal };
}

test('1. begin 返回 runId/signal，状态为 preparing', () => {
  const harness = makeHarness();
  const { runId, signal } = harness.store.begin({ sessionId: 's1', prompt: 'hello' });

  assert.equal(runId, 'run-1');
  assert.equal(signal, harness.controllers[0].signal);

  const state = harness.store.getState();
  assert.equal(state.status, STATUS.PREPARING);
  assert.equal(state.sessionId, 's1');
  assert.equal(state.prompt, 'hello');
  assert.equal(state.revision, 1);
});

test('2. active 时再次 begin 抛 RUN_IN_PROGRESS', () => {
  const harness = makeHarness();
  harness.store.begin({ sessionId: 's1', prompt: 'first' });

  assert.throws(
    () => harness.store.begin({ sessionId: 's2', prompt: 'second' }),
    error => error.code === 'RUN_IN_PROGRESS'
  );
});

test('3. prepared/stage/complete 正常推进', () => {
  const harness = makeHarness();
  const { runId } = harness.store.begin({ sessionId: 's1', prompt: 'hello' });

  harness.store.prepared(runId);
  assert.equal(harness.store.getState().status, STATUS.PREPARING);

  harness.store.stage(runId, 'planner_step');
  assert.equal(harness.store.getState().status, STATUS.RUNNING);

  harness.store.complete(runId);
  assert.equal(harness.store.getState().status, STATUS.COMPLETED);
});

test('4. stop 后 stopped，prompt 保留，且调用了 controller.abort()', () => {
  const harness = makeHarness();
  const { runId } = beginRunning(harness, 'draft-prompt');

  harness.store.stop(runId);
  assert.equal(harness.controllers[0].aborted, true);
  assert.equal(harness.store.getState().status, STATUS.STOPPING);

  harness.store.stopped(runId);
  assert.equal(harness.store.getState().status, STATUS.STOPPED);
  assert.equal(harness.store.getState().prompt, 'draft-prompt');
});

test('5. stop 后 fail/disconnect 不覆盖 stopped', () => {
  const harness = makeHarness();
  const { runId } = beginRunning(harness);

  harness.store.stop(runId);
  harness.store.stopped(runId);
  assert.equal(harness.store.getState().status, STATUS.STOPPED);

  harness.store.fail(runId, 'boom');
  assert.equal(harness.store.getState().status, STATUS.STOPPED);

  harness.store.disconnect(runId, 'EOF');
  assert.equal(harness.store.getState().status, STATUS.STOPPED);
});

test('6. 旧 runId 的 stage/complete 被忽略', () => {
  const harness = makeHarness();

  const first = harness.store.begin({ sessionId: 's1', prompt: 'first' });
  harness.store.stage(first.runId, 'a');
  harness.store.complete(first.runId);
  assert.equal(harness.store.getState().status, STATUS.COMPLETED);

  // 从终态开始新 run。
  const second = harness.store.begin({ sessionId: 's2', prompt: 'second' });
  assert.equal(harness.store.getState().runId, 'run-2');
  assert.equal(harness.store.getState().status, STATUS.PREPARING);

  // 旧 runId 迟到事件被忽略。
  harness.store.stage(first.runId, 'late');
  harness.store.complete(first.runId);
  assert.equal(harness.store.getState().status, STATUS.PREPARING);
  assert.equal(harness.store.getState().runId, 'run-2');

  // 新 run 正常推进。
  harness.store.stage(second.runId, 'b');
  assert.equal(harness.store.getState().status, STATUS.RUNNING);
});

test('7. 新 run 的 revision 递增', () => {
  const harness = makeHarness();

  const first = harness.store.begin({ sessionId: 's1', prompt: 'first' });
  assert.equal(harness.store.getState().revision, 1);
  harness.store.stage(first.runId, 'a');
  harness.store.complete(first.runId);

  harness.store.begin({ sessionId: 's2', prompt: 'second' });
  assert.equal(harness.store.getState().revision, 2);
});

test('8. deriveUi 的 canSend 在 running/stopped/unknown 为 false', () => {
  const harness = makeHarness();

  // idle：可发送。
  assert.equal(deriveUi(harness.store.getState()).canSend, true);

  // running：不可发送。
  const { runId } = beginRunning(harness);
  assert.equal(deriveUi(harness.store.getState()).canSend, false);

  // stopped：不可发送。
  harness.store.stop(runId);
  harness.store.stopped(runId);
  assert.equal(deriveUi(harness.store.getState()).canSend, false);

  // unknown：不可发送。
  const second = harness.store.begin({ sessionId: 's2', prompt: 'again' });
  harness.store.stage(second.runId, 'x');
  harness.store.disconnect(second.runId, 'EOF');
  assert.equal(deriveUi(harness.store.getState()).canSend, false);
});

test('9. 时间戳来自注入的 clock，不依赖真实计时器', () => {
  const harness = makeHarness();

  harness.tick(1000); // now = 1000
  harness.store.begin({ sessionId: 's1', prompt: 'hello' });
  assert.equal(harness.store.getState().startedAt, 1000);

  harness.tick(500); // now = 1500
  harness.store.stage('run-1', 'x');
  assert.equal(harness.store.getState().updatedAt, 1500);
});

test('10. 模块在无 DOM 的 Node 环境可用（不引用 window/document）', () => {
  // 本测试运行于 node:test，store 只依赖注入的 now / createAbortController / idFactory。
  const harness = makeHarness();
  const { runId } = harness.store.begin({ sessionId: 's1', prompt: 'hello' });
  harness.store.stage(runId, 'x');
  harness.store.complete(runId);
  assert.equal(harness.store.getState().status, STATUS.COMPLETED);
  assert.equal(typeof window, 'undefined');
  assert.equal(typeof document, 'undefined');
});

test('subscribe 在状态变化时通知监听器，unsubscribe 后不再通知', () => {
  const harness = makeHarness();
  const seen = [];
  const unsubscribe = harness.store.subscribe(state => seen.push(state.status));

  harness.store.begin({ sessionId: 's1', prompt: 'hello' });
  harness.store.stage('run-1', 'x');
  unsubscribe();
  harness.store.complete('run-1');

  assert.deepEqual(seen, [STATUS.PREPARING, STATUS.RUNNING]);
});

test('isCurrent 与 reset 行为正确', () => {
  const harness = makeHarness();
  const { runId } = harness.store.begin({ sessionId: 's1', prompt: 'hello' });

  assert.equal(harness.store.isCurrent(runId), true);
  assert.equal(harness.store.isCurrent('stale'), false);

  harness.store.reset();
  assert.equal(harness.store.getState().status, STATUS.IDLE);
  assert.equal(harness.store.getState().runId, null);
  assert.equal(harness.store.isCurrent(runId), false);
});

test('11. stop 先派发 STOP_REQUEST 再 abort，abort 触发的 disconnect 不能覆盖', () => {
  let store;
  const controller = {
    aborted: false,
    signal: {
      get aborted() {
        return controller.aborted;
      },
      addEventListener() {},
      removeEventListener() {}
    },
    abort() {
      controller.aborted = true;
      // 模拟 fetch/SSE 被 abort 后的 catch 回调。
      store.disconnect('run-1', 'aborted');
    }
  };

  store = createRunStore({
    now: () => 0,
    idFactory: () => 'run-1',
    createAbortController: () => controller
  });

  store.begin({ sessionId: 's1', prompt: 'p' });
  store.stage('run-1', 'x');
  store.stop('run-1');

  assert.equal(store.getState().status, STATUS.STOPPING);
  assert.equal(store.getState().stopRequested, true);

  store.stopped('run-1');
  assert.equal(store.getState().status, STATUS.STOPPED);
});

test('12. stop 后、stopped 前的 fail 不能覆盖 stopping', () => {
  const harness = makeHarness();
  const { runId } = beginRunning(harness);

  harness.store.stop(runId);
  harness.store.fail(runId, 'network reset');
  assert.equal(harness.store.getState().status, STATUS.STOPPING);
  assert.equal(harness.store.getState().error, null);

  harness.store.stopped(runId);
  assert.equal(harness.store.getState().status, STATUS.STOPPED);
});

test('13. reset 会 abort 活跃的 controller', () => {
  const harness = makeHarness();
  harness.store.begin({ sessionId: 's1', prompt: 'hello' });

  assert.equal(harness.controllers[0].aborted, false);
  harness.store.reset();

  assert.equal(harness.controllers[0].aborted, true);
  assert.equal(harness.store.getState().status, STATUS.IDLE);
});

test('14. abort 回调同步调用 stopped 时，最终状态是 stopped', () => {
  let store;
  const controller = {
    aborted: false,
    signal: {
      get aborted() {
        return controller.aborted;
      },
      addEventListener() {},
      removeEventListener() {}
    },
    abort() {
      controller.aborted = true;
      store.stopped('run-1');
    }
  };

  store = createRunStore({
    now: () => 0,
    idFactory: () => 'run-1',
    createAbortController: () => controller
  });

  store.begin({ sessionId: 's1', prompt: 'p' });
  store.stage('run-1', 'x');
  store.stop('run-1');

  assert.equal(store.getState().status, STATUS.STOPPED);
});
