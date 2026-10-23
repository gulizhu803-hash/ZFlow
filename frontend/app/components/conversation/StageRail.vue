<script setup>
import { computed, ref, watch } from 'vue';
import Icon from '../Icon.vue';

// Stage Rail：4 节点跑道。数据来自 run-stages 的派生结果，与 Activity Pill 同源。
const props = defineProps({
  view: { type: Object, default: null },
  error: { type: String, default: '' }
});
const emit = defineEmits(['retry', 'history']);

const STATE_TEXT = { done: '完成', active: '进行中', pending: '未开始', error: '中断' };
const nodes = computed(() => (props.view ? props.view.nodes : []));
const failed = computed(() => Boolean(props.view && props.view.failed));
// 默认展开：当前阶段 → 出错节点 → 最后一个已完成节点。
const defaultRole = computed(() => {
  const list = nodes.value;
  const active = list.find(node => node.state === 'active');
  if (active) return active.role;
  const broken = list.find(node => node.state === 'error');
  if (broken) return broken.role;
  const done = [...list].reverse().find(node => node.state === 'done');
  return done ? done.role : '';
});
const explicit = ref(null); // null = 跟随默认展开
const openRole = computed(() => (explicit.value === null ? defaultRole.value : explicit.value));
const openNode = computed(() => nodes.value.find(node => node.role === openRole.value) || null);
watch(defaultRole, () => { explicit.value = null; });

function toggle(node) {
  explicit.value = openRole.value === node.role ? '' : node.role;
}
function metaOf(node) {
  if (node.state === 'done') return node.duration || '完成';
  return STATE_TEXT[node.state] || '';
}
</script>
<template>
  <div class="stage-rail">
    <ol class="stage-rail__list" role="list">
      <li
        v-for="node in nodes"
        :key="node.role"
        class="stage-rail__item"
        :class="'stage-rail__item--' + node.state"
        :aria-current="node.state === 'active' ? 'step' : undefined"
      >
        <button
          class="stage-rail__node"
          type="button"
          :aria-expanded="openRole === node.role"
          :aria-controls="'stage-output-' + node.role"
          :aria-label="node.label + ' · ' + metaOf(node)"
          @click="toggle(node)"
        >
          <span class="stage-rail__dot" aria-hidden="true">
            <Icon v-if="node.state === 'done'" name="check" :size="12" />
            <Icon v-else-if="node.state === 'error'" name="alert" :size="12" />
            <template v-else>{{ node.index + 1 }}</template>
          </span>
          <span class="stage-rail__label">{{ node.label }}</span>
          <small class="stage-rail__meta">{{ metaOf(node) }}</small>
        </button>
        <span v-if="node.index < nodes.length - 1" class="stage-rail__line" aria-hidden="true"></span>
      </li>
    </ol>
    <div v-if="openNode" :id="'stage-output-' + openNode.role" class="stage-rail__panel">
      <p class="stage-rail__panel-head">
        <span class="stage-rail__panel-title">{{ openNode.label }} · {{ openNode.detail }}</span>
        <small v-if="openNode.duration" class="stage-rail__panel-meta">{{ openNode.duration }}</small>
      </p>
      <pre v-if="openNode.output" class="stage-rail__output">{{ openNode.output }}</pre>
      <p v-else class="stage-rail__empty">{{ openNode.state === 'pending' ? '尚未开始。' : openNode.state === 'active' ? '正在等待该阶段输出…' : '该阶段没有文本输出。' }}</p>
    </div>
    <div v-if="failed" class="stage-rail__error" role="alert">
      <p class="stage-rail__error-text">{{ error || '任务状态未知，请查看历史确认后再决定是否重试。' }}</p>
      <div class="stage-rail__actions">
        <button type="button" class="stage-rail__action" @click="emit('history')"><Icon name="clock" :size="14" />查看历史</button>
        <button type="button" class="stage-rail__action stage-rail__action--primary" @click="emit('retry')"><Icon name="refresh" :size="14" />重试</button>
      </div>
    </div>
  </div>
