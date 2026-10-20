<script setup>
import { computed, ref, watch, onBeforeUnmount, onMounted } from 'vue';
import Icon from '../components/Icon.vue';
import Modal from '../components/Modal.vue';
import OfficeDrawer from '../components/office/OfficeDrawer.vue';
import DrawioBridge from '../components/office/DrawioBridge.vue';
import GenerationReceipt from '../components/office/GenerationReceipt.vue';
import ValidationPanel from '../components/office/ValidationPanel.vue';
import { state, ownerId, nav, registerOfficeGuards, notify } from '../lib/workspace.js';
import { officeApi as api, downloadBlob, downloadName, safeFilename } from '../lib/office-api.js';
import { terminal, statusLabel } from '../lib/office-events.js';
import { useOfficeWorkspace } from '../composables/useOfficeWorkspace.js';
import { useArtifactDraft } from '../composables/useArtifactDraft.js';
import '../styles/office.css';

const owner = ownerId(state.auth), editor = ref(), editing = ref(false), editingXml = ref(''), editorReady = ref(false), editorGeneration = ref(0);
const collapsed = ref(false), fullscreen = ref(false), panel = ref('request'), drawer = ref(''), newSession = ref(false), sessionTitle = ref(''), prompt = ref(''), title = ref(''), diagramType = ref('ARCHITECTURE');
const detached = ref(null), detachedBusy = ref(false), leaveOpen = ref(false), leaveBusy = ref(false), saveBusy = ref(false), downloading = ref(false), leaveError = ref('');
const recovery = ref(null), draftDownloadError = ref('');
let leaveResolve, active = true, previewEpoch = 0;
const ws = useOfficeWorkspace(api, () => !editing.value), v = ws.view;
const drafts = useArtifactDraft(api, owner, async result => {
  if (!active || owner !== ownerId(state.auth)) return;
  if (!draft.dirty) editor.value?.saved();
  notify(`v${result.version} 已保存${draft.dirty ? '，还有未保存修改' : ''}`);
  try {
    const detail = await api.getArtifact(result.artifactId);
    if (!active || owner !== ownerId(state.auth) || v.artifact?.artifactId !== result.artifactId) return;
    v.artifact = detail;
    if (detail.revision.version === result.version) v.revision = detail.revision;
    else draft.currentVersion = detail.currentVersion;
  } catch (e) { v.notice = `v${result.version} 已保存，最新版本信息暂未刷新：${e.message}`; }
});
const draft = drafts.draft;
const currentRun = computed(() => ws.stream.state.model?.run || v.run);
const activeRun = computed(() => currentRun.value && !terminal(currentRun.value.status));
const saveLabel = computed(() => ({ saving: '正在保存…', outcomeUnknown: '确认保存结果', conflict: '版本冲突', invalid: '校验未通过' })[draft.saveState] || '保存新版本');
const displayedVersion = computed(() => editing.value ? draft.baseVersion : v.revision?.version);
const latestVersion = computed(() => Math.max(v.artifact?.currentVersion || 0, editing.value ? draft.currentVersion || 0 : 0));
const isLatest = computed(() => displayedVersion.value === latestVersion.value);
const hasWork = () => editing.value && (draft.dirty || draft.pending || draft.saveState === 'saving');
const types = [['ARCHITECTURE', '架构图'], ['FLOWCHART', '流程图'], ['SEQUENCE', '时序图'], ['ER', 'ER 图']];
const examples = [
  { label: '订单系统架构', type: 'ARCHITECTURE', prompt: '画一个订单系统架构图，包含用户、订单服务、MySQL 和 Redis，标注主要数据流。' },
  { label: '请假审批流程', type: 'FLOWCHART', prompt: '画一个请假审批流程图：员工提交申请，主管审批，通过后通知人事，驳回后允许修改并重新提交。' },
  { label: '下单时序', type: 'SEQUENCE', prompt: '画一个下单时序图，包含用户、订单服务、库存服务和支付服务，说明创建订单、扣减库存及支付确认的交互。' }
];
const time = value => value ? new Date(value).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }) : '未取得';
const exactTime = value => value ? `${value} · ${new Date(value).toLocaleString('zh-CN', { timeZoneName: 'short' })}` : '未取得';
async function copyTrace(value) {
  try { await navigator.clipboard.writeText(value); notify('问题编号已复制'); }
  catch { notify('复制未完成，请选择问题编号手动复制。', 'info'); }
}
const sameRoute = next => next?.page === 'office' && ['session', 'run', 'artifact', 'version'].every(k => String(next.params[k] || '') === String(state.route.params[k] || ''));
async function protect(next) {
  if (sameRoute(next) || !hasWork()) return true;
  if (leaveResolve) return false;
  leaveError.value = ''; leaveOpen.value = true;
  return new Promise(resolve => { leaveResolve = resolve; });
}
function resolveLeave(allowed) { leaveOpen.value = false; const resolve = leaveResolve; leaveResolve = null; resolve?.(allowed); }
async function leave(action) {
  if (leaveBusy.value) return;
  if (action === 'stay') { resolveLeave(false); return; }
  leaveBusy.value = true; leaveError.value = '';
  try {
    if (action === 'save' && (!(await save()) || draft.dirty || draft.pending)) { leaveError.value = '保存尚未确认，修改仍保留在当前页面。'; return; }
    if (action === 'download') await localDraft();
    if (action === 'discard') drafts.discard(); else drafts.persist();
    editing.value = false; resolveLeave(true);
  } catch (e) { leaveError.value = e.message; }
  finally { leaveBusy.value = false; }
}
const unregister = registerOfficeGuards(protect, () => { drafts.suspend(); ws.suspend(); resolveLeave(false); });
async function save() {
  if (!editing.value || saveBusy.value) return false;
  saveBusy.value = true;
  try { return await drafts.save(() => editor.value.requestXml()); }
  finally { saveBusy.value = false; }
}
async function localDraft() {
  draftDownloadError.value = '';
  try {
    const snapshot = await drafts.capture(() => editor.value.requestXml());
    downloadBlob(new Blob([snapshot.xml], { type: 'application/xml' }), safeFilename(v.artifact?.title) + '-未保存草稿.drawio');
  } catch (e) { draftDownloadError.value = e.message; throw e; }
}
function downloadCapturedDraft() {
  if (draft.xml) downloadBlob(new Blob([draft.xml], { type: 'application/xml' }), safeFilename(v.artifact?.title) + '-上次捕获草稿.drawio');
}
async function beginEdit() {
  if (!v.artifact || !v.revision || !v.xml) return;
  if (!isLatest.value) { await selectArtifact(v.artifact.artifactId); return; }
  if (window.matchMedia('(max-width: 700px)').matches) { v.notice = '请在桌面端编辑。此设备可以预览、生成和下载图表。'; return; }
  const recovered = drafts.restore(v.artifact.artifactId);
  drafts.begin(v.artifact.artifactId, v.revision.version, v.xml, recovered);
  draftDownloadError.value = ''; recovery.value = null;
  editingXml.value = draft.xml; editing.value = true; editorGeneration.value++; editorReady.value = false; collapsed.value = true; panel.value = 'canvas';
}
async function closeEditor() { if (!(await protect())) return; editing.value = false; fullscreen.value = false; if (v.artifact) await nav('office', { session: v.session.sessionId, artifact: v.artifact.artifactId, version: draft.baseVersion }); }
async function selectArtifact(id, version) {
  drawer.value = ''; await nav('office', { session: v.session.sessionId, artifact: id, ...(version ? { version } : {}) });
}
async function selectRun(run) { drawer.value = ''; await nav('office', { session: v.session.sessionId, run: run.runId }); }
async function previewVersion(version) {
  if (!editing.value) { await selectArtifact(v.artifact.artifactId, version); return; }
  const ticket = ++previewEpoch, id = v.artifact.artifactId; detachedBusy.value = true; drawer.value = '';
  try {
    const detail = await api.getArtifact(id); let revision = detail.revision;
    if (version && revision.version !== version) {
      let page = 1, count = 0, total = 1; revision = null;
      while (!revision && count < total) { const data = await api.listRevisions(id, { page, pageSize: 100 }); revision = data.items.find(r => r.version === version); count += data.items.length; total = data.total; page++; if (!data.items.length) break; }
    }
    if (!revision) throw new Error('该版本不存在或不可访问');
    const xml = await api.readContent(revision);
    if (active && ticket === previewEpoch && owner === ownerId(state.auth)) detached.value = { artifact: detail, revision, xml };
  } catch (e) { if (active && ticket === previewEpoch) ws.fail(e); } finally { if (ticket === previewEpoch) detachedBusy.value = false; }
}
async function download(revision = v.revision, artifact = v.artifact) {
  if (!revision || downloading.value) return; downloading.value = true; const account = ownerId(state.auth);
  try { const result = await api.downloadRevision(revision); if (active && account === ownerId(state.auth)) downloadBlob(result.blob, downloadName(result.disposition, `${artifact.title}-v${revision.version}.drawio`)); }
  catch (e) { ws.fail(e); } finally { downloading.value = false; }
}
async function generate(retry = false) {
  if (!state.auth) { state.authOpen = true; return; }
  if (!v.session || v.submitting || activeRun.value) return;
  if (hasWork() && !(await protect())) return;
  if (v.pending) { await ws.submit(); return; }
  const body = retry === true ? { ...v.run.request, sessionId: v.session.sessionId, retryOfRunId: v.run.runId } : { sessionId: v.session.sessionId, prompt: prompt.value.trim(), title: title.value.trim() || undefined, diagramType: diagramType.value, artifactTypes: ['DRAWIO'] };
  if (!body.prompt || body.prompt.length > 8000) return;
  await ws.submit(body);
}
function composerKey(e) { if ((e.ctrlKey || e.metaKey) && e.key === 'Enter' && !e.isComposing) { e.preventDefault(); generate(); } }
function key(e) {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's' && editing.value) { e.preventDefault(); if (!drawer.value && !detached.value && !leaveOpen.value && !newSession.value) save(); }
  if (e.key === 'Escape' && !drawer.value && !detached.value && !leaveOpen.value && !newSession.value) fullscreen.value = false;
}
function beforeUnload(e) { if (hasWork() || v.pending) { e.preventDefault(); e.returnValue = ''; } }
async function openDrawer(name) { drawer.value = name; if (name === 'sessions') await ws.listSessions(); if (name === 'files') await ws.listFiles(); if (name === 'revisions') await ws.listRevisions(); if (name === 'receipt') await ws.receipt(); }
async function createSession() {
  if (hasWork()) { newSession.value = false; if (!(await protect())) return; }
  const result = await ws.createSession(sessionTitle.value); if (result) { newSession.value = false; sessionTitle.value = ''; }
}
watch(() => [state.route.params.session, state.route.params.run, state.route.params.artifact, state.route.params.version].join('|'), async () => {
  if (!owner || state.route.page !== 'office') return;
  editing.value = false; fullscreen.value = false; editorReady.value = false; detached.value = null; recovery.value = null; previewEpoch++;
  await ws.load({ ...state.route.params });
  if (v.artifact) { panel.value = 'canvas'; recovery.value = drafts.restore(v.artifact.artifactId); }
}, { immediate: true });
onMounted(() => { window.addEventListener('keydown', key); window.addEventListener('beforeunload', beforeUnload); });
onBeforeUnmount(() => { active = false; previewEpoch++; drafts.suspend(); unregister(); resolveLeave(false); window.removeEventListener('keydown', key); window.removeEventListener('beforeunload', beforeUnload); });
</script>

