<script setup>
import Icon from '../Icon.vue';

// Shelf：只负责“被看见”。取前 3 条，卡片主体打开详情，运行是独立按钮。
const props = defineProps({
  agents: { type: Array, default: () => [] },
  strategies: { type: Object, default: () => ({}) }
});
const emit = defineEmits(['open', 'run']);

function strategyOf(agent) {
  return props.strategies[agent.agentType] || {};
}
function metaOf(agent) {
  return [agent.modelName, agent.tag].filter(Boolean).join(' · ');
}
</script>
<template>
  <ul class="agent-shelf" role="list">
    <li v-for="(agent, index) in agents" :key="agent.repoId || agent.agentId" class="agent-shelf__card">
      <button class="agent-shelf__main" type="button" @click="emit('open', agent)">
        <span class="agent-shelf__stage" :class="'agent-shelf__stage--' + (strategyOf(agent).color || 'sage')">
          <span class="agent-shelf__badge">{{ strategyOf(agent).label || agent.agentType }}</span>
          <span class="agent-shelf__order">{{ String(index + 1).padStart(2, '0') }}</span>
          <Icon :name="agent.icon || strategyOf(agent).icon || 'bot'" :size="42" />
        </span>
        <span class="agent-shelf__name">{{ agent.agentName }}</span>
        <span class="agent-shelf__desc">{{ agent.agentDesc || '还没有描述。' }}</span>
      </button>
      <span class="agent-shelf__foot">
        <span class="agent-shelf__meta">{{ metaOf(agent) || '未配置模型' }}</span>
        <button v-if="agent.repoType !== 'favor'" class="agent-shelf__run" type="button" @click="emit('run', agent)"><Icon name="play" :size="13" />运行</button>
        <button v-else class="agent-shelf__run" type="button" @click="emit('open', agent)">查看模板</button>
      </span>
    </li>
  </ul>
</template>
