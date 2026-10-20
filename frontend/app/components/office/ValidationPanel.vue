<script setup>
import { ref } from 'vue';
defineProps({ report: Object });
const copyStatus = ref('');
async function copyIssue(issue) {
  try { await navigator.clipboard.writeText(`页面：${issue.pageName ?? issue.pageId ?? issue.pageIndex ?? '未提供'}\n节点 ID：${issue.cellId ?? issue.nodeId ?? '未提供'}\n${issue.message || issue.reason || issue.code}`); copyStatus.value = '已复制问题详情'; }
  catch { copyStatus.value = '复制未完成，请选择问题文字手动复制。'; }
}
</script>
<template><div class="office-validation"><p v-if="copyStatus" role="status">{{ copyStatus }}</p><template v-if="report"><p>{{ report.valid ? '当前版本已通过结构校验' : '图表未通过校验，修改尚未保存' }}</p><p class="office-muted">{{ report.pageCount ?? '未知' }} 页 · {{ report.nodeCount ?? '未知' }} 个节点 · {{ report.edgeCount ?? '未知' }} 条连接</p><article v-for="(issue, i) in report.issues || []" :key="i"><strong>{{ issue.message || issue.reason || issue.code }}</strong><dl><dt>页面</dt><dd>{{ issue.pageName ?? issue.pageId ?? issue.pageIndex ?? '未提供' }}</dd><dt>节点 ID</dt><dd>{{ issue.cellId || issue.nodeId || '未提供' }}</dd><dt>原因</dt><dd>{{ issue.code || issue.errorKey }}</dd></dl><button class="secondary-button" @click="copyIssue(issue)">复制问题详情</button></article><p v-if="report.truncated">报告已截断，请修正以上问题后重新保存。</p></template><p v-else>尚无校验报告。</p></div></template>
