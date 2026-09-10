<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';
import Icon from '../components/Icon.vue';
import Modal from '../components/Modal.vue';
import AgentCard from '../components/AgentCard.vue';
import { state, isDemo, notify, report, nav } from '../lib/workspace.js';
import { post } from '../lib/api.js';
import { strategies } from '../lib/demo.js';
const keyword = ref(''), sortBy = ref('like'), page = ref(1), total = ref(0), items = ref([]), loading = ref(false), error = ref('');
const selected = ref(null), template = ref(null), comments = ref([]), comment = ref(''), commentPage = ref(1), commentTotal = ref(0), detailsBusy = ref(false), actionBusy = ref(false), deleteComment = ref(null);
let sequence = 0, detailSequence = 0, alive = true;
const pending = new Set(), timers = [];
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / 9)));
async function load() {
  const ticket = ++sequence; loading.value = true; error.value = '';
  try {
    if (isDemo.value) { let list = state.plaza.filter(a => (a.plazaTitle + a.plazaDesc).toLowerCase().includes(keyword.value.toLowerCase())); list = [...list].sort((a, b) => (b[sortBy.value + 'Count'] || 0) - (a[sortBy.value + 'Count'] || 0)); total.value = list.length; items.value = list.slice((page.value - 1) * 9, page.value * 9); }
    else { const result = await post('/workspace/plaza/page', { json: { keyword: keyword.value, sortBy: sortBy.value, sortOrder: 'desc', pageNum: page.value, pageSize: 9 } }); if (ticket !== sequence || !alive) return; items.value = result?.list || []; total.value = result?.total || 0; }
  } catch (e) { if (ticket === sequence) error.value = e.message; } finally { if (ticket === sequence) loading.value = false; }
}
function search() { page.value = 1; load(); }
function reconcile() { if (isDemo.value) return; for (const ms of [1800, 5500]) timers.push(setTimeout(() => { if (alive) load(); }, ms)); }
async function react(item, kind) {
  const key = item.plazaId + kind; if (pending.has(key)) return; pending.add(key);
  const field = kind === 'like' ? 'liked' : 'favored', count = kind + 'Count', selected = !!item[field];
  try { if (!isDemo.value) await post('/workspace/plaza/' + (selected ? 'dis' : '') + kind, { query: { plazaId: item.plazaId } }); item[field] = !selected; if (isDemo.value && kind === 'favor') { if (selected) state.repo.favor = state.repo.favor.filter(a => a.templateId !== item.templateId); else state.repo.favor.push({ ...item, repoId: 'demo-favorite-' + item.plazaId, repoType: 'favor' }); } item[count] = Math.max(0, (item[count] || 0) + (selected ? -1 : 1)); notify(isDemo.value ? (selected ? '已取消' : kind === 'like' ? '喜欢已收下' : '已收藏演示模板') : '操作已提交，正在等待同步'); reconcile(); }
  catch (e) { report(e); } finally { pending.delete(key); }
}
async function loadComments() {
  if (!selected.value) return; const id = selected.value.plazaId;
  if (isDemo.value) { comments.value = selected.value.demoComments || []; commentTotal.value = comments.value.length; return; }
  const result = await post('/workspace/plaza/comment-area', { json: { plazaId: id, pageNum: commentPage.value, pageSize: 5 } });
  if (selected.value?.plazaId !== id) return; comments.value = result?.list || []; commentTotal.value = result?.total || 0;
}
async function open(item) {
  const ticket = ++detailSequence; selected.value = item; template.value = null; comments.value = []; comment.value = ''; commentPage.value = 1; detailsBusy.value = true;
  try { const result = isDemo.value ? { ...item, agentDesc: item.plazaDesc, modelName: item.modelName, clientInfoList: (strategies[item.agentType]?.roles || []).map(role => ({ clientRole: role, systemPrompt: '围绕用户目标，完成当前角色的职责，输出清晰可靠的结果。' })) } : await post('/workspace/agent/template', { query: { templateId: item.templateId } }); if (ticket !== detailSequence || !selected.value) return; template.value = result; await loadComments(); }
  catch (e) { report(e); } finally { if (ticket === detailSequence) detailsBusy.value = false; }
}
async function fork() {
  if (actionBusy.value || !selected.value) return; actionBusy.value = true;
  try { if (isDemo.value) { const agent = { ...template.value, agentId: 'demo-copy-' + Date.now(), repoId: 'demo-copy-repo-' + Date.now(), repoType: 'fork' }; state.repo.fork.unshift(agent); state.agents.push(agent); } else await post('/workspace/agent/fork', { query: { templateId: selected.value.templateId } }); notify(isDemo.value ? '演示模板已复制到工作区' : '复制请求已提交，请稍后刷新工作区确认'); selected.value = null; nav('agents'); }
  catch (e) { report(e); } finally { actionBusy.value = false; }
}
async function sendComment() {
  if (!comment.value.trim() || actionBusy.value) return; actionBusy.value = true;
  try {
    if (isDemo.value) { if (!selected.value.demoComments) selected.value.demoComments = []; selected.value.demoComments.unshift({ commentId: 'demo-comment-' + Date.now(), userName: '演示访客', mine: true, commentContent: comment.value.trim(), createTime: new Date().toISOString() }); await loadComments(); }
    else { await post('/workspace/plaza/comment', { json: { plazaId: selected.value.plazaId, commentContent: comment.value.trim() } }); timers.push(setTimeout(() => { if (alive && selected.value) loadComments().catch(report); }, 2000)); }
    comment.value = ''; notify(isDemo.value ? '演示评论已添加' : '评论已提交，稍后同步');
  } catch (e) { report(e); } finally { actionBusy.value = false; }
}
async function removeComment(item) { if (actionBusy.value) return; actionBusy.value = true; try { if (!isDemo.value) await post('/workspace/plaza/discomment', { query: { commentId: item.commentId, plazaId: selected.value.plazaId } }); else { selected.value.demoComments = selected.value.demoComments.filter(c => c.commentId !== item.commentId); await loadComments(); } deleteComment.value = null; notify(isDemo.value ? '演示评论已删除' : '删除评论请求已提交'); } catch (e) { report(e); } finally { actionBusy.value = false; } }
onMounted(load); onBeforeUnmount(() => { alive = false; sequence++; detailSequence++; timers.forEach(clearTimeout); });
</script>
<template>
  <div class="page"><header class="page-heading"><div><div class="eyebrow">GOOD IDEAS ARE BETTER TOGETHER</div><h1>发现一点新可能</h1><p>来自创造者们的灵感，成为你的下一位得力伙伴。</p></div><span class="community-mark"><span>✳</span> MADE TO BE SHARED</span></header>
    <section class="plaza-hero"><div><span class="tag">THE COMMUNITY COLLECTION</span><h2>让好想法，<br />在彼此之间生长。</h2><p>研究、写作、编程、学习。<br />总有一个智能体，能让你的日常轻松一点。</p><button class="primary-button" @click="nav('agents', { create: '1' })">创造你的独特伙伴 <Icon name="arrow" :size="17" /></button></div><div class="community-art" aria-hidden="true"><div class="community-tile tile-a"><Icon name="pen" :size="40" /><span>Make something.</span></div><div class="community-tile tile-b"><Icon name="bot" :size="48" /><span>Think together.</span></div><div class="community-tile tile-c"><Icon name="code" :size="37" /><span>Keep exploring.</span></div><span class="community-star">✳</span></div></section>
    <div class="toolbar"><div class="tabs"><button :class="{ active: sortBy === 'like' }" @click="sortBy = 'like'; search()">热门推荐</button><button :class="{ active: sortBy === 'favor' }" @click="sortBy = 'favor'; search()">最多收藏</button><button :class="{ active: sortBy === 'comment' }" @click="sortBy = 'comment'; search()">大家在聊</button><button :class="{ active: sortBy === '' }" @click="sortBy = ''; search()">最新发布</button></div><form class="search-field" @submit.prevent="search"><Icon name="search" :size="16" /><input v-model="keyword" placeholder="搜索你需要的能力" aria-label="搜索广场" /><button type="submit" class="text-button">搜索</button></form></div>
    <div v-if="isDemo" class="demo-notice"><i class="tiny-dot"></i>社区示例内容 · 点赞、评论与复制仅用于演示</div><div v-if="error" class="form-error row spread">{{ error }}<button class="text-button" @click="load">重试</button></div><div v-else-if="loading" class="loading-placeholder">正在发现好用的智能体…</div><div v-else-if="items.length" class="card-grid plaza-grid"><AgentCard v-for="item in items" :key="item.plazaId" :agent="item" community @open="open" @like="react($event, 'like')" @favor="react($event, 'favor')" /></div><div v-else class="empty-state"><Icon name="compass" :size="40" /><h2>好想法还在路上</h2><p>没有找到相关智能体，试试其他关键词，或者分享你的第一个作品。</p></div><div v-if="pageCount > 1" class="pagination"><button class="secondary-button" :disabled="page <= 1 || loading" @click="page--; load()">上一页</button><span>{{ page }} / {{ pageCount }}</span><button class="secondary-button" :disabled="page >= pageCount || loading" @click="page++; load()">下一页</button></div>
  </div>
  <Modal v-if="selected" :title="selected.plazaTitle" wide @close="selected = null; detailSequence++"><div class="modal-body"><div v-if="detailsBusy" class="loading-placeholder">正在读取模板…</div><template v-if="template"><div class="detail-overview"><span class="agent-avatar" :class="strategies[template.agentType]?.color"><Icon :name="template.icon || strategies[template.agentType]?.icon" :size="25" /></span><div><h3>{{ template.agentName }}</h3><p>由 {{ selected.userName }} 分享 · {{ strategies[template.agentType]?.label }}</p></div><button class="primary-button" :disabled="actionBusy" @click="fork"><Icon name="copy" :size="16" />复制到工作区</button></div><p class="detail-description">{{ template.agentDesc || selected.plazaDesc }}</p><div class="flow-preview"><template v-for="(role, i) in strategies[template.agentType]?.roles || []" :key="role"><Icon v-if="i" name="arrow" :size="15" /><span><i>{{ i + 1 }}</i>{{ role }}</span></template></div><div class="template-facts"><span><Icon name="layers" :size="16" />{{ template.modelName || '未标注模型' }}</span><span><Icon name="plug" :size="16" />{{ template.mcpInfoList?.length || 0 }} 个工具</span></div><details v-for="(role, i) in template.clientInfoList" :key="i" class="template-prompt"><summary>{{ role.clientRole }}<Icon name="down" :size="15" /></summary><pre>{{ role.systemPrompt }}</pre></details><div v-for="(tool, i) in template.mcpInfoList" :key="i" class="info-note">{{ tool.mcpName }}<span v-if="tool.requiredSecrets?.length"> · 使用前需补充：{{ tool.requiredSecrets.join('、') }}</span></div><section class="comments-section"><div class="section-heading"><h2>一起聊聊 <span class="count">{{ commentTotal }}</span></h2><button class="text-button" @click="loadComments().catch(report)">刷新评论</button></div><form class="comment-form" @submit.prevent="sendComment"><label class="sr-only" for="comment-content">评论内容</label><textarea id="comment-content" v-model="comment" rows="2" placeholder="分享你的使用想法…" required /><button class="primary-button" :disabled="actionBusy || !comment.trim()">发表评论</button></form><div v-for="item in comments" :key="item.commentId" class="comment-item"><span class="mini-avatar">{{ item.userName?.slice(0,1) }}</span><div><strong>{{ item.userName }}</strong><p>{{ item.commentContent }}</p><time>{{ item.createTime?.replace('T',' ').slice(0,16) }}</time></div><button v-if="item.mine && deleteComment !== item.commentId" class="icon-button" aria-label="删除我的评论" @click="deleteComment = item.commentId"><Icon name="trash" :size="14" /></button><button v-else-if="item.mine" class="text-button" :disabled="actionBusy" @click="removeComment(item)">确认删除</button></div><p v-if="!comments.length" class="small muted centered">还没有评论，留下第一个想法吧。</p><div v-if="commentTotal > 5 && !isDemo" class="pagination"><button :disabled="commentPage === 1" @click="commentPage--; loadComments().catch(report)">上一页</button><span>{{ commentPage }}</span><button :disabled="commentPage * 5 >= commentTotal" @click="commentPage++; loadComments().catch(report)">下一页</button></div></section></template></div></Modal>
</template>
