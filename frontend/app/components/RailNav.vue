<script setup>
import Icon from './Icon.vue';
defineProps({
  items: { type: Array, default: () => [] },
  current: { type: String, default: '' },
  title: { type: String, default: '主导航' },
  account: { type: String, default: 'Z' }
});
const emit = defineEmits(['select']);
</script>
<template>
  <nav class="rail-nav" :aria-label="title">
    <div class="rail-nav__list">
      <button
        v-for="item in items"
        :key="item.id"
        class="rail-nav__item"
        :class="{ active: item.id === current }"
        :aria-current="item.id === current ? 'page' : undefined"
        :aria-label="item.name"
        :title="item.name"
        @click="emit('select', item.id)"
      >
        <Icon :name="item.icon" :size="20" />
        <span class="rail-nav__label">{{ item.name }}</span>
      </button>
    </div>
    <div class="rail-nav__footer">
      <button
        class="rail-nav__item"
        :class="{ active: current === 'settings' }"
        aria-label="设置"
        title="设置"
        @click="emit('select', 'settings')"
      >
        <Icon name="settings" :size="20" />
        <span class="rail-nav__label">设置</span>
      </button>
      <button
        class="rail-nav__item"
        :class="{ active: current === 'account' }"
        aria-label="账号"
        title="账号"
        @click="emit('select', 'account')"
      >
        <span class="rail-nav__avatar" aria-hidden="true">{{ account }}</span>
        <span class="rail-nav__label">账号</span>
      </button>
    </div>
  </nav>
</template>
