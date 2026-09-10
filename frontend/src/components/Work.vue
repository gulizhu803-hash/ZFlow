<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { marked } from 'marked';
import hljs from 'highlight.js';
import DOMPurify from 'dompurify';
import {
    dispatchArmory,
    executeAgentStream,
    insertSession,
    listSessions,
    listWorkAnswerMessages,
    listWorkSseMessages,
    queryAgentList,
    updateSession
} from '../request/api';
import { normalizeError, notifyAppError } from '../request/request';
import { formatMcpJson } from '../utils/StringUtil';
import { areCardListsEqual, areMessageListsEqual, createStableRecordId, toSafeTimestamp } from '../utils/MessageRenderUtil';
import { useAgentSettingsStore, useAgentStore, useChatStore, useSettingsStore, useWelcomeLaunchStore } from '../router/pinia';
import { getStrategyTone } from '../utils/StrategyTone';
import Footer from './Footer.vue';
import FlowIcon from './ui/FlowIcon.vue';
import WorkspaceHeader from './ui/WorkspaceHeader.vue';
import { previewAgents, previewCards, previewWorkMessages } from '../utils/workspacePreview';
const props = defineProps({ preview: Boolean });
const previewRoute = useRoute();
const roleNames = { INSPECTOR: '理解任务', PLANNER: '规划步骤', RUNNER: '执行任务', REPLIER: '汇总结果', ANALYZER: '分析任务', PERFORMER: '执行任务', SUPERVISOR: '检查结果', SUMMARIZER: '汇总结果', OBSERVER: '观察现状', REASONER: '推理分析', ACTOR: '采取行动', EVALUATOR: '评估结果' };
const stageName = (card) => roleNames[String(card.clientType).toUpperCase()] || roleNames[String(card.sectionType).split('_')[0].toUpperCase()] || card.clientType || '执行阶段';

const router = useRouter();
const agentStore = useAgentStore();
const chatStore = useChatStore();
const settingsStore = useAgentSettingsStore();
const uiSettingsStore = useSettingsStore();
const welcomeLaunchStore = useWelcomeLaunchStore();

const agentOptions = ref([]);
const pendingAgentId = ref('');
const agentDropdownOpen = ref(false);
const agentSelectRef = ref(null);

const leftScrollRef = ref(null);
const rightScrollRef = ref(null);
const inputValue = ref('');
const showSettings = ref(false);
const isLeftAtBottom = ref(true);
const isRightAtBottom = ref(true);
const messageLoading = ref(false);
const sendError = ref('');
const skipNextLoadSessionId = ref(null);
let autoRefreshTimer = null;

const settingsForm = reactive({
    maxRetry: settingsStore.maxRetry,
    maxRound: settingsStore.maxRound,
    maxPace: settingsStore.maxPace
});

const OVERVIEW_SECTION_TYPES = new Set(['summarizer_overview', 'replier_overview', 'evaluator_overview']);
const resolveAgentType = (value) => {
    const normalized = (value || '').toString().trim().toLowerCase();
    return normalized === 'step' || normalized === 'loop' || normalized === 'react' ? normalized : '';
};

const pickData = (resp, message = '操作失败') => {
    if (resp && typeof resp === 'object' && Object.prototype.hasOwnProperty.call(resp, 'code')) {
        if (resp.code !== 200) {
            const err = new Error(resp.info || message);
            err.status = 500;
            throw err;
        }
        return resp.data;
    }
    return resp?.data ?? resp?.result ?? resp;
};

const normalizeSessionType = (value) => (value ? value.toString().toLowerCase() : '');

const mapSession = (session) => {
    if (!session) return null;
    return {
        sessionId: session.sessionId,
        sessionUser: session.userName || session.sessionUser || '',
        title: session.sessionTitle || '新会话',
        sessionType: normalizeSessionType(session.sessionType || session.type),
        createdAt: session.createTime ? Date.parse(session.createTime) : Date.now(),
        messages: [],
        cards: []
    };
};

const SESSION_INVALID_HINT = '会话已失效，请新建会话';

const isInvalidSessionErrorMessage = (message) => {
    if (!message) return false;
    return (
        message.includes('会话不存在') ||
        message.includes('会话已失效') ||
        message.includes('会话类型不匹配') ||
        message.includes('无权限访问该会话') ||
        message.includes('无权限修改该会话')
    );
};

const listRemoteWorkSessions = async (options = {}) => {
    const resp = await listSessions(options);
    const list = pickData(resp, '获取会话失败') || [];
    const mapped = (Array.isArray(list) ? list : []).map(mapSession).filter(Boolean);
    return mapped.filter((item) => item.sessionType === 'work');
};

const refreshWorkSessions = async (options = {}) => {
    const workList = await listRemoteWorkSessions(options);
    agentStore.setSessions(workList);
    return workList;
};

const dropInvalidWorkSession = (sessionId = agentStore.currentSessionId) => {
    if (sessionId) {
        agentStore.removeSession(sessionId);
    }
    if (agentStore.sessions.length === 0 && chatStore.chats.length === 0) {
        router.replace('/welcome');
    }
    sendError.value = SESSION_INVALID_HINT;
};

