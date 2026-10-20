import { reactive, computed } from 'vue';
import { sampleContent, templates, typeInfo } from './artifacts.js';

const STORAGE_KEY = 'zflow-next-workspace-v1';
const empty = () => ({ sessions: [], artifacts: [], agents: [], sources: [], preferences: { name: '我的工作空间', compact: false } });
let initial = empty(), loadError = '';
try {
  const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null');
  if (saved && ['sessions', 'artifacts', 'agents', 'sources'].every(key => Array.isArray(saved[key]))) initial = { ...initial, ...saved, preferences: { ...initial.preferences, ...saved.preferences } };
} catch { loadError = '本地数据无法读取。现有存储已保留，请先导出备份。'; }
export const data = reactive(initial);
export const ui = reactive({ page: 'home', id: '', mobile: false, search: false, help: false, toast: null, pending: null, draft: '' });
const clone = value => JSON.parse(JSON.stringify(value));
const id = () => crypto.randomUUID();
let toastTimer, runTimer;
export function notify(message, error = false) { clearTimeout(toastTimer); ui.toast = { message, error }; toastTimer = setTimeout(() => ui.toast = null, 4300); }
if (loadError) notify(loadError, true);
export function commit(update) {
  const next = clone(data); update(next);
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify(next)); Object.assign(data, next); return true; }
  catch { notify('本地存储空间不足，修改尚未保存。请导出作品后再试。', true); return false; }
}
export function readRoute() { const [page, routeId] = location.hash.replace(/^#\/?/, '').split('/'); ui.page = page || 'home'; ui.id = routeId || ''; ui.mobile = false; }
export function go(page, routeId = '') { location.hash = `/${page}${routeId ? '/' + routeId : ''}`; ui.mobile = false; }
export const recentSessions = computed(() => [...data.sessions].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)));
export const currentSession = computed(() => data.sessions.find(s => s.id === ui.id));
export const currentArtifact = computed(() => data.artifacts.find(a => a.id === currentSession.value?.artifactId));
export function begin({ prompt, type = 'chat', templateId, attachments = [], sourceIds = [] }) {
  if (ui.pending) { notify('还有一个示例正在准备，请稍候。'); return; }
  if (!prompt?.trim()) return;
  const template = templates.find(t => t.id === templateId);
  const sessionId = id(), now = new Date().toISOString();
  const title = template?.title || prompt.trim().slice(0, 48);
  const session = { id: sessionId, title, type, status: 'preparing', artifactId: null, updatedAt: now, messages: [{ id: id(), role: 'user', text: prompt.trim(), attachments: attachments.map(a => a.name), sourceIds }] };
  if (!commit(d => d.sessions.unshift(session))) return;
  ui.pending = { sessionId, type }; go('workspace', sessionId);
  runTimer = setTimeout(() => {
    const artifactId = type === 'chat' ? null : id();
    const content = type === 'chat' ? null : sampleContent(type);
    const result = commit(d => {
      const target = d.sessions.find(s => s.id === sessionId); if (!target) return;
      target.status = 'ready'; target.artifactId = artifactId;
      target.messages.push({ id: id(), role: 'assistant', text: type === 'chat' ? '可以从目标、输入资料和交付形式三个方面梳理这项工作。\n\n1. 目标：你希望解决什么具体问题？\n2. 资料：有哪些需求说明或已有文档？\n3. 交付：需要一张图、一份文档，还是任务清单？\n\n你也可以在下方选择作品类型，体验完整的编辑流程。', example: true });
      if (artifactId) {
        target.messages[target.messages.length - 1].text = `已打开${typeInfo[type].label}示例，你可以在右侧直接编辑。\n\n${type === 'diagram' ? '示例将系统拆成应用入口、服务网关、业务服务和数据存储四个层次。点击节点可以修改名称。' : type === 'document' ? '示例包含项目目标、模块职责、数据一致性与实施计划。点击「编辑」可以修改全文。' : '示例按任务、模块、优先级和状态组织。点击「编辑」可以修改单元格。'}\n\n保存后，作品会出现在文件库，并保留每次修改的版本。`;
        d.artifacts.unshift({ id: artifactId, sessionId, title, type, example: true, updatedAt: now, content, revisions: [{ version: 1, date: now, content: clone(content), title }] });
      }
    });
    ui.pending = null;
    if (!result) notify('示例准备完成，但本地保存失败，请清理存储后重试。', true);
  }, 1200);
}
export function stopPreview() { if (!ui.pending) return; clearTimeout(runTimer); const pendingId = ui.pending.sessionId; if (commit(d => { const s = d.sessions.find(s => s.id === pendingId); if (s) s.status = 'cancelled'; })) { ui.pending = null; notify('已停止准备示例'); } }
export function reply(sessionId, text) {
  if (!text.trim()) return;
  commit(d => { const s = d.sessions.find(s => s.id === sessionId); if (!s) return; s.updatedAt = new Date().toISOString(); s.messages.push({ id: id(), role: 'user', text }, { id: id(), role: 'assistant', text: '你的修改意见已保留在这段对话中。当前为设计预览，不会调用模型；你可以通过作品上的「编辑」手动修改内容并保存新版本。', example: true }); });
}
export function saveArtifact(artifactId, content, title) {
  const now = new Date().toISOString();
  const saved = commit(d => {
    const a = d.artifacts.find(a => a.id === artifactId); if (!a) return;
    a.content = clone(content); a.title = title.trim() || a.title; a.updatedAt = now;
    a.revisions.push({ version: a.revisions.length + 1, date: now, content: clone(content), title: a.title });
    const s = d.sessions.find(s => s.id === a.sessionId); if (s) { s.title = a.title; s.updatedAt = now; }
  });
  if (saved) notify('已保存到本地，并保留新版本'); return saved;
}
export function removeArtifact(artifactId) { const saved = commit(d => { const a = d.artifacts.find(a => a.id === artifactId); d.artifacts = d.artifacts.filter(a => a.id !== artifactId); if (a) d.sessions = d.sessions.filter(s => s.id !== a.sessionId); }); if (saved) notify('作品已删除'); }
export function formatTime(value) { const date = new Date(value); return new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(date); }
export function addAgent(agent) { return commit(d => d.agents.push({ ...agent, id: id() })); }
export function exportWorkspace() {
  const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })); const link = document.createElement('a'); link.href = url; link.download = 'zflow-next-workspace.json'; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
}
