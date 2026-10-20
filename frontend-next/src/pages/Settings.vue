<script setup>
import { ref } from 'vue';
import Icon from '../components/Icon.vue';
import { data, commit, notify, exportWorkspace } from '../lib/store.js';
const name = ref(data.preferences.name);
function saveName() { if (!name.value.trim()) return; if (commit(d => d.preferences.name = name.value.trim())) notify('工作空间名称已更新'); }
function preference(key, value) { commit(d => d.preferences[key] = value); }
</script>
<template>
  <div class="page settings-page"><header class="page-heading"><div><span class="small-label">按照你的习惯工作</span><h1>设置</h1><p>管理工作空间、界面偏好与本地数据。</p></div></header>
    <section class="settings-section"><h2>工作空间</h2><form class="settings-row" @submit.prevent="saveName"><div><strong>空间名称</strong><p>显示在导航与页面顶部。</p></div><div class="inline-form"><input v-model="name" aria-label="工作空间名称" maxlength="30" required /><button class="secondary-button" type="submit">保存</button></div></form></section>
    <section class="settings-section"><h2>外观与偏好</h2><div class="settings-row"><div><strong>界面主题</strong><p>白色、浅灰与克莱因蓝，保持清晰的视觉层级。</p></div><div class="theme-choice"><span class="theme-swatch"><i></i><i></i><i></i></span><span>明亮</span><Icon name="Check" :size="16" /></div></div><div class="settings-row"><div><strong>紧凑导航</strong><p>缩小导航间距，在侧栏显示更多内容。</p></div><button class="toggle" :class="{ on: data.preferences.compact }" role="switch" :aria-checked="!!data.preferences.compact" aria-label="紧凑导航" @click="preference('compact', !data.preferences.compact)"><span></span></button></div><div class="settings-row"><div><strong>按 Enter 发送</strong><p>关闭后，Enter 换行，使用发送按钮提交。</p></div><button class="toggle" :class="{ on: data.preferences.enterToSend !== false }" role="switch" :aria-checked="data.preferences.enterToSend !== false" aria-label="按 Enter 发送" @click="preference('enterToSend', data.preferences.enterToSend === false)"><span></span></button></div></section>
    <section class="settings-section"><h2>数据与存储</h2><div class="settings-row"><div><strong>导出工作空间</strong><p>备份 {{ data.artifacts.length }} 份作品、{{ data.sessions.length }} 段对话与 {{ data.sources.length }} 份资料。</p></div><button class="secondary-button" @click="exportWorkspace"><Icon name="Download" :size="15" />导出 JSON 备份</button></div><div class="settings-storage-note"><Icon name="LockKeyhole" :size="17" /><p>数据仅保存在当前浏览器。清除浏览器数据会删除本地记录，建议定期导出重要作品。</p></div></section>
    <section class="settings-section about-section"><h2>关于这个设计</h2><div class="about-brand"><img src="/favicon.svg" alt="" /><div><strong>ZFlow · 智能工作空间</strong><span>独立 UI 设计预览</span></div></div><p>基于 ZFlow 的对话、办公、智能体与知识库能力重新设计。当前版本提供示例对话、本地编辑、版本历史与文件导出，尚未连接现有后端服务。</p><div class="reference-links"><a href="https://claude.com/features/artifacts" target="_blank" rel="noreferrer">Claude Artifacts <Icon name="ArrowUpRight" :size="14" /></a><a href="https://www.perplexity.ai/hub/blog/introducing-internal-knowledge-search-and-spaces" target="_blank" rel="noreferrer">Perplexity Spaces <Icon name="ArrowUpRight" :size="14" /></a><a href="https://linear.app/now/how-we-redesigned-the-linear-ui" target="_blank" rel="noreferrer">Linear <Icon name="ArrowUpRight" :size="14" /></a></div></section>
  </div>
</template>