<template>
  <div class="office-page" :class="{ 'office-fullscreen': fullscreen }">
    <header class="office-heading"><div class="office-heading-title"><span class="office-mark"><Icon name="workflow" :size="24" /></span><div><h1>智能办公</h1><p :title="v.session?.title">{{ v.session?.title || '把需求变成可以继续编辑的图表' }}</p></div></div><div class="office-actions"><button v-if="owner" class="secondary-button" @click="openDrawer('sessions')"><Icon name="list" :size="16" />切换会话</button><button class="primary-button" @click="owner ? newSession = true : state.authOpen = true"><Icon name="plus" :size="16" />新建会话</button></div></header>
    <div v-if="!owner" class="office-guest"><span class="office-large-icon"><Icon name="workflow" :size="42" /></span><h2>从一个需求，开始一张图</h2><p>生成架构图、流程图、时序图或 ER 图。<br />在画布上继续编辑，保存每一次修改，随时找回历史版本。</p><button class="primary-button" @click="state.authOpen = true">登录并开始 <Icon name="arrow" :size="16" /></button><span>登录后连接真实服务</span></div>
    <template v-else>
      <div v-if="v.error" class="office-alert" role="alert"><Icon name="alert" :size="18" /><span>{{ v.error }}<small v-if="v.errorTraceId" class="office-trace">问题编号：{{ v.errorTraceId }}</small></span><button v-if="v.errorTraceId" @click="copyTrace(v.errorTraceId)">复制问题编号</button><button @click="ws.load({ ...state.route.params })" :disabled="editing">重新读取</button><button aria-label="关闭错误提示" @click="v.error = ''"><Icon name="close" :size="16" /></button></div>
      <p v-if="v.notice" class="office-note" role="status">{{ v.notice }}</p>
      <nav class="office-panel-tabs" aria-label="办公面板"><button :class="{ active: panel === 'request' }" @click="panel = 'request'">需求与记录</button><button :class="{ active: panel === 'canvas' }" @click="panel = 'canvas'">图表工作区</button></nav>
      <div class="office-layout" :class="{ 'request-collapsed': collapsed, 'show-request': panel === 'request', 'show-canvas': panel === 'canvas' }">
        <aside class="office-request"><header><h2>需求与记录</h2><button class="icon-button" :disabled="v.listsBusy.runs" aria-label="刷新运行列表" @click="ws.listRuns()"><Icon name="refresh" :size="16" /></button></header>
          <div class="office-run-list"><p v-if="!v.session" class="office-muted">创建一个办公会话，开始生成图表。</p><p v-else-if="!v.runs.length" class="office-muted">这个会话还没有生成记录。</p><button v-for="run in v.runs" :key="run.runId" class="office-run-card" :class="{ selected: run.runId === v.run?.runId }" @click="selectRun(run)"><span><Icon name="workflow" :size="16" /><strong :title="run.title">{{ run.title || '未命名图表' }}</strong></span><small :title="exactTime(run.createdAt)">{{ statusLabel(run) }} · {{ time(run.createdAt) }}</small><em v-if="run.generationMode === 'fixture'">固定样例</em></button><button v-if="v.runs.length < v.runsTotal" class="secondary-button full" :disabled="v.listsBusy.runs" @click="ws.listRuns(true)">加载更多运行</button></div>
          <section v-if="currentRun" class="office-run-detail"><div class="office-live-state" role="status"><span :class="{ pulse: activeRun }"></span>{{ v.cancelling ? '正在请求取消' : statusLabel(currentRun) }}</div><p v-if="currentRun.generationMode === 'fixture'" class="office-muted">固定样例，未调用模型</p><details><summary>查看原始需求</summary><p>{{ v.run?.request?.prompt || '正在读取需求' }}</p></details><p v-if="currentRun.error" class="office-error-text">{{ currentRun.error.message }}</p><p v-if="ws.stream.state.warning" class="office-muted">{{ ws.stream.state.warning }}</p><p v-if="['offline', 'reconnecting'].includes(ws.stream.state.connection)" class="office-warning">连接中断，任务状态待确认</p><button class="text-button" @click="openDrawer('receipt')">查看生成记录 <Icon name="chevron" :size="14" /></button><button v-if="['FAILED', 'CANCELLED'].includes(currentRun.status)" class="secondary-button full" :disabled="v.submitting || !!v.pending" @click="generate(true)">按原需求重试</button></section>
          <form class="office-composer" @submit.prevent="generate"><label for="office-prompt">描述你想画的图</label><textarea id="office-prompt" v-model="prompt" maxlength="8000" rows="3" :disabled="!!v.pending" placeholder="例如：画一个订单系统架构图，标注服务、数据库和数据流…" @keydown="composerKey" /><div class="office-input-meta"><span>Ctrl / ⌘ + Enter 生成</span><span>{{ prompt.length }} / 8000</span></div><label class="sr-only" for="office-type">图表类型</label><select id="office-type" v-model="diagramType" :disabled="!!v.pending"><option v-for="[value, label] in types" :key="value" :value="value">{{ label }}</option></select><details class="office-title-field"><summary>设置文件标题（可选）</summary><input v-model="title" aria-label="文件标题" maxlength="200" :disabled="!!v.pending" placeholder="给这张图起个名字" /></details><p v-if="v.pending" class="office-warning">正在确认任务是否已提交。将用原需求确认，请勿重复创建。</p><button v-if="activeRun" type="button" class="secondary-button full" :disabled="v.cancelling || currentRun.status === 'CANCEL_REQUESTED'" @click="ws.cancel"><Icon name="stop" :size="16" />{{ currentRun.status === 'CANCEL_REQUESTED' ? '正在取消…' : '取消生成' }}</button><button v-else class="primary-button full" :disabled="!v.session || v.submitting || v.rateLimited || (!v.pending && !prompt.trim())"><Icon name="sparkles" :size="16" />{{ v.submitting ? '正在提交…' : v.rateLimited ? '服务繁忙，请稍候' : v.pending ? '确认提交结果' : '生成图表' }}</button></form>
        </aside>
        <section class="office-canvas" :aria-busy="v.busy"><header class="artifact-toolbar"><div class="artifact-heading"><button class="icon-button request-toggle" :aria-label="collapsed ? '展开需求栏' : '收起需求栏'" @click="collapsed = !collapsed"><Icon name="list" :size="18" /></button><div><h2 :title="v.artifact?.title">{{ v.artifact?.title || '图表工作区' }}</h2><p v-if="v.revision">v{{ displayedVersion }} · {{ editing ? (displayedVersion === 1 ? '生成' : '手动') : v.revision.origin === 'GENERATED' ? '生成' : '手动' }} · {{ isLatest ? '最新版本' : '历史版本' }}</p><p v-else>预览、编辑与版本，集中在这里</p></div></div><div class="office-actions"><button class="secondary-button" @click="openDrawer('files')" :disabled="!v.session"><Icon name="file" :size="16" />文件</button><template v-if="v.revision"><button class="secondary-button" @click="openDrawer('revisions')"><Icon name="clock" :size="16" />版本</button><button class="secondary-button" :disabled="downloading" @click="download()">下载 v{{ v.revision.version }}</button><button v-if="!editing" class="primary-button" :disabled="!v.xml || v.busy" @click="beginEdit"><Icon name="pen" :size="16" /><span class="office-edit-desktop">{{ isLatest ? '编辑图表' : '打开最新版本' }}</span><span class="office-edit-mobile">{{ isLatest ? '桌面端编辑' : '打开最新版本' }}</span></button><template v-else><button class="primary-button" :disabled="saveBusy || (!draft.dirty && !draft.pending) || !editorReady" @click="save"><Icon name="check" :size="16" />{{ saveLabel }}</button><button class="secondary-button" @click="fullscreen = !fullscreen">{{ fullscreen ? '退出全屏' : '全屏编辑' }}</button><button class="secondary-button" @click="closeEditor">结束编辑</button></template></template></div></header>
          <div v-if="editing" class="office-save-status" role="status"><span>{{ draft.saveState === 'outcomeUnknown' ? '正在确认保存结果，请保留原请求' : draft.saveState === 'saving' ? '正在保存…' : draft.dirty ? '有未保存的修改' : `已保存 v${draft.baseVersion}` }}</span><span v-if="draft.persistedAt && draft.exportedEpoch === draft.editEpoch">本机草稿已保留于 {{ new Date(draft.persistedAt).toLocaleTimeString('zh-CN') }}</span><span v-else-if="draft.dirty">最新改动尚未确认备份</span><button class="text-button" @click="localDraft().catch(ws.fail)">下载本地草稿</button></div>
          <div v-if="editing && (draft.error || draft.storageWarning || draft.currentVersion > draft.baseVersion)" class="office-save-error" role="alert"><strong>{{ draft.currentVersion > draft.baseVersion ? `服务器已有 v${draft.currentVersion}，你的修改尚未保存。` : draft.error || draft.storageWarning }}</strong><p v-if="draft.storageWarning">{{ draft.storageWarning }}</p><p v-if="draft.saveState === 'conflict'">当前草稿会继续保留。查看最新版本不会替换正在编辑的内容。</p><div class="office-actions"><button v-if="draft.currentVersion" class="secondary-button" :disabled="detachedBusy" @click="previewVersion()">查看最新版本</button><button v-if="draft.report" class="secondary-button" @click="openDrawer('validation')">查看校验报告</button><button v-if="draft.saveState === 'outcomeUnknown'" class="secondary-button" :disabled="saveBusy" @click="save">确认原保存结果</button></div></div>
          <div v-if="!editing && recovery" class="office-save-status" role="status"><span>此文件有本机未保存草稿，基于 v{{ recovery.baseVersion }}。</span><button class="text-button" :disabled="!v.xml || v.busy" @click="beginEdit">{{ isLatest ? '继续编辑草稿' : '打开最新版本并处理草稿' }}</button></div>
          <div v-if="editing && draftDownloadError" class="office-save-error" role="alert"><p>{{ draftDownloadError }}</p><p>上次捕获的草稿可能不包含最新改动，下载后请保留此页核对。</p><button class="secondary-button" :disabled="!draft.xml" @click="downloadCapturedDraft">下载上次捕获草稿</button></div>
          <div v-if="v.busy" class="office-empty" role="status"><Icon name="refresh" :size="32" /><h3>正在读取办公记录…</h3></div>
          <DrawioBridge v-else-if="v.xml" :key="editing ? `edit-${editorGeneration}` : `${v.artifact.artifactId}-${v.revision.version}`" ref="editor" :xml="editing ? editingXml : v.xml" :mode="editing ? 'edit' : 'view'" @ready="editorReady = true" @dirty="drafts.changed" @save-requested="save" @close-requested="closeEditor" />
          <div v-else-if="v.revision" class="office-empty"><Icon name="file" :size="36" /><h2>图表已保存，预览暂时无法加载</h2><p>v{{ v.revision.version }} 的文件仍在服务器上。可以重新读取，或下载后在 draw.io 中打开。</p><div class="office-actions"><button class="primary-button" @click="ws.load({ ...state.route.params })">重新读取图表</button><button class="secondary-button" :disabled="downloading" @click="download()">下载 v{{ v.revision.version }}</button></div></div>
          <div v-else class="office-empty"><div class="office-empty-diagram" aria-hidden="true"><span><Icon name="chat" :size="23" /></span><i></i><span><Icon name="workflow" :size="28" /></span><i></i><span><Icon name="file" :size="23" /></span></div><h2>{{ activeRun ? statusLabel(currentRun) : '从需求出发，把想法画清楚' }}</h2><p>{{ activeRun ? '任务在后台继续执行，可以稍后返回查看。' : '描述结构、参与者和关系，生成后在画布上继续完善。' }}</p><div v-if="!activeRun" class="office-examples"><span>试试这些示例需求</span><button v-for="example in examples" :key="example.type" @click="prompt = example.prompt; title = example.label; diagramType = example.type; panel = 'request'; collapsed = false"><Icon name="workflow" :size="17" />{{ example.label }}<Icon name="arrow" :size="15" /></button></div><button v-if="!v.session" class="primary-button" @click="newSession = true">创建办公会话</button></div>
          <footer class="office-canvas-footer"><span>{{ editing ? '显式保存才会生成新版本；本地草稿未经服务端校验。' : '图表正文由 draw.io 编辑器处理；下载始终对应已保存版本。' }}</span><button v-if="v.revision?.validationReport" class="text-button" @click="openDrawer('validation')">校验报告</button></footer>
        </section>
      </div>
    </template>
  </div>
  <OfficeDrawer v-if="drawer" :title="({ sessions: '办公会话', files: '会话文件', revisions: '版本历史', receipt: '生成记录', validation: '校验报告' })[drawer]" @close="drawer = ''">
    <template v-if="drawer === 'sessions'"><p v-if="v.listsBusy.sessions" class="office-muted" role="status">正在读取会话…</p><button v-for="session in v.sessions" :key="session.sessionId" class="office-drawer-row" @click="drawer = ''; nav('office', { session: session.sessionId })"><strong>{{ session.title }}</strong><small :title="exactTime(session.createdAt)">{{ time(session.createdAt) }}</small></button><p v-if="!v.sessions.length && !v.listsBusy.sessions" class="office-muted">还没有办公会话。</p><button v-if="v.sessions.length < v.sessionsTotal" class="secondary-button full" :disabled="v.listsBusy.sessions" @click="ws.listSessions(true)">加载更多会话</button></template>
    <template v-if="drawer === 'files'"><p v-if="v.listsBusy.files" class="office-muted" role="status">正在读取文件…</p><button v-for="file in v.files" :key="file.artifactId" class="office-drawer-row" @click="selectArtifact(file.artifactId)"><strong>{{ file.title }}</strong><small :title="exactTime(file.updatedAt)">最新 v{{ file.currentVersion }} · {{ time(file.updatedAt) }}</small></button><p v-if="!v.files.length && !v.listsBusy.files" class="office-muted">这个会话还没有文件。</p><button v-if="v.files.length < v.filesTotal" class="secondary-button full" :disabled="v.listsBusy.files" @click="ws.listFiles(true)">加载更多文件</button></template>
    <template v-if="drawer === 'revisions'"><p v-if="v.listsBusy.revisions" class="office-muted" role="status">正在读取版本…</p><p class="office-muted">历史版本只读查看；生成记录始终指向当次生成版本。</p><button v-for="revision in v.revisions" :key="revision.revisionId" class="office-drawer-row" :class="{ selected: revision.version === displayedVersion }" @click="previewVersion(revision.version)"><strong>v{{ revision.version }} · {{ revision.origin === 'GENERATED' ? '生成' : '手动' }} <span v-if="revision.version === latestVersion">· 最新</span></strong><small :title="exactTime(revision.createdAt)">{{ time(revision.createdAt) }} · {{ (revision.sizeBytes / 1024).toFixed(1) }} KB</small></button><button v-if="v.revisions.length < v.revisionsTotal" class="secondary-button full" :disabled="v.listsBusy.revisions" @click="ws.listRevisions(true)">加载更多版本</button></template>
    <template v-if="drawer === 'receipt'"><GenerationReceipt :receipt="v.receipt" :run="v.run" /><button class="secondary-button" @click="ws.receipt">重新读取记录</button><p v-if="v.run?.error?.traceId" class="office-muted">问题编号：{{ v.run.error.traceId }} <button class="text-button" @click="copyTrace(v.run.error.traceId)">复制编号</button></p></template>
    <ValidationPanel v-if="drawer === 'validation'" :report="draft.report || v.revision?.validationReport" />
  </OfficeDrawer>
  <Modal v-if="newSession" title="新建办公会话" @close="newSession = false"><form class="modal-body form-stack office-dialog" @submit.prevent="createSession"><label>会话标题<input v-model="sessionTitle" maxlength="200" required placeholder="例如：订单系统设计" :disabled="v.sessionCreating || v.sessionUnknown" /></label><p v-if="v.sessionUnknown" class="office-warning">创建结果尚未确认，已重新查询会话列表。请先检查是否已有该会话，避免重复创建。</p><div v-if="v.sessionUnknown" class="office-actions"><button type="button" class="secondary-button" @click="newSession = false; openDrawer('sessions')">检查会话列表</button><button type="button" class="text-button" @click="v.sessionUnknown = false">已核实未创建</button></div><button class="primary-button full" :disabled="!sessionTitle.trim() || v.sessionCreating || v.sessionUnknown">{{ v.sessionCreating ? '正在创建…' : '创建会话' }}</button></form></Modal>
  <Modal v-if="leaveOpen" title="修改尚未保存" @close="resolveLeave(false)"><div class="modal-body office-dialog"><p>离开前，请保存修改或下载草稿。后台生成任务会继续执行。</p><p v-if="leaveError" class="office-error-text" role="alert">{{ leaveError }}</p><div class="office-leave-actions"><button class="primary-button" :disabled="leaveBusy || !editorReady" @click="leave('save')">保存并离开</button><button class="secondary-button" :disabled="leaveBusy || !editorReady" @click="leave('download')">下载草稿并离开</button><button class="secondary-button" :disabled="leaveBusy" @click="leave('stay')">留在此页</button><button class="office-discard" :disabled="leaveBusy" @click="leave('discard')">放弃改动并离开</button></div></div></Modal>
  <OfficeDrawer v-if="detached" full :title="`${detached.artifact.title} · v${detached.revision.version}（只读）`" @close="detached = null"><template #actions><div class="office-actions"><button class="secondary-button" @click="download(detached.revision, detached.artifact)">下载 v{{ detached.revision.version }}</button><button class="primary-button" @click="detached = null">返回编辑</button></div></template><p class="office-muted">正在编辑的草稿仍保留，当前查看不会更改保存基线。</p><DrawioBridge :key="detached.revision.revisionId" :xml="detached.xml" mode="view" /></OfficeDrawer>
</template>
