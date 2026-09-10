<script setup>
import { computed, onBeforeUnmount, watch } from 'vue';
import { RouterView, useRoute } from 'vue-router';
import Sidebar from './components/Sidebar.vue';
import Toast from './components/Toast.vue';
import './workspace.css';
import { useSettingsStore } from './router/pinia';

const route = useRoute();
const hideSidebar = computed(() => route.meta?.hideSidebar);
const isPreview = computed(() => Boolean(import.meta.env.DEV && route.meta?.preview));

const settingsStore = useSettingsStore();
const resolvedTheme = computed(() => (settingsStore.theme === 'dark' ? 'dark' : 'light'));
let switchRaf = 0;

watch(
    resolvedTheme,
    (theme) => {
        const root = document.documentElement;
        root.classList.add('theme-switching');
        root.setAttribute('data-theme', theme);
        root.style.colorScheme = theme;
        if (switchRaf) cancelAnimationFrame(switchRaf);
        switchRaf = requestAnimationFrame(() => {
            root.classList.remove('theme-switching');
            switchRaf = 0;
        });
    },
    { immediate: true }
);

onBeforeUnmount(() => {
    if (switchRaf) {
        cancelAnimationFrame(switchRaf);
    }
    document.documentElement.classList.remove('theme-switching');
});
</script>

<template>
    <div class="workspace-app" :class="{ 'no-sidebar': hideSidebar }">
        <Sidebar v-if="!hideSidebar" :preview="isPreview" />
        <div class="workspace-main">
            <div v-if="isPreview" class="preview-notice"><span>界面预览 · 示例数据，仅展示页面与交互</span><RouterLink to="/login">登录使用真实服务 →</RouterLink></div>
            <div class="workspace-route"><RouterView v-slot="{ Component }"><component :is="Component" :key="route.path" :preview="isPreview" /></RouterView></div>
            <Toast />
        </div>
    </div>
</template>
