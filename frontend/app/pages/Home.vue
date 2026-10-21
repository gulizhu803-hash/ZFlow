<script setup>
import { ref, computed, nextTick } from 'vue';
import Icon from '../components/Icon.vue';
import Sheet from '../components/Sheet.vue';
import TodayRail from '../components/home/TodayRail.vue';
import AgentShelf from '../components/home/AgentShelf.vue';
import { state, isDemo, nav, launch, recentSessions } from '../lib/workspace.js';
import { useRunStore } from '../lib/use-run-store.js';

const { runState } = useRunStore();
const prompt = ref('');
const mode = ref('work');
const composer = ref(null);
const todayOpen = ref(false);
const featured = computed(() => (state.repo.self || []).slice(0, 3));

const now = new Date();
const date = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(now).replace('星期', ' · 星期');
const greeting = now.getHours() < 5 ? '夜深了' : now.getHours() < 12 ? '早上好' : now.getHours() < 18 ? '下午好' : '晚上好';
const name = computed(() => state.auth?.userName || '创造者');

const SUGGESTIONS = { work: ['整理一份竞品分析', '把会议记录变成待办', '规划下周的学习路线'], chat: ['解释这段代码', '帮我想三个标题', '把这段话改得更简洁'] };
const suggestions = computed(() => SUGGESTIONS[mode.value]);

const ACTIVE_STATUS = ['preparing', 'running', 'stopping'];
const activeRun = computed(() => (ACTIVE_STATUS.includes(runState.value?.status) ? runState.value : null));
const runStatusText = computed(() => ({ preparing: '准备中', running: '运行中', stopping: '正在停止' })[activeRun.value?.status] || '运行中');
const runSession = computed(() => (state.sessions || []).find(session => session.sessionId === activeRun.value?.sessionId));
const runPage = computed(() => (runSession.value?.sessionType === 'chat' ? 'chat' : 'work'));

const TYPE_LABEL = { chat: '自由对话', work: '智能任务', office: '智能办公' };
const typeLabel = type => TYPE_LABEL[type] || '会话';
const sessionIcon = type => (type === 'chat' ? 'chat' : 'workflow');
const shortTime = value => (value ? String(value).replace('T', ' ').slice(5, 16) : '');
const recent = computed(() => recentSessions.value.slice(0, 8));

const todayItems = computed(() => {
  const run = activeRun.value;
  const items = [];
  if (run) {
    items.push({ key: 'run', kind: 'run', live: true, actionable: true, title: run.prompt || '正在执行任务', meta: `${runStatusText.value} · ${typeLabel(runSession.value?.sessionType || 'work')}` });
  }
  if (isDemo.value && !run) {
    const sessions = recentSessions.value;
    const latestRun = sessions.find(session => session.type === 'work');
    if (latestRun) {
      items.push({ key: 'demo-run', kind: 'recent', icon: 'play', actionable: true, title: latestRun.title, meta: `演示 · 最近运行 · ${typeLabel(latestRun.type)} · ${shortTime(latestRun.createdAt)}`, session: latestRun });
    } else {
      items.push({ key: 'demo-run', kind: 'run', empty: true, title: '今天还没有运行记录', meta: '从上方输入框开始第一个任务' });
    }
    items.push({ key: 'demo-schedule', kind: 'schedule', actionable: true, title: '每日灵感简报', meta: '演示 · 每天 09:00' });
    const second = sessions[1] && sessions[1].id !== latestRun?.id ? sessions[1] : sessions.find(session => session.id !== latestRun?.id);
    if (second) {
      items.push({ key: 'demo-recent', kind: 'recent', icon: sessionIcon(second.type), actionable: true, title: second.title, meta: `演示 · ${typeLabel(second.type)} · ${shortTime(second.createdAt)}`, session: second });
    } else {
      items.push({ key: 'demo-recent', kind: 'recent', empty: true, title: '还没有更多最近会话', meta: '从上方输入框开始第一个任务' });
    }
    return items;
  }
  if (!run) items.push({ key: 'run', kind: 'run', empty: true, title: '没有正在运行的任务', meta: '提交任务后，进度会出现在这里' });
  items.push({ key: 'schedule', kind: 'schedule', empty: true, actionable: true, title: '今天没有定时安排', meta: '在定时任务里安排重复工作' });
  const latest = recentSessions.value[0];
  if (latest) items.push({ key: 'recent', kind: 'recent', icon: sessionIcon(latest.type), actionable: true, title: latest.title, meta: `${typeLabel(latest.type)} · ${shortTime(latest.createdAt)}`, session: latest });
  else items.push({ key: 'recent', kind: 'recent', empty: true, title: '还没有最近会话', meta: '从上方输入框开始第一个任务' });
  return items;
});

const bands = [
  { no: '01', label: '分步执行', note: '先规划，再行动' },
  { no: '02', label: '循环优化', note: '执行、评估、改进' },
  { no: '03', label: '自主探索', note: '观察与推理交替' }
];