const ensureWorkSessionValid = async (session, options = {}) => {
    if (!session?.sessionId) return null;
    const silentToast = Boolean(options.silentToast);
    try {
        const workList = await listRemoteWorkSessions(silentToast ? { toast: false } : {});
        const matched = workList.find((item) => item.sessionId === session.sessionId);
        if (!matched) {
            dropInvalidWorkSession(session.sessionId);
            return null;
        }
        return matched;
    } catch (error) {
        if (silentToast) {
            sendError.value = normalizeError(error).message || '获取会话失败';
        } else {
            notifyAppError(error, '获取会话失败');
        }
        return null;
    }
};

const mapMessage = (message, sessionId = '', seenMap = new Map()) => {
    const role = message?.messageRole || message?.role || 'assistant';
    const content = message?.messageContent || message?.content || '';
    const createTime = message?.createTime || message?.createdAt || '';
    return {
        id: createStableRecordId({
            sourceId: message?.messageSeq || message?.messageId || message?.id,
            sessionId,
            prefix: 'msg',
            signatureParts: [createTime, role, content],
            seenMap
        }),
        role,
        content,
        pending: false,
        error: null,
        createdAt: toSafeTimestamp(createTime)
    };
};

const mapCard = (message, sessionId = '', seenMap = new Map()) => {
    const raw = message?.messageContent || '';
    let parsed = null;
    try {
        parsed = raw ? JSON.parse(raw) : null;
    } catch (error) {
        parsed = null;
    }
    const payload = parsed && typeof parsed === 'object' ? parsed : { sectionContent: raw };
    const createTime = message?.createTime || '';
    return {
        id: createStableRecordId({
            sourceId: message?.messageSeq || payload?.id || message?.id,
            sessionId,
            prefix: 'card',
            signatureParts: [createTime, payload.sectionType, payload.sectionContent, payload.round, payload.pace, payload.step, payload.timestamp],
            seenMap
        }),
        clientType: payload.clientType || '',
        sectionType: payload.sectionType || '',
        sectionContent: payload.sectionContent || '',
        round: payload.round ?? null,
        pace: payload.pace ?? null,
        step: payload.step ?? null,
        timestamp: payload.timestamp ?? null
    };
};

const renderer = new marked.Renderer();
renderer.code = (code, infostring) => {
    const lang = (infostring || '').match(/\S*/)?.[0] || '';
    if (lang && hljs.getLanguage(lang)) {
        return `<pre><code class="hljs language-${lang}">${hljs.highlight(code, {
            language: lang,
            ignoreIllegals: true
        }).value}</code></pre>`;
    }
    return `<pre><code class="hljs">${hljs.highlightAuto(code).value}</code></pre>`;
};

marked.setOptions({ breaks: true, gfm: true, renderer });

const renderMarkdown = (text) => {
    if (!text) return '';
    return DOMPurify.sanitize(marked.parse(text), { ADD_ATTR: ['class'] });
};

const currentAgentId = computed({
    get: () => {
        if (props.preview) return pendingAgentId.value;
        const session = agentStore.currentSession;
        if (session) {
            return session.agentId || '';
        }
        return pendingAgentId.value || '';
    },
    set: (value) => {
        if (props.preview) { pendingAgentId.value = value; return; }
        const session = agentStore.currentSession;
        if (session?.sessionId) {
            if (agentStore.isSessionSelectionLocked(session.sessionId)) {
                return;
            }
            agentStore.setSessionAgent(session.sessionId, value || '');
            return;
        }
        pendingAgentId.value = value || '';
    }
});
const isAgentLocked = computed(() => {
    if (props.preview) return false;
    const sessionId = agentStore.currentSession?.sessionId || '';
    if (!sessionId) return false;
    return agentStore.isSessionSelectionLocked(sessionId);
});

const currentAgentLabel = computed(() => {
    if (!currentAgentId.value) {
        return '选择 ZFlow';
    }
    const match = agentOptions.value.find((item) => item.value === currentAgentId.value);
    return match?.label || currentAgentId.value;
});

const currentAgentType = computed(() => {
    if (!currentAgentId.value) return '';
    const match = agentOptions.value.find((item) => item.value === currentAgentId.value);
    return resolveAgentType(match?.agentType || '');
});

const isReactAgent = computed(() => currentAgentType.value === 'react');
const isDarkTheme = computed(() => uiSettingsStore.theme === 'dark');
const currentAgentTone = computed(() => getStrategyTone(currentAgentType.value || 'react', isDarkTheme.value));
const currentAgentTypeLabel = computed(() => (currentAgentType.value || '').toUpperCase());
const getStrategyBadgeStyle = (strategy) => {
    const tone = getStrategyTone(strategy, isDarkTheme.value);
    return {
        borderColor: tone.badgeBorder,
        backgroundColor: tone.badgeBg,
        color: tone.badgeText
    };
};
const currentAgentDesc = computed(() => {
    if (!currentAgentId.value) return '';
    const match = agentOptions.value.find((item) => item.value === currentAgentId.value);
    return (match?.desc || '').trim() || '暂无描述';
});

