<script setup>
import Icon from './Icon.vue';
defineProps({ current: { type: String, default: '' } });
const emit = defineEmits(['navigate', 'more']);
const items = [
  { id: 'home', name: '首页', icon: 'home' },
  { id: 'chat', name: '对话', icon: 'chat' },
  { id: 'work', name: '任务', icon: 'sparkles' },
  { id: 'agents', name: '智能体', icon: 'bot' }
];
</script>
<template>
  <nav class="tab-bar" aria-label="移动端导航">
    <button
      v-for="item in items"
      :key="item.id"
      class="tab-bar__item"
      :class="{ active: item.id === current }"
      :aria-current="item.id === current ? 'page' : undefined"
      @click="emit('navigate', item.id)"
    >
      <Icon :name="item.icon" :size="22" />
      <span>{{ item.name }}</span>
    </button>
    <button class="tab-bar__item" aria-label="更多" aria-haspopup="dialog" @click="emit('more')">
      <Icon name="more" :size="22" />
      <span>更多</span>
    </button>
  </nav>
</template>
