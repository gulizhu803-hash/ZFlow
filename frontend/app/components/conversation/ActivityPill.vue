<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import Icon from '../Icon.vue';
import { formatClock } from '../../lib/run-stages.js';

// Activity Pill：只读 run 状态（status / startedAt / updatedAt）与同一份阶段派生。
// 计时只来自 runState.startedAt 或注入的 clock，不依赖任何假数据。
const props = defineProps({
  status: { type: String, default: 'idle' },
  view: { type: Object, default: null }, // deriveRunStages 的结果，与 StageRail 共用
  startedAt: { type: Number, default: null },
  updatedAt: { type: Number, default: null },
  error: { type: String, default: '' },
  collapseDelay: { type: Number, default: 4000 },
  clock: { type: Function, default: () => Date.now() }
});
const emit = defineEmits(['open']);

const IN_FLIGHT = ['preparing', 'running', 'stopping'];
const TONES = { preparing: 'active', running: 'active', stopping: 'warning', completed: 'success', failed: 'danger', stopped: 'muted', unknown: 'warning' };
const LABELS = { preparing: '准备中', running: '正在执行', stopping: '正在停止', completed: '已完成 · 查看结果', failed: '执行失败', stopped: '已停止接收', unknown: '状态未知' };

const compact = ref(false);
const elapsed = ref(0);
let ticker = null;
let collapseTimer = null;

const inFlight = computed(() => IN_FLIGHT.includes(props.status));
const tone = computed(() => TONES[props.status] || 'neutral');
const label = computed(() => LABELS[props.status] || '');
const text = computed(() => (props.status === 'running' && props.view ? '正在执行 · ' + props.view.reached + '/' + props.view.total : label.value));
const note = computed(() => (props.status === 'failed' || props.status === 'unknown' ? props.error : ''));
// 4 秒后收起只发生在 completed；failed / unknown / stopped 保持展开。
const isCompact = computed(() => compact.value && props.status === 'completed');
const clockText = computed(() => (elapsed.value > 0 ? formatClock(elapsed.value) : ''));
const ariaLabel = computed(() => [isCompact.value ? '已完成，查看结果' : text.value, clockText.value, note.value].filter(Boolean).join('，'));

function stopTicker() {
  if (ticker !== null) { clearInterval(ticker); ticker = null; }
}
function stopCollapseTimer() {
  if (collapseTimer !== null) { clearTimeout(collapseTimer); collapseTimer = null; }
}
function syncElapsed() {
  stopTicker();
  if (props.startedAt == null) { elapsed.value = 0; return; }
  if (inFlight.value) {
    elapsed.value = Math.max(0, props.clock() - props.startedAt);
    ticker = setInterval(() => { elapsed.value = Math.max(0, props.clock() - props.startedAt); }, 1000);
    return;
  }
  const end = props.updatedAt != null ? props.updatedAt : props.clock();
  elapsed.value = Math.max(0, end - props.startedAt);
}
function syncCollapse() {
  stopCollapseTimer();
  compact.value = false;
  if (props.status !== 'completed') return;
  collapseTimer = setTimeout(() => { compact.value = true; collapseTimer = null; }, Math.max(0, props.collapseDelay));
}

watch(() => props.status, () => { syncElapsed(); syncCollapse(); });
watch(() => props.startedAt, syncElapsed);
onMounted(() => { syncElapsed(); syncCollapse(); });
onBeforeUnmount(() => { stopTicker(); stopCollapseTimer(); });
</script>
<template>
  <button
    class="activity-pill"
    :class="['activity-pill--' + tone, { 'activity-pill--compact': isCompact }]"
    type="button"
    aria-haspopup="dialog"
    :aria-label="ariaLabel"
    @click="emit('open')"
  >
    <span class="activity-pill__dot" aria-hidden="true">
      <Icon v-if="isCompact || status === 'completed'" name="check" :size="12" />
      <Icon v-else-if="status === 'failed' || status === 'unknown'" name="alert" :size="12" />
      <Icon v-else-if="status === 'stopped'" name="stop" :size="11" />
    </span>
    <span class="activity-pill__body">
      <span class="activity-pill__label">{{ isCompact ? '已完成' : text }}</span>
      <small v-if="note && !isCompact" class="activity-pill__note">{{ note }}</small>
    </span>
    <span v-if="isCompact" class="activity-pill__action">查看结果</span>
    <span v-else-if="clockText" class="activity-pill__clock">{{ clockText }}</span>
  </button>