const fetchAgents = async () => {
    try {
        const resp = await queryAgentList();
        const list = pickData(resp, '获取 ZFlow 列表失败') || [];
        const normalized = list
            .map((item) => {
                if (!item || typeof item !== 'object') return null;
                const agentId = item.agentId || '';
                const agentName = item.agentName || item.name || agentId;
                const agentDesc = item.agentDesc || item.desc || '';
                const agentType = resolveAgentType(item.agentType || item.type || '');
                if (!agentId) return null;
                return { label: agentName || agentId, value: agentId, desc: agentDesc, agentType };
            })
            .filter(Boolean);
        const seen = new Set();
        const unique = normalized.filter((item) => {
            if (seen.has(item.value)) return false;
            seen.add(item.value);
            return true;
        });
        agentOptions.value = unique;
        if (currentAgentId.value && !unique.some((item) => item.value === currentAgentId.value)) {
            currentAgentId.value = '';
        }
        return unique;
    } catch (error) {
        console.warn('获取 ZFlow 列表失败', error);
        return [];
    }
};

const messages = computed(() => props.preview ? previewWorkMessages : agentStore.currentMessages);
const cards = computed(() => props.preview ? previewCards : agentStore.currentCards);
const sending = computed(() => !props.preview && agentStore.sending);
const userMessageCount = computed(() => messages.value.filter((item) => item.role === 'user').length);

const handleLeftScroll = () => {
    const el = leftScrollRef.value;
    if (!el) return;
    const distance = el.scrollHeight - el.scrollTop - el.clientHeight;
    isLeftAtBottom.value = distance < 80;
};

const handleRightScroll = () => {
    const el = rightScrollRef.value;
    if (!el) return;
    const distance = el.scrollHeight - el.scrollTop - el.clientHeight;
    isRightAtBottom.value = distance < 80;
};

const scrollLeftToBottom = (smooth = true) => {
    nextTick(() => {
        const el = leftScrollRef.value;
        if (!el) return;
        el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto' });
    });
};

const scrollRightToBottom = (smooth = true) => {
    nextTick(() => {
        const el = rightScrollRef.value;
        if (!el) return;
        el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto' });
    });
};

const loadWorkMessages = async (sessionId, options = {}) => {
    if (props.preview) return;
    const silent = Boolean(options?.silent);
    if (!sessionId) {
        if (agentStore.currentSessionId) {
            agentStore.setSessionMessages(agentStore.currentSessionId, []);
            agentStore.setSessionCards(agentStore.currentSessionId, []);
        }
        return;
    }
    if (!silent) {
        messageLoading.value = true;
    }
    try {
        const [sseResp, answerResp] = await Promise.all([
            listWorkSseMessages({ sessionId }),
            listWorkAnswerMessages({ sessionId })
        ]);
        const sseList = pickData(sseResp, '获取会话卡片失败') || [];
        const answerList = pickData(answerResp, '获取会话消息失败') || [];
        const cardSeenMap = new Map();
        const messageSeenMap = new Map();
        const mappedCards = (Array.isArray(sseList) ? sseList : [])
            .map((item) => mapCard(item, sessionId, cardSeenMap))
            .filter((card) => !OVERVIEW_SECTION_TYPES.has(card.sectionType));
        const mappedMessages = (Array.isArray(answerList) ? answerList : []).map((item) => mapMessage(item, sessionId, messageSeenMap));
        if (sessionId) {
            const existingSession = agentStore.sessions.find((item) => item.sessionId === sessionId);
            const prevCards = existingSession?.cards || [];
            const prevMessages = existingSession?.messages || [];
            if (!areCardListsEqual(prevCards, mappedCards)) {
                agentStore.setSessionCards(sessionId, mappedCards);
            }
            if (!areMessageListsEqual(prevMessages, mappedMessages)) {
                agentStore.setSessionMessages(sessionId, mappedMessages);
            }
        }
    } catch (error) {
        const message = normalizeError(error).message || '获取消息失败';
        if (isInvalidSessionErrorMessage(message)) {
            dropInvalidWorkSession(agentStore.currentSessionId);
            return;
        }
        notifyAppError(error, '获取消息失败');
    } finally {
        if (!silent) {
            messageLoading.value = false;
        }
    }
};

const ensureWorkSession = async ({ forceNew = false, sessionTitle = '新会话', silentToast = false } = {}) => {
    if (!forceNew && agentStore.currentSession) {
        if (!agentStore.currentSessionId && agentStore.currentSession?.sessionId) {
            agentStore.setCurrentSessionId(agentStore.currentSession.sessionId);
        }
        return agentStore.currentSession;
    }
    try {
        const resp = await insertSession(
            { sessionTitle: sessionTitle || '新会话', sessionType: 'work' },
            silentToast ? { toast: false } : {}
        );
        const created = mapSession(pickData(resp, '创建会话失败'));
        if (created) {
            agentStore.upsertSession(created);
            if (forceNew) {
                skipNextLoadSessionId.value = created.sessionId;
            }
            agentStore.setCurrentSessionId(created.sessionId);
            if (pendingAgentId.value) {
                agentStore.setSessionAgent(created.sessionId, pendingAgentId.value);
            }
            return created;
        }
        const workList = await refreshWorkSessions(silentToast ? { toast: false } : {});
        const session = workList[0] || null;
        if (!session) return null;
        agentStore.setCurrentSessionId(session.sessionId);
        return session;
    } catch (error) {
        if (silentToast) {
            sendError.value = normalizeError(error).message || '创建会话失败';
        } else {
            notifyAppError(error, '创建会话失败');
        }
        return null;
    }
};

