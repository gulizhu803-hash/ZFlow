// run-stages.js
// ZFlow P1-3B-2 Run 模式阶段派生：把 run-store 的 stages 归一为「检查 / 规划 / 执行 / 回复」四节点。
// 纯函数、无副作用、无时钟依赖（时间戳由 run 状态注入），StageRail / ActivityPill / Inspector 共用。
import { sectionLabels } from './demo.js';

export const RUN_STAGES = Object.freeze([
  { role: 'check', label: '检查' },
  { role: 'plan', label: '规划' },
  { role: 'execute', label: '执行' },
  { role: 'reply', label: '回复' }
]);

export const STAGE_TOTAL = RUN_STAGES.length;

// sectionType 前缀 → 阶段角色。后端用 <role>_<section> 命名，这里只认前缀。
const ROLE_PREFIXES = {
  check: ['inspector', 'analyzer', 'observer'],
  plan: ['planner', 'reasoner'],
  execute: ['runner', 'performer', 'actor'],
  reply: ['replier', 'summarizer', 'evaluator']
};

const ROLE_BY_PREFIX = Object.fromEntries(
  Object.entries(ROLE_PREFIXES).flatMap(([role, prefixes]) => prefixes.map(prefix => [prefix, role]))
);

const IN_FLIGHT = ['preparing', 'running', 'stopping'];
const ERROR_STATUS = ['failed', 'unknown'];
const TERMINAL = ['completed', 'failed', 'stopped', 'unknown', 'history'];

// sectionType → 阶段角色；无法归类（stream / complete / 空值）返回 null，不伪造节点。
export function stageRoleOf(sectionType) {
  if (typeof sectionType !== 'string') return null;
  const prefix = sectionType.trim().toLowerCase().split('_')[0];
  if (!prefix) return null;
  return ROLE_BY_PREFIX[prefix] || null;
}

// 毫秒 → mm:ss（计时只做展示，取值来自 run 状态的 startedAt / 注入时钟）。
export function formatClock(ms) {
  const total = Number.isFinite(ms) && ms > 0 ? Math.floor(ms / 1000) : 0;
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return String(minutes).padStart(2, '0') + ':' + String(seconds).padStart(2, '0');
}

function toEntries(stages) {
  const list = Array.isArray(stages) ? stages : [];
  const entries = [];
  for (const item of list) {
    if (!item || typeof item !== 'object') continue;
    const stage = item.stage && typeof item.stage === 'object' ? item.stage : item;
    const role = stageRoleOf(stage.sectionType);
    if (!role) continue;
    const at = Number.isFinite(item.at) ? item.at : (Number.isFinite(stage.at) ? stage.at : null);
    entries.push({ role, at, stage });
  }
  return entries;
}

/**
 * 从 run 状态派生四节点视图。
 * @param {{ status?: string, stages?: Array, updatedAt?: number|null }} input
 * @returns {{ status: string, total: number, reached: number, current: string|null, currentIndex: number,
 *            activeIndex: number, inFlight: boolean, failed: boolean, progressLabel: string,
 *            nodes: Array<{ role: string, label: string, index: number, state: 'done'|'active'|'pending'|'error',
 *                            count: number, detail: string, output: string, durationMs: number|null, duration: string }> }}
 */
export function deriveRunStages({ status = 'idle', stages = [], updatedAt = null } = {}) {
  const entries = toEntries(stages);

  // 同一阶段可能有多条事件（多轮/多步），保留首末时间与最后一条输出。
  const byRole = new Map();
  for (const entry of entries) {
    const record = byRole.get(entry.role);
    if (!record) {
      byRole.set(entry.role, { firstAt: entry.at, lastAt: entry.at, stage: entry.stage, count: 1 });
      continue;
    }
    record.count += 1;
    record.stage = entry.stage;
    if (entry.at != null) {
      if (record.firstAt == null) record.firstAt = entry.at;
      record.lastAt = entry.at;
    }
  }

  const inFlight = IN_FLIGHT.includes(status);
  const isError = ERROR_STATUS.includes(status);
  const terminal = TERMINAL.includes(status);
  const lastEntry = entries.length ? entries[entries.length - 1] : null;
  const lastRole = lastEntry ? lastEntry.role : null;
  const reachedRoles = RUN_STAGES.map(item => item.role).filter(role => byRole.has(role));
  const indices = RUN_STAGES.map(item => item.role);
  // 终态没有后续事件，用 updatedAt 收尾；in-flight 时保持 null（不假装已结束）。
  const fallbackEnd = terminal && Number.isFinite(updatedAt)
    ? updatedAt
    : (lastEntry && lastEntry.at != null ? lastEntry.at : null);

  const nodes = RUN_STAGES.map((meta, index) => {
    const record = byRole.get(meta.role);
    let state = 'pending';
    if (record) {
      if (isError && meta.role === lastRole) state = 'error';
      else if (inFlight && meta.role === lastRole) state = 'active';
      else state = 'done';
    }
    const nextRole = reachedRoles.find(role => indices.indexOf(role) > index);
    const nextRecord = nextRole ? byRole.get(nextRole) : null;
    const start = record ? record.firstAt : null;
    const end = nextRecord ? nextRecord.firstAt : (record ? fallbackEnd : null);
    const durationMs = start != null && end != null && end >= start ? end - start : null;
    return {
      role: meta.role,
      label: meta.label,
      index,
      state,
      count: record ? record.count : 0,
      detail: record ? (sectionLabels[record.stage.sectionType] || record.stage.label || meta.label) : meta.label,
      // 归一化后的 stage 用 content；历史里的原始 entity 用 sectionContent。
      output: record ? String(record.stage.content || record.stage.sectionContent || '') : '',
      durationMs,
      duration: durationMs != null ? formatClock(durationMs) : ''
    };
  });

  return {
    status,
    total: STAGE_TOTAL,
    reached: reachedRoles.length,
    current: lastRole,
    currentIndex: lastRole ? indices.indexOf(lastRole) : -1,
    activeIndex: inFlight && lastRole ? indices.indexOf(lastRole) : -1,
    inFlight,
    failed: isError,
    progressLabel: reachedRoles.length + '/' + STAGE_TOTAL,
    nodes
  };
}
