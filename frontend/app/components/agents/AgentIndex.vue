<script setup>
import Icon from '../Icon.vue';

// Index：紧凑 ListRow，只展示 Shelf 之后的部分；没有 lastUsed 时只显示创建时间。
const props = defineProps({
  agents: { type: Array, default: () => [] },
  strategies: { type: Object, default: () => ({}) },
  total: { type: Number, default: 0 }
});
const emit = defineEmits(['open', 'run']);

function strategyOf(agent) {
  return props.strategies[agent.agentType] || {};
}
function createdOf(agent) {
  if (!agent.createTime) return '—';
  const text = String(agent.createTime).replace('T', ' ');
  return text.slice(0, 16) || '—';
}
</script>
<template>
  <div class="agent-index">
    <p v-if="!agents.length" class="agent-index__empty">{{ total ? '其余智能体已全部显示在上方。' : '还没有智能体。' }}</p>
    <ul v-else class="agent-index__list" role="list">
      <li v-for="agent in agents" :key="agent.repoId || agent.agentId" class="agent-index__row">
        <button class="agent-index__main" type="button" @click="emit('open', agent)">
          <span class="agent-index__name">{{ agent.agentName }}</span>
          <span class="agent-index__desc">{{ agent.agentDesc || '还没有描述。' }}</span>
        </button>
        <span class="agent-index__cell agent-index__strategy">{{ strategyOf(agent).label || agent.agentType }}</span>
        <span class="agent-index__cell agent-index__model">{{ agent.modelName || '—' }}</span>
        <span class="agent-index__cell agent-index__time">{{ createdOf(agent) }}</span>
        <span class="agent-index__actions">
          <button v-if="agent.repoType !== 'favor'" class="agent-index__run" type="button" @click="emit('run', agent)"><Icon name="play" :size="13" />运行</button>
          <button v-else class="agent-index__run" type="button" @click="emit('open', agent)">模板</button>
        </span>
      </li>
    </ul>
  </div>
</template>