const renameSessionIfNeeded = async (session, content, options = {}) => {
    if (!session) return;
    if (session.title && session.title !== '新会话') return;
    const silentToast = Boolean(options.silentToast);
    const nextTitle = content.slice(0, 20) || '新会话';
    agentStore.updateSessionTitle(session.sessionId, nextTitle);
    try {
        await updateSession({ sessionId: session.sessionId, sessionTitle: nextTitle }, silentToast ? { toast: false } : {});
    } catch (error) {
        if (!silentToast) {
            notifyAppError(error, '更新会话失败');
        }
    }
};

watch(
    cards,
    () => {
        if (isLeftAtBottom.value) scrollLeftToBottom(true);
    },
    { deep: true }
);

watch(
    messages,
    () => {
        if (isRightAtBottom.value) scrollRightToBottom(true);
    },
    { deep: true }
);

watch(
    () => inputValue.value,
    () => {
        if (sendError.value && userMessageCount.value < 3) {
            sendError.value = '';
        }
    }
);

watch(
    userMessageCount,
    (count) => {
        if (count >= 3) {
            sendError.value = '当前会话已达到 3 条用户消息上限，请新建会话';
        }
    },
    { immediate: true }
);

watch(isAgentLocked, (locked) => {
    if (locked) {
        agentDropdownOpen.value = false;
    }
});

watch(
    () => agentStore.currentSessionId,
    async (sessionId) => {
        sendError.value = '';
        if (sessionId && skipNextLoadSessionId.value === sessionId) {
            skipNextLoadSessionId.value = null;
            nextTick(() => {
                scrollLeftToBottom(false);
                scrollRightToBottom(false);
            });
            return;
        }
        const session = agentStore.currentSession;
        if (session?.sessionId) {
            await loadWorkMessages(session.sessionId);
        }
        nextTick(() => {
            scrollLeftToBottom(false);
            scrollRightToBottom(false);
        });
    },
    { immediate: true }
);

const toggleAgentDropdown = () => {
    if (agentOptions.value.length === 0 || isAgentLocked.value) return;
    agentDropdownOpen.value = !agentDropdownOpen.value;
};

const selectAgent = async (value) => {
    if (props.preview) { pendingAgentId.value = value; return; }
    if (isAgentLocked.value) return;
    currentAgentId.value = value;
    agentDropdownOpen.value = false;
    try {
        await dispatchArmory({ armoryType: 'work', armoryId: value });
    } catch (error) {
        console.warn('绑定 Work armory 失败', error);
    }
};

const handleClickOutside = (event) => {
    const target = event.target;
    const inAgent = agentSelectRef.value && agentSelectRef.value.contains(target);
    if (!inAgent) agentDropdownOpen.value = false;
};

const handleEscClose = (event) => {
    if (event.key === 'Escape') {
        showSettings.value = false;
        agentDropdownOpen.value = false;
    }
};

const handleKeydown = (event) => {
    if (event.key === 'Enter' && (event.metaKey || event.ctrlKey) && !event.isComposing) {
        event.preventDefault();
        sendMessage();
        return;
    }
    if (event.key === 'Escape') {
        agentDropdownOpen.value = false;
    }
};

const openSettings = () => {
    settingsForm.maxRetry = settingsStore.maxRetry;
    settingsForm.maxRound = settingsStore.maxRound;
    settingsForm.maxPace = settingsStore.maxPace;
    showSettings.value = true;
};

const clampNumber = (value, min, max, fallback = min) => {
    const num = Number(value);
    if (!Number.isFinite(num)) return fallback;
    return Math.min(max, Math.max(min, Math.floor(num)));
};

const saveSettings = () => {
    if (props.preview) { showSettings.value = false; return; }
    settingsStore.updateSettings({
        maxRetry: clampNumber(settingsForm.maxRetry, 1, 3, 2),
        maxRound: clampNumber(settingsForm.maxRound, 1, 3, 2),
        maxPace: clampNumber(settingsForm.maxPace, 3, 5, 3)
    });
    showSettings.value = false;
};

const rangeStyle = (value, min = 1, max = 3) => {
    const clamped = clampNumber(value, min, max, min);
    const denominator = Math.max(1, max - min);
    const percent = ((clamped - min) / denominator) * 100;
    return {
        background: `linear-gradient(90deg, var(--accent-color) ${percent}%, var(--progress-track) ${percent}%)`
    };
};

const resolveAgentDesc = (agentId) => {
    const match = agentOptions.value.find((item) => item.value === agentId);
    return (match?.desc || '').trim() || '暂无';
};

const buildExecutePayload = (userMessage, sessionId, agentId) => ({
    agentId: agentId || currentAgentId.value,
    agentDesc: resolveAgentDesc(agentId || currentAgentId.value),
    userMessage,
    sessionId,
    maxRound: settingsStore.maxRound,
    maxRetry: settingsStore.maxRetry,
    maxPace: clampNumber(settingsStore.maxPace, 3, 5, 3)
});

