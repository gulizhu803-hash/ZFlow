<script setup>
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue';
import { marked } from 'marked';
import DOMPurify from 'dompurify';
import Icon from '../components/Icon.vue';
import Modal from '../components/Modal.vue';
import { state, isDemo, nav, notify, report } from '../lib/workspace.js';
import { post, stream } from '../lib/api.js';
import { parseJSON } from '../lib/protocol.js';
import { strategies, sectionLabels } from '../lib/demo.js';
import { createConversationAdapter } from '../../ui-v2-p0/src/conversation-adapter.js';
import { deriveUi } from '../../ui-v2-p0/src/run-state.js';
import { useRunStore } from '../lib/use-run-store.js';
const work = computed(() => state.route.page === 'work'), type = computed(() => work.value ? 'work' : 'chat');
const messages = ref([]), events = ref([]), input = ref(state.draft), activeId = ref(''), loading = ref(false);
state.draft = '';
const agentId = ref(state.route.params.agent || state.agents[0]?.agentId || ''), clientId = ref(state.clients[0]?.clientId || ''), ragTag = ref(state.route.params.rag || ''), mcpIds = ref([]);
const optionsOpen = ref(false), maxRound = ref(3), maxRetry = ref(2), maxPace = ref(5), scrollPane = ref(), rename = ref(null), deleteTarget = ref(null), sessionTitle = ref('');
const sessions = computed(() => state.sessions.filter(s => s.sessionType === type.value));
const selectedAgent = computed(() => state.agents.find(a => a.agentId === agentId.value));
const maxMessages = computed(() => work.value ? 3 : 20), userCount = computed(() => messages.value.filter(m => m.messageRole === 'user').length);

// —— P0 集成：run 归模块级 store 所有，页面只做视图派生。 ——
const { store, runState } = useRunStore();
let historyRequest = 0, alive = true;
// 当前视图来源：实时 run（liveRunActive）还是历史加载（historyViewStatus）。
const liveRunActive = ref(false);
// store 里的 run 只在「当前会话」或「新会话尚未绑定」时可见。
// 避免切到会话 B 时，把会话 A 的运行状态/Stop 按钮显示在 B 上。
const storeRunVisible = computed(() => {
  const s = runState.value;
  if (!s.runId) return false;
  if (!activeId.value) return true;
  return !s.sessionId || s.sessionId === activeId.value;
});
const historyViewStatus = ref('idle');
const historyError = ref('');
// store 状态 → 模板既有的 pill CSS class（不改样式，复用既有 class）。
const STORE_TO_PILL = { idle: 'idle', preparing: 'running', running: 'running', stopping: 'stopped', stopped: 'stopped', completed: 'complete', failed: 'failed', unknown: 'unknown' };
const busy = computed(() => storeRunVisible.value && deriveUi(runState.value).showPill);
const canSend = computed(() => input.value.trim() && deriveUi(runState.value).canSend && !loading.value && userCount.value < maxMessages.value && (work.value ? selectedAgent.value : clientId.value));
const status = computed(() => storeRunVisible.value ? (STORE_TO_PILL[runState.value.status] || 'idle') : historyViewStatus.value);
const statusText = computed(() => {
  if (storeRunVisible.value) {
    const s = runState.value.status;
    switch (s) {
      case 'preparing': return '准备中';
      case 'running': return '正在执行';
      case 'stopping': return '正在停止';
      case 'stopped': return '已停止接收';
      case 'completed': return isDemo.value ? '演示完成' : work.value ? '执行流已完成' : '回复完成';
      case 'failed': return '执行失败';
      case 'unknown': return '连接已中断 · 状态未知';
      default: return '等待开始';
    }
  }
  return ({ idle: '等待开始', history: '历史记录', unknown: '连接已中断 · 状态未知' })[historyViewStatus.value] || '等待开始';
});
const errorText = computed(() => {
  if (historyError.value) return historyError.value;
  if (!storeRunVisible.value) return '';
  const s = runState.value.status;
  if (s === 'failed') return runState.value.error || '执行失败';
  if (s === 'unknown') {
    if (runState.value.error === 'EOF without complete') return '连接已结束，但未收到完成事件。请查看会话历史确认执行状态。';
    if (runState.value.error === 'invalid stage payload') return '收到无法解析的任务阶段，执行状态未知，请查看历史记录。';
    return ''; // 网络中断等：交给模板的 unknown 提示条。
  }
  return '';
});
const html = content => DOMPurify.sanitize(marked.parse(content || '', { breaks: true }), { FORBID_TAGS: ['img', 'iframe', 'style', 'input'] });
function scrollBottom() { nextTick(() => { if (scrollPane.value) scrollPane.value.scrollTop = scrollPane.value.scrollHeight; }); }
function rememberDemo() { if (isDemo.value && activeId.value) { state.demoHistory[activeId.value] = messages.value.map(m => ({ ...m })); state.demoEvents[activeId.value] = events.value.map(e => ({ ...e })); } }
function pause(ms, signal) { return new Promise((resolve, reject) => { const cancel = () => { clearTimeout(timer); reject(new DOMException('Aborted', 'AbortError')); }; const timer = setTimeout(() => { signal.removeEventListener('abort', cancel); resolve(); }, ms); signal.addEventListener('abort', cancel, { once: true }); if (signal.aborted) cancel(); }); }

