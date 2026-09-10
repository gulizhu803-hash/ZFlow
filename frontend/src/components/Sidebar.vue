<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAgentStore, useAuthStore, useChatStore, useSettingsStore } from '../router/pinia';
import { deleteSession, insertSession, listSessions, updateSession } from '../request/api';
import { notifyAppError } from '../request/request';
import { mapWorkspaceSession, unwrapWorkspaceData, workspacePath } from '../utils/workspace';
import { previewSessions } from '../utils/workspacePreview';
import FlowIcon from './ui/FlowIcon.vue';

const props = defineProps({ preview: Boolean });
const route = useRoute();
const router = useRouter();
const chatStore = useChatStore();
const agentStore = useAgentStore();
const authStore = useAuthStore();
const settings = useSettingsStore();
const mobileOpen = ref(false);
const query = ref('');
const loading = ref(false);
const loadError = ref('');
const creating = ref(false);
const editTarget = ref(null);
const editTitle = ref('');
const deleteTarget = ref(null);
const saving = ref(false);
const user = computed(() => props.preview ? { username: '体验者' } : authStore.user || { username: '访客' });
const activePath = computed(() => route.path.replace('/preview', ''));
const sessions = computed(() => {
    const all = props.preview ? previewSessions : [...chatStore.chats.map(s => ({ ...s, sessionType: 'chat' })), ...agentStore.sessions.map(s => ({ ...s, sessionType: 'work' }))];
    return [...all].sort((a, b) => b.createdAt - a.createdAt).filter(s => s.title.toLowerCase().includes(query.value.toLowerCase()));
});
const navigation = [
    { path: '/welcome', label: '工作台', icon: 'home' },
    { path: '/chat', label: 'AI 对话', icon: 'chat' },
    { path: '/work', label: 'Agent 任务', icon: 'flow' },
    { path: '/studio', label: '创建智能体', icon: 'spark' },
    { path: '/repository', label: '我的空间', icon: 'folder' },
    { path: '/plaza', label: '模板广场', icon: 'grid' }
];
const go = (path) => { mobileOpen.value = false; router.push(workspacePath(path, props.preview)); };
const selectSession = (session) => {
    if (!props.preview) {
        if (session.sessionType === 'work') agentStore.setCurrentSessionId(session.sessionId);
        else chatStore.setCurrentChatId(session.sessionId);
    }
    go(session.sessionType === 'work' ? '/work' : '/chat');
};
const isSelected = (s) => s.sessionType === 'work' ? activePath.value === '/work' && agentStore.currentSessionId === s.sessionId : activePath.value === '/chat' && chatStore.currentChatId === s.sessionId;
const loadSessions = async () => {
    if (props.preview || !authStore.isLogin) return;
    loading.value = true;
    loadError.value = '';
    try {
        const data = unwrapWorkspaceData(await listSessions());
        const all = (Array.isArray(data) ? data : []).filter(s => s?.sessionId).map(mapWorkspaceSession);
        chatStore.setChats(all.filter(s => s.sessionType === 'chat'));
        agentStore.setSessions(all.filter(s => s.sessionType === 'work'));
        if (!chatStore.chats.some(s => s.sessionId === chatStore.currentChatId)) chatStore.setCurrentChatId(chatStore.chats[0]?.sessionId || null);
        if (!agentStore.sessions.some(s => s.sessionId === agentStore.currentSessionId)) agentStore.setCurrentSessionId(agentStore.sessions[0]?.sessionId || null);
    } catch (error) { loadError.value = '暂时无法加载会话'; }
    finally { loading.value = false; }
};
const newChat = async () => {
    if (props.preview) { go('/chat'); return; }
    if (creating.value) return;
    creating.value = true;
    try {
        const session = mapWorkspaceSession(unwrapWorkspaceData(await insertSession({ sessionTitle: '新会话', sessionType: 'chat' })));
        if (!session.sessionId) throw new Error('未能创建会话');
        chatStore.upsertChat(session);
        chatStore.setCurrentChatId(session.sessionId);
        go('/chat');
    } catch (error) { notifyAppError(error, '创建会话失败'); }
    finally { creating.value = false; }
};
const rename = (session) => { editTarget.value = session; editTitle.value = session.title; };
const saveTitle = async () => {
    if (!editTitle.value.trim() || saving.value) return;
    saving.value = true;
    try {
        const s = editTarget.value;
        unwrapWorkspaceData(await updateSession({ sessionId: s.sessionId, sessionTitle: editTitle.value.trim() }));
        if (s.sessionType === 'work') agentStore.updateSessionTitle(s.sessionId, editTitle.value.trim());
        else chatStore.updateChatTitle(s.sessionId, editTitle.value.trim());
        editTarget.value = null;
    } catch (error) { notifyAppError(error, '重命名失败'); }
    finally { saving.value = false; }
};
const removeSession = async () => {
    if (saving.value) return;
    saving.value = true;
    try {
        const s = deleteTarget.value;
        unwrapWorkspaceData(await deleteSession({ sessionId: s.sessionId }));
        if (s.sessionType === 'work') agentStore.removeSession(s.sessionId);
        else chatStore.removeChat(s.sessionId);
        deleteTarget.value = null;
    } catch (error) { notifyAppError(error, '删除失败'); }
    finally { saving.value = false; }
};
watch(() => route.fullPath, () => { mobileOpen.value = false; });
watch(() => props.preview, loadSessions);
onMounted(loadSessions);
</script>