const sendMessage = async (options = {}) => {
    if (props.preview) { sendError.value = '这是执行过程示例。登录并连接模型后，即可运行真实任务。'; return; }
    const content = (options.content ?? inputValue.value).trim();
    const requestedAgentId = (options.agentId || '').trim();
    const resolvedAgentId = requestedAgentId || currentAgentId.value;
    if (!content || sending.value || !resolvedAgentId) return;
    sendError.value = '';
    const session = await ensureWorkSession({
        forceNew: Boolean(options.forceNew),
        sessionTitle: options.sessionTitle || '新会话',
        silentToast: true
    });
    if (!session) return;
    const validSession = await ensureWorkSessionValid(session, { silentToast: true });
    if (!validSession) return;
    if (userMessageCount.value >= 3) {
        sendError.value = '当前会话已达到 3 条用户消息上限，请新建会话';
        return;
    }
    if (validSession?.sessionId) {
        agentStore.setSessionAgent(validSession.sessionId, resolvedAgentId);
    }
    agentStore.stopCurrentRequest();
    agentStore.setSending(true);
    const controller = new AbortController();
    agentStore.setAbortController(controller);
    agentStore.setSessionSelectionLocked(validSession.sessionId, true);
    agentDropdownOpen.value = false;
    agentStore.addUserMessage(content);
    renameSessionIfNeeded(validSession, content, { silentToast: true });
    if (!Object.prototype.hasOwnProperty.call(options, 'content')) {
        inputValue.value = '';
    }
    scrollRightToBottom(true);
    await runExecute(content, controller, validSession.sessionId, resolvedAgentId);
};

const runExecute = async (content, controller, sessionId, agentId) => {
    const assistantMessage = agentStore.addAssistantMessage({ pending: true, content: '' });
    const events = [];
    let closed = false;

    const finish = () => {
        if (closed) return;
        closed = true;
        const reversed = [...events].reverse();
        const summarizerEvent = reversed.find((item) => item?.sectionType === 'summarizer_overview');
        const replierEvent = reversed.find((item) => item?.sectionType === 'replier_overview');
        const evaluatorEvent = reversed.find((item) => item?.sectionType === 'evaluator_overview');
        const answer = summarizerEvent?.sectionContent || replierEvent?.sectionContent || evaluatorEvent?.sectionContent || '';
        agentStore.updateAssistantMessage(assistantMessage.id, {
            content: answer || '（无内容）',
            pending: false,
            error: null
        });
        agentStore.setSending(false);
        agentStore.setAbortController(null);
        scrollRightToBottom(true);
    };

    const handleError = (error) => {
        if (closed) return;
        const friendly = normalizeError(error);
        closed = true;
        agentStore.updateAssistantMessage(assistantMessage.id, {
            content: friendly.message || '请求失败',
            pending: false,
            error: friendly
        });
        agentStore.setSending(false);
        agentStore.setAbortController(null);
        scrollRightToBottom(true);
    };

    try {
        await executeAgentStream({
            ...buildExecutePayload(content, sessionId, agentId),
            signal: controller.signal,
            onData: (payload) => {
                if (typeof payload === 'string') {
                    handleError(new Error(payload));
                    return;
                }
                if (!payload || typeof payload !== 'object') return;
                const event = {
                    clientType: payload.clientType || '',
                    sectionType: payload.sectionType || '',
                    sectionContent: payload.sectionContent || '',
                    round: payload.round ?? null,
                    pace: payload.pace ?? null,
                    step: payload.step ?? null,
                    timestamp: payload.timestamp ?? null
                };
                events.push(event);
                if (!OVERVIEW_SECTION_TYPES.has(event.sectionType)) {
                    agentStore.addCard(event);
                }
                if (isLeftAtBottom.value) scrollLeftToBottom(true);
            },
            onError: handleError,
            onDone: finish
        });
    } catch (error) {
        handleError(error);
    }
};

const handleStop = () => {
    if (!sending.value) return;
    agentStore.stopCurrentRequest();
    const lastAssistant = [...messages.value].filter((item) => item.role === 'assistant').pop();
    if (lastAssistant && lastAssistant.pending) {
        agentStore.updateAssistantMessage(lastAssistant.id, {
            pending: false,
            error: { message: '已停止生成' }
        });
    }
};

const getContent = (message) => {
    if (!message?.content) return '';
    const raw = message.content.toString();
    return formatMcpJson(raw);
};

const getCardContent = (card) => {
    if (!card?.sectionContent) return '';
    return formatMcpJson(card.sectionContent);
};