// —— 真实 transport：只在 prepare/stream*/loadHistory 里碰 post/stream，其余逻辑走 adapter + store。 ——
const transport = {
  async prepare({ mode, agentId, clientId, prompt, signal }) {
    if (!isDemo.value) await post('/ai/armory', { json: { armoryType: mode, armoryId: mode === 'work' ? agentId : clientId }, signal });
    if (!activeId.value) {
      const session = isDemo.value
        ? { sessionId: 'demo-' + Date.now(), sessionTitle: prompt.slice(0, 30), sessionType: mode, createTime: new Date().toISOString().slice(0, 19) }
        : await post('/session/insert', { query: { sessionTitle: prompt.slice(0, 30), sessionType: mode }, signal });
      activeId.value = session.sessionId; state.sessions.unshift(session);
      return { sessionId: session.sessionId };
    }
    return { sessionId: activeId.value };
  },
  async streamChat({ clientId, sessionId, prompt, ragTag, mcpIds, signal }, onEvent) {
    if (isDemo.value) {
      const text = '很高兴和你一起探索这个想法。\n\n我们可以先把「' + prompt + '」拆解成几个清晰的小问题，再逐步深入。你可以通过下方的知识库和工具设置，给 AI 更多背景。\n\n> 这是演示回复，未调用模型。登录后，你的真实 AI 伙伴会在这里回答。';
      for (let i = 0; i < text.length; i += 5) { await pause(35, signal); onEvent({ data: text.slice(i, i + 5) }); }
      return;
    }
    await stream('/ai/chat/stream', { clientId, sessionId, userMessage: prompt, mcpIdList: mcpIds, ...(ragTag ? { ragTag } : {}) }, onEvent, signal);
  },
  async streamWork({ agentId, sessionId, prompt, maxRound, maxRetry, maxPace, signal }, onEvent) {
    if (isDemo.value) {
      const stageTypes = ({ step: ['inspector_mcp', 'planner_step', 'runner_result', 'replier_overview'], loop: ['analyzer_demand', 'analyzer_strategy', 'performer_result', 'summarizer_overview'], react: ['observer_demand', 'reasoner_action', 'actor_result', 'evaluator_overview'] })[selectedAgent.value?.agentType] || ['inspector_mcp', 'planner_step', 'runner_result', 'replier_overview'];
      let stageIndex = 0;
      for (const entity of [
        { clientType: 'INSPECTOR', sectionType: 'inspector_mcp', sectionContent: '演示：识别任务所需的检索与资料阅读能力。' },
        { clientType: 'PLANNER', sectionType: 'planner_step', sectionContent: '1. 明确目标与输出格式\n2. 收集和整理相关信息\n3. 形成可执行的结论' },
        { clientType: 'RUNNER', sectionType: 'runner_result', sectionContent: '演示：阶段信息会在此实时展开，帮助你了解智能体正在做什么。' },
        { clientType: 'REPLIER', sectionType: 'replier_overview', sectionContent: '### 让目标更清晰一点\n\n你提出了：**' + prompt.replace(/[<>]/g, '') + '**\n\n一个好的开始是明确三件事：\n\n1. **期望成果**：最终希望获得报告、方案，还是可执行的步骤？\n2. **已知信息**：可以补充背景，或关联自己的知识库。\n3. **完成标准**：定义范围与约束，让智能体知道何时完成。\n\n> 以上为界面演示内容，未实际检索或调用模型。登录后可以执行真实任务。' }
      ]) { entity.sectionType = stageTypes[stageIndex++]; entity.clientType = entity.sectionType.split('_')[0].toUpperCase(); await pause(650, signal); onEvent({ event: 'message', data: JSON.stringify(entity) }); }
      onEvent({ event: 'complete', data: '{"clientType":"complete","sectionContent":"执行完成"}' });
      return;
    }
    await stream('/ai/work/execute', { agentId, agentDesc: selectedAgent.value?.agentDesc, sessionId, userMessage: prompt, maxRound, maxRetry, maxPace }, onEvent, signal);
  },
  async loadHistory({ mode, sessionId }) {
    if (isDemo.value) {
      const saved = state.demoHistory[sessionId];
      return {
        messages: saved ? saved.map(m => ({ ...m })) : [
          { messageRole: 'user', messageContent: state.sessions.find(s => s.sessionId === sessionId)?.sessionTitle || '开始一个新想法', messageSeq: 1 },
          { messageRole: 'assistant', messageContent: '这是一个**示例会话**。\n\n在这里，你可以继续讨论想法、整理资料，或查看智能体的执行过程。登录并连接服务后，这里会展示你的真实会话记录。', messageSeq: 2 }
        ],
        stages: state.demoEvents[sessionId] || []
      };
    }
    const [history, stages] = await Promise.all([post('/session/message/' + (mode === 'work' ? 'work-answer' : 'chat'), { query: { sessionId } }), mode === 'work' ? post('/session/message/work-sse', { query: { sessionId } }) : Promise.resolve([])]);
    return { messages: history || [], stages: (stages || []).map(m => { try { return parseJSON(m.messageContent); } catch { return { sectionContent: m.messageContent, sectionType: '历史记录' }; } }) };
  }
};

