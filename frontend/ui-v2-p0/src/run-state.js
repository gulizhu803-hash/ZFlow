// run-state.js
// ZFlow「Quiet Canvas 2.0」P0 运行状态机（纯逻辑，无 DOM、无时钟依赖、无副作用）。
// 本模块只做纯函数，时间戳由调用方通过事件的 `at` 字段注入，便于测试与复用。
// 状态机与 store 分离：reduce 负责状态迁移，store 负责持有 AbortController 与订阅。

export const STATUS = Object.freeze({
  IDLE: 'idle',
  PREPARING: 'preparing',
  RUNNING: 'running',
  STOPPING: 'stopping',
  STOPPED: 'stopped',
  COMPLETED: 'completed',
  FAILED: 'failed',
  UNKNOWN: 'unknown'
});

// 事件类型（字符串字面量；store 派发与测试都使用这些常量）。
export const EVENT = Object.freeze({
  SUBMIT: 'SUBMIT',
  PREPARED: 'PREPARED',
  STAGE: 'STAGE',
  COMPLETE: 'COMPLETE',
  FAIL: 'FAIL',
  STOP_REQUEST: 'STOP_REQUEST',
  STOPPED: 'STOPPED',
  DISCONNECT: 'DISCONNECT',
  RESET: 'RESET'
});

// 非终态（in-flight）状态：这些状态下不允许再次 begin / SUBMIT。
const ACTIVE_STATUSES = [STATUS.PREPARING, STATUS.RUNNING, STATUS.STOPPING];

// 构造「已有运行中任务」错误，供 store.begin 与 reduce 的 SUBMIT 分支复用。
export function runInProgressError() {
  const error = new Error('已有任务正在执行，请勿重复提交');
  error.code = 'RUN_IN_PROGRESS';
  return error;
}

// 把 error 归一化为字符串（UI 与序列化都更安全）。
function normalizeError(error) {
  if (error == null) return null;
  if (typeof error === 'string') return error;
  if (error instanceof Error) return error.message;
  return String(error);
}

// 初始状态：idle，run 相关字段全部为空。
export function initialState() {
  return {
    sessionId: null,
    runId: null,
    status: STATUS.IDLE,
    stage: null,
    stages: [],
    prompt: '',
    startedAt: null,
    updatedAt: null,
    error: null,
    revision: 0,
    stopRequested: false,
    stopFrom: null
  };
}

// 事件是否属于当前 run；旧 runId 的迟到事件一律忽略，不能修改新 run。
function isCurrentRun(state, event) {
  return state.runId != null && event != null && event.runId === state.runId;
}

export function reduce(state, event) {
  if (!event || typeof event.type !== 'string') return state;

  switch (event.type) {
    case EVENT.SUBMIT: {
      // SUBMIT 只能从 idle 或终态开始；有 active run 时抛错（store 层会先拦截）。
      if (ACTIVE_STATUSES.includes(state.status)) throw runInProgressError();
      const at = event.at ?? event.startedAt ?? null;
      return {
        ...state,
        sessionId: event.sessionId ?? null,
        runId: event.runId,
        status: STATUS.PREPARING,
        stage: null,
        stages: [],
        prompt: event.prompt ?? '',
        startedAt: at,
        updatedAt: at,
        error: null,
        revision: state.revision + 1,
        stopRequested: false,
        stopFrom: null
      };
    }

    case EVENT.PREPARED: {
      // PREPARED 只在 preparing；装配/建会话完成，仍保持 preparing，等待首个 STAGE。
      // 新会话的 sessionId 在 prepare 之后才拿到，需要在这里补写，供跨路由/恢复使用。
      if (!isCurrentRun(state, event)) return state;
      if (state.status !== STATUS.PREPARING) return state;
      return {
        ...state,
        sessionId: event.sessionId ?? state.sessionId,
        updatedAt: event.at ?? state.updatedAt
      };
    }

    case EVENT.STAGE: {
      // STAGE 从 preparing 或 running 进入 running；记录阶段与时间。
      if (!isCurrentRun(state, event)) return state;
      if (state.status !== STATUS.PREPARING && state.status !== STATUS.RUNNING) return state;
      const at = event.at ?? state.updatedAt;
      const stage = event.stage ?? state.stage;
      return {
        ...state,
        status: STATUS.RUNNING,
        stage,
        stages: [...state.stages, { stage, at }],
        updatedAt: at,
        error: null
      };
    }

    case EVENT.COMPLETE: {
      // COMPLETE 允许从 preparing（0 token 的空回复）或 running 进入；
      // stopping/stopped 后的 complete 忽略。
      if (!isCurrentRun(state, event)) return state;
      if (state.status !== STATUS.PREPARING && state.status !== STATUS.RUNNING) return state;
      return { ...state, status: STATUS.COMPLETED, updatedAt: event.at ?? state.updatedAt };
    }

    case EVENT.FAIL: {
      // FAIL 从 preparing/running/stopping 进入 failed。
      // 用户已经请求 Stop 后，网络错误不能把 stopped/stopping 覆盖成 failed。
      if (!isCurrentRun(state, event)) return state;
      if (state.stopRequested) return state;
      if (
        state.status !== STATUS.PREPARING &&
        state.status !== STATUS.RUNNING &&
        state.status !== STATUS.STOPPING
      ) return state;
      return {
        ...state,
        status: STATUS.FAILED,
        error: normalizeError(event.error),
        updatedAt: event.at ?? state.updatedAt
      };
    }

    case EVENT.STOP_REQUEST: {
      // STOP_REQUEST 从 preparing/running 进入 stopping，并记录 stopFrom。
      if (!isCurrentRun(state, event)) return state;
      if (state.status !== STATUS.PREPARING && state.status !== STATUS.RUNNING) return state;
      return {
        ...state,
        status: STATUS.STOPPING,
        stopRequested: true,
        stopFrom: state.status,
        updatedAt: event.at ?? state.updatedAt
      };
    }

    case EVENT.STOPPED: {
      // STOPPED 从 stopping 进入 stopped（显式终态）。
      if (!isCurrentRun(state, event)) return state;
      if (state.status !== STATUS.STOPPING) return state;
      return { ...state, status: STATUS.STOPPED, updatedAt: event.at ?? state.updatedAt };
    }

    case EVENT.DISCONNECT: {
      // DISCONNECT 从 preparing/running/stopping 进入 unknown；
      // 若 stopRequested 为 true（用户已请求停止），不能覆盖为 unknown。
      if (!isCurrentRun(state, event)) return state;
      if (
        state.status !== STATUS.PREPARING &&
        state.status !== STATUS.RUNNING &&
        state.status !== STATUS.STOPPING
      ) return state;
      if (state.stopRequested) return state;
      return {
        ...state,
        status: STATUS.UNKNOWN,
        error: normalizeError(event.error),
        updatedAt: event.at ?? state.updatedAt
      };
    }

    case EVENT.RESET: {
      // RESET 回到 idle；revision 作为生成计数保持单调递增，用于丢弃旧回调。
      return { ...initialState(), revision: state.revision };
    }

    default:
      return state;
  }
}

