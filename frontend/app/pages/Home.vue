<script setup>
import { ref, computed } from 'vue';
import Icon from '../components/Icon.vue';
import AgentCard from '../components/AgentCard.vue';
import { state, isDemo, nav, launch } from '../lib/workspace.js';
const prompt = ref(''), mode = ref('work');
const date = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(new Date());
const featured = computed(() => (state.repo.self || []).slice(0, 3));
function begin() { state.draft = prompt.value; nav(mode.value); }
</script>
<template>
  <div class="page home-page">
    <header class="page-heading"><div><div class="eyebrow">YOUR SPACE TO CREATE</div><h1>你好，{{ state.auth?.userName || '创造者' }} <span class="greeting-sun">✳</span></h1><p>让灵感有个起点，让工作轻一点。</p></div><span class="date-label"><Icon name="sun" :size="16" />{{ date }}</span></header>
    <section class="home-hero">
      <div class="hero-main">
        <div class="hero-copy"><span class="hero-kicker"><i class="tiny-dot"></i> SMALL AGENTS. BIG POSSIBILITIES.</span><h2>好想法，<br />值得即刻<span class="underlined">行动。</span></h2><p>从灵感到成果，和你的 AI 伙伴一起。<br />告诉它目标，把专注留给真正重要的事。</p></div>
        <div class="orbit-art" aria-hidden="true"><div class="orbit-ring ring-one"></div><div class="orbit-ring ring-two"></div><div class="orbit-ring ring-three"></div><div class="orbit-core"><svg viewBox="0 0 80 80"><path d="M22 53V29l18 18 18-18v24" fill="none" stroke="currentColor" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/><circle cx="58" cy="20" r="4" fill="currentColor"/></svg></div><span class="orbit-node node-1"><Icon name="pen" :size="21" /></span><span class="orbit-node node-2"><Icon name="code" :size="22" /></span><span class="orbit-node node-3"><Icon name="book" :size="22" /></span><span class="orbit-star star-1">✧</span><span class="orbit-star star-2">✳</span><span class="orbit-caption">a little help, a lot of possibility</span></div>
        <form class="hero-composer" @submit.prevent="begin"><label class="sr-only" for="home-prompt">你想完成什么</label><textarea id="home-prompt" v-model="prompt" rows="2" placeholder="今天，有什么想一起完成的？" @keydown.enter.exact="event => { if (!event.isComposing) { event.preventDefault(); begin(); } }" /><div class="composer-bottom"><div class="segmented compact"><button type="button" :class="{ active: mode === 'work' }" @click="mode = 'work'"><Icon name="sparkles" :size="14" />智能任务</button><button type="button" :class="{ active: mode === 'chat' }" @click="mode = 'chat'"><Icon name="chat" :size="14" />自由对话</button></div><button class="send-button" aria-label="开始新任务" type="submit"><Icon name="arrow" :size="19" /></button></div></form>
      </div>
      <aside class="getting-started"><span class="light-eyebrow">MAKE IT YOURS</span><h3>你的下一位得力伙伴，<br />由你定义。</h3><p>从一个简单的想法开始，<br />搭建属于自己的智能体。</p><div class="mini-flow"><span><Icon name="sparkles" :size="17" />定义目标</span><i></i><span><Icon name="layers" :size="17" />选择能力</span><i></i><span><Icon name="play" :size="17" />开始协作</span></div><button class="light-button" @click="nav('agents', { create: '1' })">创建我的智能体 <Icon name="arrow" :size="17" /></button><small>无需从头开始，灵感随时就绪。</small></aside>
    </section>
    <div class="quick-actions"><button @click="nav('chat')"><span class="quick-icon sage"><Icon name="chat" /></span><span><strong>聊聊新想法</strong><small>一个问题，开启更多可能</small></span><Icon name="arrow" :size="18" /></button><button @click="nav('knowledge')"><span class="quick-icon peach"><Icon name="book" /></span><span><strong>连接你的知识</strong><small>让 AI 更懂你的工作</small></span><Icon name="arrow" :size="18" /></button><button @click="nav('plaza')"><span class="quick-icon lavender"><Icon name="compass" /></span><span><strong>发现好用的智能体</strong><small>借一点灵感，即刻出发</small></span><Icon name="arrow" :size="18" /></button></div>
    <section class="section"><div class="section-heading"><h2>你的 AI 伙伴 <span class="count">{{ state.repo.self?.length || 0 }}</span></h2><button class="text-button" @click="nav('agents')">查看全部 <Icon name="arrow" :size="16" /></button></div><div v-if="featured.length" class="card-grid"><AgentCard v-for="agent in featured" :key="agent.agentId" :agent="agent" @run="launch" @open="nav('agents', { detail: agent.agentId })" /></div><div v-else class="empty-inline"><Icon name="bot" :size="28" /><span>给你的工作区添一位新伙伴。</span><button class="text-button" @click="nav('agents', { create: '1' })">创建智能体 →</button></div></section>
    <section class="section recent-section"><div class="section-heading"><h2>继续上次的灵感</h2><span class="muted small">{{ isDemo ? '示例会话' : '最近会话' }}</span></div><div class="session-table"><button v-for="session in state.sessions.slice(0, 4)" :key="session.sessionId" class="session-row" @click="nav(session.sessionType === 'work' ? 'work' : 'chat', { session: session.sessionId })"><span class="session-symbol"><Icon :name="session.sessionType === 'work' ? 'workflow' : 'chat'" :size="18" /></span><strong>{{ session.sessionTitle }}</strong><span class="session-kind">{{ session.sessionType === 'work' ? '智能任务' : '自由对话' }}</span><time>{{ session.createTime?.replace('T', ' ').slice(5, 16) }}</time><Icon name="chevron" :size="16" /></button><div v-if="!state.sessions.length" class="empty-inline">还没有会话，从上方开始一个新想法吧。</div></div></section>
    <footer class="page-footer"><span class="footer-mark">✳</span> 给想法一点空间，让可能自然发生。<span>ZFlow Workspace</span></footer>
  </div>
</template>
