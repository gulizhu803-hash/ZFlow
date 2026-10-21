<script setup>
import { computed } from 'vue';
import Icon from '../Icon.vue';
const props = defineProps({
  clients: { type: Array, default: () => [] },
  rags: { type: Array, default: () => [] },
  mcps: { type: Array, default: () => [] },
  clientId: { type: String, default: '' },
  ragTag: { type: String, default: '' },
  mcpIds: { type: Array, default: () => [] },
  busy: { type: Boolean, default: false },
  runState: { type: Object, default: null },
  recentRuns: { type: Array, default: () => [] }
});
const emit = defineEmits(['update:clientId', 'update:ragTag', 'update:mcpIds', 'open']);
const ACTIVE_STATUS = ['preparing', 'running', 'stopping'];
const activeRun = computed(() => (props.runState && ACTIVE_STATUS.includes(props.runState.status) ? props.runState : null));
const runStatusText = computed(() => ({ preparing: '准备中', running: '运行中', stopping: '正在停止' })[activeRun.value?.status] || '运行中');
function toggleMcp(id, checked) {
  const next = checked ? [...props.mcpIds, id] : props.mcpIds.filter(x => x !== id);
  emit('update:mcpIds', next);
}
</script>
<template>
  <div class="context-rail">
    <section class="context-rail__section">
      <h3 class="context-rail__label">模型</h3>
      <select :value="clientId" :disabled="busy" class="context-rail__select" aria-label="选择对话客户端" @change="emit('update:clientId', $event.target.value)">
        <option v-if="!clients.length" value="">暂无可用对话客户端</option>
        <option v-for="client in clients" :key="client.clientId" :value="client.clientId">{{ client.clientName }} · {{ client.modelName }}</option>
      </select>
    </section>
    <section class="context-rail__section">
      <h3 class="context-rail__label">知识库</h3>
      <select :value="ragTag" :disabled="busy" class="context-rail__select" aria-label="关联知识库" @change="emit('update:ragTag', $event.target.value)">
        <option value="">不使用知识库</option>
        <option v-for="rag in rags" :key="rag.ragTag" :value="rag.ragTag">{{ rag.ragTag }}</option>
      </select>
    </section>
    <section class="context-rail__section">
      <h3 class="context-rail__label">工具</h3>
      <div class="context-rail__tools">
        <label v-for="mcp in mcps" :key="mcp.mcpId" class="context-rail__check"><input type="checkbox" :checked="mcpIds.includes(mcp.mcpId)" :disabled="busy" @change="toggleMcp(mcp.mcpId, $event.target.checked)" /><span>{{ mcp.mcpName }}</span></label>
        <span v-if="!mcps.length" class="context-rail__empty">暂无可用工具</span>
      </div>
    </section>
    <section v-if="activeRun" class="context-rail__section">
      <h3 class="context-rail__label">当前运行</h3>
      <p class="context-rail__note">{{ runStatusText }} · {{ activeRun.prompt || '正在执行' }}</p>
    </section>
    <section v-if="recentRuns.length" class="context-rail__section">
      <h3 class="context-rail__label">最近运行</h3>
      <button v-for="run in recentRuns" :key="run.sessionId" class="context-rail__run" type="button" @click="emit('open', run)">{{ run.sessionTitle }}</button>
    </section>
    <p class="context-rail__footer">上下文会随会话保存</p>
  </div>
</template>
<style scoped>
.context-rail { display: flex; flex-direction: column; gap: var(--z-space-5); padding: var(--z-space-5); min-width: 0; }
.context-rail__section { display: flex; flex-direction: column; gap: var(--z-space-2); }
.context-rail__label { font: 600 11px/1 var(--z-sans); letter-spacing: .08em; text-transform: uppercase; color: var(--z-ink-4); }
.context-rail__select { width: 100%; border: 1px solid var(--z-line); background: var(--z-paper); color: var(--z-ink); border-radius: var(--z-r-control); padding: 8px 10px; font: 400 13px/1.4 var(--z-sans); }
.context-rail__select:disabled { opacity: .5; }
.context-rail__tools { display: flex; flex-direction: column; gap: var(--z-space-2); }
.context-rail__check { display: flex; align-items: center; gap: var(--z-space-2); font: 400 13px/1.4 var(--z-sans); color: var(--z-ink-2); }
.context-rail__check input { accent-color: var(--z-blue); }
.context-rail__empty { font: 400 12px/1.5 var(--z-sans); color: var(--z-ink-4); }
.context-rail__note { font: 400 13px/1.5 var(--z-sans); color: var(--z-ink-2); overflow-wrap: anywhere; }
.context-rail__run { text-align: left; font: 400 13px/1.5 var(--z-sans); color: var(--z-ink-2); padding: 6px 0; overflow-wrap: anywhere; }
.context-rail__run:hover { color: var(--z-blue); }
.context-rail__footer { margin-top: auto; padding-top: var(--z-space-4); border-top: 1px solid var(--z-line); font: 400 11px/1.5 var(--z-sans); color: var(--z-ink-4); }
</style>