function openTodayItem(item) {
  if (item.kind === 'run') nav(runPage.value, activeRun.value?.sessionId ? { session: activeRun.value.sessionId } : {});
  else if (item.kind === 'schedule') nav('schedules');
  else if (item.session) nav(item.session.type, { session: item.session.id });
}
function begin() {
  const text = prompt.value.trim();
  if (!text) { composer.value?.focus(); return; }
  state.draft = text;
  nav(mode.value);
}
function onEnter(event) { if (event.isComposing) return; event.preventDefault(); begin(); }
function applySuggestion(text) { prompt.value = text; nextTick(() => composer.value?.focus()); }
</script>
<template>
  <div class="page home-page">
    <div class="home-grid">
      <section class="home-lead" aria-labelledby="home-title">
        <p class="home-eyebrow">{{ date }}</p>
        <h1 id="home-title" class="home-title">{{ greeting }}，{{ name }}。</h1>
        <p class="home-subtitle">今天想完成什么？说清目标，剩下的交给智能体。</p>
        <form class="home-composer" @submit.prevent="begin">
          <label class="sr-only" for="home-prompt">你想完成什么</label>
          <textarea id="home-prompt" ref="composer" v-model="prompt" rows="2" placeholder="描述你想完成的事，或粘贴一段材料…" @keydown.enter.exact="onEnter" />
          <div class="home-composer__bar">
            <div class="home-mode" role="group" aria-label="任务模式">
              <button type="button" :class="{ active: mode === 'work' }" :aria-pressed="mode === 'work'" @click="mode = 'work'"><Icon name="sparkles" :size="14" />智能任务</button>
              <button type="button" :class="{ active: mode === 'chat' }" :aria-pressed="mode === 'chat'" @click="mode = 'chat'"><Icon name="chat" :size="14" />自由对话</button>
            </div>
            <button class="home-send" type="submit" :disabled="!prompt.trim()" aria-label="开始新任务"><Icon name="arrow" :size="19" /></button>
          </div>
        </form>
        <div class="home-suggestions" aria-label="建议">
          <button v-for="suggestion in suggestions" :key="suggestion" class="home-suggestion" type="button" @click="applySuggestion(suggestion)">{{ suggestion }}</button>
        </div>
      </section>
      <aside class="home-today">
        <TodayRail :items="todayItems" @select="openTodayItem" @open-today="todayOpen = true" />
      </aside>
    </div>

    <section class="home-ink" aria-label="创建智能体与执行策略">
      <div class="home-ink__copy">
        <p class="home-ink__line">好想法，值得有自己的智能体。</p>
        <button class="home-ink__cta" type="button" @click="nav('agents', { create: '1' })">创建智能体 <Icon name="arrow" :size="16" /></button>
      </div>
      <ol class="home-ink__strategies">
        <li v-for="band in bands" :key="band.no"><span class="home-ink__no">{{ band.no }}</span><span class="home-ink__label">{{ band.label }}</span><span class="home-ink__note">{{ band.note }}</span></li>
      </ol>
    </section>

    <section class="home-shelf" aria-labelledby="home-shelf-title">
      <header class="home-shelf__head">
        <div class="home-shelf__heading"><h2 id="home-shelf-title">你的 AI 伙伴</h2><span class="home-shelf__count">{{ featured.length }}</span></div>
        <div class="home-shelf__actions">
          <button class="home-quiet home-shelf__create" type="button" @click="nav('agents', { create: '1' })">创建智能体</button>
          <button class="home-quiet" type="button" @click="nav('agents')">查看全部 <Icon name="arrow" :size="15" /></button>
        </div>
      </header>
      <AgentShelf v-if="featured.length" :agents="featured" @run="launch" @open="agent => nav('agents', { detail: agent.agentId })" />
      <div v-else class="home-shelf__empty">
        <p>还没有智能体。创建一个，或去广场看看。</p>
        <div class="home-shelf__empty-actions">
          <button class="home-link" type="button" @click="nav('agents', { create: '1' })">创建智能体</button>
          <button class="home-link" type="button" @click="nav('plaza')">去广场</button>
        </div>
      </div>
    </section>

    <Sheet v-if="todayOpen" title="今天" @close="todayOpen = false">
      <div class="home-today-sheet">
        <section class="home-today-sheet__section">
          <h3>运行中</h3>
          <p v-if="activeRun" class="home-today-sheet__note">{{ runStatusText }} · {{ activeRun.prompt || '正在执行任务' }}</p>
          <p v-else class="home-today-sheet__note is-empty">没有正在运行的任务。</p>
          <button v-if="activeRun" class="home-link" type="button" @click="todayOpen = false; openTodayItem({ kind: 'run' })">回到任务</button>
        </section>
        <section class="home-today-sheet__section">
          <h3>定时安排</h3>
          <p class="home-today-sheet__note is-empty">今天没有定时安排。</p>
          <button class="home-link" type="button" @click="todayOpen = false; nav('schedules')">去定时任务</button>
        </section>
        <section class="home-today-sheet__section">
          <h3>最近会话 <span class="home-today-sheet__count">{{ recent.length }}</span></h3>
          <div class="home-today-sheet__list">
            <button v-for="session in recent" :key="session.id" class="home-today-sheet__row" type="button" @click="todayOpen = false; nav(session.type, { session: session.id })">
              <Icon :name="sessionIcon(session.type)" :size="17" />
              <span class="home-today-sheet__title">{{ session.title }}</span>
              <span class="home-today-sheet__meta">{{ typeLabel(session.type) }} · {{ shortTime(session.createdAt) }}</span>
            </button>
            <p v-if="!recent.length" class="home-today-sheet__note is-empty">还没有会话。</p>
          </div>
        </section>
      </div>
    </Sheet>
  </div>
</template>
