<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue';
import Icon from './Icon.vue';
defineProps({
  title: { type: String, default: '' },
  size: { type: String, default: 'md' } // 'md' = 560px, 'lg' = 760px
});
const emit = defineEmits(['close']);
const panel = ref();
const titleId = 'sheet-title-' + Math.random().toString(36).slice(2);
let previous;
let previousOverflow = '';
function key(event) {
  if (event.key === 'Escape') emit('close');
  if (event.key !== 'Tab') return;
  const nodes = [...panel.value.querySelectorAll('button, input, textarea, select, a[href], [tabindex="0"]')].filter(el => !el.disabled && el.offsetParent !== null);
  if (!nodes.length) return;
  const first = nodes[0], last = nodes[nodes.length - 1];
  const active = document.activeElement;
  // Panel 本身获得焦点，或焦点意外跑到 Sheet 外时，拉回第一个/最后一个可聚焦项。
  if (active === panel.value || !panel.value.contains(active)) {
    (event.shiftKey ? last : first).focus();
    event.preventDefault();
    return;
  }
  if (event.shiftKey && active === first) { last.focus(); event.preventDefault(); }
  if (!event.shiftKey && active === last) { first.focus(); event.preventDefault(); }
}
onMounted(async () => {
  previous = document.activeElement;
  previousOverflow = document.body.style.overflow;
  document.body.style.overflow = 'hidden';
  document.addEventListener('keydown', key);
  await nextTick();
  (panel.value.querySelector('[autofocus], input, textarea, button') || panel.value).focus();
});
onBeforeUnmount(() => {
  document.body.style.overflow = previousOverflow;
  document.removeEventListener('keydown', key);
  if (previous && typeof previous.focus === 'function') previous.focus();
});
</script>
<template>
  <Teleport to="body">
    <div class="sheet-backdrop" @mousedown.self="emit('close')">
      <section ref="panel" class="sheet" :class="'sheet--' + size" role="dialog" aria-modal="true" :aria-labelledby="title ? titleId : undefined" :aria-label="title ? undefined : '对话框'" tabindex="-1">
        <header class="sheet__head">
          <h2 :id="titleId">{{ title }}</h2>
          <button class="icon-button" aria-label="关闭" @click="emit('close')"><Icon name="close" /></button>
        </header>
        <div class="sheet__body"><slot /></div>
      </section>
    </div>
  </Teleport>
</template>
