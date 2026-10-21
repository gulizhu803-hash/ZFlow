import test from 'node:test';
import assert from 'node:assert/strict';
import { createRunStore } from '../src/run-store.js';
import { STATUS } from '../src/run-state.js';
import { createConversationAdapter } from '../src/conversation-adapter.js';
import { loadSSE } from './helpers/sse.mjs';

const fixture = name => new URL('../fixtures/' + name, import.meta.url);

function makeAbortError(message = 'aborted') {
  const error = new Error(message);
  error.name = 'AbortError';
  return error;
}

// 可注入的 transport：计数每个方法调用次数，方法体可整体替换。
function makeTransport(overrides = {}) {
  const calls = { prepare: 0, streamChat: 0, streamWork: 0, loadHistory: 0 };
  const wrap = (name, fallback) => (overrides[name]
    ? async (...args) => { calls[name] += 1; return overrides[name](...args); }
    : async () => { calls[name] += 1; return fallback(); });
  return {
    calls,
    prepare: wrap('prepare', () => ({ sessionId: 's1' })),
    streamChat: wrap('streamChat', () => undefined),
    streamWork: wrap('streamWork', () => undefined),
    loadHistory: wrap('loadHistory', () => ({ messages: [], stages: [] }))
  };
}

function makeHarness({ now = () => 0, transport } = {}) {
  const store = createRunStore({ now });
  const t = transport || makeTransport();
  const collected = { user: [], token: [], stage: [], session: [], stageMeta: [], tokenMeta: [] };
  const adapter = createConversationAdapter({
    store,
    transport: t,
    now,
    onUserMessage: m => collected.user.push(m),
    onToken: (d, meta) => { collected.token.push(d); collected.tokenMeta.push(meta); },
    onStage: (s, meta) => { collected.stage.push(s); collected.stageMeta.push(meta); },
    onSession: s => collected.session.push(s)
  });
  return { store, adapter, transport: t, collected };
}

async function waitFor(predicate, timeoutMs = 1000) {
  const start = Date.now();
  while (!predicate()) {
    if (Date.now() - start > timeoutMs) throw new Error('waitFor 超时');
    await new Promise(resolve => setTimeout(resolve, 0));
  }
}

test('1. chat-happy：token 回调、completed、onUserMessage 一次', async () => {
  const { store, adapter, collected } = makeHarness({
    transport: makeTransport({
      streamChat: ({ clientId, sessionId, prompt, ragTag, mcpIds, signal }, onEvent) => {
        loadSSE(fixture('chat-happy.sse')).forEach(e => onEvent(e));
        return Promise.resolve();
      }
    })
  });

  const result = await adapter.send({ mode: 'chat', clientId: 'c1', prompt: '拆解想法' });

  assert.equal(result.sessionId, 's1');
  assert.equal(store.getState().sessionId, 's1'); // prepare 后回写新会话
  assert.equal(store.getState().status, STATUS.COMPLETED);
  assert.deepEqual(collected.token, [
    '好的，我来帮你',
    '拆解这个想法。',
    '第一步，先明确目标与范围；',
    '第二步，再整理已有信息。'
  ]);
  assert.equal(collected.user.length, 1);
  assert.deepEqual(collected.user[0], { role: 'user', content: '拆解想法' });
  assert.equal(collected.stage.length, 0); // chat token 不触发 onStage
});

test('2. work-happy：stage 形状正确、completed', async () => {
  const { store, adapter, collected } = makeHarness({
    transport: makeTransport({
      streamWork: ({ agentId, sessionId, prompt, maxRound, maxRetry, maxPace, signal }, onEvent) => {
        loadSSE(fixture('work-happy.sse')).forEach(e => onEvent(e));
        return Promise.resolve();
      }
    })
  });

  await adapter.send({ mode: 'work', agentId: 'a1', prompt: '整理报告' });

  assert.equal(store.getState().status, STATUS.COMPLETED);
  assert.equal(collected.stage.length, 3);

  assert.equal(collected.stage[0].clientType, 'INSPECTOR');
  assert.equal(collected.stage[0].sectionType, 'inspector_mcp');
  assert.equal(collected.stage[0].label, '工具检查');
  assert.equal(collected.stage[0].content, '识别任务所需的检索与资料阅读能力。');
  assert.equal(collected.stage[0].round, 1);
  assert.equal(collected.stage[0].step, 1);

  assert.equal(collected.stage[1].clientType, 'PLANNER');
  assert.equal(collected.stage[2].clientType, 'RUNNER');
  assert.equal(collected.stage[2].sectionType, 'runner_result');
});

