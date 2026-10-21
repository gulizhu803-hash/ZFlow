<script setup>
import { computed } from 'vue';
import Icon from '../Icon.vue';
const props = defineProps({ items: { type: Array, default: () => [] } });
const emit = defineEmits(['select', 'open-today']);
const count = computed(() => String(props.items.filter(item => !item.empty).length).padStart(2, '0'));
const iconFor = item => item.icon || ({ run: 'play', schedule: 'clock', recent: 'chat' })[item.kind] || 'clock';
</script>
<template>
  <section class="today-rail" aria-labelledby="home-today-label">
    <header class="today-rail__head">
      <span id="home-today-label" class="today-rail__label">TODAY</span>
      <span class="today-rail__count" :aria-label="`今日 ${count} 项`">{{ count }}</span>
    </header>
    <div class="today-rail__list">
      <component :is="item.actionable ? 'button' : 'div'" v-for="item in items" :key="item.key" class="today-rail__row" :class="{ 'is-empty': item.empty, 'is-live': item.live }" :type="item.actionable ? 'button' : undefined" @click="item.actionable && emit('select', item)">
        <span class="today-rail__icon"><i v-if="item.live" class="today-rail__pulse" aria-hidden="true"></i><Icon v-else :name="iconFor(item)" :size="16" /></span>
        <span class="today-rail__body"><strong class="today-rail__title">{{ item.title }}</strong><span class="today-rail__meta">{{ item.meta }}</span></span>
        <Icon v-if="item.actionable" class="today-rail__arrow" name="chevron" :size="15" />
      </component>
    </div>
    <button class="today-rail__footer" type="button" @click="emit('open-today')">打开今日视图 <Icon name="arrow" :size="15" /></button>
  </section>
</template>
