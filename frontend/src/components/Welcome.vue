<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { queryAgentList } from '../request/api';
import { useAgentStore, useChatStore, useWelcomeLaunchStore } from '../router/pinia';
import { unwrapWorkspaceData, workspacePath } from '../utils/workspace';
import { previewAgents, previewSessions } from '../utils/workspacePreview';
import FlowIcon from './ui/FlowIcon.vue';
import WorkspaceHeader from './ui/WorkspaceHeader.vue';
import Footer from './Footer.vue';

const props = defineProps({ preview: Boolean });
const router = useRouter();
const chatStore = useChatStore();
const agentStore = useAgentStore();
const launchStore = useWelcomeLaunchStore();
const prompt = ref('');
const mode = ref('chat');
const agents = ref([]);
const loading = ref(false);
const agentError = ref('');
const composer = ref(null);
const dateLabel = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(new Date());
const suggestions = [
    { icon: 'pen', title: '帮我找点灵感', prompt: '我想开始一个新项目，请帮我梳理一些有趣且可落地的方向。' },
    { icon: 'book', title: '读懂复杂知识', prompt: '请用直观的例子，向我解释 RAG 检索增强生成的工作原理。' },
    { icon: 'code', title: '一起解决问题', prompt: '请帮助我分析一个技术问题，先问我你需要了解的背景。' },
    { icon: 'file', title: '整理行动计划', prompt: '帮我把一个想法拆解成可执行的计划，先了解我的目标和时间安排。' }
];
const visibleAgents = computed(() => (props.preview ? previewAgents : agents.value).slice(0, 3));
const recent = computed(() => (props.preview ? previewSessions : [...chatStore.chats.map(s => ({ ...s, sessionType: 'chat' })), ...agentStore.sessions.map(s => ({ ...s, sessionType: 'work' }))]).sort((a, b) => b.createdAt - a.createdAt).slice(0, 3));
const go = (path) => router.push(workspacePath(path, props.preview));
const start = () => {
    if (!prompt.value.trim()) { composer.value?.focus(); return; }
    if (!props.preview) launchStore.setTask({ type: mode.value, prompt: prompt.value.trim(), sessionTitle: prompt.value.trim().slice(0, 24) });
    const target = workspacePath(mode.value === 'work' ? '/work' : '/chat', props.preview);
    router.push(props.preview ? { path: target, query: { prompt: prompt.value.trim() } } : target);
};
const chooseSuggestion = (suggestion) => { prompt.value = suggestion.prompt; composer.value?.focus(); };
const openAgent = (agent) => {
    if (props.preview) { go('/work'); return; }
    router.push(`/detail/${encodeURIComponent(agent.agentId)}`);
};
const openSession = (session) => {
    if (!props.preview) {
        if (session.sessionType === 'work') agentStore.setCurrentSessionId(session.sessionId);
        else chatStore.setCurrentChatId(session.sessionId);
    }
    go(session.sessionType === 'work' ? '/work' : '/chat');
};
const timeLabel = (value) => {
    const days = Math.floor((Date.now() - value) / 86400000);
    return days < 1 ? '今天' : days === 1 ? '昨天' : new Date(value).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
};
const loadAgents = async () => {
    if (props.preview) return;
    loading.value = true;
    agentError.value = '';
    try {
        const data = unwrapWorkspaceData(await queryAgentList());
        agents.value = Array.isArray(data) ? data : [];
    } catch { agentError.value = '暂时无法加载智能体'; }
    finally { loading.value = false; }
};
onMounted(loadAgents);
</script>

