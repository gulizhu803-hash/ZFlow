<script setup>
import Icon from '../Icon.vue';
import { strategies } from '../../lib/demo.js';
defineProps({ agents: { type: Array, default: () => [] } });
const emit = defineEmits(['run', 'open']);
const typeOf = agent => strategies[agent.agentType] || {};
const nameOf = agent => agent.plazaTitle || agent.agentName || '未命名智能体';
const descOf = agent => agent.plazaDesc || agent.agentDesc || '为你的工作流提供智能支持。';
const iconOf = agent => agent.icon || typeOf(agent).icon || 'sparkles';
const indexOf = i => String(i + 1).padStart(2, '0');
</script>
<template>
  <ul class="agent-shelf">
    <li v-for="(agent, i) in agents" :key="agent.agentId || i" class="agent-shelf__item">
      <button class="agent-shelf__stage" type="button" :aria-label="`查看 ${nameOf(agent)}`" @click="emit('open', agent)">
        <span class="agent-shelf__index">{{ indexOf(i) }}</span>
        <span class="agent-shelf__type">{{ typeOf(agent).label || agent.agentType || '智能体' }}</span>
        <span class="agent-shelf__glyph"><Icon :name="iconOf(agent)" :size="40" /></span>
      </button>
      <button class="agent-shelf__title" type="button" @click="emit('open', agent)">{{ nameOf(agent) }}</button>
      <p class="agent-shelf__desc">{{ descOf(agent) }}</p>
      <div class="agent-shelf__meta">
        <span class="agent-shelf__facts">{{ [agent.modelName, agent.tag || typeOf(agent).label].filter(Boolean).join(' · ') }}</span>
        <button class="agent-shelf__run" type="button" @click="emit('run', agent)">{{ agent.repoType === 'favor' ? '查看模板' : '开始任务' }} <Icon name="arrow" :size="14" /></button>
      </div>
    </li>
  </ul>
</template>