// 刷新恢复：只返回 idle / history / unknown，绝不返回 running。
// history 是「读到了历史」的派生结果，不是 run 状态，也不代表 completed。
export function deriveHistoryStatus({ messages = [], stages = [] } = {}) {
  const list = Array.isArray(messages) ? messages : [];
  const stageList = Array.isArray(stages) ? stages : [];
  if (list.length === 0 && stageList.length === 0) return STATUS.IDLE;
  if (list.length === 0) return 'history';

  let lastUserIndex = -1;
  for (let i = 0; i < list.length; i += 1) {
    const message = list[i];
    if (message && message.messageRole === 'user') lastUserIndex = i;
  }

  // 没有 user 消息：只有已记录内容，没有悬空的待确认提交。
  if (lastUserIndex === -1) return 'history';

  const after = list.slice(lastUserIndex + 1);
  const hasAssistantAfter = after.some(
    message => message && message.messageRole === 'assistant' && String(message.messageContent || '').trim()
  );
  // 只看最后一个阶段，避免把上一轮的 complete 误判成本轮已完成。
  const hasCompleteStage = isCompleteMarker(stageList[stageList.length - 1]);

  if (hasAssistantAfter || hasCompleteStage) return 'history';
  return STATUS.UNKNOWN;
}

// 判断某个阶段对象是否携带「完成」标记。
function isCompleteMarker(item) {
  if (!item || typeof item !== 'object') return false;
  return (
    item.clientType === 'complete' ||
    item.sectionType === 'complete' ||
    item.event === 'complete' ||
    item.type === 'complete'
  );
}

// 从 run 状态派生 UI 文案与可用性；UI 层不应各自维护 boolean。
// tone 取值：neutral / active / warning / muted / success / danger。
export function deriveUi(state) {
  switch (state.status) {
    case STATUS.IDLE:
      return { status: STATUS.IDLE, label: '等待开始', detail: '可以开始新的任务', tone: 'neutral', canSend: true, showPill: false };
    case STATUS.PREPARING:
      return { status: STATUS.PREPARING, label: '准备中', detail: '正在准备，请稍候', tone: 'active', canSend: false, showPill: true };
    case STATUS.RUNNING:
      return { status: STATUS.RUNNING, label: '正在执行', detail: '正在接收阶段信息', tone: 'active', canSend: false, showPill: true };
    case STATUS.STOPPING:
      return { status: STATUS.STOPPING, label: '正在停止', detail: '正在停止接收', tone: 'warning', canSend: false, showPill: true };
    case STATUS.STOPPED:
      // preparing 阶段停止：尚未进入执行，使用「已取消准备」而非「后台可能继续」。
      if (state.stopFrom === STATUS.PREPARING) {
        return { status: STATUS.STOPPED, label: '已取消准备', detail: '尚未进入执行阶段', tone: 'muted', canSend: false, showPill: false };
      }
      // 运行中停止：只能「停止接收」，不能承诺后台已停止。
      return { status: STATUS.STOPPED, label: '已停止接收', detail: '后台可能继续，请查看历史确认', tone: 'muted', canSend: false, showPill: false };
    case STATUS.COMPLETED:
      return { status: STATUS.COMPLETED, label: '已完成', detail: '已收到完成事件，可查看结果', tone: 'success', canSend: true, showPill: false };
    case STATUS.FAILED:
      return { status: STATUS.FAILED, label: '执行失败', detail: state.error || '执行失败，可点击重试', tone: 'danger', canSend: true, showPill: false };
    case STATUS.UNKNOWN:
      return { status: STATUS.UNKNOWN, label: '连接已中断 · 状态未知', detail: '任务状态未知，请查看历史确认，勿重复提交', tone: 'warning', canSend: false, showPill: false };
    default:
      return { status: state.status, label: '状态未知', detail: '', tone: 'neutral', canSend: false, showPill: false };
  }
}
