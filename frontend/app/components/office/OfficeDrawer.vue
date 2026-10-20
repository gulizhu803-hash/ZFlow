<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue';
import Icon from '../Icon.vue';
defineProps({ title: String, full: Boolean }); const emit = defineEmits(['close']); const panel = ref(); let previous;
function key(e) {
  if (e.key === 'Escape') { e.stopPropagation(); emit('close'); }
  if (e.key !== 'Tab') return;
  const nodes = [...panel.value.querySelectorAll('button,input,select,textarea,a[href],[tabindex="0"]')].filter(el => !el.disabled && el.offsetParent !== null);
  const first = nodes[0], last = nodes.at(-1);
  if (!first) { e.preventDefault(); return; }
  if (e.shiftKey && (document.activeElement === first || document.activeElement === panel.value)) { last.focus(); e.preventDefault(); }
  if (!e.shiftKey && document.activeElement === last) { first.focus(); e.preventDefault(); }
}
onMounted(async () => { previous = document.activeElement; document.addEventListener('keydown', key); await nextTick(); panel.value.focus(); });
onBeforeUnmount(() => { document.removeEventListener('keydown', key); previous?.focus(); });
</script>
<template><Teleport to="body"><div class="office-drawer-shade" @mousedown.self="emit('close')"><section ref="panel" class="office-drawer" :class="{ 'is-full': full }" role="dialog" aria-modal="true" :aria-label="title" tabindex="-1"><header><h2 :title="title">{{ title }}</h2><slot name="actions" /><button class="icon-button" aria-label="关闭侧栏" @click="emit('close')"><Icon name="close" /></button></header><div class="office-drawer-body"><slot /></div></section></div></Teleport></template>
