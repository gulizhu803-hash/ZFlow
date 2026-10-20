import { reactive, computed } from 'vue';
import { post, readAuth, saveAuth } from './api.js';
import { demoAgents, demoClients, demoModels, demoMcps, demoRags, demoSessions, demoPlaza } from './demo.js';
import { clearOwnerDrafts } from './office-drafts.js';
const clone = value => JSON.parse(JSON.stringify(value));
export const state = reactive({
  auth: readAuth(), loading: false, loadError: '', toast: null, authOpen: false, searchOpen: false, helpOpen: false,
  repo: { self: [], fork: [], favor: [] }, clients: [], agents: [], models: [], mcps: [], rags: [], sessions: [], officeSessions: [],
  plaza: clone(demoPlaza), demoHistory: {}, demoEvents: {}, demoTasks: [], draft: '', route: { page: 'home', params: {} }
});
export const isDemo = computed(() => !state.auth);
export const ownerId = auth => String(auth?.userId ?? auth?.userName ?? '');
export const recentSessions = computed(() => {
  const items = new Map();
  for (const s of state.sessions) if (['chat', 'work', 'office'].includes(s.sessionType)) items.set(s.sessionId, { id: s.sessionId, title: s.sessionTitle, type: s.sessionType, createdAt: s.createTime });
  for (const s of state.officeSessions) items.set(s.sessionId, { id: s.sessionId, title: s.title, type: 'office', createdAt: s.createdAt });
  return [...items.values()].sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)));
});
function readOfficeResume() {
  try {
    const value = JSON.parse(sessionStorage.getItem('zflow.office.resume'));
    return value?.owner && value.route?.page === 'office' && value.route.params && typeof value.route.params === 'object' ? value : null;
  } catch { return null; }
}
function clearOfficeResume() { try { sessionStorage.removeItem('zflow.office.resume'); } catch {} }
let routeGuard = null, authSuspend = null, routeTicket = 0, acceptedHash = window.location.hash || '#/home', resumeAuth = readOfficeResume();
export function registerOfficeGuards(guard, suspend) {
  routeGuard = guard; authSuspend = suspend;
  return () => { if (routeGuard === guard) { routeGuard = null; authSuspend = null; } };
}
function hashFor(page, params = {}) {
  const query = new URLSearchParams(Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ''));
  return '#/' + page + (query.size ? '?' + query : '');
}
export function replaceRoute(page, params) {
  acceptedHash = hashFor(page, params); history.replaceState(null, '', acceptedHash);
  state.route = { page, params: Object.fromEntries(new URLSearchParams(acceptedHash.split('?')[1])) };
}
export function notify(message, type = 'success') {
  state.toast = { message, type, id: Date.now() };
  const id = state.toast.id;
  setTimeout(() => { if (state.toast?.id === id) state.toast = null; }, 5000);
}
export function report(error) { if (error.name !== 'AbortError') notify(error.message || '操作未完成，请稍后重试', 'error'); }
export async function nav(page, params = {}) {
  const hash = hashFor(page, params), ticket = ++routeTicket;
  if (routeGuard && !(await routeGuard({ page, params }))) return false;
  if (ticket !== routeTicket) return false;
  history.pushState(null, '', hash); acceptedHash = hash; state.route = { page, params }; return true;
}
export async function readRoute() {
  const hash = window.location.hash || '#/home', ticket = ++routeTicket;
  const [path, search] = window.location.hash.replace(/^#\/?/, '').split('?');
  const next = { page: path || 'home', params: Object.fromEntries(new URLSearchParams(search)) };
  if (routeGuard && !(await routeGuard(next))) { if (ticket === routeTicket) history.replaceState(null, '', acceptedHash); return; }
  if (ticket !== routeTicket) return;
  if (state.route.page !== (path || 'home')) window.scrollTo(0, 0);
  acceptedHash = hash; state.route = next;
}
export function needAccount() { if (isDemo.value) { state.authOpen = true; return false; } return true; }
let generation = 0;
export async function refresh() {
  const ticket = ++generation;
  state.loadError = '';
  if (isDemo.value) {
    state.repo = { self: clone(demoAgents), fork: [], favor: [] }; state.agents = clone(demoAgents);
    state.clients = clone(demoClients); state.models = clone(demoModels); state.mcps = clone(demoMcps); state.rags = clone(demoRags); state.sessions = clone(demoSessions); state.loading = false;
    return;
  }
  state.loading = true;
  const resources = [['repo', '/workspace/repo/map'], ['clients', '/user/query/chat-client'], ['agents', '/user/query/work-agent'], ['models', '/user/query/model'], ['mcps', '/user/query/mcp'], ['rags', '/user/query/rag'], ['sessions', '/session/list']];
  const result = await Promise.allSettled(resources.map(([, path]) => post(path)));
  if (ticket !== generation || isDemo.value) return;
  const failed = [];
  result.forEach((entry, index) => {
    if (entry.status === 'fulfilled') state[resources[index][0]] = entry.value || (index === 0 ? { self: [], fork: [], favor: [] } : []);
    else failed.push(entry.reason.message);
  });
  state.loadError = [...new Set(failed)].join('；'); state.loading = false;
}
export async function signIn(auth) {
  const returning = resumeAuth && resumeAuth.owner === ownerId(auth) ? resumeAuth.route : null;
  if (resumeAuth && resumeAuth.owner !== ownerId(auth)) { try { clearOwnerDrafts(sessionStorage, resumeAuth.owner); sessionStorage.removeItem('zflow.office.submit.' + resumeAuth.owner); } catch {} }
  resumeAuth = null; clearOfficeResume();
  saveAuth(auth); state.auth = auth; state.authOpen = false;
  state.repo = { self: [], fork: [], favor: [] };
  for (const key of ['clients', 'agents', 'models', 'mcps', 'rags', 'sessions']) state[key] = [];
  state.officeSessions = []; state.demoHistory = {}; state.demoEvents = {}; state.draft = '';
  replaceRoute(returning?.page || (state.route.page === 'office' ? 'office' : 'home'), returning?.params || {}); await refresh();
}
export async function signOut() {
  if (routeGuard && !(await routeGuard({ page: 'home', params: {} }))) return;
  const owner = ownerId(state.auth); authSuspend?.();
  try { clearOwnerDrafts(sessionStorage, owner); sessionStorage.removeItem('zflow.office.submit.' + owner); } catch {}
  resumeAuth = null; clearOfficeResume(); generation++; saveAuth(null); state.auth = null; state.officeSessions = []; state.demoHistory = {}; state.demoEvents = {}; state.draft = ''; replaceRoute('home', {}); refresh();
}
export function launch(agent) {
  if (agent.repoType === 'favor' || !agent.agentId) { nav('agents'); notify('请先将模板复制到工作区，再选择可执行的智能体', 'info'); return; }
  nav('work', { agent: agent.agentId });
}
window.addEventListener('miniagent:unauthorized', () => {
  if (!state.auth) return;
  resumeAuth = { owner: ownerId(state.auth), route: { page: state.route.page, params: { ...state.route.params } } };
  try { if (state.route.page === 'office') sessionStorage.setItem('zflow.office.resume', JSON.stringify(resumeAuth)); } catch {}
  authSuspend?.(); generation++; saveAuth(null); state.auth = null; state.officeSessions = []; state.sessions = [];
  state.repo = { self: [], fork: [], favor: [] }; for (const key of ['clients', 'agents', 'models', 'mcps', 'rags']) state[key] = [];
  state.draft = ''; state.authOpen = true; state.loading = false; state.loadError = '';
});