<template>
<section class="workspace-home">
    <WorkspaceHeader title="工作台" :subtitle="dateLabel"><span class="header-workspace-label"><span></span>你的灵感工作空间</span></WorkspaceHeader>
    <div class="home-scroll"><main class="home-content">
        <section class="home-hero">
            <div class="hero-copy"><div class="eyebrow"><span class="tiny-star">✳</span> A LITTLE SPARK, A NEW POSSIBILITY</div><h1>让每一个想法，<br /><span>都有下一步。</span></h1><p>对话、探索、创造。知流，让 AI 成为你的行动搭档。</p></div>
            <div class="hero-art" aria-hidden="true"><div class="orbit orbit-one"></div><div class="orbit orbit-two"></div><div class="orbit orbit-three"></div><div class="art-center"><FlowIcon name="spark" :size="45" /></div><span class="art-node node-chat"><FlowIcon name="chat" :size="24" /></span><span class="art-node node-flow"><FlowIcon name="flow" :size="23" /></span><span class="art-node node-book"><FlowIcon name="book" :size="22" /></span><span class="art-dot dot-one"></span><span class="art-dot dot-two"></span><span class="art-caption">ideas into flow.</span></div>
        </section>
        <form class="home-composer" @submit.prevent="start"><label for="home-prompt" class="sr-only">描述你的问题或任务</label><textarea id="home-prompt" ref="composer" v-model="prompt" placeholder="有什么想法？把问题或任务交给知流…" rows="2" @keydown.enter.exact="if (!$event.isComposing) { $event.preventDefault(); start(); }"></textarea><div class="home-composer-toolbar"><div class="composer-mode" aria-label="任务模式"><button type="button" :class="{ selected: mode === 'chat' }" :aria-pressed="mode === 'chat'" @click="mode = 'chat'"><FlowIcon name="chat" :size="16" />自由对话</button><button type="button" :class="{ selected: mode === 'work' }" :aria-pressed="mode === 'work'" @click="mode = 'work'"><FlowIcon name="flow" :size="16" />Agent 任务</button></div><div class="composer-submit"><span>Enter 开始，Shift + Enter 换行</span><button class="send-icon" :disabled="!prompt.trim()" aria-label="开始任务"><FlowIcon name="up" :size="21" /></button></div></div></form>
        <div class="prompt-suggestions"><span>不如试试</span><button v-for="item in suggestions" :key="item.title" @click="chooseSuggestion(item)"><FlowIcon :name="item.icon" :size="15" />{{ item.title }}<span>↗</span></button></div>
        <section class="agents-section"><div class="section-heading"><div><h2>找到你的 AI 搭档 <span v-if="preview" class="quiet-badge">示例</span></h2><p>把专业的事，交给擅长的智能体。</p></div><button class="text-button" @click="go('/repository')">我的空间<FlowIcon name="arrow" :size="16" /></button></div>
            <div v-if="loading" class="agents-grid" aria-label="正在加载智能体"><div v-for="i in 3" :key="i" class="agent-skeleton"></div></div>
            <div v-else-if="agentError" class="home-empty"><FlowIcon name="flow" /><span>{{ agentError }}</span><button class="text-button" @click="loadAgents">重新加载</button></div>
            <div v-else-if="!visibleAgents.length" class="home-empty"><FlowIcon name="spark" /><div><strong>你的第一位 AI 搭档，等你来创造</strong><p>组合模型、工具与执行策略，创建专属智能体。</p></div><button class="soft-button" @click="go('/studio')">创建智能体<FlowIcon name="plus" :size="16" /></button></div>
            <div v-else class="agents-grid"><button v-for="(agent, index) in visibleAgents" :key="agent.agentId" class="agent-card" @click="openAgent(agent)"><div class="agent-card-top"><span class="agent-icon" :class="agent.tone || ['mint', 'peach', 'lavender'][index % 3]"><FlowIcon :name="agent.icon || ['globe', 'pen', 'code'][index % 3]" :size="23" /></span><span class="agent-strategy">{{ agent.agentType || 'AGENT' }}</span><FlowIcon name="arrow" :size="17" /></div><h3>{{ agent.agentName || agent.name }}</h3><p>{{ agent.agentDesc || agent.desc || '用模型和工具，将你的任务一步步完成。' }}</p><div class="agent-card-bottom"><span class="status-dot"></span>开始协作<span>↗</span></div></button></div>
        </section>
        <section class="home-lower"><div class="recent-section"><div class="section-heading"><h2>继续上次的灵感</h2><FlowIcon name="clock" :size="17" /></div><div v-if="!recent.length" class="recent-empty">还没有历史会话。你的下一次灵感，会留在这里。</div><button v-for="session in recent" :key="session.sessionId" class="recent-row" @click="openSession(session)"><span class="recent-icon"><FlowIcon :name="session.sessionType === 'work' ? 'flow' : 'chat'" :size="17" /></span><span>{{ session.title }}</span><small>{{ timeLabel(session.createdAt) }}</small><FlowIcon name="chevron" :size="14" /></button></div><button class="create-agent-card" @click="go('/studio')"><div><span class="eyebrow">BUILT BY YOU</span><h3>你的工作方式，<br />你的专属智能体。</h3><span class="create-agent-link">创建一个 ZFlow <FlowIcon name="arrow" :size="16" /></span></div><div class="mini-flow" aria-hidden="true"><FlowIcon name="flow" :size="67" /></div></button></section>
    </main><Footer /></div>
</section>
</template>