<template>
    <div class="mobile-workspace-bar"><button class="icon-button" aria-label="打开导航" :aria-expanded="mobileOpen" @click="mobileOpen = !mobileOpen"><FlowIcon name="menu" /></button><span class="mobile-brand">ZFlow <small>知流</small></span><button class="icon-button" aria-label="新建对话" @click="newChat"><FlowIcon name="plus" /></button></div>
    <button v-if="mobileOpen" class="sidebar-scrim" aria-label="关闭导航" @click="mobileOpen = false"></button>
    <aside class="workspace-sidebar" :class="{ 'is-open': mobileOpen }" @keydown.esc="mobileOpen = false">
        <button class="workspace-brand" @click="go('/welcome')"><span class="brand-symbol"><svg viewBox="0 0 32 32" fill="none"><path d="M8 8h16L8 24h16M13 5h11v11M8 16v11h11" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round" /></svg></span><span>ZFlow <small>知流</small></span><span class="brand-beta">BETA</span></button>
        <button class="workspace-switch" @click="go('/welcome')"><span class="workspace-avatar">Z</span><span>个人工作空间<small>让想法，自然发生</small></span><FlowIcon name="down" :size="14" /></button>
        <button class="new-chat-button" :disabled="creating" @click="newChat"><FlowIcon name="plus" :size="18" /><span>{{ creating ? '创建中…' : '开启新对话' }}</span><span class="button-key">↗</span></button>
        <nav class="workspace-nav" aria-label="主导航"><button v-for="item in navigation" :key="item.path" :class="{ active: activePath === item.path }" :aria-current="activePath === item.path ? 'page' : undefined" @click="go(item.path)"><FlowIcon :name="item.icon" :size="19" /><span>{{ item.label }}</span><span v-if="item.path === '/work'" class="nav-badge">AGENT</span></button></nav>
        <div class="sidebar-history">
            <div class="sidebar-section-label">最近会话 <span>{{ sessions.length.toString().padStart(2, '0') }}</span></div>
            <label class="history-search"><FlowIcon name="search" :size="15" /><input v-model="query" placeholder="搜索会话…" aria-label="搜索会话" /></label>
            <div class="history-list">
                <p v-if="loading" class="sidebar-empty">正在加载会话…</p>
                <button v-else-if="loadError" class="sidebar-retry" @click="loadSessions">{{ loadError }} · 重试</button>
                <p v-else-if="!sessions.length" class="sidebar-empty">{{ query ? '没有找到相关会话' : '从一次新的对话开始吧' }}</p>
                <div v-for="session in sessions" :key="session.sessionId" class="history-row" :class="{ selected: isSelected(session) }"><button class="history-link" :title="session.title" @click="selectSession(session)"><FlowIcon :name="session.sessionType === 'work' ? 'flow' : 'chat'" :size="15" /><span>{{ session.title }}</span></button><div v-if="!preview" class="history-actions"><button class="icon-button" aria-label="重命名会话" @click="rename(session)"><FlowIcon name="pen" :size="13" /></button><button class="icon-button" aria-label="删除会话" @click="deleteTarget = session"><FlowIcon name="trash" :size="13" /></button></div></div>
            </div>
        </div>
        <div class="sidebar-bottom"><div class="sidebar-note"><FlowIcon name="leaf" :size="18" /><div>小小想法，大有可能。<small>和你的 AI 搭档，一起开始。</small></div></div><div class="sidebar-utilities"><button @click="go('/setting')"><FlowIcon name="sliders" :size="17" />设置</button><button class="icon-button" :aria-label="settings.theme === 'dark' ? '切换浅色主题' : '切换深色主题'" @click="settings.updateSettings({ theme: settings.theme === 'dark' ? 'light' : 'dark' })"><FlowIcon :name="settings.theme === 'dark' ? 'sun' : 'moon'" :size="17" /></button></div><div class="sidebar-profile"><button @click="go('/setting')"><span class="profile-avatar">{{ user.username.slice(0, 1).toUpperCase() }}</span><span>{{ user.username }}<small>{{ preview ? '界面体验' : '个人账户' }}</small></span></button><button v-if="!preview" class="icon-button" aria-label="退出登录" @click="authStore.logout()"><FlowIcon name="logout" :size="17" /></button><FlowIcon v-else name="chevron" :size="16" /></div></div>
    </aside>
    <Teleport to="body"><div v-if="editTarget || deleteTarget" class="workspace-modal-backdrop" @click.self="!saving && (editTarget = null, deleteTarget = null)" @keydown.esc="!saving && (editTarget = null, deleteTarget = null)"><form class="workspace-modal" role="dialog" aria-modal="true" :aria-label="editTarget ? '重命名会话' : '删除会话'" @submit.prevent="editTarget ? saveTitle() : removeSession()"><h2>{{ editTarget ? '给会话起个名字' : '删除这段会话？' }}</h2><input v-if="editTarget" v-model="editTitle" aria-label="会话名称" maxlength="30" autofocus /><p v-else>「{{ deleteTarget.title }}」及其消息将被删除。</p><div class="modal-actions"><button type="button" class="soft-button" :disabled="saving" @click="editTarget = null; deleteTarget = null">取消</button><button class="primary-button" :disabled="saving || (editTarget && !editTitle.trim())">{{ saving ? '处理中…' : editTarget ? '保存名称' : '确认删除' }}</button></div></form></div></Teleport>
</template>
