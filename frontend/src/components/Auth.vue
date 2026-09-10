<script setup>
import { reactive, ref } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { login, register } from '../request/api';
import { parseAuthPayload } from '../request/auth';
import { useAuthStore, useSettingsStore } from '../router/pinia';
import FlowIcon from './ui/FlowIcon.vue';
const canPreview = import.meta.env.DEV;
import Footer from './Footer.vue';

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const settingsStore = useSettingsStore();

const mode = ref(route.path.includes('register') ? 'register' : 'login');
const loading = ref(false);
const error = ref('');
const target = ref('chat');

const form = reactive({
    username: '',
    password: ''
});

const syncModeFromRoute = () => {
    mode.value = route.path.includes('register') ? 'register' : 'login';
    target.value = 'chat';
};

const switchMode = (next) => {
    if (mode.value === next) return;
    mode.value = next;
    error.value = '';
    const path = next === 'login' ? '/login' : '/register';
    router.replace({ path, query: route.query });
};

syncModeFromRoute();

const submit = async () => {
    if (loading.value) return;
    if (!form.username || !form.password) {
        error.value = '请填写用户名和密码';
        return;
    }
    loading.value = true;
    error.value = '';
    try {
        const api = mode.value === 'login' ? login : register;
        const resp = await api({ username: form.username.trim(), password: form.password });
        const { token, user } = parseAuthPayload(resp);
        if (!token || !user) {
            throw new Error('登录信息异常');
        }
        if (user.userStatus === 0) {
            throw new Error('账号已被禁用');
        }
        authStore.setAuth({ token, user });
        settingsStore.updateSettings({ token });
        router.push('/welcome');
    } catch (e) {
        error.value = e?.message || '操作失败，请稍后重试';
    } finally {
        loading.value = false;
    }
};
</script>

<template>
<div class="auth-workspace">
    <div class="auth-stage">
        <section class="auth-story"><div class="auth-brand"><span class="brand-symbol"><FlowIcon name="spark" :size="23" /></span>ZFlow <small>知流</small></div><div class="auth-story-content"><div class="eyebrow">YOUR IDEAS. IN FLOW.</div><h1>一个想法，<br />无数种可能。</h1><p>让知识相连，让灵感流动。<br />在知流，找到与你并肩的 AI 搭档。</p><div class="auth-illustration" aria-hidden="true"><div class="auth-flow-node"><FlowIcon name="chat" :size="25" /><span>对话</span></div><span class="auth-flow-line"></span><div class="auth-flow-node central"><FlowIcon name="spark" :size="33" /></div><span class="auth-flow-line"></span><div class="auth-flow-node"><FlowIcon name="flow" :size="25" /><span>行动</span></div></div></div><span class="auth-story-foot">A little spark. A new possibility.</span></section>
        <section class="auth-form-section"><form class="auth-form" @submit.prevent="submit"><span class="eyebrow">WELCOME TO ZFLOW</span><h2>{{ mode === 'login' ? '欢迎回来。' : '从这里，开始创造。' }}</h2><p>{{ mode === 'login' ? '登录你的空间，继续上次的灵感。' : '创建一个账户，开启你的 AI 工作空间。' }}</p><label for="auth-username">用户名</label><input id="auth-username" v-model="form.username" autocomplete="username" placeholder="输入你的用户名" :disabled="loading" required /><label for="auth-password">密码</label><input id="auth-password" v-model="form.password" type="password" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" placeholder="输入你的密码" :disabled="loading" required /><div v-if="error" class="auth-error" role="alert">{{ error }}</div><button class="primary-button auth-submit" :disabled="loading">{{ loading ? '请稍候…' : mode === 'login' ? '登录工作空间' : '创建账户' }}<FlowIcon name="arrow" :size="17" /></button><div class="auth-switch">{{ mode === 'login' ? '第一次来到知流？' : '已经有账户？' }}<button type="button" :disabled="loading" @click="switchMode(mode === 'login' ? 'register' : 'login')">{{ mode === 'login' ? '创建账户' : '立即登录' }}</button></div><RouterLink v-if="canPreview" class="auth-preview" to="/preview/welcome"><FlowIcon name="globe" :size="15" />先看看新版工作台<FlowIcon name="arrow" :size="14" /></RouterLink></form></section>
    </div><Footer layout="auth" />
</div>
</template>
