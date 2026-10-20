<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue';
import Icon from './components/Icon.vue';
import Modal from './components/Modal.vue';
import Home from './pages/Home.vue';
import Workspace from './pages/Workspace.vue';
import Library from './pages/Library.vue';
import Agents from './pages/Agents.vue';
import Knowledge from './pages/Knowledge.vue';
import Settings from './pages/Settings.vue';
import { data, ui, go, readRoute, recentSessions } from './lib/store.js';
import { typeInfo } from './lib/artifacts.js';
const menu = [{ id: 'home', name: '工作台', icon: 'LayoutGrid' }, { id: 'chat', name: '自由对话', icon: 'MessageSquare' }, { id: 'office', name: '智能办公', icon: 'Workflow' }, { id: 'library', name: '文件库', icon: 'FolderOpen', section: true }, { id: 'agents', name: '智能体', icon: 'Bot' }, { id: 'knowledge', name: '知识库', icon: 'BookOpen' }];
const views = { home: Home, workspace: Workspace, chat: Workspace, office: Workspace, library: Library, agents: Agents, knowledge: Knowledge, settings: Settings };
const pageName = computed(() => ui.page === 'workspace' ? '工作空间' : menu.find(m => m.id === ui.page)?.name || (ui.page === 'settings' ? '设置' : '页面不存在'));
const query = ref(''), collapsed = ref(false), smallScreen = ref(window.innerWidth <= 760);
const results = computed(() => [...menu.map(m => ({ ...m, label: m.name, detail: '页面', page: m.id })), ...recentSessions.value.map(s => ({ id: s.id, label: s.title, detail: '本地对话', icon: typeInfo[s.type]?.icon, page: 'workspace', routeId: s.id }))].filter(r => r.label.toLowerCase().includes(query.value.trim().toLowerCase())));
function shortcut(e) { if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); ui.search = !ui.search; query.value = ''; } }
function resize() { smallScreen.value = window.innerWidth <= 760; }
function navigateSearch(result) { ui.search = false; go(result.page, result.routeId); }
onMounted(() => { readRoute(); window.addEventListener('hashchange', readRoute); window.addEventListener('keydown', shortcut); window.addEventListener('resize', resize); });
onBeforeUnmount(() => { window.removeEventListener('hashchange', readRoute); window.removeEventListener('keydown', shortcut); window.removeEventListener('resize', resize); });
</script>
<template>
  <a href="#main-content" class="skip-link" @click.prevent="$refs.main.focus()">跳到主内容</a>
  <div class="app-shell" :class="{ 'sidebar-collapsed': collapsed, 'compact-density': data.preferences.compact }">
    <button v-if="ui.mobile" class="sidebar-scrim" aria-label="关闭导航" @click="ui.mobile = false"></button>
    <aside class="sidebar" :class="{ open: ui.mobile }" :inert="smallScreen && !ui.mobile">
      <div class="sidebar-brand"><a href="#/home" class="brand" aria-label="ZFlow 首页"><img src="/favicon.svg" alt="" /><span>ZFlow</span></a><button class="icon-button collapse-button" aria-label="收起侧栏" @click="collapsed = !collapsed"><Icon name="PanelLeftClose" :size="17" /></button><button class="icon-button mobile-close" aria-label="关闭导航" @click="ui.mobile = false"><Icon name="X" /></button></div>
      <button class="workspace-switch" @click="go('settings')"><span class="workspace-symbol"><Icon name="Layers" :size="16" /></span><span>{{ data.preferences.name }}</span><Icon name="ChevronsUpDown" :size="13" /></button>
      <button class="new-task" @click="go('chat')"><Icon name="Plus" :size="17" /><span>新建任务</span><span class="new-task-shortcut"><Icon name="Pencil" :size="13" /></span></button>
      <nav aria-label="主导航"><template v-for="item in menu" :key="item.id"><div v-if="item.section" class="nav-section">工作空间</div><a :href="`#/${item.id}`" class="nav-item" :class="{ active: ui.page === item.id || (ui.page === 'workspace' && item.id === 'office') }" :aria-current="ui.page === item.id ? 'page' : undefined" :title="item.name" @click="ui.mobile = false"><Icon :name="item.icon" :size="18" /><span>{{ item.name }}</span><span v-if="item.id === 'library' && data.artifacts.length" class="nav-counter">{{ data.artifacts.length }}</span></a></template></nav>
      <div class="sidebar-recents"><div class="nav-section">最近对话 <button class="icon-button" aria-label="搜索对话" @click="ui.search = true"><Icon name="Search" :size="13" /></button></div><a v-for="session in recentSessions.slice(0, 4)" :key="session.id" :href="`#/workspace/${session.id}`" class="sidebar-session" :title="session.title" @click="ui.mobile = false"><span class="session-dot"></span><span>{{ session.title }}</span></a><p v-if="!recentSessions.length" class="no-recents">下一段思考，从这里留下。</p></div>
      <div class="sidebar-bottom"><div class="preview-note"><span class="preview-note-icon"><Icon name="Boxes" :size="19" /></span><strong>你的专属工作空间</strong><p>将知识、对话与作品<br />放在一起，随时继续。</p><button class="text-button" @click="ui.help = true">了解工作方式 <Icon name="ArrowUpRight" :size="14" /></button></div><button class="nav-item" @click="ui.help = true"><Icon name="CircleHelp" :size="17" /><span>帮助与快捷键</span></button><a class="nav-item" href="#/settings" :class="{ active: ui.page === 'settings' }" title="设置"><Icon name="Settings2" :size="17" /><span>设置</span></a><button class="profile-button" @click="go('settings')"><span class="avatar">Z</span><span><strong>本地工作空间</strong><small>个人空间</small></span><Icon name="ChevronsUpDown" :size="14" /></button></div>
    </aside>
    <div class="main-shell"><header class="topbar"><div class="breadcrumb"><button class="icon-button mobile-menu" aria-label="打开导航" @click="ui.mobile = true"><Icon name="Menu" /></button><button v-if="collapsed" class="icon-button expand-button" aria-label="展开侧栏" @click="collapsed = false"><Icon name="PanelLeftOpen" /></button><span>{{ data.preferences.name }}</span><span class="breadcrumb-slash">/</span><strong>{{ pageName }}</strong></div><div class="topbar-actions"><button class="search-trigger" aria-label="搜索工作空间" @click="ui.search = true; query = ''"><Icon name="Search" :size="15" /><span>搜索</span><kbd>Ctrl K</kbd></button><span class="topbar-divider"></span><button class="preview-badge" @click="ui.help = true"><span></span>设计预览</button><button class="icon-button" aria-label="打开使用说明" @click="ui.help = true"><Icon name="CircleHelp" :size="17" /></button></div></header>
      <main id="main-content" ref="main" tabindex="-1" :class="{ 'workspace-main': ['workspace', 'chat', 'office'].includes(ui.page) }"><component :is="views[ui.page]" v-if="views[ui.page]" :key="ui.page + (ui.page === 'workspace' ? ui.id : '')" /><div v-else class="page empty-state"><Icon name="FolderOpen" :size="36" /><h1>这个页面还不存在</h1><button class="primary-button" @click="go('home')">回到工作台</button></div></main>
    </div>
  </div>
  <Transition name="toast"><div v-if="ui.toast" class="toast" :class="{ error: ui.toast.error }" role="status"><Icon :name="ui.toast.error ? 'CircleAlert' : 'CheckCheck'" :size="18" /><span>{{ ui.toast.message }}</span><button class="icon-button" aria-label="关闭提示" @click="ui.toast = null"><Icon name="X" :size="15" /></button></div></Transition>
  <Modal v-if="ui.search" title="搜索工作空间" @close="ui.search = false"><div class="search-field command-search"><Icon name="Search" /><input v-model="query" autofocus placeholder="搜索页面、对话和作品…" aria-label="搜索关键词" @keydown.enter.prevent="results[0] && navigateSearch(results[0])" /><kbd>Esc</kbd></div><div class="command-results"><button v-for="result in results" :key="result.id" @click="navigateSearch(result)"><span class="file-icon"><Icon :name="result.icon" /></span><span><strong>{{ result.label }}</strong><small>{{ result.detail }}</small></span><Icon name="CornerDownLeft" :size="15" /></button><p v-if="!results.length" class="empty-inline">没有找到「{{ query }}」，试试其他关键词。</p></div></Modal>
  <Modal v-if="ui.help" title="让工作连贯地发生" @close="ui.help = false"><div class="help-intro"><img src="/favicon.svg" alt="ZFlow" /><p>从一句需求开始，在同一个空间里<br />思考、编辑，并留下作品。</p></div><div class="help-step"><span>1</span><div><strong>选择一个起点</strong><p>自由输入任务，或从架构图、方案、任务清单示例开始。</p></div></div><div class="help-step"><span>2</span><div><strong>在作品上继续工作</strong><p>编辑节点、文档和表格，保存新版本，随时下载。</p></div></div><div class="help-step"><span>3</span><div><strong>回到你的工作空间</strong><p>对话、作品和资料保存在当前浏览器，刷新后仍可找回。</p></div></div><div class="info-note"><Icon name="Eye" :size="18" /><p>这是独立 UI 设计预览。对话回复与初始作品为示例，不调用 AI 或原项目后端；编辑、版本保存及文件导出可实际使用。</p></div><div class="keyboard-guide"><span>搜索 <kbd>Ctrl K</kbd></span><span>换行 <kbd>Shift Enter</kbd></span><span>关闭弹窗 <kbd>Esc</kbd></span></div></Modal>
</template>
