<script setup>
import { ref, watch } from 'vue';
import Icon from './Icon.vue';
import { data, notify } from '../lib/store.js';
const props = defineProps({ compact: Boolean, busy: Boolean, defaultType: { type: String, default: 'chat' }, initial: { type: String, default: '' }, followup: Boolean });
const emit = defineEmits(['submit', 'stop']);
const text = ref(props.initial), type = ref(props.defaultType), files = ref([]), upload = ref(), sourcesOpen = ref(false), sourceIds = ref([]);
watch(() => props.initial, value => text.value = value);
watch(() => props.defaultType, value => type.value = value);
function submit() { if (!text.value.trim() || props.busy) return; emit('submit', { prompt: text.value, type: type.value, attachments: files.value, sourceIds: sourceIds.value }); text.value = ''; files.value = []; }
function keydown(event) { if (event.key === 'Enter' && !event.shiftKey && !event.isComposing && data.preferences.enterToSend !== false) { event.preventDefault(); submit(); } }
function attach(event) { const incoming = [...event.target.files]; files.value = [...files.value, ...incoming.map(f => ({ name: f.name, size: f.size }))].slice(0, 5); event.target.value = ''; notify('附件已添加到预览；本次仅保留文件名，不会上传文件。'); }
defineExpose({ focus: () => document.getElementById(props.compact ? 'reply-input' : 'home-input')?.focus() });
</script>
<template>
  <form class="composer" :class="{ compact }" @submit.prevent="submit">
    <div v-if="files.length" class="attachment-list"><span v-for="(file, i) in files" :key="i"><Icon name="File" :size="14" />{{ file.name }}<button type="button" :aria-label="'移除附件 ' + file.name" @click="files.splice(i, 1)"><Icon name="X" :size="12" /></button></span></div>
    <label class="sr-only" :for="compact ? 'reply-input' : 'home-input'">{{ followup ? '继续对话' : '描述你的任务' }}</label>
    <textarea :id="compact ? 'reply-input' : 'home-input'" v-model="text" :rows="compact ? 2 : 3" :placeholder="followup ? '补充想法，或描述需要调整的地方…' : '描述你想完成的事，或从下面的灵感开始…'" @keydown="keydown" maxlength="8000" />
    <div class="composer-toolbar"><div class="composer-tools"><input ref="upload" class="sr-only" type="file" multiple tabindex="-1" @change="attach" /><button type="button" class="icon-button" title="添加附件（本地预览）" aria-label="添加附件" @click="upload.click()"><Icon name="Plus" :size="20" /></button><span class="tool-divider"></span>
      <label v-if="!followup" class="composer-mode"><Icon :name="({ chat: 'Sparkles', diagram: 'Workflow', document: 'FileText', table: 'Table2' })[type]" :size="15" /><select v-model="type" aria-label="任务类型"><option value="chat">自由对话</option><option value="diagram">生成图表</option><option value="document">起草文档</option><option value="table">制作表格</option></select><Icon name="ChevronDown" :size="12" /></label>
      <div class="source-selector"><button type="button" class="quiet-button source-button" :class="{ selected: sourceIds.length }" @click="sourcesOpen = !sourcesOpen" :aria-expanded="sourcesOpen"><Icon name="BookOpen" :size="15" /><span>知识库{{ sourceIds.length ? ` · ${sourceIds.length}` : '' }}</span></button><div v-if="sourcesOpen" class="source-popover"><strong>选择参考资料</strong><p v-if="!data.sources.length">知识库还没有资料，可在知识库页面添加。</p><label v-for="source in data.sources" :key="source.id"><input type="checkbox" v-model="sourceIds" :value="source.id" />{{ source.name }}</label><small>设计预览记录选择，不执行检索。</small><button type="button" class="text-button" @click="sourcesOpen = false">完成</button></div></div>
    </div><div class="composer-send"><span v-if="!compact" class="composer-hint">Shift + Enter 换行</span><button v-if="busy" type="button" class="send-button" aria-label="停止准备示例" @click="emit('stop')"><Icon name="Square" :size="15" /></button><button v-else type="submit" class="send-button" :disabled="!text.trim()" aria-label="发送任务"><Icon name="ArrowUp" :size="20" /></button></div></div>
  </form>
</template>
