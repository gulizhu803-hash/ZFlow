<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue';
import Icon from './components/Icon.vue';
import Modal from './components/Modal.vue';
import RailNav from './components/RailNav.vue';
import TabBar from './components/TabBar.vue';
import Sheet from './components/Sheet.vue';
import Home from './pages/Home.vue';
import Conversation from './pages/Conversation.vue';
import Agents from './pages/Agents.vue';
import Knowledge from './pages/Knowledge.vue';
import Plaza from './pages/Plaza.vue';
import Settings from './pages/Settings.vue';
import Schedules from './pages/Schedules.vue';
import Admin from './pages/Admin.vue';
import Office from './pages/Office.vue';
import { state, isDemo, nav, readRoute, refresh, signIn, signOut, report, recentSessions, ownerId } from './lib/workspace.js';
import { post } from './lib/api.js';
import { modeForPage } from './lib/shell-mode.js';
const brandUrl = import.meta.env.BASE_URL + 'brand.svg';
const menu = [
  { id: 'home', name: '工作台', icon: 'grid' }, { id: 'chat', name: '自由对话', icon: 'chat' }, { id: 'work', name: '智能任务', icon: 'sparkles' },
  { id: 'agents', name: '我的智能体', icon: 'bot', section: true }, { id: 'knowledge', name: '知识库', icon: 'book' }, { id: 'plaza', name: '发现广场', icon: 'compass' }, { id: 'schedules', name: '定时任务', icon: 'clock' }
];
menu.splice(3, 0, { id: 'office', name: '智能办公', icon: 'workflow' });
const railItems = menu.map(({ id, name, icon }) => ({ id, name, icon }));
const moreItems = [
  { id: 'knowledge', name: '知识库', icon: 'book' },
  { id: 'plaza', name: '发现广场', icon: 'compass' },
  { id: 'schedules', name: '定时任务', icon: 'clock' },
  { id: 'office', name: '智能办公', icon: 'workflow' },
  { id: 'settings', name: '设置', icon: 'settings' }
];
const views = { home: Home, chat: Conversation, work: Conversation, office: Office, agents: Agents, knowledge: Knowledge, plaza: Plaza, schedules: Schedules, settings: Settings, admin: Admin };
const pageName = computed(() => menu.find(i => i.id === state.route.page)?.name || ({ settings: '个人设置', admin: '管理中心' })[state.route.page] || '页面未找到');
const mode = computed(() => modeForPage(state.route.page));
const smallScreen = ref(window.matchMedia('(max-width: 767px)').matches);
const onResize = () => { smallScreen.value = window.matchMedia('(max-width: 767px)').matches; };
const mobile = ref(false), authMode = ref('login'), userName = ref(''), password = ref(''), authBusy = ref(false), authError = ref(''), query = ref(''), moreOpen = ref(false);
const searchResults = computed(() => [
  ...menu.map(item => ({ title: item.name, description: '前往页面', icon: item.icon, page: item.id })),
  ...recentSessions.value.map(s => ({ title: s.title, description: '已加载的最近会话', icon: s.type === 'office' ? 'workflow' : 'chat', page: s.type, params: { session: s.id } })),
  ...(state.repo.self || []).map(a => ({ title: a.agentName, description: '我的智能体', icon: 'bot', page: 'agents', params: { detail: a.agentId } }))
].filter(item => item.title?.toLowerCase().includes(query.value.toLowerCase())).slice(0, 12));
function go(page, params) { mobile.value = false; nav(page, params); }
function onRailSelect(id) {
  if (id === 'account') { if (isDemo.value) state.authOpen = true; else go('settings'); return; }
  go(id);
}
function onTabNavigate(page) { go(page); }
function onTabMore() { mobile.value = false; moreOpen.value = true; }
function onMoreSelect(item) { moreOpen.value = false; go(item.id); }
async function authenticate() {
  if (!userName.value.trim() || !password.value.trim() || authBusy.value) return;
  authBusy.value = true; authError.value = '';
  try { const auth = await post('/user/auth/' + authMode.value, { json: { userName: userName.value.trim(), password: password.value } }); password.value = ''; await signIn(auth); }
  catch (error) { authError.value = error.message; } finally { authBusy.value = false; }
}
function shortcut(event) { if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); state.searchOpen = !state.searchOpen; } }
onMounted(() => { readRoute(); refresh().catch(report); window.addEventListener('hashchange', readRoute); window.addEventListener('keydown', shortcut); window.addEventListener('resize', onResize); });
onBeforeUnmount(() => { window.removeEventListener('hashchange', readRoute); window.removeEventListener('keydown', shortcut); window.removeEventListener('resize', onResize); });
</script>
<template>
  <div class="app-shell" :class="'mode-' + mode">
    <button v-if="mobile" class="sidebar-scrim" aria-label="关闭导航" @click="mobile = false"></button>
    <aside :inert="smallScreen && !mobile" class="sidebar" :class="{ 'is-open': mobile }">
      <a class="brand" href="#/home"><img :src="brandUrl" alt="" /><span>ZFlow<span class="brand-dot">.</span></span></a>
      <button class="workspace-picker" @click="go('settings')"><span class="workspace-avatar">Z</span><span><strong>我的工作空间</strong><small>Personal workspace</small></span><Icon name="down" :size="14" /></button>
      <button class="new-task-button" @click="go('work')"><Icon name="plus" :size="18" /><span>开启新任务</span><span class="new-task-spark">✦</span></button>
      <nav aria-label="主导航"><div v-for="item in menu" :key="item.id"><div v-if="item.section" class="nav-section-label">工作空间</div><a :href="'#/' + item.id" class="nav-item" :class="{ active: state.route.page === item.id }" :aria-current="state.route.page === item.id ? 'page' : undefined" @click="mobile = false"><Icon :name="item.icon" :size="19" /><span>{{ item.name }}</span><span v-if="item.id === 'agents' && state.repo.self?.length" class="nav-count">{{ state.repo.self.length }}</span><span v-if="item.id === 'plaza'" class="nav-new">NEW</span></a></div></nav>
      <div class="sidebar-bottom"><div class="sidebar-tip"><span class="tip-flower">✳</span><strong>小小智能体，大大可能。</strong><p>看看其他创造者<br />正在用 AI 做些什么。</p><button class="text-button" @click="go('plaza')">寻找灵感 <Icon name="arrow" :size="15" /></button></div><button class="nav-item" @click="state.helpOpen = true"><Icon name="help" :size="18" /><span>使用指南</span><Icon name="external" :size="14" /></button><a v-if="state.auth?.userRole === 'admin'" class="nav-item" href="#/admin"><Icon name="shield" :size="18" /><span>管理中心</span></a><a class="nav-item" :class="{ active: state.route.page === 'settings' }" href="#/settings"><Icon name="settings" :size="18" /><span>设置</span></a><button class="profile-button" @click="isDemo ? state.authOpen = true : go('settings')"><span class="profile-avatar">{{ state.auth?.userName?.slice(0, 1).toUpperCase() || 'Z' }}</span><span><strong>{{ state.auth?.userName || '访客工作空间' }}</strong><small>{{ isDemo ? '登录以连接你的 AI' : '个人账号' }}</small></span><Icon :name="isDemo ? 'arrow' : 'more'" :size="17" /></button></div>
    </aside>
    <RailNav :items="railItems" :current="state.route.page" :account="state.auth?.userName?.slice(0, 1).toUpperCase() || 'Z'" title="主导航" @select="onRailSelect" />
    <div class="main-shell"><header class="topbar"><div class="breadcrumb"><button class="icon-button mobile-menu" aria-label="打开导航" @click="mobile = true"><Icon name="menu" /></button><span>工作空间</span><span class="slash">/</span><strong>{{ pageName }}</strong></div><div class="topbar-actions"><button class="search-trigger" aria-label="搜索工作空间" @click="state.searchOpen = true"><Icon name="search" :size="16" /><span>搜索任何内容</span><kbd>Ctrl K</kbd></button><span class="topbar-divider"></span><button v-if="isDemo" class="mode-badge" @click="state.authOpen = true"><i></i>演示模式 <Icon name="down" :size="12" /></button><span v-else class="mode-badge connected"><i></i>已登录</span><button class="top-avatar" aria-label="账号设置" @click="isDemo ? state.authOpen = true : go('settings')">{{ state.auth?.userName?.slice(0, 1).toUpperCase() || 'Z' }}</button></div></header>
      <div v-if="state.loadError && state.route.page !== 'office'" class="connection-error" role="alert"><Icon name="alert" :size="17" /><span>{{ state.loadError }}</span><button @click="refresh">重新连接</button></div>
      <div v-if="state.loading" class="loading-bar" aria-label="正在加载工作空间"></div>
      <main id="main-content"><component :is="views[state.route.page]" v-if="views[state.route.page]" :key="state.route.page + (state.auth ? ownerId(state.auth) : 'demo')" /><div v-else class="empty-state"><Icon name="compass" :size="40" /><h1>还没走到的地方</h1><p>这个页面不存在，回到工作台继续吧。</p><button class="primary-button" @click="go('home')">返回工作台</button></div></main>
    </div>
    <TabBar :current="state.route.page" @navigate="onTabNavigate" @more="onTabMore" />
    <Sheet v-if="moreOpen" title="更多" @close="moreOpen = false">
      <div class="sheet-menu">
        <button v-for="item in moreItems" :key="item.id" @click="onMoreSelect(item)"><Icon :name="item.icon" :size="18" /><span>{{ item.name }}</span><Icon name="arrow" :size="16" /></button>
        <button @click="moreOpen = false; state.helpOpen = true"><Icon name="help" :size="18" /><span>使用指南</span><Icon name="external" :size="14" /></button>
        <button v-if="state.auth?.userRole === 'admin'" @click="onMoreSelect({ id: 'admin' })"><Icon name="shield" :size="18" /><span>管理中心</span><Icon name="arrow" :size="16" /></button>
      </div>
    </Sheet>
  </div>
  <Transition name="toast"><div v-if="state.toast" class="toast-message" :class="state.toast.type" role="status"><Icon :name="state.toast.type === 'error' ? 'alert' : 'checkCircle'" :size="19" /><span>{{ state.toast.message }}</span><button class="icon-button" aria-label="关闭通知" @click="state.toast = null"><Icon name="close" :size="16" /></button></div></Transition>
  <Modal v-if="state.authOpen" :title="authMode === 'login' ? '欢迎回到 ZFlow' : '开始你的创造之旅'" @close="state.authOpen = false; password = ''; authError = ''"><form class="modal-body form-stack" @submit.prevent="authenticate"><div class="auth-intro"><img :src="brandUrl" alt="ZFlow" /><p>连接你的模型、知识与智能体。<br />让好想法自然发生。</p></div><div class="segmented"><button type="button" :class="{ active: authMode === 'login' }" @click="authMode = 'login'; authError = ''">登录</button><button type="button" :class="{ active: authMode === 'register' }" @click="authMode = 'register'; authError = ''">注册</button></div><label>用户名<input v-model="userName" autocomplete="username" required placeholder="输入你的用户名" /></label><label>密码<input v-model="password" type="password" :autocomplete="authMode === 'login' ? 'current-password' : 'new-password'" required placeholder="输入密码" /></label><div v-if="authError" class="form-error" role="alert">{{ authError }}</div><button class="primary-button full" :disabled="authBusy">{{ authBusy ? '正在连接…' : authMode === 'login' ? '登录工作空间' : '创建账号' }}<Icon name="arrow" :size="17" /></button><p class="small muted centered">当前访客可体验界面，登录后使用真实服务。</p></form></Modal>
  <Modal v-if="state.searchOpen" title="搜索工作空间" @close="state.searchOpen = false; query = ''"><div class="modal-body"><div class="search-field large"><Icon name="search" /><input v-model="query" placeholder="搜索页面、智能体、会话…" aria-label="搜索工作空间内容" autofocus /></div><div class="command-results"><button v-for="(result, i) in searchResults" :key="i" @click="go(result.page, result.params); state.searchOpen = false; query = ''"><Icon :name="result.icon" :size="18" /><span>{{ result.title }}<small>{{ result.description }}</small></span><Icon name="arrow" :size="16" /></button><div v-if="!searchResults.length" class="empty-inline">没有找到相关内容，试试其他关键词。</div></div></div></Modal>
  <Modal v-if="state.helpOpen" title="从一个好想法开始" @close="state.helpOpen = false"><div class="modal-body guide"><p>ZFlow 把对话、智能任务和你的知识连接在一起。</p><ol><li><strong>连接模型</strong><p>登录后，在设置中添加可用的模型服务。对话客户端由服务端提供。</p></li><li><strong>创建 AI 伙伴</strong><p>选择模型、执行策略和工具，写清它的职责，也可以从广场复制模板。</p></li><li><strong>交给它一个目标</strong><p>进入智能任务，跟随实时阶段输出查看进展。自由对话更适合即时问答。</p></li></ol><div class="info-note">演示模式中的内容用于体验页面，不会调用模型或向后端保存。</div><button class="primary-button full" @click="state.helpOpen = false; go('agents')">去认识我的 AI 伙伴 <Icon name="arrow" :size="17" /></button></div></Modal>
</template>
