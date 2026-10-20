<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue';
import Icon from './Icon.vue';
defineProps({ title: String, wide: Boolean });
const emit = defineEmits(['close']);
const dialog = ref(null);
let previous;
onMounted(() => { previous = document.activeElement; dialog.value.showModal(); });
onBeforeUnmount(() => { dialog.value?.close(); previous?.focus?.(); });
</script>
<template>
  <dialog ref="dialog" class="modal" :class="{ wide }" aria-labelledby="modal-title" @cancel.prevent="emit('close')" @click="e => { if (e.target === dialog) { const b = dialog.getBoundingClientRect(); if (e.clientX < b.left || e.clientX > b.right || e.clientY < b.top || e.clientY > b.bottom) emit('close'); } }">
    <header class="modal-heading"><h2 id="modal-title">{{ title }}</h2><button class="icon-button" aria-label="关闭弹窗" @click="emit('close')"><Icon name="X" /></button></header>
    <div class="modal-content"><slot /></div>
  </dialog>
</template>