const adapter = createConversationAdapter({
  store,
  transport,
  parse: parseJSON,
  onUserMessage: m => {
    if (!alive || !liveRunActive.value) return;
    messages.value.push({ messageRole: 'user', messageContent: m.content, messageSeq: messages.value.length + 1 });
    if (!work.value) messages.value.push({ messageRole: 'assistant', messageContent: '', messageSeq: messages.value.length + 1 });
    input.value = ''; scrollBottom();
  },
  onToken: data => {
    if (!alive || !liveRunActive.value) return;
    const last = messages.value[messages.value.length - 1];
    if (last && last.messageRole === 'assistant') { last.messageContent += data; scrollBottom(); }
  },
  onStage: stage => {
    if (!alive || !liveRunActive.value) return;
    const entity = stage.raw && typeof stage.raw === 'object' ? stage.raw : { sectionType: stage.sectionType, clientType: stage.clientType, round: stage.round, step: stage.step, sectionContent: stage.content };
    events.value.push(entity);
    if (['summarizer_overview', 'replier_overview', 'evaluator_overview'].includes(entity.sectionType)) messages.value.push({ messageRole: 'assistant', messageContent: entity.sectionContent, messageSeq: messages.value.length + 1 });
    scrollBottom();
  }
});

// demo 会话在 run 进入终态时落盘，保证切走再切回能看到演示历史。
watch(runState, (next, prev) => {
  if (isDemo.value && activeId.value && ['completed', 'failed', 'stopped', 'unknown'].includes(next.status) && prev && ['preparing', 'running', 'stopping'].includes(prev.status)) rememberDemo();
});
// 页面重建后 run 可能仍在 store 里；终态时重新拉一次历史，避免“已完成但没有答案”。
watch(() => runState.value.status, status => {
  if (!liveRunActive.value && activeId.value && ['completed', 'failed', 'stopped', 'unknown'].includes(status)) {
    loadSession(activeId.value);
  }
});

