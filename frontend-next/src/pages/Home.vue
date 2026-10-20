<script setup>
import { computed, ref } from 'vue';
import Icon from '../components/Icon.vue';
import Composer from '../components/Composer.vue';
import ArtifactPreview from '../components/ArtifactPreview.vue';
import { templates, sampleContent, typeInfo } from '../lib/artifacts.js';
import { begin, recentSessions, go, formatTime } from '../lib/store.js';
const filter = ref('all');
const visibleTemplates = computed(() => filter.value === 'all' ? templates : templates.filter(t => t.type === filter.value));
const date = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(new Date());
</script>
<template>
  <div class="page home-page">
    <section class="home-intro"><div class="home-greeting"><span class="intro-mark"><Icon name="Sparkles" :size="20" /></span><span>你的智能工作空间</span><time>{{ date }}</time></div><h1>让想法，更进一步<span class="blue-period">。</span></h1><p>一起思考、梳理知识，把复杂的工作变成清晰的作品。</p></section>
    <Composer @submit="begin" />
    <div class="prompt-suggestions"><button @click="begin({ prompt: '帮我梳理这个项目的系统架构', type: 'diagram', templateId: 'architecture' })"><Icon name="Workflow" :size="15" />画一张架构图</button><button @click="begin({ prompt: '帮我整理一份项目技术方案', type: 'document', templateId: 'proposal' })"><Icon name="FileText" :size="15" />起草技术方案</button><button @click="go('knowledge')"><Icon name="BookOpen" :size="15" />整理我的知识</button><button @click="go('agents')"><Icon name="Bot" :size="15" />找一个 AI 助手</button></div>
    <section class="templates-section"><div class="section-heading"><div><h2>从这里开始</h2><span class="section-caption">给下一件事，一个清晰的起点</span></div><div class="filter-tabs" aria-label="模板类型"><button v-for="tab in [{ id: 'all', label: '全部' }, { id: 'diagram', label: '图表' }, { id: 'document', label: '文档' }, { id: 'table', label: '表格' }]" :key="tab.id" :class="{ active: filter === tab.id }" :aria-pressed="filter === tab.id" @click="filter = tab.id">{{ tab.label }}</button></div></div>
      <div class="template-grid"><button v-for="template in visibleTemplates" :key="template.id" class="template-card" @click="begin({ prompt: template.prompt, type: template.type, templateId: template.id })"><div class="template-visual" :class="template.type"><div class="template-preview-label"><span>{{ template.label }}</span><span>示例</span></div><ArtifactPreview :type="template.type" :content="sampleContent(template.type)" miniature /><span class="template-open"><Icon name="ArrowUpRight" :size="17" /></span></div><div class="template-description"><span class="template-kind"><Icon :name="template.icon" :size="14" />{{ template.tag }}</span><h3>{{ template.name }}</h3><p>{{ template.description }}</p></div></button></div>
    </section>
    <section class="recent-section"><div class="section-heading"><div><h2>最近的工作</h2><span class="section-caption">继续上一次的思考</span></div><button class="text-button" @click="go('library')">打开文件库 <Icon name="ArrowRight" :size="15" /></button></div><div v-if="recentSessions.length" class="recent-list"><button v-for="session in recentSessions.slice(0, 3)" :key="session.id" class="recent-row" @click="go('workspace', session.id)"><span class="file-icon"><Icon :name="typeInfo[session.type]?.icon" /></span><strong>{{ session.title }}</strong><span class="subtle-tag">本地预览</span><time>{{ formatTime(session.updatedAt) }}</time><Icon name="ChevronRight" :size="16" /></button></div><div v-else class="recent-empty"><span class="empty-file-icon"><Icon name="FolderOpen" :size="23" /></span><div><strong>每一份好想法，都有迹可循</strong><p>从上方开始一个任务，你的对话与作品会保存在这里。</p></div><Icon name="ArrowUpRight" :size="20" /></div></section>
    <footer class="page-footer"><span><Icon name="LockKeyhole" :size="13" />独立设计预览 · 数据保存在当前浏览器</span><span>ZFlow</span></footer>
  </div>
</template>
