// conversation-adapter.js
// ZFlow「Quiet Canvas 2.0」P0 Conversation 适配层：把 transport 的 SSE 流式事件
// 驱动到 run-store。本层不直接依赖真实 fetch；transport 由测试或页面注入。
//
// 职责边界：
//   - 状态机是唯一事实源（run-store）；本层只做「事件 → store 调用」的映射。
//   - 同一次 adapter 生命周期只允许一个 active run；active 时再次 send 直接抛 RUN_IN_PROGRESS。
//   - 用户 Stop 只能落到 stopped；网络 abort（非用户 Stop）落到 unknown。

import { normalizeStage } from './stage.js';
import { STATUS, deriveHistoryStatus, runInProgressError } from './run-state.js';

function isAbortError(error) {
  return error != null && typeof error === 'object' && error.name === 'AbortError';
}

function asString(value) {
  return value == null ? '' : String(value);
}

export function createConversationAdapter({
  store,
  transport,
  now = () => Date.now(),
  parse = JSON.parse,
  onUserMessage,
  onToken,
  onStage,
  onSession
} = {}) {
  if (!store || !transport) {
    throw new Error('createConversationAdapter 需要注入 store 与 transport');
  }

  // 本 adapter 生命周期内的 active runId；send 开始时同步赋值，结束后清空。
  let activeRunId = null;

  function getActiveRunId() {
    return activeRunId;
  }

  // 用户 Stop：只「停止接收」。不直接写 stopped，由 transport abort 引发的
  // AbortError 落到 send 的 catch 再转 stopped。
  function stop() {
    if (activeRunId == null) return;
    store.stop(activeRunId);
  }

  async function loadHistory({ mode, sessionId } = {}) {
    const { messages, stages } = await transport.loadHistory({ mode, sessionId });
    // 历史只能派生 idle / history / unknown，绝不 running。
    const status = deriveHistoryStatus({ messages, stages });
    return { status, messages, stages };
  }

  function handleWorkEvent(runId, event) {
    if (activeRunId !== runId) return; // 迟到事件（stop 后 / 旧 run）直接丢弃。
    if (!event || typeof event !== 'object') return;
    // Stop 之后不再把阶段推给 UI，也不让 fail/complete 改状态。
    if (store.getState().stopRequested) return;
    const status = store.getState().status;
    if (status !== STATUS.PREPARING && status !== STATUS.RUNNING) return;

    if (event.event === 'error') {
      store.fail(runId, event.data);
      return;
    }

    if (event.event === 'complete') {
      let complete = null;
      try {
        complete = parse(event.data);
      } catch {
        store.disconnect(runId, 'invalid stage payload');
        return;
      }
      if (complete && complete.clientType === 'complete') store.complete(runId);
      else store.disconnect(runId, 'invalid stage payload');
      return;
    }

    let entity;
    try {
      entity = parse(event.data);
    } catch {
      store.disconnect(runId, 'invalid stage payload');
      return;
    }
    const stage = normalizeStage(entity, now());
    store.stage(runId, stage);
    if (onStage) onStage(stage);
  }

  async function send(request = {}) {
    // 防重复：active 时直接抛 RUN_IN_PROGRESS，不碰 transport。
    if (activeRunId != null) throw runInProgressError();

    const mode = request.mode === 'work' ? 'work' : 'chat';
    const prompt = asString(request.prompt);

    // begin 生成 runId / signal，并写入 prompt（失败/停止后仍保留）。
    const { runId, signal } = store.begin({ sessionId: request.sessionId ?? null, prompt });
    activeRunId = runId;

    try {
      const prepared = await transport.prepare({
        mode,
        agentId: request.agentId,
        clientId: request.clientId,
        prompt,
        signal
      });

      const sessionId = prepared?.sessionId ?? request.sessionId ?? null;
      // prepare 期间用户可能已经 Stop：不要继续发流、不要追加用户消息。
      if (store.getState().stopRequested) {
        store.stopped(runId);
        return { runId, sessionId };
      }
      store.prepared(runId, sessionId);
      if (onSession) onSession({ sessionId });
      // 只有在 prepare 成功后，才确认用户消息被接受。
      if (onUserMessage) onUserMessage({ role: 'user', content: prompt });

      if (mode === 'work') {
        await transport.streamWork({
          agentId: request.agentId,
          sessionId,
          prompt,
          maxRound: request.maxRound,
          maxRetry: request.maxRetry,
          maxPace: request.maxPace,
          signal
        }, event => handleWorkEvent(runId, event));
        // stream resolve：complete 已在事件里处理；这里只处理「EOF 无 complete」。
        // 如果用户已 Stop 但流没有以 AbortError 结束，必须落 stopped，不能卡在 stopping。
        if (store.getState().stopRequested) store.stopped(runId);
        else store.disconnect(runId, 'EOF without complete');
      } else {
        let firstToken = true;
        await transport.streamChat({
          clientId: request.clientId,
          sessionId,
          prompt,
          ragTag: request.ragTag,
          mcpIds: request.mcpIds,
          signal
        }, event => {
          if (activeRunId !== runId) return; // stop 后迟到 token 直接丢弃。
          if (store.getState().stopRequested) return;
          const status = store.getState().status;
          if (status !== STATUS.PREPARING && status !== STATUS.RUNNING) return;
          const data = event?.data ?? '';
          if (firstToken) {
            firstToken = false;
            // 首个 token 前用合成 stage 让状态从 preparing 进入 running。
            store.stage(runId, normalizeStage({ sectionType: 'stream', label: '回复' }, now()));
          }
          // chat 的 token 是文本片段，绝不当成 work 的阶段 JSON。
          if (onToken) onToken(data);
        });
        if (store.getState().stopRequested) store.stopped(runId);
        else store.complete(runId);
      }

      return { runId, sessionId };
    } catch (error) {
      if (isAbortError(error)) {
        if (store.getState().stopRequested) store.stopped(runId);
        else store.disconnect(runId, 'aborted');
      } else if (store.getState().stopRequested) {
        store.stopped(runId);
      } else {
        store.fail(runId, error);
      }
      throw error;
    } finally {
      if (activeRunId === runId) activeRunId = null;
    }
  }

  return { send, stop, loadHistory, getActiveRunId };
}
