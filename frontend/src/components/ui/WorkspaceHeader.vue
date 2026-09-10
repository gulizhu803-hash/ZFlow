<script setup>
import FlowIcon from './FlowIcon.vue';
defineProps({ title: String, subtitle: String });
</script>
<template>
  <header class="workspace-header">
    <div class="workspace-breadcrumb"><span>个人工作空间</span><FlowIcon name="chevron" :size="13" /><strong>{{ title }}</strong></div>
    <div class="workspace-header-end"><span v-if="subtitle" class="header-subtitle">{{ subtitle }}</span><slot /></div>
  </header>
</template>
