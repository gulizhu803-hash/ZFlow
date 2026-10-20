<script setup>
import { computed, ref } from 'vue';
import Icon from '../components/Icon.vue';
import Modal from '../components/Modal.vue';
import { data, commit, notify, formatTime, ui, go } from '../lib/store.js';
const query = ref(''), upload = ref(), dragging = ref(false), reading = ref(false), selected = ref(null), noteOpen = ref(false), noteName = ref(''), noteText = ref(''), removing = ref(null);
const sources = computed(() => data.sources.filter(s => s.name.toLowerCase().includes(query.value.toLowerCase())));
async function addFiles(files) {
  dragging.value = false; if (reading.value) return; reading.value = true;
  const incoming = [], errors = [];
  try {
    for (const file of [...files].slice(0, 20)) {
      if (!/\.(md|txt|csv|json)$/i.test(file.name)) { errors.push('仅支持 TXT、Markdown、CSV 和 JSON'); continue; }
      if (file.size > 256 * 1024) { errors.push('单个文件不能超过 256 KB'); continue; }
      incoming.push({ id: crypto.randomUUID(), name: file.name, content: await file.text(), size: file.size, addedAt: new Date().toISOString() });
    }
    if (data.sources.length + incoming.length > 20 || data.sources.reduce((n, s) => n + s.content.length, 0) + incoming.reduce((n, s) => n + s.content.length, 0) > 1000000) { notify('预览最多保存 20 份资料、总计 100 万字符，请先删除不需要的资料。', true); return; }
    if (incoming.length && commit(d => d.sources.push(...incoming))) notify(`已在本地添加 ${incoming.length} 份资料${errors.length ? '，部分文件未导入' : ''}`);
    else if (errors.length) notify(errors[0], true);
  } catch { notify('文件读取失败，请重新选择。', true); } finally { reading.value = false; if (upload.value) upload.value.value = ''; }
}
function addNote() {
  if (data.sources.length >= 20 || data.sources.reduce((n, s) => n + s.content.length, 0) + noteText.value.length > 1000000) { notify('本地资料数量或大小已达预览上限，请先导出并整理资料。', true); return; }
  if (commit(d => d.sources.push({ id: crypto.randomUUID(), name: noteName.value.trim() + '.md', content: noteText.value, size: new Blob([noteText.value]).size, addedAt: new Date().toISOString() }))) { noteOpen.value = false; noteName.value = ''; noteText.value = ''; notify('笔记已保存到本地'); }
}
function ask(source) { ui.draft = `请帮我梳理《${source.name}》的重点。\n\n${source.content.slice(0, 6000)}`; selected.value = null; go('chat'); }
function download(source) { const url = URL.createObjectURL(new Blob([source.content], { type: 'text/plain;charset=utf-8' })); const a = document.createElement('a'); a.href = url; a.download = source.name; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000); }
</script>
<template>
  <div class="page knowledge-page"><header class="page-heading"><div><span class="small-label">让思考有据可依</span><h1>知识库</h1><p>把散落的文档与笔记，整理成随时可用的上下文。</p></div><div class="heading-actions"><button class="secondary-button" @click="noteOpen = true"><Icon name="Pencil" :size="15" />新建笔记</button><button class="primary-button" :disabled="reading" @click="upload.click()"><Icon name="Plus" :size="16" />添加资料</button></div></header><input ref="upload" type="file" accept=".md,.txt,.csv,.json" multiple class="sr-only" tabindex="-1" @change="addFiles($event.target.files)" />
    <button class="upload-zone" :class="{ dragging }" :disabled="reading" @click="upload.click()" @dragover.prevent="dragging = true" @dragleave.prevent="dragging = false" @drop.prevent="addFiles($event.dataTransfer.files)"><span class="upload-symbol"><Icon :name="reading ? 'LoaderCircle' : 'Upload'" :size="25" /></span><strong>{{ reading ? '正在读取资料…' : '将资料拖到这里，或点击选择文件' }}</strong><p>TXT、Markdown、CSV、JSON · 单个文件最大 256 KB</p><span class="upload-privacy"><Icon name="LockKeyhole" :size="12" />资料保存在当前浏览器</span></button>
    <div class="section-heading knowledge-heading"><div><h2>我的资料 <span class="heading-count">{{ data.sources.length }}</span></h2><span class="section-caption">本地预览 · 尚未连接知识检索服务</span></div><label class="search-field"><Icon name="Search" :size="15" /><input v-model="query" placeholder="搜索资料…" aria-label="搜索资料" /></label></div>
    <div v-if="sources.length" class="knowledge-table"><div class="knowledge-table-head"><span>资料名称</span><span>大小</span><span>添加时间</span><span></span></div><div v-for="source in sources" :key="source.id" class="knowledge-row"><button class="knowledge-file" @click="selected = source"><span class="file-icon"><Icon name="FileText" /></span><span><strong>{{ source.name }}</strong><small>本地文件 · 可预览</small></span></button><span class="muted small">{{ (source.size / 1024).toFixed(1) }} KB</span><time>{{ formatTime(source.addedAt) }}</time><div><button class="icon-button" :aria-label="'下载 ' + source.name" @click="download(source)"><Icon name="Download" :size="15" /></button><button class="icon-button" :aria-label="'删除 ' + source.name" @click="removing = source"><Icon name="Trash2" :size="15" /></button></div></div></div><div v-else class="empty-inline knowledge-empty"><Icon name="BookOpen" :size="28" /><strong>{{ query ? '没有找到相关资料' : '从一份文档，开始建立你的知识库' }}</strong><span>{{ query ? '试试其他关键词。' : '添加需求、技术笔记或项目说明，在对话中继续思考。' }}</span></div>
  </div><Modal v-if="noteOpen" title="新建知识笔记" @close="noteOpen = false"><form class="form-stack" @submit.prevent="addNote"><label>笔记名称<input v-model="noteName" required maxlength="80" autofocus placeholder="例如：订单系统需求" /></label><label>内容<textarea v-model="noteText" required rows="10" maxlength="50000" placeholder="记录需要整理的知识与背景资料…" /></label><div class="modal-actions"><button type="button" class="secondary-button" @click="noteOpen = false">取消</button><button class="primary-button">保存笔记</button></div></form></Modal>
  <Modal v-if="selected" :title="selected.name" wide @close="selected = null"><pre class="source-content">{{ selected.content }}</pre><div class="modal-actions"><button class="secondary-button" @click="download(selected)"><Icon name="Download" :size="15" />下载原文</button><button class="primary-button" @click="ask(selected)"><Icon name="MessageSquare" :size="15" />带入对话预览</button></div></Modal>
  <Modal v-if="removing" title="删除资料" @close="removing = null"><p>从本地知识库删除「{{ removing.name }}」？原始文件不会受到影响。</p><div class="modal-actions"><button class="secondary-button" @click="removing = null">取消</button><button class="danger-button" @click="commit(d => d.sources = d.sources.filter(s => s.id !== removing.id)); removing = null">删除资料</button></div></Modal>
</template>
