<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue';
import { editorUrl, editorMessage } from '../../lib/drawio-protocol.js';
const props = defineProps({ xml: String, mode: { type: String, default: 'view' } });
const emit = defineEmits(['ready', 'dirty', 'saveRequested', 'closeRequested', 'error']);
const frame = ref(), frameKey = ref(0), status = ref('loading'), error = ref(''), preview = ref(''), pages = ref([]), activePage = ref(''), zoom = ref(1);
let url, timer, loaded = false, bridgeEpoch = 0, requestNo = 0, initialXml = '', lastXml = '';
const pending = new Map();
try { url = editorUrl(import.meta.env.VITE_DRAWIO_URL, window.location.origin, import.meta.env.VITE_DRAWIO_ORIGIN); } catch (e) { error.value = e.message; status.value = 'failed'; }
function send(message) { frame.value?.contentWindow?.postMessage(JSON.stringify(message), url.origin); }
function fail(message) { status.value = 'failed'; error.value = message; emit('error', message); }
function request(format, extras = {}) {
  if (!loaded) return Promise.reject(new Error('编辑器尚未就绪，请稍后重试'));
  const requestId = `${bridgeEpoch}-${++requestNo}`;
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => { pending.delete(requestId); reject(new Error('编辑器导出超时，最新修改尚未备份，请保留此页')); }, 10000);
    pending.set(requestId, { resolve, reject, timeout });
    send({ action: 'export', format, requestId, ...extras });
  });
}
async function renderPreview() {
  try {
    const result = await request('svg', { pageId: activePage.value, embedImages: false, embedFonts: false, border: 24, background: '#ffffff' });
    if (!/^data:image\/svg\+xml[;,]/.test(result.data || '')) throw new Error('预览格式无效');
    preview.value = result.data; status.value = 'ready'; emit('ready');
  } catch (e) { fail(e.message); }
}
function receive(event) {
  if (!url) return;
  const message = editorMessage(event, url.origin, frame.value?.contentWindow); if (!message) return;
  if (message.event === 'configure') send({ action: 'configure', config: { defaultVertexStyle: { html: '0' }, defaultEdgeStyle: { html: '0' }, compressXml: false } });
  if (message.event === 'init') send({ action: 'load', xml: props.xml, autosave: props.mode === 'edit' ? 1 : 0, modified: false });
  if (message.event === 'load' && !loaded) {
    loaded = true; clearTimeout(timer); initialXml = message.xml || props.xml; lastXml = initialXml;
    if (props.mode === 'view') renderPreview(); else { status.value = 'ready'; emit('ready'); }
  }
  if (message.event === 'autosave' && loaded && props.mode === 'edit' && message.xml && message.xml !== lastXml) {
    lastXml = message.xml; emit('dirty', message.xml);
  }
  if (message.event === 'save' && loaded && props.mode === 'edit') {
    if (message.xml && message.xml !== lastXml) { lastXml = message.xml; emit('dirty', message.xml); }
    emit('saveRequested');
  }
  if (message.event === 'exit') emit('closeRequested');
  if (message.event === 'export') {
    let original = message.message;
    if (typeof original === 'string') { try { original = JSON.parse(original); } catch { return; } }
    const entry = pending.get(original?.requestId); if (!entry) return;
    clearTimeout(entry.timeout); pending.delete(original.requestId);
    if (message.error) entry.reject(new Error('编辑器导出失败，请保留当前页面')); else entry.resolve(message);
  }
}
function destroy() { bridgeEpoch++; clearTimeout(timer); loaded = false; for (const item of pending.values()) { clearTimeout(item.timeout); item.reject(new Error('编辑器已关闭')); } pending.clear(); }
async function retry() { destroy(); status.value = 'loading'; error.value = ''; frameKey.value++; await nextTick(); timer = setTimeout(() => fail('编辑器尚未加载，可重试或下载文件'), 15000); }
async function requestXml() { const result = await request('xml'); if (!result.xml) throw new Error('编辑器未返回 XML'); return result.xml; }
function saved() { send({ action: 'status', message: '已保存', modified: false }); }
defineExpose({ requestXml, saved });
onMounted(() => {
  const doc = new DOMParser().parseFromString(props.xml || '', 'application/xml');
  pages.value = [...doc.querySelectorAll('mxfile > diagram')].map(d => ({ id: d.getAttribute('id'), name: d.getAttribute('name') || '未命名页' })); activePage.value = pages.value[0]?.id;
  window.addEventListener('message', receive); if (url) timer = setTimeout(() => fail('编辑器尚未加载，可重试或下载文件'), 15000);
});
onBeforeUnmount(() => { destroy(); window.removeEventListener('message', receive); preview.value = ''; });
</script>
<template>
  <div class="drawio-bridge" :class="{ 'is-preview': mode === 'view' }">
    <iframe v-if="url" :key="frameKey" ref="frame" :src="url.href" title="draw.io 图表编辑器" :class="{ 'preview-engine': mode === 'view' }" :tabindex="mode === 'view' ? -1 : 0" :inert="mode === 'view'" sandbox="allow-scripts allow-same-origin allow-downloads allow-forms allow-modals" referrerpolicy="strict-origin" />
    <div v-if="mode === 'view' && preview" class="office-preview">
      <div class="preview-controls"><label v-if="pages.length > 1">页面 <select v-model="activePage" @change="renderPreview"><option v-for="p in pages" :key="p.id" :value="p.id">{{ p.name }}</option></select></label><button @click="zoom = Math.max(.5, zoom - .25)" aria-label="缩小预览">−</button><span>{{ Math.round(zoom * 100) }}%</span><button @click="zoom = Math.min(3, zoom + .25)" aria-label="放大预览">+</button><button @click="zoom = 1">适应画布</button><span>只读预览</span></div>
      <div class="preview-image"><img :src="preview" :style="{ width: `${zoom * 100}%`, maxWidth: 'none' }" alt="当前版本的图表预览" /></div>
    </div>
    <div v-if="status !== 'ready'" class="editor-message" role="status"><span>{{ error || '正在加载图表…' }}</span><button v-if="status === 'failed' && url" class="secondary-button" @click="retry()">重新加载编辑器</button></div>
  </div>
</template>