test('3. work-error：failed', async () => {
  const { store, adapter } = makeHarness({
    transport: makeTransport({
      streamWork: ({ signal }, onEvent) => {
        loadSSE(fixture('work-error.sse')).forEach(e => onEvent(e));
        return Promise.resolve();
      }
    })
  });

  await adapter.send({ mode: 'work', agentId: 'a1', prompt: '任务' });

  assert.equal(store.getState().status, STATUS.FAILED);
  assert.equal(store.getState().error, '模型调用失败：超出速率限制，请稍后重试。');
});

test('4. work-eof：unknown，不 completed', async () => {
  const { store, adapter } = makeHarness({
    transport: makeTransport({
      streamWork: ({ signal }, onEvent) => {
        loadSSE(fixture('work-eof.sse')).forEach(e => onEvent(e));
        return Promise.resolve();
      }
    })
  });

  await adapter.send({ mode: 'work', agentId: 'a1', prompt: '任务' });

  assert.equal(store.getState().status, STATUS.UNKNOWN);
  assert.notEqual(store.getState().status, STATUS.COMPLETED);
  assert.equal(store.getState().error, 'EOF without complete');
});

test('5. 用户 Stop：stopped，不是 unknown/failed/completed', async () => {
  let capturedOnEvent;
  const { store, adapter } = makeHarness({
    transport: makeTransport({
      streamWork: ({ signal }, onEvent) => {
        capturedOnEvent = onEvent;
        return new Promise((resolve, reject) => {
          signal.addEventListener('abort', () => reject(makeAbortError()));
        });
      }
    })
  });

  const p = adapter.send({ mode: 'work', agentId: 'a1', prompt: '任务' });
  await waitFor(() => capturedOnEvent != null);

  adapter.stop();
  await p.catch(() => {}); // send 会把 AbortError rethrow，这里吞掉。

  assert.equal(store.getState().status, STATUS.STOPPED);
  assert.notEqual(store.getState().status, STATUS.UNKNOWN);
  assert.notEqual(store.getState().status, STATUS.FAILED);
  assert.notEqual(store.getState().status, STATUS.COMPLETED);
  assert.equal(store.getState().prompt, '任务'); // stop 后 prompt 仍保留
});

test('6. 网络 AbortError（未 Stop）：unknown', async () => {
  const { store, adapter } = makeHarness({
    transport: makeTransport({
      streamWork: async () => { throw makeAbortError('network aborted'); }
    })
  });

  await assert.rejects(
    adapter.send({ mode: 'work', agentId: 'a1', prompt: '任务' }),
    error => error.name === 'AbortError'
  );

  assert.equal(store.getState().status, STATUS.UNKNOWN);
  assert.equal(store.getState().error, 'aborted');
});

test('7. 重复 send：RUN_IN_PROGRESS，transport.prepare 只调用一次', async () => {
  const { store, adapter, transport } = makeHarness({
    transport: makeTransport({
      streamWork: ({ signal }) => new Promise((resolve, reject) => {
        signal.addEventListener('abort', () => reject(makeAbortError()));
      })
    })
  });

  const p1 = adapter.send({ mode: 'work', agentId: 'a1', prompt: '第一次' });
  await waitFor(() => transport.calls.streamWork === 1);

  await assert.rejects(
    adapter.send({ mode: 'work', agentId: 'a1', prompt: '第二次' }),
    error => error.code === 'RUN_IN_PROGRESS'
  );

  assert.equal(transport.calls.prepare, 1);

  // 清理：结束第一个 active run，避免悬挂。
  adapter.stop();
  await p1.catch(() => {});
  assert.equal(store.getState().status, STATUS.STOPPED);
});

