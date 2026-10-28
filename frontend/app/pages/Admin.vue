<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import Icon from '../components/Icon.vue';
import Modal from '../components/Modal.vue';
import schema from '../lib/admin-schema.json';
import { state, notify, report } from '../lib/workspace.js';
import { post } from '../lib/api.js';
const allowed = computed(() => state.auth?.userRole === 'admin');
const resources = {
  api: { name: '模型服务', id: ['apiId'], columns: ['apiId', 'apiBaseUrl', 'apiCompletionsPath'] },
  model: { name: '模型', id: ['modelId'], columns: ['modelId', 'modelName', 'modelType', 'apiId'] },
  mcp: { name: 'MCP 工具', id: ['mcpId'], columns: ['mcpId', 'mcpName', 'mcpType', 'mcpDesc'] },
  advisor: { name: '顾问', id: ['advisorId'], columns: ['advisorId', 'advisorName', 'advisorType'] },
  prompt: { name: '提示词', id: ['promptId'], columns: ['promptId', 'promptName'] },
  client: { name: '客户端', id: ['clientId'], columns: ['clientId', 'clientName', 'modelName', 'clientStatus'], status: 'clientStatus' },
  agent: { name: '智能体', id: ['agentId'], columns: ['agentId', 'agentName', 'agentType', 'agentStatus'], status: 'agentStatus' },
  user: { name: '用户', id: ['userName'], columns: ['userName', 'userRole', 'userStatus'], status: 'userStatus' },
  task: { name: '定时任务', id: ['taskId'], columns: ['taskId', 'taskDesc', 'taskCron', 'taskStatus'], status: 'taskStatus' },
  config: { name: '客户端关联', id: ['clientId', 'configType', 'configValue'], columns: ['clientId', 'configType', 'configValue', 'configStatus'], status: 'configStatus' },
  flow: { name: '执行流程', id: ['agentId', 'clientId'], columns: ['agentId', 'clientId', 'clientRole', 'flowSeq'] },
  template: { name: '模板', id: ['templateId'], columns: ['templateId', 'agentName', 'agentType', 'modelName'] },
  plaza: { name: '广场内容', id: ['plazaId'], columns: ['plazaId', 'plazaTitle', 'agentName', 'userName'] },
  session: { name: '用户会话', id: ['sessionId'], columns: ['sessionId', 'sessionTitle', 'userName', 'sessionType'] }
};
const tab = ref('dashboard'), data = ref(null), items = ref([]), total = ref(0), page = ref(1), keyword = ref(''), scope = ref(''), loading = ref(false), error = ref('');
const dialog = ref(''), target = ref(null), form = reactive({}), busy = ref(false), dicts = reactive({}), history = ref([]);
const fields = computed(() => (schema[tab.value] || []).filter(f => !f.key.startsWith('origin')));
const resource = computed(() => resources[tab.value]);
const label = key => (schema[tab.value]?.find(f => f.key === key)?.label.split(/[；（]/)[0]) || ({ userName: '用户名', sessionId: '会话标识', sessionTitle: '会话名称', sessionType: '类型' })[key] || key;
const longText = key => /Prompt|Param|Desc|snapshot/i.test(key);
const sensitive = key => /key|secret|password/i.test(key);
let generation = 0;
const flowDicts = reactive({ agentId: [], clientId: [] });
const flowDictStatus = reactive({ agentId: '', clientId: '' });
const flowDictError = ref('');
const scopeFilter = ref('');
const dictFilter = reactive({ agentId: '', clientId: '' });
const hasDict = key => flowDictStatus[key] === 'ready' && flowDicts[key].length > 0;
function dictOptions(key, current) {
  const keyword = (dictFilter[key] || '').trim().toLowerCase(), list = flowDicts[key] || [];
  const options = (keyword ? list.filter(option => option.text.toLowerCase().includes(keyword) || option.value.toLowerCase().includes(keyword)) : list).slice();
  if (current && !options.some(option => option.value === current)) options.unshift({ value: current, text: current + '（当前值）' });
  return options;
}
function chooseAgent(event) { if (event?.target) scope.value = event.target.value; page.value = 1; load(); }
// 字典按需加载：仅在进入「执行流程」页时拉取，失败时降级为手动输入标识。
async function loadFlowDicts() {
  if (!allowed.value) return;
  if (flowDictStatus.agentId === 'ready' && flowDictStatus.clientId === 'ready') return;
  if (flowDictStatus.agentId === 'loading' || flowDictStatus.clientId === 'loading') return;
  flowDictError.value = ''; flowDictStatus.agentId = 'loading'; flowDictStatus.clientId = 'loading';
  const failures = [];
  try {
    const agents = await post('/admin/agent/list', { json: {} }) || [];
    flowDicts.agentId = agents.filter(agent => agent?.agentId).map(agent => ({ value: agent.agentId, text: [agent.agentName, agent.agentType].filter(Boolean).join(' · ') || agent.agentId }));
    flowDictStatus.agentId = 'ready';
  } catch (e) { flowDictStatus.agentId = 'failed'; failures.push('智能体字典加载失败：' + e.message); report(e); }
  try {
    const clients = await post('/admin/flow/client') || [];
    flowDicts.clientId = clients.filter(client => client?.clientId).map(client => ({ value: client.clientId, text: [client.clientName || client.client?.clientName, client.clientId].filter(Boolean).join(' · ') }));
    flowDictStatus.clientId = 'ready';
  } catch (e) { flowDictStatus.clientId = 'failed'; failures.push('客户端字典加载失败：' + e.message); report(e); }
  flowDictError.value = failures.join('；');
}
async function load() {
  if (!allowed.value) return;
  const ticket = ++generation; loading.value = true; error.value = '';
  try {
    let result;
    if (tab.value === 'dashboard') { result = await post('/admin/dashboard'); if (ticket === generation) data.value = result; }
    else {
      if (tab.value === 'session') result = await post('/admin/session/list');
      else if (tab.value === 'config') { const map = await post('/admin/config/list', { json: scope.value ? { keyword: scope.value } : {} }); result = Object.values(map || {}).flat(); }
      else if (tab.value === 'flow') result = scope.value ? await post('/admin/flow/agent', { query: { agentId: scope.value } }) : [];
      else result = await post('/admin/' + tab.value + '/page', { json: { keyword: keyword.value, pageNum: page.value, pageSize: 10 } });
      if (ticket !== generation) return;
      items.value = Array.isArray(result) ? result : result?.list || []; total.value = Array.isArray(result) ? result.length : result?.total || 0;
    }
  } catch (e) { if (ticket === generation) error.value = e.message; } finally { if (ticket === generation) loading.value = false; }
}
function switchTab(key) { tab.value = key; page.value = 1; keyword.value = ''; scope.value = ''; scopeFilter.value = ''; items.value = []; if (key === 'flow') loadFlowDicts(); load(); }
function open(item = null) {
  target.value = item; Object.keys(form).forEach(k => delete form[k]);
  for (const field of fields.value) { let value = item?.[field.key]; if (field.key === 'systenPrompt') value = item?.systemPrompt; form[field.key] = value != null ? String(value) : field.key.endsWith('Status') ? '1' : ''; }
  error.value = ''; dictFilter.agentId = ''; dictFilter.clientId = ''; dialog.value = 'edit';
}
function keyQuery(item) { return Object.fromEntries(resource.value.id.map(key => [key, item[key]])); }
async function save() {
  if (busy.value) return; busy.value = true; error.value = '';
  try {
    const json = {};
    for (const field of fields.value) {
      const value = form[field.key];
      if (field.required && !String(value).trim()) throw new Error('请填写：' + label(field.key));
      if (field.type.startsWith('number')) { if (value === '') { if (!field.required) continue; } if (!/^-?\d+$/.test(value)) throw new Error(label(field.key) + '必须为整数'); if (field.min && BigInt(value) < BigInt(field.min)) throw new Error(label(field.key) + '不能小于 ' + field.min); if (field.max && BigInt(value) > BigInt(field.max)) throw new Error(label(field.key) + '不能大于 ' + field.max); json[field.key] = Number.isSafeInteger(Number(value)) ? Number(value) : BigInt(value); }
      else if (value !== '' || field.required) json[field.key] = value;
    }
    if (target.value) for (const field of schema[tab.value].filter(f => f.key.startsWith('origin'))) { const original = field.key.slice(6); const key = original[0].toLowerCase() + original.slice(1); json[field.key] = target.value[key]; }
    if (tab.value === 'task') { if (json.taskCron.trim().split(/\s+/).length !== 6) throw new Error('定时规则需包含六个字段'); const params = JSON.parse(json.taskParam); if (!params.userMessage?.trim() || !['maxRound','maxRetry','maxPace'].every(key => Number.isInteger(params[key]) && params[key] >= 1)) throw new Error('任务参数需要 userMessage 和三个正整数执行参数'); }
    await post('/admin/' + tab.value + '/' + (target.value ? 'update' : 'insert'), { json });
    if (tab.value === 'flow') { scope.value = json.agentId; scopeFilter.value = ''; }
    dialog.value = ''; Object.keys(form).forEach(k => delete form[k]); notify('管理配置已保存'); await load();
  } catch (e) { error.value = e.message; } finally { busy.value = false; }
}
async function remove() { if (busy.value) return; busy.value = true; try { await post('/admin/' + tab.value + '/delete', { query: keyQuery(target.value) }); dialog.value = ''; notify('记录已删除'); await load(); } catch (e) { error.value = e.message; } finally { busy.value = false; } }
async function toggle(item) { if (busy.value) return; busy.value = true; try { const field = resource.value.status; await post('/admin/' + tab.value + '/toggle', { query: { ...keyQuery(item), [field]: item[field] === 1 ? 0 : 1 } }); await load(); notify('状态已更新'); } catch (e) { report(e); } finally { busy.value = false; } }
async function showHistory(item) { target.value = item; dialog.value = 'history'; history.value = []; try { history.value = await post('/session/message/' + (item.sessionType === 'work' ? 'work-answer' : 'chat'), { query: { sessionId: item.sessionId } }) || []; } catch (e) { report(e); } }
const graph = computed(() => { const points = data.value?.graphInfo?.messageLastWeek || []; const max = Math.max(1, ...points.map(p => p.count || 0)); return points.map((p, i) => ({ ...p, height: Math.max(2, (p.count || 0) / max * 120) })); });
onMounted(async () => { if (!allowed.value) return; load(); const keys = ['clientType', 'agentType', 'configType', 'clientRole', 'userRole', 'apiId', 'modelId']; const results = await Promise.allSettled(keys.map(key => post('/admin/list/' + key))); results.forEach((result, i) => { if (result.status === 'fulfilled') dicts[keys[i]] = result.value || []; }); });
</script>
<template>
  <div v-if="!allowed" class="empty-state"><Icon name="shield" :size="42" /><h1>这里需要管理员权限</h1><p>请使用管理员账号登录后进入管理中心。</p><button v-if="!state.auth" class="primary-button" @click="state.authOpen = true">登录账号</button></div>
  <div v-else class="page admin-page"><header class="page-heading"><div><div class="eyebrow">A CLEAR VIEW OF EVERYTHING</div><h1>管理中心</h1><p>照顾系统运行的每一环，让协作保持顺畅。</p></div><button class="secondary-button" @click="load"><Icon name="refresh" :size="16" />刷新</button></header><div class="admin-tabs"><button :class="{ active: tab === 'dashboard' }" @click="switchTab('dashboard')">概览</button><button v-for="(r, key) in resources" :key="key" :class="{ active: tab === key }" @click="switchTab(key)">{{ r.name }}</button></div><div v-if="error && !dialog" class="form-error">{{ error }}</div><div v-if="loading" class="loading-placeholder">正在读取管理数据…</div><template v-else-if="tab === 'dashboard'"><div class="admin-stat-grid"><article v-for="(title, key) in { userCount: '用户', agentCount: '智能体', modelCount: '模型', sessionCount: '会话', messageCount: '消息', taskCount: '定时任务' }" :key="key"><span>{{ title }}</span><strong>{{ data?.countInfo?.[key] ?? '—' }}</strong><Icon name="layers" :size="18" /></article></div><div class="paper-panel"><h2>最近 7 天的消息</h2><div v-if="graph.length" class="activity-bars"><div v-for="point in graph" :key="point.date"><span>{{ point.count }}</span><i :style="{ height: point.height + 'px' }"></i><small>{{ point.date }}</small></div></div><div v-else class="empty-inline">暂无消息数据</div></div></template><template v-else><div class="toolbar"><h2 class="subheading">{{ resource.name }} <span class="count">{{ total }}</span></h2><div class="row"><form v-if="tab !== 'session'" class="search-field" @submit.prevent="page = 1; load()"><Icon name="search" :size="16" /><template v-if="tab === 'flow'"><input v-if="hasDict('agentId')" v-model="scopeFilter" placeholder="搜索智能体名称或 ID" aria-label="筛选智能体" /><select v-if="hasDict('agentId')" v-model="scope" class="filter-select" aria-label="选择智能体" @change="chooseAgent($event)"><option value="">全部智能体</option><option v-for="option in dictOptions('agentId', scope)" :key="option.value" :value="option.value">{{ option.text }}</option></select><input v-else v-model="scope" placeholder="输入智能体 ID 查看流程" aria-label="关联标识" /></template><input v-else-if="tab === 'config'" v-model="scope" placeholder="筛选客户端 ID" aria-label="关联标识" /><input v-else v-model="keyword" placeholder="输入关键词" aria-label="筛选管理记录" /><button class="text-button">查询</button></form><button v-if="tab !== 'session'" class="primary-button" @click="open()"><Icon name="plus" :size="16" />新增</button></div></div><div v-if="tab === 'flow' && flowDictError" class="form-error">{{ flowDictError }}；下拉已降级为手动输入</div><div class="data-table-wrap"><table class="data-table"><thead><tr><th v-for="key in resource.columns" :key="key">{{ label(key) }}</th><th>操作</th></tr></thead><tbody><tr v-for="(item, index) in items" :key="index"><td v-for="key in resource.columns" :key="key"><span v-if="key === resource.status" class="status-pill">{{ item[key] === 1 ? '启用' : '禁用' }}</span><span v-else>{{ String(item[key] ?? '—').slice(0, 90) }}</span></td><td><div class="actions"><template v-if="tab !== 'session'"><button class="icon-button" aria-label="编辑记录" @click="open(item)"><Icon name="pen" :size="16" /></button><button v-if="resource.status" class="icon-button" aria-label="切换启用状态" :disabled="busy" @click="toggle(item)"><Icon :name="item[resource.status] === 1 ? 'stop' : 'play'" :size="16" /></button><button class="icon-button" aria-label="删除记录" @click="target = item; dialog = 'delete'; error = ''"><Icon name="trash" :size="16" /></button></template><button v-else class="text-button" @click="showHistory(item)">查看消息</button></div></td></tr></tbody></table><div v-if="!items.length" class="empty-inline">{{ tab === 'flow' && !scope ? '选择一个智能体，查看它的执行流程。' : '暂无符合条件的记录。' }}</div></div><div v-if="total > 10 && !['session','config','flow'].includes(tab)" class="pagination"><button :disabled="page <= 1" @click="page--; load()">上一页</button><span>{{ page }} / {{ Math.ceil(total / 10) }}</span><button :disabled="page * 10 >= total" @click="page++; load()">下一页</button></div></template></div>
  <Modal v-if="dialog === 'edit'" :title="(target ? '编辑' : '新增') + resource.name" wide @close="!busy && (dialog = '')"><form class="modal-body form-stack" @submit.prevent="save"><div v-if="target && tab === 'client'" class="info-note">客户端列表不返回类型和执行角色，请根据现有配置补全后保存。</div><div v-if="tab === 'flow' && flowDictError" class="info-note warning">{{ flowDictError }}；可手动输入标识后保存。</div><div class="form-grid"><label v-for="field in fields" :key="field.key" :class="{ 'span-all': longText(field.key) }">{{ label(field.key) }}{{ field.required ? ' *' : '' }}<template v-if="tab === 'flow' && ['agentId', 'clientId'].includes(field.key)"><template v-if="hasDict(field.key)"><input v-model="dictFilter[field.key]" placeholder="搜索名称或 ID" aria-label="筛选下拉选项" /><select v-model="form[field.key]" :required="field.required"><option value="">请选择</option><option v-for="option in dictOptions(field.key, form[field.key])" :key="option.value" :value="option.value">{{ option.text }}</option></select></template><input v-else v-model="form[field.key]" :required="field.required" autocomplete="off" placeholder="手动输入" /></template><select v-else-if="dicts[field.key]?.length" v-model="form[field.key]" :required="field.required"><option value="">请选择</option><option v-for="value in dicts[field.key]" :key="value" :value="value">{{ value }}</option></select><select v-else-if="field.key.endsWith('Status')" v-model="form[field.key]"><option value="1">启用</option><option value="0">禁用</option></select><textarea v-else-if="longText(field.key)" v-model="form[field.key]" :required="field.required" rows="5" spellcheck="false" /><input v-else v-model="form[field.key]" :type="sensitive(field.key) ? 'password' : 'text'" :inputmode="field.type.startsWith('number') ? 'numeric' : undefined" :required="field.required" autocomplete="off" /><small>{{ field.key }}</small></label></div><div v-if="error" class="form-error">{{ error }}</div><div class="form-actions"><button type="button" class="secondary-button" :disabled="busy" @click="dialog = ''">取消</button><button class="primary-button" :disabled="busy">{{ busy ? '保存中…' : '保存配置' }}</button></div></form></Modal>
  <Modal v-if="dialog === 'delete'" title="确认删除这条记录？" @close="!busy && (dialog = '')"><div class="modal-body form-stack"><p class="small muted">此操作无法恢复。若其他资源依赖该记录，服务端可能拒绝删除。</p><div v-if="error" class="form-error">{{ error }}</div><button class="danger-button" :disabled="busy" @click="remove">确认删除</button></div></Modal>
  <Modal v-if="dialog === 'history'" :title="target.sessionTitle" wide @close="dialog = ''"><div class="modal-body admin-history"><article v-for="(message, i) in history" :key="i"><strong>{{ message.messageRole }}</strong><pre>{{ message.messageContent }}</pre></article><p v-if="!history.length" class="muted small">暂无消息</p></div></Modal>
</template>
