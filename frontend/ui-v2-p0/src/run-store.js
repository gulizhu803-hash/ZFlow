// run-store.js
// ZFlow「Quiet Canvas 2.0」P0 运行 store：持有状态机、AbortController 与订阅。
// 纯内存实现，不碰 DOM；时钟（now）、AbortController、idFactory 均可注入，便于测试。
// 关键语义：
//   - 同一时间只允许一个 in-flight run；begin 在非终态抛 RUN_IN_PROGRESS。
//   - Stop 只能「停止接收」（abort + STOP_REQUEST），不承诺后台停止。
//   - stopped 是显式终态，只能由 stopped(runId) 触发，disconnect 不能代替。
//   - 所有带 runId 的方法都先做 isCurrent 校验，旧 run 的迟到调用被忽略。

import { STATUS, initialState, reduce, runInProgressError } from './run-state.js';

const ACTIVE_STATUSES = [STATUS.PREPARING, STATUS.RUNNING, STATUS.STOPPING];

function defaultIdFactory() {
  return 'run-' + Math.random().toString(36).slice(2, 10) + '-' + Date.now().toString(36);
}

export function createRunStore({
  now = () => Date.now(),
  createAbortController = () => new AbortController(),
  idFactory = defaultIdFactory
} = {}) {
  let state = initialState();
  let controller = null;
  const listeners = new Set();

  function commit(next) {
    if (next === state) return state;
    state = next;
    for (const listener of listeners) {
      try {
        listener(state);
      } catch {
        // 监听器异常不应中断状态推进。
      }
    }
    return state;
  }

  function dispatch(event) {
    return commit(reduce(state, event));
  }

  function getState() {
    return state;
  }

  function subscribe(listener) {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  }

  function isCurrent(runId) {
    return state.runId != null && state.runId === runId;
  }

  function begin({ sessionId, prompt } = {}) {
    if (ACTIVE_STATUSES.includes(state.status)) throw runInProgressError();
    const runId = idFactory();
    const ctrl = createAbortController();
    controller = ctrl;
    dispatch({
      type: 'SUBMIT',
      runId,
      sessionId: sessionId ?? null,
      prompt: prompt ?? '',
      at: now()
    });
    return { runId, signal: ctrl.signal };
  }

  function prepared(runId, sessionId = null) {
    if (!isCurrent(runId)) return;
    dispatch({ type: 'PREPARED', runId, sessionId, at: now() });
  }

  function stage(runId, stage) {
    if (!isCurrent(runId)) return;
    dispatch({ type: 'STAGE', runId, stage, at: now() });
  }

  function complete(runId) {
    if (!isCurrent(runId)) return;
    dispatch({ type: 'COMPLETE', runId, at: now() });
  }

  function fail(runId, error) {
    if (!isCurrent(runId)) return;
    dispatch({ type: 'FAIL', runId, error: error ?? null, at: now() });
  }

  function stop(runId) {
    if (!isCurrent(runId)) return;
    if (state.status !== STATUS.PREPARING && state.status !== STATUS.RUNNING) return;
    // 先派发 STOP_REQUEST，再 abort：abort 可能同步触发 fetch/SSE 的 catch，
    // 如果先 abort，catch 里的 disconnect 会在 stopRequested 之前把状态改成 unknown。
    dispatch({ type: 'STOP_REQUEST', runId, at: now() });
    controller?.abort();
  }

  function stopped(runId) {
    if (!isCurrent(runId)) return;
    dispatch({ type: 'STOPPED', runId, at: now() });
  }

  function disconnect(runId, error) {
    if (!isCurrent(runId)) return;
    dispatch({ type: 'DISCONNECT', runId, error: error ?? null, at: now() });
  }

  function reset() {
    const active = controller;
    controller = null;
    // 先 RESET 清空 runId，再 abort；abort 触发的迟到回调会被 isCurrent 丢弃。
    dispatch({ type: 'RESET' });
    active?.abort();
  }

  return {
    getState,
    subscribe,
    begin,
    prepared,
    stage,
    complete,
    fail,
    stop,
    stopped,
    disconnect,
    reset,
    isCurrent
  };
}