test('8. prepare 失败：failed，onUserMessage 不触发', async () => {
  const { store, adapter, collected } = makeHarness({
    transport: makeTransport({
      prepare: async () => { throw new Error('装配失败'); }
    })
  });

  await assert.rejects(
    adapter.send({ mode: 'work', agentId: 'a1', prompt: '任务' }),
    error => error.message === '装配失败'
  );

  assert.equal(store.getState().status, STATUS.FAILED);
  assert.equal(store.getState().error, '装配失败');
  assert.equal(collected.user.length, 0);
});

test('9. stop 后迟到 stage/complete 不改状态', async () => {
  let capturedOnEvent;
  const { store, adapter } = makeHarness({
    transport: makeTransport({
      streamWork: ({ signal }, onEvent) => {
        capturedOnEvent = onEvent;
        return new Promise((resolve, reject) => {
          signal.addEventListener('abort', () => reject(makeAbortError()));
        });
      }
    })
  });

  const p = adapter.send({ mode: 'work', agentId: 'a1', prompt: '任务' });
  await waitFor(() => capturedOnEvent != null);

  capturedOnEvent({ event: 'message', data: '{"clientType":"PLANNER","sectionType":"planner_step"}' });

  adapter.stop();
  await p.catch(() => {});
  assert.equal(store.getState().status, STATUS.STOPPED);

  // 迟到事件不得改状态。
  capturedOnEvent({ event: 'message', data: '{"clientType":"RUNNER","sectionType":"runner_result"}' });
  capturedOnEvent({ event: 'complete', data: '{"clientType":"complete"}' });
  assert.equal(store.getState().status, STATUS.STOPPED);
});

test('10. loadHistory：返回 deriveHistoryStatus 的 history/unknown，绝不 running', async () => {
  let historyResult = {
    messages: [
      { messageRole: 'user', messageContent: 'hi' },
      { messageRole: 'assistant', messageContent: 'ok' }
    ],
    stages: []
  };
  const { adapter } = makeHarness({
    transport: makeTransport({ loadHistory: async () => historyResult })
  });

  let result = await adapter.loadHistory({ mode: 'chat', sessionId: 's1' });
  assert.equal(result.status, 'history');
  assert.notEqual(result.status, 'running');

  historyResult = { messages: [{ messageRole: 'user', messageContent: 'hi' }], stages: [] };
  result = await adapter.loadHistory({ mode: 'chat', sessionId: 's1' });
  assert.equal(result.status, 'unknown');
  assert.notEqual(result.status, 'running');

  historyResult = { messages: [], stages: [] };
  result = await adapter.loadHistory({ mode: 'chat', sessionId: 's1' });
  assert.equal(result.status, 'idle');
  assert.notEqual(result.status, 'running');
});

test('11. stage 的 at 来自注入 now', async () => {
  let clock = 1000;
  const { adapter, collected } = makeHarness({
    now: () => clock,
    transport: makeTransport({
      streamWork: ({ signal }, onEvent) => {
        clock = 2000;
        onEvent({ event: 'message', data: '{"clientType":"INSPECTOR","sectionType":"inspector_mcp"}' });
        clock = 3000;
        onEvent({ event: 'complete', data: '{"clientType":"complete"}' });
        return Promise.resolve();
      }
    })
  });

  await adapter.send({ mode: 'work', agentId: 'a1', prompt: '任务' });

  assert.equal(collected.stage.length, 1);
  assert.equal(collected.stage[0].at, 2000);
});

test('12. onStage 只在 work 阶段触发，chat token 不触发 onStage', async () => {
  const chatHarness = makeHarness({
    transport: makeTransport({
      streamChat: ({ signal }, onEvent) => {
        onEvent({ data: 'a' });
        onEvent({ data: 'b' });
        return Promise.resolve();
      }
    })
  });
  await chatHarness.adapter.send({ mode: 'chat', clientId: 'c1', prompt: 'hi' });
  assert.equal(chatHarness.collected.stage.length, 0);

  const workHarness = makeHarness({
    transport: makeTransport({
      streamWork: ({ signal }, onEvent) => {
        onEvent({ event: 'message', data: '{"clientType":"PLANNER","sectionType":"planner_step"}' });
        onEvent({ event: 'complete', data: '{"clientType":"complete"}' });
        return Promise.resolve();
      }
    })
  });
  await workHarness.adapter.send({ mode: 'work', agentId: 'a1', prompt: 'hi' });
  assert.equal(workHarness.collected.stage.length, 1);
});

