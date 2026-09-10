<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue';
import Icon from './Icon.vue';
defineProps({ title: String, wide: Boolean });
const emit = defineEmits(['close']), panel = ref(); let previous;
function key(event) {
  if (event.key === 'Escape') emit('close');
  if (event.key !== 'Tab') return;
  const nodes = [...panel.value.querySelectorAll('button, input, textarea, select, a[href], [tabindex="0"]')].filter(el => !el.disabled && el.offsetParent !== null);
  if (!nodes.length) return;
  const first = nodes[0], last = nodes[nodes.length - 1];
  if (event.shiftKey && document.activeElement === first) { last.focus(); event.preventDefault(); }
  if (!event.shiftKey && document.activeElement === last) { first.focus(); event.preventDefault(); }
}
onMounted(async () => { previous = document.activeElement; document.body.style.overflow = 'hidden'; document.addEventListener('keydown', key); await nextTick(); (panel.value.querySelector('[autofocus], input, textarea') || panel.value).focus(); });
onBeforeUnmount(() => { document.body.style.overflow = ''; document.removeEventListener('keydown', key); previous?.focus(); });
</script>
<template><Teleport to="body"><div class="modal-backdrop" @mousedown.self="emit('close')"><section ref="panel" class="modal" :class="{ wide }" role="dialog" aria-modal="true" :aria-label="title" tabindex="-1"><header class="modal-head"><h2>{{ title }}</h2><button class="icon-button" aria-label="关闭弹窗" @click="emit('close')"><Icon name="close" /></button></header><slot /></section></div></Teleport></template>
