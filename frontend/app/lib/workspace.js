import { reactive, computed } from 'vue';
import { post, readAuth, saveAuth } from './api.js';
import { demoAgents, demoClients, demoModels, demoMcps, demoRags, demoSessions, demoPlaza } from './demo.js';
const clone = value => JSON.parse(JSON.stringify(value));
export const state = reactive({
  auth: readAuth(), loading: false, loadError: '', toast: null, authOpen: false, searchOpen: false, helpOpen: false,
  repo: { self: [], fork: [], favor: [] }, clients: [], agents: [], models: [], mcps: [], rags: [], sessions: [],
  plaza: clone(demoPlaza), demoHistory: {}, demoEvents: {}, demoTasks: [], draft: '', route: { page: 'home', params: {} }
});
export const isDemo = computed(() => !state.auth);
export function notify(message, type = 'success') {
  state.toast = { message, type, id: Date.now() };
  const id = state.toast.id;
  setTimeout(() => { if (state.toast?.id === id) state.toast = null; }, 5000);
}
export function report(error) { if (error.name !== 'AbortError') notify(error.message || '操作未完成，请稍后重试', 'error'); }
export function nav(page, params = {}) {
  const query = new URLSearchParams(Object.entries(params).filter(([, v]) => v !== undefined));
  window.location.hash = '/' + page + (query.size ? '?' + query : '');
}
export function readRoute() {
  const [path, search] = window.location.hash.replace(/^#\/?/, '').split('?');
  if (state.route.page !== (path || 'home')) window.scrollTo(0, 0);
  state.route = { page: path || 'home', params: Object.fromEntries(new URLSearchParams(search)) };
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
  saveAuth(auth); state.auth = auth; state.authOpen = false;
  state.repo = { self: [], fork: [], favor: [] };
  for (const key of ['clients', 'agents', 'models', 'mcps', 'rags', 'sessions']) state[key] = [];
  state.demoHistory = {}; state.demoEvents = {}; state.draft = ''; nav('home'); await refresh();
}
export function signOut() { generation++; saveAuth(null); state.auth = null; state.demoHistory = {}; state.demoEvents = {}; state.draft = ''; nav('home'); refresh(); }
export function launch(agent) {
  if (agent.repoType === 'favor' || !agent.agentId) { nav('agents'); notify('请先将模板复制到工作区，再选择可执行的智能体', 'info'); return; }
  nav('work', { agent: agent.agentId });
}
window.addEventListener('miniagent:unauthorized', () => { signOut(); state.authOpen = true; });