test('13. prepare 期间 Stop：不继续发流、不追加用户消息', async () => {
  let resolvePrepare;
  const { store, adapter, transport, collected } = makeHarness({
    transport: makeTransport({
      prepare: () => new Promise(resolve => { resolvePrepare = resolve; }),
      streamWork: () => { throw new Error('不应启动流'); }
    })
  });

  const p = adapter.send({ mode: 'work', agentId: 'a1', prompt: '任务' });
  await waitFor(() => resolvePrepare != null);

  adapter.stop();
  resolvePrepare({ sessionId: 's1' });
  await p.catch(() => {});

  assert.equal(store.getState().status, STATUS.STOPPED);
  assert.equal(collected.user.length, 0);
  assert.equal(transport.calls.streamWork, 0);
});

test('14. Stop 后流正常 resolve：必须 stopped，不能卡在 stopping/unknown', async () => {
  let resolveStream;
  const { store, adapter } = makeHarness({
    transport: makeTransport({
      streamWork: () => new Promise(resolve => { resolveStream = resolve; })
    })
  });

  const p = adapter.send({ mode: 'work', agentId: 'a1', prompt: '任务' });
  await waitFor(() => resolveStream != null);

  adapter.stop();
  resolveStream();
  await p.catch(() => {});

  assert.equal(store.getState().status, STATUS.STOPPED);
  assert.notEqual(store.getState().status, STATUS.STOPPING);
  assert.notEqual(store.getState().status, STATUS.UNKNOWN);
});

test('15. Stop 后迟到阶段不再触发 onStage', async () => {
  let capturedOnEvent;
  let resolveStream;
  const { store, adapter, collected } = makeHarness({
    transport: makeTransport({
      streamWork: ({ signal }, onEvent) => {
        capturedOnEvent = onEvent;
        return new Promise(resolve => { resolveStream = resolve; });
      }
    })
  });

  const p = adapter.send({ mode: 'work', agentId: 'a1', prompt: '任务' });
  await waitFor(() => capturedOnEvent != null && resolveStream != null);

  adapter.stop();
  capturedOnEvent({ event: 'message', data: '{"clientType":"RUNNER","sectionType":"runner_result"}' });
  resolveStream();
  await p.catch(() => {});

  assert.equal(store.getState().status, STATUS.STOPPED);
  assert.equal(collected.stage.length, 0);
});

test('16. chat Stop 后流正常 resolve：stopped，不 complete', async () => {
  let resolveStream;
  const { store, adapter } = makeHarness({
    transport: makeTransport({
      streamChat: () => new Promise(resolve => { resolveStream = resolve; })
    })
  });

  const p = adapter.send({ mode: 'chat', clientId: 'c1', prompt: 'hi' });
  await waitFor(() => resolveStream != null);

  adapter.stop();
  resolveStream();
  await p.catch(() => {});

  assert.equal(store.getState().status, STATUS.STOPPED);
  assert.notEqual(store.getState().status, STATUS.COMPLETED);
});
test('17. 回调携带 runId/sessionId，页面重建后可过滤旧 run', async () => {
  const { store, adapter, collected } = makeHarness({
    transport: makeTransport({
      streamWork: ({ sessionId }, onEvent) => {
        onEvent({ event: 'message', data: '{"clientType":"RUNNER","sectionType":"runner_result"}' });
        onEvent({ event: 'complete', data: '{"clientType":"complete"}' });
        return Promise.resolve();
      }
    })
  });

  await adapter.send({ mode: 'work', agentId: 'a1', prompt: '任务' });
  const runId = store.getState().runId;

  assert.equal(collected.stageMeta.length, 1);
  assert.equal(collected.stageMeta[0].runId, runId);
  assert.equal(collected.stageMeta[0].sessionId, 's1');
});
