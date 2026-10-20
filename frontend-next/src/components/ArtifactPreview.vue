<script setup>
import { computed, useId } from 'vue';
import { diagramPath } from '../lib/artifacts.js';
const props = defineProps({ type: String, content: Object, miniature: Boolean, editable: Boolean, selected: String });
const emit = defineEmits(['select', 'change']);
const markerId = 'arrow-' + useId().replaceAll(':', '');
const documentLines = computed(() => (props.content?.text || '').split('\n').filter(Boolean));
function updateCell(row, column, value) { const copy = JSON.parse(JSON.stringify(props.content)); copy.rows[row][column] = value; emit('change', copy); }
</script>
<template>
  <div class="artifact-preview" :class="[type, { miniature }]">
    <svg v-if="type === 'diagram'" viewBox="0 0 660 440" role="img" aria-label="订单系统架构图示例">
      <defs><marker :id="markerId" markerWidth="7" markerHeight="7" refX="5" refY="3.5" orient="auto"><path d="M0 0 L6 3.5 L0 7" fill="none" stroke="currentColor" /></marker></defs>
      <path v-for="([a, b], i) in content.edges" :key="i" class="diagram-edge" :d="diagramPath(content, a, b)" :marker-end="`url(#${markerId})`" />
      <g v-for="node in content.nodes" :key="node.id" :transform="`translate(${node.x} ${node.y})`" :class="{ 'diagram-node': true, editable, selected: selected === node.id, gateway: node.id === 'gateway' }" :tabindex="editable ? 0 : undefined" :role="editable ? 'button' : undefined" :aria-label="editable ? '编辑节点 ' + node.label : undefined" @click="editable && emit('select', node.id)" @keydown.enter="editable && emit('select', node.id)" @keydown.space.prevent="editable && emit('select', node.id)">
        <rect :width="node.w" :height="node.h" rx="7" /><text :x="node.w / 2" y="25" text-anchor="middle" class="node-name">{{ node.label }}</text><text :x="node.w / 2" y="43" text-anchor="middle" class="node-detail">{{ node.detail }}</text>
      </g>
    </svg>
    <div v-else-if="type === 'document'" class="document-sheet">
      <div class="doc-topline"><span>ZFlow / 项目文档</span><span>方案示例</span></div>
      <template v-for="(line, index) in documentLines" :key="index">
        <h2 v-if="line.startsWith('# ')">{{ line.slice(2) }}</h2><h3 v-else-if="line.startsWith('## ')">{{ line.slice(3) }}</h3><p v-else :class="{ 'doc-list': line.startsWith('- ') }">{{ line.startsWith('- ') ? line.slice(2) : line }}</p>
      </template>
    </div>
    <div v-else-if="type === 'table'" class="sheet-wrap"><div class="sheet-title">项目任务清单 <span>表格示例</span></div><table class="artifact-table"><thead><tr><th class="row-index"></th><th v-for="(column, i) in content.columns" :key="i">{{ column }}</th></tr></thead><tbody><tr v-for="(row, r) in content.rows" :key="r"><td class="row-index">{{ r + 1 }}</td><td v-for="(cell, c) in row" :key="c"><input v-if="editable" :value="cell" :aria-label="`第 ${r + 1} 行 ${content.columns[c]}`" @input="updateCell(r, c, $event.target.value)" /><span v-else :class="{ 'table-status': c === 3, 'table-priority': c === 2 }">{{ cell }}</span></td></tr></tbody></table></div>
  </div>
</template>