const consumeWelcomeLaunchTask = async () => {
    const task = welcomeLaunchStore.takeTask('work');
    const prompt = (task?.prompt || '').trim();
    if (!prompt) return;

    if (!agentOptions.value.length) {
        await fetchAgents();
    }

    if (task.agentId) {
        pendingAgentId.value = task.agentId;
        if (!isAgentLocked.value) {
            currentAgentId.value = task.agentId;
            try {
                await dispatchArmory({ armoryType: 'work', armoryId: task.agentId });
            } catch (error) {
                console.warn('绑定 Work armory 失败', error);
            }
        }
    } else if (!currentAgentId.value && agentOptions.value.length > 0) {
        currentAgentId.value = agentOptions.value[0].value;
    }

    if (!currentAgentId.value) {
        sendError.value = '暂无可用 ZFlow，请先在后台配置 ZFlow';
        return;
    }

    const session = await ensureWorkSession({
        forceNew: true,
        sessionTitle: task.sessionTitle || '新会话',
        silentToast: true
    });
    if (!session) return;
    const validSession = await ensureWorkSessionValid(session, { silentToast: true });
    if (!validSession) return;

    const resolvedAgentId = (task.agentId || currentAgentId.value || '').trim();
    if (!resolvedAgentId) {
        sendError.value = '暂无可用 ZFlow，请先在后台配置 ZFlow';
        return;
    }
    agentStore.setSessionAgent(validSession.sessionId, resolvedAgentId);
    currentAgentId.value = resolvedAgentId;

    inputValue.value = prompt;
    await nextTick();
    await sendMessage({ agentId: resolvedAgentId });
};

onMounted(() => {
    if (props.preview) {
        agentOptions.value = previewAgents.slice(0, 1).map(a => ({ value: a.agentId, label: a.agentName, desc: a.agentDesc, agentType: a.agentType }));
        pendingAgentId.value = previewAgents[0].agentId;
        inputValue.value = String(previewRoute.query.prompt || '');
        return;
    }
    document.addEventListener('click', handleClickOutside);
    window.addEventListener('keydown', handleEscClose);
    scrollLeftToBottom(false);
    scrollRightToBottom(false);
    fetchAgents().then(async () => {
        if (agentStore.currentSession) {
            await ensureWorkSessionValid(agentStore.currentSession);
        }
        await consumeWelcomeLaunchTask();
    });
    autoRefreshTimer = window.setInterval(async () => {
        if (sending.value || messageLoading.value) return;
        const session = agentStore.currentSession;
        const sessionId = session?.sessionId || '';
        if (!sessionId) return;
        await loadWorkMessages(sessionId, { silent: true });
    }, 5000);
});

onBeforeUnmount(() => {
    document.removeEventListener('click', handleClickOutside);
    window.removeEventListener('keydown', handleEscClose);
    if (autoRefreshTimer) {
        window.clearInterval(autoRefreshTimer);
        autoRefreshTimer = null;
    }
});
</script>

