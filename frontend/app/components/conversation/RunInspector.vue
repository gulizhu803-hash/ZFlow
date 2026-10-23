<script setup>
import { computed } from 'vue';
import Icon from '../Icon.vue';

// Run Inspector（360）。复用 Composer 之外的同一批执行参数，不新增任何后端请求。
const props = defineProps({
  maxRound: { type: Number, default: 3 },
  maxRetry: { type: Number, default: 2 },
  maxPace: { type: Number, default: 5 },
  ragTag: { type: String, default: '' },
  mcpIds: { type: Array, default: () => [] },
  rags: { type: Array, default: () => [] },
  mcps: { type: Array, default: () => [] },
  busy: { type: Boolean, default: false },
  view: { type: Object, default: null },
  status: { type: String, default: 'idle' },
  error: { type: String, default: '' }
});
const emit = defineEmits(['update:maxRound', 'update:maxRetry', 'update:maxPace', 'update:ragTag', 'update:mcpIds', 'retry', 'history']);

const STATE_TEXT = { done: '完成', active: '进行中', pending: '未开始', error: '中断' };
const nodes = computed(() => (props.view ? props.view.nodes : []));
const failed = computed(() => Boolean(props.view && props.view.failed));
const current = computed(() => nodes.value.find(node => node.state === 'active') || nodes.value.find(node => node.state === 'error') || null);

function setNumber(event, key) {
  emit(key, Number(event.target.value));
}
function toggleMcp(id, checked) {
  emit('update:mcpIds', checked ? [...props.mcpIds, id] : props.mcpIds.filter(item => item !== id));
}
</script>
<template>
  <aside class="run-inspector" aria-label="执行面板">
    <div class="run-inspector__scroll">
      <section class="run-inspector__section">
        <h3 class="run-inspector__label">参数</h3>
        <div class="run-inspector__fields">
          <label class="run-inspector__field">最大轮次<input type="number" min="1" :value="maxRound" :disabled="busy" @change="setNumber($event, 'update:maxRound')" /></label>
          <label class="run-inspector__field">最大重试<input type="number" min="1" :value="maxRetry" :disabled="busy" @change="setNumber($event, 'update:maxRetry')" /></label>
          <label class="run-inspector__field">最大步数<input type="number" min="1" :value="maxPace" :disabled="busy" @change="setNumber($event, 'update:maxPace')" /></label>
        </div>
      </section>
      <section class="run-inspector__section">
        <h3 class="run-inspector__label">知识库</h3>
        <select class="run-inspector__select" :value="ragTag" :disabled="busy" aria-label="关联知识库" @change="emit('update:ragTag', $event.target.value)">
          <option value="">不使用知识库</option>
          <option v-for="rag in rags" :key="rag.ragTag" :value="rag.ragTag">{{ rag.ragTag }}</option>
        </select>
      </section>
      <section class="run-inspector__section">
        <h3 class="run-inspector__label">工具</h3>
        <div class="run-inspector__tools">
          <label v-for="mcp in mcps" :key="mcp.mcpId" class="run-inspector__check">
            <input type="checkbox" :checked="mcpIds.includes(mcp.mcpId)" :disabled="busy" @change="toggleMcp(mcp.mcpId, $event.target.checked)" />
            <span>{{ mcp.mcpName }}</span>
          </label>
          <span v-if="!mcps.length" class="run-inspector__empty">暂无可用工具</span>
        </div>
      </section>
      <section class="run-inspector__section">
        <h3 class="run-inspector__label">阶段</h3>
        <ol class="run-inspector__stages" role="list">
          <li v-for="node in nodes" :key="node.role" class="run-inspector__stage" :class="'run-inspector__stage--' + node.state">
            <span class="run-inspector__stage-name">{{ node.label }}</span>
            <small class="run-inspector__stage-meta">{{ node.state === 'done' ? node.duration || '完成' : STATE_TEXT[node.state] }}</small>
          </li>
        </ol>
        <div v-if="current" class="run-inspector__current">
          <p class="run-inspector__current-head">{{ current.label }} · {{ current.detail }}</p>
          <pre v-if="current.output" class="run-inspector__output">{{ current.output }}</pre>
          <p v-else class="run-inspector__empty">{{ current.state === 'pending' ? '该阶段尚未开始。' : '该阶段还没有文本输出。' }}</p>
        </div>
        <div v-if="failed" class="run-inspector__error">
          <p class="run-inspector__error-text" role="alert">{{ error || '任务状态未知，请查看历史确认。' }}</p>
          <div class="run-inspector__actions">
            <button type="button" class="run-inspector__action" @click="emit('history')"><Icon name="clock" :size="14" />查看历史</button>
            <button type="button" class="run-inspector__action run-inspector__action--primary" @click="emit('retry')"><Icon name="refresh" :size="14" />重试</button>
          </div>
        </div>
      </section>
    </div>
    <p class="run-inspector__footer">后台可能继续运行，状态以历史为准。</p>
  </aside>
