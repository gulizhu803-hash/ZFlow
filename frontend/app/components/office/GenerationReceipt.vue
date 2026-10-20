<script setup>
defineProps({ receipt: Object, run: Object });
const value = v => v == null ? '未取得' : v;
const time = v => v ? new Date(v).toLocaleString('zh-CN', { timeZoneName: 'short' }) : '未取得';
</script>
<template>
  <div v-if="receipt" class="office-receipt">
    <p v-if="receipt.generationMode === 'fixture'" class="office-note">固定样例，未调用模型</p>
    <dl><dt>开始时间</dt><dd>{{ time(run?.startedAt) }}</dd><dt>结束时间</dt><dd>{{ time(run?.finishedAt) }}</dd></dl>
    <dl><dt>模型</dt><dd>{{ value(receipt.model) }}</dd><dt>调用尝试</dt><dd>{{ value(receipt.attemptCount) }}</dd><dt>耗时</dt><dd>{{ receipt.durationMs == null ? '未取得' : (receipt.durationMs / 1000).toFixed(1) + ' 秒' }}</dd><dt>输入 Token</dt><dd>{{ value(receipt.knownUsage?.inputTokens) }}</dd><dt>输出 Token</dt><dd>{{ value(receipt.knownUsage?.outputTokens) }}</dd><dt>总已知 Token</dt><dd>{{ value(receipt.knownUsage?.totalTokens) }}</dd></dl>
    <p v-if="!receipt.usageComplete" class="office-muted">仅含已取得用量；未知值不计为零。</p>
    <article v-for="attempt in receipt.attempts || []" :key="attempt.attemptNo" class="receipt-attempt"><strong>第 {{ attempt.attemptNo }} 次调用</strong><dl><dt>用途</dt><dd>{{ ({ GENERATE: '生成图表', REPAIR: '修复图表', generate: '生成图表', repair: '修复图表' })[attempt.purpose] || attempt.purpose }}</dd><dt>结果</dt><dd>{{ ({ SUCCEEDED: '成功', FAILED: '失败', SUCCESS: '成功' })[attempt.outcome] || attempt.outcome }}</dd><dt>耗时</dt><dd>{{ attempt.durationMs == null ? '未取得' : attempt.durationMs + ' ms' }}</dd></dl></article>
  </div><p v-else class="office-muted">生成记录尚未取得，可重试读取。</p>
</template>
