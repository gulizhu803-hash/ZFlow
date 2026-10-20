import { parseJSON } from './protocol.js';
export const terminal = status => ['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(status);
export const statusLabel = run => ({ QUEUED: '排队中', CANCEL_REQUESTED: '正在取消，结果尚未发布', SUCCEEDED: '已生成', FAILED: '生成未完成', CANCELLED: '已取消' })[run?.status] || ({ GENERATING: '正在生成图表', VALIDATING: '正在检查图表结构', SAVING: '正在保存生成结果' })[run?.stage] || '正在执行';
const events = new Set(['run.queued', 'run.started', 'step.started', 'step.completed', 'artifact.ready', 'run.cancel_requested', 'run.completed', 'run.failed', 'run.cancelled']);
export function eventState(snapshot) { return { run: { ...snapshot }, snapshotSeq: Number(snapshot.lastEventSeq || 0), confirmedSeq: 0, timeline: [] }; }
export function applySnapshot(state, snapshot) {
  if (snapshot.runId !== state.run.runId || Number(snapshot.lastEventSeq) < state.snapshotSeq) return;
  if (terminal(state.run.status) && !terminal(snapshot.status)) return;
  state.run = { ...snapshot }; state.snapshotSeq = Number(snapshot.lastEventSeq || 0);
}
export function reduceOfficeEvent(state, event) {
  let data; try { data = typeof event.data === 'string' ? parseJSON(event.data) : event.data; } catch { return 'protocol'; }
  if (!data || data.schemaVersion !== 1 || data.runId !== state.run.runId || !Number.isSafeInteger(data.seq) || data.seq < 1 || String(data.seq) !== event.id || !events.has(event.event)) return 'protocol';
  if (data.seq <= state.confirmedSeq) return 'duplicate';
  if (data.seq !== state.confirmedSeq + 1) return 'gap';
  if (event.event === 'artifact.ready' && (!data.artifactId || !Number.isInteger(data.version) || data.version < 1)) return 'protocol';
  if (data.status && !['QUEUED', 'RUNNING', 'CANCEL_REQUESTED', 'SUCCEEDED', 'FAILED', 'CANCELLED'].includes(data.status)) return 'protocol';
  if (data.seq > state.snapshotSeq && !terminal(state.run.status)) {
    if (data.status) state.run.status = data.status;
    if (data.stage) state.run.stage = data.stage;
    if (event.event === 'artifact.ready') {
      const refs = state.run.artifacts || [];
      state.run.artifacts = [...refs.filter(a => a.artifactId !== data.artifactId || a.version !== data.version), { artifactId: data.artifactId, version: data.version, title: data.title, type: data.type }];
    }
  }
  state.timeline.push({ ...data, event: event.event });
  state.confirmedSeq = data.seq;
  return 'applied';
}