</template>
<style scoped>
.stage-rail { display: flex; flex-direction: column; gap: var(--z-space-3); min-width: 0; }
.stage-rail__list { display: flex; align-items: flex-start; margin: 0; padding: 0; list-style: none; min-width: 0; }
.stage-rail__item { display: flex; align-items: flex-start; flex: 1 1 0; min-width: 0; }
.stage-rail__item:last-child { flex: 0 0 auto; }
.stage-rail__node { display: flex; flex-direction: column; align-items: center; gap: 4px; min-width: 44px; min-height: 44px; padding: 4px 2px; border-radius: var(--z-r-control); color: var(--z-ink-3); background: none; }
.stage-rail__node:focus-visible { outline: none; box-shadow: var(--z-focus); }
.stage-rail__dot { display: inline-flex; align-items: center; justify-content: center; width: 22px; height: 22px; box-sizing: border-box; border-radius: 50%; border: 1px solid var(--z-line-strong); background: var(--z-paper); color: var(--z-ink-4); font: 600 11px/1 var(--z-sans); transition: background var(--z-dur-fast) var(--z-ease-out), border-color var(--z-dur-fast) var(--z-ease-out); }
.stage-rail__label { font: 500 11px/1.2 var(--z-sans); letter-spacing: .02em; }
.stage-rail__meta { font: 400 10px/1 var(--z-sans); color: var(--z-ink-4); font-variant-numeric: tabular-nums; }
.stage-rail__line { flex: 1; min-width: 6px; height: 1px; margin-top: 15px; background: var(--z-line-strong); }

/* done：蓝底对勾 */
.stage-rail__item--done .stage-rail__dot { background: var(--z-blue-fill); border-color: var(--z-blue-fill); color: var(--z-on-blue); }
.stage-rail__item--done .stage-rail__label { color: var(--z-ink); }
/* active：2px 蓝环 + 4px 柔光 */
.stage-rail__item--active .stage-rail__dot { border: 2px solid var(--z-blue); color: var(--z-blue); box-shadow: 0 0 0 4px var(--z-blue-soft); }
.stage-rail__item--active .stage-rail__label { color: var(--z-ink); }
.stage-rail__item--active .stage-rail__meta { color: var(--z-blue); }
/* error：红色错误态 */
.stage-rail__item--error .stage-rail__dot { background: rgba(215, 0, 21, .10); border-color: var(--z-red); color: var(--z-red); }
.stage-rail__item--error .stage-rail__label { color: var(--z-red); }
.stage-rail__item--error .stage-rail__meta { color: var(--z-red); }

.stage-rail__panel { display: flex; flex-direction: column; gap: 6px; padding: var(--z-space-3); border: 1px solid var(--z-line); border-radius: var(--z-r-card); background: var(--z-paper); }
.stage-rail__panel-head { display: flex; align-items: baseline; justify-content: space-between; gap: var(--z-space-2); }
.stage-rail__panel-title { font: 500 11px/1.3 var(--z-sans); color: var(--z-ink-2); }
.stage-rail__panel-meta { font: 400 10px/1 var(--z-mono); color: var(--z-ink-4); }
.stage-rail__output { max-height: 150px; margin: 0; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; font: 400 12px/1.6 var(--z-sans); color: var(--z-ink-2); }
.stage-rail__empty { font: 400 12px/1.6 var(--z-sans); color: var(--z-ink-4); }

.stage-rail__error { display: flex; flex-direction: column; gap: var(--z-space-2); padding: var(--z-space-3); border: 1px solid rgba(215, 0, 21, .24); border-radius: var(--z-r-card); background: rgba(215, 0, 21, .06); }
.stage-rail__error-text { font: 400 12px/1.6 var(--z-sans); color: var(--z-red); overflow-wrap: anywhere; }
.stage-rail__actions { display: flex; gap: var(--z-space-2); }
.stage-rail__action { display: inline-flex; align-items: center; gap: 6px; min-height: 32px; padding: 0 var(--z-space-3); border: 1px solid var(--z-line-strong); border-radius: var(--z-r-control); background: var(--z-paper); color: var(--z-ink-2); font: 500 12px/1 var(--z-sans); }
.stage-rail__action:hover { color: var(--z-ink); }
.stage-rail__action--primary { border-color: transparent; background: var(--z-blue-fill); color: var(--z-on-blue); }
.stage-rail__action--primary:hover { background: var(--z-blue-hover); color: var(--z-on-blue); }

@media (max-width: 767px) {
  .stage-rail__node { min-width: 0; padding: 4px 0; }
  .stage-rail__label { font-size: 10px; }
  .stage-rail__meta { font-size: 9px; }
  .stage-rail__output { max-height: 120px; }
}
</style>