function stop() {
  // 只停止当前会话的 run；切到别的会话时不要把 A 的 Stop 显示/作用到 B。
  if (!storeRunVisible.value) return;
  const runId = runState.value.runId;
  if (runId) store.stop(runId);
  else adapter.stop();
}
function resetRunIfNotActive() {
  const s = runState.value.status;
  if (s !== 'preparing' && s !== 'running' && s !== 'stopping') store.reset();
}
async function loadSession(id) {
  const ticket = ++historyRequest;
  activeId.value = id || ''; messages.value = []; events.value = []; historyError.value = ''; loading.value = true;
  liveRunActive.value = false; historyViewStatus.value = 'idle';
  if (!id) { resetRunIfNotActive(); loading.value = false; return; }
  try {
    const result = await adapter.loadHistory({ mode: type.value, sessionId: id });
    if (ticket !== historyRequest || !alive) return;
    messages.value = result.messages || [];
    events.value = result.stages || [];
    historyViewStatus.value = result.status || 'idle';
    resetRunIfNotActive();
    scrollBottom();
  } catch (error) {
    if (ticket === historyRequest && alive) historyError.value = error.message;
  } finally {
    if (ticket === historyRequest) loading.value = false;
  }
}
watch(() => state.route.params.session, id => loadSession(id), { immediate: true });
watch(() => state.route.params.agent, id => { if (id) agentId.value = id; });
watch(() => state.clients, list => { if (!list.some(c => c.clientId === clientId.value)) clientId.value = list[0]?.clientId || ''; });
watch(() => state.agents, list => { if (!list.some(a => a.agentId === agentId.value)) agentId.value = list[0]?.agentId || ''; });
function chooseSession(session) { nav(type.value, { session: session.sessionId, agent: agentId.value }); }
function newSession() { if (!state.route.params.session) loadSession(); else nav(type.value, { agent: agentId.value }); }
async function send() {
  if (!canSend.value) return;
  if (work.value && ![maxRound.value, maxRetry.value, maxPace.value].every(n => Number.isInteger(n) && n >= 1)) { notify('执行参数必须为正整数', 'info'); return; }
  if (!activeId.value && sessions.value.length >= 3) { notify('每种类型最多保留 3 个会话，请选择已有会话或删除一个旧会话', 'info'); return; }
  const prompt = input.value.trim();
  liveRunActive.value = true; historyError.value = '';
  try {
    await adapter.send({ mode: type.value, prompt, sessionId: activeId.value || undefined, agentId: agentId.value, clientId: clientId.value, ragTag: ragTag.value, mcpIds: mcpIds.value, maxRound: maxRound.value, maxRetry: maxRetry.value, maxPace: maxPace.value });
  } catch (error) {
    // store 已写入终态（failed/stopped/unknown），错误展示由 errorText computed 从 store 派生。
  }
}
async function saveTitle() { try { const target = rename.value; if (!sessionTitle.value.trim()) return; if (!isDemo.value) await post('/session/update', { query: { sessionId: target.sessionId, sessionTitle: sessionTitle.value.trim() } }); target.sessionTitle = sessionTitle.value.trim(); rename.value = null; notify('会话名称已更新'); } catch (error) { report(error); } }
async function removeSession() { try { const id = deleteTarget.value.sessionId; if (!isDemo.value) await post('/session/delete', { query: { sessionId: id } }); state.sessions = state.sessions.filter(s => s.sessionId !== id); deleteTarget.value = null; if (activeId.value === id) { nav(type.value); loadSession(); } notify('会话已删除'); } catch (error) { report(error); } }
async function copy(text) { try { await navigator.clipboard.writeText(text); notify('已复制回复'); } catch { notify('无法访问剪贴板，请选择文本复制', 'info'); } }
onBeforeUnmount(() => { alive = false; historyRequest++; });
</script>
<template>
  <div class="conversation-layout">
    <aside class="conversation-sidebar"><div class="row spread"><h2>{{ work ? '任务记录' : '我的对话' }}</h2><button class="icon-button" aria-label="新建会话" @click="newSession"><Icon name="plus" :size="18" /></button></div><button class="secondary-button full" @click="newSession"><Icon name="plus" :size="16" />{{ work ? '开始新任务' : '开启新对话' }}</button><span class="conversation-section-label">最近 · {{ sessions.length }} / 3</span><div class="history-list"><div v-for="session in sessions" :key="session.sessionId" class="history-item" :class="{ active: activeId === session.sessionId }"><button class="history-title" @click="chooseSession(session)"><Icon :name="work ? 'workflow' : 'chat'" :size="15" /><span>{{ session.sessionTitle }}</span></button><div class="history-actions"><button class="icon-button" aria-label="重命名会话" @click="rename = session; sessionTitle = session.sessionTitle"><Icon name="pen" :size="13" /></button><button class="icon-button" aria-label="删除会话" @click="deleteTarget = session"><Icon name="trash" :size="13" /></button></div></div></div><div class="conversation-tip"><Icon name="sparkles" :size="18" /><p>{{ work ? '把目标说清楚，让每一步都有方向。' : '好问题，是一切好想法的开始。' }}</p></div></aside>
    <section class="conversation-main"><header class="conversation-header"><div class="row"><span class="quick-icon sage"><Icon :name="work ? 'sparkles' : 'chat'" :size="19" /></span><div><h1>{{ work ? '和智能体一起，把想法落地' : '给每一个好奇，留一点空间' }}</h1><p>{{ work ? '目标、行动与结果，清晰可见。' : '聊想法，读资料，找到新的可能。' }}</p></div></div><span v-if="isDemo" class="tag sage">演示体验</span></header>
      <div class="mobile-conversation-tools"><select :value="activeId" aria-label="切换会话" @change="nav(type, { session: $event.target.value, agent: agentId })"><option value="">新会话</option><option v-for="session in sessions" :key="session.sessionId" :value="session.sessionId">{{ session.sessionTitle }}</option></select><button class="text-button" @click="newSession"><Icon name="plus" :size="16" />新会话</button></div><div ref="scrollPane" class="conversation-scroll" aria-live="polite" aria-relevant="additions text"><div v-if="loading" class="loading-placeholder">正在加载会话…</div><div v-else-if="!messages.length" class="conversation-welcome"><span class="welcome-flower">✳</span><span class="eyebrow">A LITTLE CURIOSITY GOES A LONG WAY</span><h2>{{ work ? '你的下一个目标，是什么？' : '今天，想从哪里开始？' }}</h2><p>{{ work ? '研究一个问题，打磨一篇文章，或探索一个项目。' : '让零散的想法在对话中，慢慢变得清晰。' }}</p><div class="prompt-suggestions"><button v-for="suggestion in (work ? ['研究 AI Agent 的产品设计趋势', '为我的新项目整理一份行动计划', '分析并优化这篇文章的结构'] : ['帮我拆解一个复杂的概念', '一起头脑风暴下一个好点子', '如何更高效地整理学习笔记？'])" :key="suggestion" @click="input = suggestion"><Icon :name="work ? 'workflow' : 'chat'" :size="17" />{{ suggestion }}<Icon name="arrow" :size="16" /></button></div></div>
        <div v-if="work && events.length" class="execution-log"><div class="row spread"><h3><Icon name="workflow" :size="17" />执行过程 <span>{{ events.length }} 个阶段</span></h3><span class="status-pill" :class="status">{{ statusText }}</span></div><details v-for="(event, index) in events" :key="index" :open="index === events.length - 1 && busy"><summary><span class="stage-number">{{ String(index + 1).padStart(2, '0') }}</span><span>{{ sectionLabels[event.sectionType] || event.sectionType || event.clientType || '阶段输出' }}</span><small v-if="event.round">第 {{ event.round }} 轮</small><small v-if="event.step">步骤 {{ event.step }}</small><Icon name="down" :size="14" /></summary><pre>{{ event.sectionContent }}</pre></details></div>
        <div v-if="messages.length && !loading" class="message-list"><article v-for="(message, index) in messages" :key="activeId + ':' + (message.messageType || type) + ':' + (message.messageSeq || index)" class="message" :class="message.messageRole"><span class="message-avatar"><Icon v-if="message.messageRole !== 'user'" name="sparkles" :size="18" /><template v-else>{{ state.auth?.userName?.slice(0,1) || '我' }}</template></span><div class="message-content"><div class="message-author">{{ message.messageRole === 'user' ? '你' : 'ZFlow' }}<span v-if="message.messageRole !== 'user' && isDemo" class="tag sage">演示回复</span></div><div v-if="message.messageRole === 'user'" class="user-content">{{ message.messageContent }}</div><div v-else-if="message.messageContent" class="markdown" v-html="html(message.messageContent)"></div><span v-else class="typing-indicator">正在思考<span>•••</span></span><button v-if="message.messageRole !== 'user' && message.messageContent" class="icon-button" aria-label="复制回复" @click="copy(message.messageContent)"><Icon name="copy" :size="14" /></button></div></article></div>
        <div v-if="errorText" class="form-error conversation-error" role="alert">{{ errorText }}</div><div v-if="status === 'unknown' && !errorText" class="info-note warning conversation-error">已断开前端连接，后台任务可能继续执行。可以重新打开会话查看历史，请勿重复提交同一任务。</div>
      </div>
      <div class="conversation-compose"><div v-if="optionsOpen" class="compose-options"><template v-if="work"><label v-for="(_, i) in 3" :key="i">{{ ['最大轮次', '最大重试', '最大步数'][i] }}<input v-if="i === 0" v-model.number="maxRound" type="number" min="1" :disabled="busy" /><input v-else-if="i === 1" v-model.number="maxRetry" type="number" min="1" :disabled="busy" /><input v-else v-model.number="maxPace" type="number" min="1" :disabled="busy" /></label></template><template v-else><label>关联知识库<select v-model="ragTag" :disabled="busy"><option value="">不使用知识库</option><option v-for="rag in state.rags" :key="rag.ragTag" :value="rag.ragTag">{{ rag.ragTag }}</option></select></label><div class="tool-checkboxes"><label v-for="mcp in state.mcps" :key="mcp.mcpId" class="checkbox-line"><input v-model="mcpIds" type="checkbox" :value="mcp.mcpId" :disabled="busy" />{{ mcp.mcpName }}</label><span v-if="!state.mcps.length">暂无可用工具</span></div></template></div>
        <div v-if="userCount >= maxMessages" class="info-note warning">本会话已达到 {{ maxMessages }} 条用户消息，请创建或切换会话。</div><form class="chat-composer" @submit.prevent="send"><textarea v-model="input" :placeholder="work ? '描述你的目标，剩下的我们一起完成…' : '把你的想法写在这里…'" aria-label="消息内容" rows="3" :disabled="busy || loading || userCount >= maxMessages" @keydown.enter.exact="event => { if (!event.isComposing) { event.preventDefault(); send(); } }" /><div class="composer-bottom"><div class="row"><select v-if="work" v-model="agentId" class="composer-select" aria-label="选择执行智能体" :disabled="busy"><option v-if="!state.agents.length" value="">暂无可用智能体</option><option v-for="agent in state.agents" :key="agent.agentId" :value="agent.agentId">{{ agent.agentName }}</option></select><select v-else v-model="clientId" class="composer-select" aria-label="选择对话客户端" :disabled="busy"><option v-if="!state.clients.length" value="">暂无可用对话客户端</option><option v-for="client in state.clients" :key="client.clientId" :value="client.clientId">{{ client.clientName }} · {{ client.modelName }}</option></select><button type="button" class="icon-button" aria-label="会话选项" :aria-expanded="optionsOpen" @click="optionsOpen = !optionsOpen"><Icon name="settings" :size="17" /></button></div><button v-if="busy" type="button" class="send-button" aria-label="停止接收" @click="stop"><Icon name="stop" :size="15" /></button><button v-else class="send-button" type="submit" aria-label="发送消息" :disabled="!canSend || (work && [maxRound,maxRetry,maxPace].some(n => !Number.isInteger(n) || n < 1))"><Icon name="up" :size="19" /></button></div></form><div class="composer-hint"><span>{{ isDemo ? '当前为界面演示，登录后使用真实 AI 服务。' : work ? '断开连接不会保证停止后台任务。' : 'AI 可能会出错，请核实重要信息。' }}</span><span>Enter 发送 · Shift Enter 换行</span></div>
      </div>
    </section>
  </div>
  <Modal v-if="rename" title="给会话一个新名字" @close="rename = null"><form class="modal-body form-stack" @submit.prevent="saveTitle"><label>会话名称<input v-model="sessionTitle" required maxlength="80" /></label><button class="primary-button">保存名称</button></form></Modal>
  <Modal v-if="deleteTarget" title="删除这个会话？" @close="deleteTarget = null"><div class="modal-body form-stack"><p class="muted small">「{{ deleteTarget.sessionTitle }}」及其消息将被删除，此操作无法恢复。</p><div class="form-actions"><button class="secondary-button" @click="deleteTarget = null">保留会话</button><button class="danger-button" @click="removeSession">确认删除</button></div></div></Modal>
</template>
