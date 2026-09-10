<script setup>
import Icon from './Icon.vue';
import { strategies } from '../lib/demo.js';
defineProps({ agent: Object, community: Boolean });
defineEmits(['open', 'run', 'like', 'favor']);
</script>
<template>
  <article class="agent-card">
    <div class="agent-card-top"><span class="agent-avatar" :class="strategies[agent.agentType]?.color || 'sage'"><Icon :name="agent.icon || strategies[agent.agentType]?.icon" :size="25" /></span><span class="tag" :class="strategies[agent.agentType]?.color">{{ strategies[agent.agentType]?.label || agent.agentType }}</span></div>
    <button class="card-title" @click="$emit('open', agent)">{{ agent.plazaTitle || agent.agentName }}<Icon name="external" :size="15" /></button>
    <p>{{ agent.plazaDesc || agent.agentDesc || '为你的工作流提供智能支持。' }}</p>
    <footer v-if="!community"><span><i class="tiny-dot"></i>{{ agent.tag || '我的智能体' }}</span><button class="text-button" @click="$emit(agent.repoType === 'favor' ? 'open' : 'run', agent)">{{ agent.repoType === 'favor' ? '查看模板' : '开始任务' }} <Icon name="arrow" :size="15" /></button></footer>
    <footer v-else><span class="creator"><span class="mini-avatar">{{ agent.userName?.slice(0, 1) }}</span>{{ agent.userName }}</span><div class="row"><button class="reaction" :class="{ selected: agent.liked }" :aria-label="(agent.liked ? '取消点赞 ' : '点赞 ') + agent.plazaTitle" @click="$emit('like', agent)"><Icon name="heart" :size="15" />{{ agent.likeCount || 0 }}</button><button class="reaction" :class="{ selected: agent.favored }" :aria-label="(agent.favored ? '取消收藏 ' : '收藏 ') + agent.plazaTitle" @click="$emit('favor', agent)"><Icon name="bookmark" :size="15" /></button></div></footer>
  </article>
</template>