</template>
<style scoped>
.run-inspector { display: flex; flex-direction: column; min-height: 0; height: 100%; }
.run-inspector__scroll { display: flex; flex-direction: column; gap: var(--z-space-5); flex: 1; min-height: 0; overflow-y: auto; padding: var(--z-space-5); }
.run-inspector__section { display: flex; flex-direction: column; gap: var(--z-space-2); min-width: 0; }
.run-inspector__label { font: 600 10px/1 var(--z-sans); letter-spacing: .08em; text-transform: uppercase; color: var(--z-ink-4); }
.run-inspector__fields { display: flex; flex-direction: column; gap: var(--z-space-2); }
.run-inspector__field { display: flex; align-items: center; justify-content: space-between; gap: var(--z-space-3); font: 400 13px/1.4 var(--z-sans); color: var(--z-ink-2); }
.run-inspector__field input { width: 72px; padding: 7px 8px; border: 1px solid var(--z-line); border-radius: var(--z-r-control); background: var(--z-paper); color: var(--z-ink); font: 400 13px/1 var(--z-sans); text-align: right; }
.run-inspector__field input:disabled { opacity: .5; }
.run-inspector__select { width: 100%; padding: 8px 10px; border: 1px solid var(--z-line); border-radius: var(--z-r-control); background: var(--z-paper); color: var(--z-ink); font: 400 13px/1.4 var(--z-sans); }
.run-inspector__select:disabled { opacity: .5; }
.run-inspector__tools { display: flex; flex-direction: column; gap: var(--z-space-2); }
.run-inspector__check { display: flex; align-items: center; gap: var(--z-space-2); font: 400 13px/1.4 var(--z-sans); color: var(--z-ink-2); }
.run-inspector__check input { accent-color: var(--z-blue); }
.run-inspector__check input:disabled { opacity: .5; }
.run-inspector__empty { font: 400 12px/1.6 var(--z-sans); color: var(--z-ink-4); }
.run-inspector__stages { display: flex; flex-direction: column; gap: 1px; margin: 0; padding: 0; list-style: none; }
.run-inspector__stage { display: flex; align-items: center; justify-content: space-between; gap: var(--z-space-2); min-height: 32px; padding: 0 var(--z-space-2); border-radius: var(--z-r-chip); font: 400 13px/1.4 var(--z-sans); color: var(--z-ink-2); }
.run-inspector__stage--done { color: var(--z-ink); }
.run-inspector__stage--active { background: var(--z-blue-soft); color: var(--z-blue); }
.run-inspector__stage--error { background: rgba(215, 0, 21, .08); color: var(--z-red); }
.run-inspector__stage-meta { font: 400 11px/1 var(--z-sans); color: var(--z-ink-4); font-variant-numeric: tabular-nums; }
.run-inspector__stage--active .run-inspector__stage-meta { color: var(--z-blue); }
.run-inspector__stage--error .run-inspector__stage-meta { color: var(--z-red); }
.run-inspector__current { display: flex; flex-direction: column; gap: 6px; padding: var(--z-space-3); border: 1px solid var(--z-line); border-radius: var(--z-r-card); background: var(--z-paper-2); }
.run-inspector__current-head { font: 500 11px/1.3 var(--z-sans); color: var(--z-ink-3); }
.run-inspector__output { max-height: 180px; margin: 0; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; font: 400 12px/1.6 var(--z-sans); color: var(--z-ink-2); }
.run-inspector__error { display: flex; flex-direction: column; gap: var(--z-space-2); }
.run-inspector__error-text { font: 400 12px/1.6 var(--z-sans); color: var(--z-red); overflow-wrap: anywhere; }
.run-inspector__actions { display: flex; gap: var(--z-space-2); }
.run-inspector__action { display: inline-flex; align-items: center; gap: 6px; min-height: 32px; padding: 0 var(--z-space-3); border: 1px solid var(--z-line-strong); border-radius: var(--z-r-control); background: var(--z-paper); color: var(--z-ink-2); font: 500 12px/1 var(--z-sans); }
.run-inspector__action--primary { border-color: transparent; background: var(--z-blue-fill); color: var(--z-on-blue); }
.run-inspector__footer { flex: none; padding: var(--z-space-3) var(--z-space-5) var(--z-space-4); border-top: 1px solid var(--z-line); font: 400 11px/1.5 var(--z-sans); color: var(--z-ink-4); }
</style>