</template>
<style scoped>
.activity-pill {
  display: inline-flex;
  align-items: center;
  gap: var(--z-space-2);
  height: 36px;
  max-width: 100%;
  padding: 0 var(--z-space-3);
  border: 1px solid var(--z-line);
  border-radius: var(--z-r-capsule);
  background: var(--z-glass);
  backdrop-filter: var(--z-glass-blur);
  color: var(--z-ink-2);
  font: 500 12px/1 var(--z-sans);
  text-align: left;
  box-shadow: var(--z-shadow-1);
  transition: background var(--z-dur-fast) var(--z-ease-out), color var(--z-dur-fast) var(--z-ease-out);
}
.activity-pill:hover { color: var(--z-ink); border-color: var(--z-line-strong); }
.activity-pill:focus-visible { outline: none; box-shadow: var(--z-focus); }
.activity-pill__dot { position: relative; display: inline-flex; align-items: center; justify-content: center; width: 14px; height: 14px; flex: none; border-radius: 50%; background: var(--z-fill); color: var(--z-ink-3); }
.activity-pill__body { display: inline-flex; align-items: center; gap: var(--z-space-2); min-width: 0; }
.activity-pill__label { white-space: nowrap; }
.activity-pill__note { overflow: hidden; max-width: 220px; white-space: nowrap; text-overflow: ellipsis; font: 400 11px/1 var(--z-sans); color: var(--z-ink-3); }
.activity-pill__clock { flex: none; font: 500 11px/1 var(--z-mono); color: var(--z-ink-3); font-variant-numeric: tabular-nums; }
.activity-pill__action { flex: none; font: 500 11px/1 var(--z-sans); color: var(--z-blue); }

/* active：pulse + 2px 柔光；reduced-motion 下只留静态色。 */
.activity-pill--active { color: var(--z-ink); border-color: rgba(10, 102, 255, .28); }
.activity-pill--active .activity-pill__dot { background: var(--z-blue-fill); box-shadow: 0 0 0 4px var(--z-blue-soft); }
.activity-pill--active .activity-pill__dot::after { content: ''; position: absolute; inset: -4px; border-radius: 50%; border: 1px solid var(--z-blue); opacity: 0; animation: activity-pulse 1.8s var(--z-ease-out) infinite; }
.activity-pill--success .activity-pill__dot { background: var(--z-blue-fill); color: var(--z-on-blue); }
.activity-pill--danger .activity-pill__dot { background: rgba(215, 0, 21, .12); color: var(--z-red); }
.activity-pill--danger .activity-pill__label { color: var(--z-red); }
.activity-pill--warning .activity-pill__dot { background: rgba(178, 80, 0, .12); color: var(--z-orange); }
.activity-pill--warning .activity-pill__label { color: var(--z-orange); }
.activity-pill--muted { color: var(--z-ink-3); }

.activity-pill--compact { height: 28px; padding: 0 10px; background: var(--z-paper); backdrop-filter: none; font-size: 11px; color: var(--z-ink-3); }
.activity-pill--compact .activity-pill__label { color: var(--z-ink-2); }

@keyframes activity-pulse {
  0% { opacity: .5; transform: scale(.8); }
  70% { opacity: 0; transform: scale(1.35); }
  100% { opacity: 0; transform: scale(1.35); }
}
@media (prefers-reduced-motion: reduce) {
  .activity-pill--active .activity-pill__dot::after { animation: none; }
}
</style>