<template>
    <section class="conversation-page work-page">
        <header><WorkspaceHeader title="Agent 任务" subtitle="复杂的任务，一步步完成" /><div class="conversation-toolbar"><label class="conversation-select"><FlowIcon name="flow" :size="18" /><span>智能体</span><select :value="currentAgentId" :disabled="isAgentLocked" aria-label="选择智能体" @change="selectAgent($event.target.value)"><option value="" disabled>选择你的 AI 搭档</option><option v-for="agent in agentOptions" :key="agent.value" :value="agent.value">{{ agent.label }}</option></select></label><span v-if="currentAgentTypeLabel" class="agent-strategy">{{ currentAgentTypeLabel }}</span><span class="work-description">{{ currentAgentDesc }}</span><div class="conversation-tools"><button class="soft-button" @click="openSettings"><FlowIcon name="sliders" :size="15" />执行设置</button></div></div></header>

        <div class="overflow-hidden bg-[var(--bg-page)]"><div class="work-panels">
            <section ref="leftScrollRef" class="work-panel" @scroll="handleLeftScroll"><div class="work-panel-title"><FlowIcon name="flow" :size="18" />执行过程<small>{{ preview ? '示例 · 4 个阶段' : sending ? '正在执行…' : cards.length ? cards.length + ' 条阶段记录' : '等待任务开始' }}</small></div><div v-if="messageLoading" class="work-empty">正在加载执行记录…</div><div v-else-if="!cards.length" class="work-empty"><FlowIcon name="flow" :size="35" /><p>每一步，都清晰可见。<br />开始任务后，在这里查看智能体的执行过程。</p></div><div v-else><details v-for="(card, index) in cards" :key="card.id" class="execution-step" open><summary><span class="step-marker">{{ index + 1 }}</span>{{ stageName(card) }}<FlowIcon name="down" :size="13" /></summary><div class="step-meta"><span v-if="card.round != null">第 {{ card.round }} 轮</span><span v-if="card.step != null">步骤 {{ card.step }}</span><span v-if="card.pace != null">进度 {{ card.pace }}</span></div><p>{{ getCardContent(card) }}</p></details></div></section>
            <section ref="rightScrollRef" class="work-panel result-panel" @scroll="handleRightScroll"><div class="work-panel-title"><FlowIcon name="file" :size="18" />任务与结果<small>{{ preview ? '示例内容' : '随执行实时更新' }}</small></div><div v-if="!messages.length" class="work-empty"><FlowIcon name="spark" :size="35" /><p>给一个目标，收获一份成果。<br />你的任务和最终回答将出现在这里。</p></div><div v-for="message in messages" :key="message.id" class="work-message" :class="{ 'has-error': message.error }"><div class="message-author"><FlowIcon :name="message.role === 'user' ? 'chat' : 'spark'" :size="14" />{{ message.role === 'user' ? '你的任务' : 'ZFlow · 执行结果' }}</div><p v-if="message.role === 'user' || message.pending" class="work-user-message">{{ message.content || '正在整理结果…' }}</p><div v-else class="markdown-body work-result" v-html="renderMarkdown(getContent(message))"></div></div></section>
        </div></div>

        <div class="bg-[var(--bg-page)]">
            <div class="mx-auto flex w-full max-w-[900px] flex-col gap-0 py-[12px] pl-[24px] pr-[calc(24px+var(--scrollbar-w))]">
                <div class="conversation-composer flex flex-col gap-[10px]">
                    <textarea
                        v-model="inputValue"
                        class="w-full resize-none rounded-[12px] border border-[var(--border-color)] bg-white px-[14px] py-[12px] text-[14px] shadow-[inset_0_1px_2px_rgba(15,23,42,0.06)] disabled:bg-[#f4f6fb]"
                        rows="2"
                        aria-label="输入 Agent 任务"
                        placeholder="描述你的目标，让智能体帮你一步步完成…"
                        :disabled="sending"
                        @keydown="handleKeydown"
                    ></textarea>
                    <div v-if="sendError" class="text-[12px] text-[var(--text-secondary)]">
                        {{ sendError }}
                    </div>
                    <div class="flex items-center justify-end gap-[10px]"><span class="composer-footnote"><FlowIcon name="flow" :size="13" />Ctrl / ⌘ + Enter 开始执行</span>
                        <button
                            class="inline-flex h-[36px] items-center justify-center rounded-[12px] border border-[var(--border-color)] bg-white px-[14px] py-[9px] font-bold leading-[1.1] text-[var(--text-primary)] transition-all duration-200 hover:bg-[#f7f9fc] disabled:cursor-not-allowed disabled:opacity-70"
                            type="button"
                            v-if="sending"
                            @click="handleStop"
                        >
                            停止生成
                        </button>
                        <button
                            class="inline-flex h-[36px] items-center justify-center rounded-[12px] border border-[var(--accent-color)] bg-[var(--accent-color)] px-[14px] py-[9px] font-bold leading-[1.1] text-white transition-all duration-200 hover:brightness-95 disabled:cursor-not-allowed disabled:opacity-70"
                            type="button"
                            :disabled="sending || !inputValue.trim() || !currentAgentId || userMessageCount >= 3"
                            @click="sendMessage"
                        >
                            {{ sending ? '执行中…' : '开始执行 →' }}
                        </button>
                    </div>
                </div>
            </div>
        </div>

        <Footer />

        <div v-if="showSettings" class="conversation-drawer fixed inset-0 z-[20] grid place-items-center bg-[rgba(0,0,0,0.35)] p-[20px]" @click.self="showSettings = false">
            <div class="w-full max-w-[520px] rounded-[16px] border border-[var(--border-color)] bg-white shadow-[0_20px_50px_rgba(15,23,42,0.2)]">
                <div class="flex items-center justify-between border-b border-[var(--border-color)] px-[18px] pt-[14px] pb-[10px]">
                    <div class="text-[18px] font-bold">回答设置</div>
                    <button class="text-[22px] text-[var(--text-secondary)]" type="button" @click="showSettings = false">×</button>
                </div>
                <div class="flex flex-col gap-[14px] px-[18px] py-[14px]">
                    <div class="flex flex-col gap-[8px]">
                        <div class="inline-flex items-center gap-[8px]">
                            <label class="font-semibold text-[var(--text-primary)]">maxRetry / 最多重试</label>
                            <span
                                class="inline-flex h-[24px] items-center rounded-full border px-[10px] text-[11px] font-bold uppercase tracking-[0.08em]"
                                :style="getStrategyBadgeStyle('step')"
                            >
                                STEP
                            </span>
                        </div>
                        <div class="flex flex-col gap-[10px]">
                            <input
                                v-model.number="settingsForm.maxRetry"
                                type="range"
                                min="1"
                                max="3"
                                step="1"
                                class="h-[6px] w-full cursor-pointer appearance-none rounded-full bg-[var(--progress-track)] [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:h-[14px] [&::-webkit-slider-thumb]:w-[14px] [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-[var(--accent-color)] [&::-webkit-slider-thumb]:shadow-[0_6px_14px_rgba(47,124,246,0.35)] [&::-moz-range-thumb]:h-[14px] [&::-moz-range-thumb]:w-[14px] [&::-moz-range-thumb]:rounded-full [&::-moz-range-thumb]:bg-[var(--accent-color)]"
                                :style="rangeStyle(settingsForm.maxRetry, 1, 3)"
                            />
                            <div class="flex items-center justify-between px-[2px]">
                                <div v-for="value in [1, 2, 3]" :key="`retry-dot-${value}`" class="flex flex-col items-center gap-[6px]">
                                    <span
                                        class="h-[8px] w-[8px] rounded-full"
                                        :class="value === settingsForm.maxRetry ? 'bg-[var(--accent-color)]' : 'bg-[#cbd5e1]'"
                                    ></span>
                                    <span
                                        class="text-[12px]"
                                        :class="settingsForm.maxRetry === value ? 'text-[var(--accent-color)] font-semibold' : 'text-[var(--text-secondary)]'"
                                    >
                                        {{ value }}
                                    </span>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="flex flex-col gap-[8px]">
                        <div class="inline-flex items-center gap-[8px]">
                            <label class="font-semibold text-[var(--text-primary)]">maxRound / 最大轮次</label>
                            <span
                                class="inline-flex h-[24px] items-center rounded-full border px-[10px] text-[11px] font-bold uppercase tracking-[0.08em]"
                                :style="getStrategyBadgeStyle('loop')"
                            >
                                LOOP
                            </span>
                        </div>
                        <div class="flex flex-col gap-[10px]">
                            <input
                                v-model.number="settingsForm.maxRound"
                                type="range"
                                min="1"
                                max="3"
                                step="1"
                                class="h-[6px] w-full cursor-pointer appearance-none rounded-full bg-[var(--progress-track)] [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:h-[14px] [&::-webkit-slider-thumb]:w-[14px] [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-[var(--accent-color)] [&::-webkit-slider-thumb]:shadow-[0_6px_14px_rgba(47,124,246,0.35)] [&::-moz-range-thumb]:h-[14px] [&::-moz-range-thumb]:w-[14px] [&::-moz-range-thumb]:rounded-full [&::-moz-range-thumb]:bg-[var(--accent-color)]"
                                :style="rangeStyle(settingsForm.maxRound, 1, 3)"
                            />
                            <div class="flex items-center justify-between px-[2px]">
                                <div v-for="value in [1, 2, 3]" :key="`round-dot-${value}`" class="flex flex-col items-center gap-[6px]">
                                    <span
                                        class="h-[8px] w-[8px] rounded-full"
                                        :class="value === settingsForm.maxRound ? 'bg-[var(--accent-color)]' : 'bg-[#cbd5e1]'"
                                    ></span>
                                    <span
                                        class="text-[12px]"
                                        :class="settingsForm.maxRound === value ? 'text-[var(--accent-color)] font-semibold' : 'text-[var(--text-secondary)]'"
                                    >
                                        {{ value }}
                                    </span>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="flex flex-col gap-[8px]">
                        <div class="inline-flex items-center gap-[8px]">
                            <label class="font-semibold text-[var(--text-primary)]">maxPace / 最大步伐</label>
                            <span
                                class="inline-flex h-[24px] items-center rounded-full border px-[10px] text-[11px] font-bold uppercase tracking-[0.08em]"
                                :style="getStrategyBadgeStyle('react')"
                            >
                                REACT
                            </span>
                        </div>
                        <div class="flex flex-col gap-[10px]">
                            <input
                                v-model.number="settingsForm.maxPace"
                                type="range"
                                min="3"
                                max="5"
                                step="1"
                                class="h-[6px] w-full cursor-pointer appearance-none rounded-full bg-[var(--progress-track)] [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:h-[14px] [&::-webkit-slider-thumb]:w-[14px] [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-[var(--accent-color)] [&::-webkit-slider-thumb]:shadow-[0_6px_14px_rgba(47,124,246,0.35)] [&::-moz-range-thumb]:h-[14px] [&::-moz-range-thumb]:w-[14px] [&::-moz-range-thumb]:rounded-full [&::-moz-range-thumb]:bg-[var(--accent-color)]"
                                :style="rangeStyle(settingsForm.maxPace, 3, 5)"
                            />
                            <div class="flex items-center justify-between px-[2px]">
                                <div v-for="value in [3, 4, 5]" :key="`pace-dot-${value}`" class="flex flex-col items-center gap-[6px]">
                                    <span
                                        class="h-[8px] w-[8px] rounded-full"
                                        :class="value === settingsForm.maxPace ? 'bg-[var(--accent-color)]' : 'bg-[#cbd5e1]'"
                                    ></span>
                                    <span
                                        class="text-[12px]"
                                        :class="settingsForm.maxPace === value ? 'text-[var(--accent-color)] font-semibold' : 'text-[var(--text-secondary)]'"
                                    >
                                        {{ value }}
                                    </span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="flex justify-end gap-[10px] border-t border-[var(--border-color)] px-[18px] pt-[12px] pb-[16px]">
                    <button
                        class="inline-flex items-center justify-center rounded-[12px] border border-[var(--border-color)] bg-white px-[14px] py-[9px] font-bold leading-[1.1] text-[var(--text-primary)] transition-all duration-200 hover:bg-[#f7f9fc]"
                        type="button"
                        @click="showSettings = false"
                    >
                        取消
                    </button>
                    <button
                        class="inline-flex items-center justify-center rounded-[12px] border border-[var(--accent-color)] bg-[var(--accent-color)] px-[14px] py-[9px] font-bold leading-[1.1] text-white transition-all duration-200 hover:brightness-95"
                        type="button"
                        @click="saveSettings"
                    >
                        保存
                    </button>
                </div>
            </div>
        </div>
    </section>
</template>

<style scoped>
.caret {
    display: inline-block;
    width: 0;
    height: 0;
    border-left: 6px solid transparent;
    border-right: 6px solid transparent;
    border-top: 7px solid #94a3b8;
    transform-origin: center;
    transition: transform 0.15s ease;
}

.caret-open {
    transform: rotate(0deg);
}

.caret-closed {
    transform: rotate(-90deg);
}
</style>
