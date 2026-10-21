<script setup>
import Icon from '../Icon.vue';
import Sheet from '../Sheet.vue';
defineProps({
  sessions: { type: Array, default: () => [] },
  activeId: { type: String, default: '' },
  work: { type: Boolean, default: false }
});
const emit = defineEmits(['close', 'select', 'new', 'rename', 'delete']);
</script>
<template>
  <Sheet title="会话" @close="emit('close')">
    <div class="session-sheet">
      <button class="session-sheet__new" type="button" @click="emit('new')"><Icon name="plus" :size="16" />{{ work ? '开始新任务' : '开启新对话' }}</button>
      <div class="session-sheet__list">
        <div v-for="session in sessions" :key="session.sessionId" class="session-sheet__row" :class="{ active: activeId === session.sessionId }">
          <button class="session-sheet__select" type="button" @click="emit('select', session)">
            <Icon :name="work ? 'workflow' : 'chat'" :size="16" />
            <span class="session-sheet__title">{{ session.sessionTitle }}</span>
          </button>
          <span class="session-sheet__actions">
            <button class="icon-button" type="button" aria-label="重命名会话" @click="emit('rename', session)"><Icon name="pen" :size="13" /></button>
            <button class="icon-button" type="button" aria-label="删除会话" @click="emit('delete', session)"><Icon name="trash" :size="13" /></button>
          </span>
        </div>
        <p v-if="!sessions.length" class="session-sheet__empty">还没有会话。</p>
      </div>
    </div>
  </Sheet>
</template>
<style scoped>
.session-sheet { display: flex; flex-direction: column; gap: var(--z-space-4); }
.session-sheet__new { display: flex; align-items: center; gap: var(--z-space-2); width: 100%; min-height: 40px; padding: 0 var(--z-space-4); border: 1px solid var(--z-line); border-radius: var(--z-r-control); background: var(--z-paper-2); color: var(--z-ink); font: 500 13px/1 var(--z-sans); }
.session-sheet__new:hover { background: var(--z-fill); }
.session-sheet__list { display: flex; flex-direction: column; }
.session-sheet__row { display: flex; align-items: center; gap: var(--z-space-2); min-height: 48px; border-bottom: 1px solid var(--z-line); }
.session-sheet__row.active .session-sheet__title { color: var(--z-blue); }
.session-sheet__select { display: flex; align-items: center; gap: var(--z-space-3); flex: 1; min-width: 0; padding: var(--z-space-2) 0; text-align: left; color: var(--z-ink-2); }
.session-sheet__select:hover .session-sheet__title { color: var(--z-blue); }
.session-sheet__title { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; font: 400 14px/1.5 var(--z-sans); color: var(--z-ink); }
.session-sheet__actions { display: flex; gap: var(--z-space-1); opacity: 0; }
.session-sheet__row:hover .session-sheet__actions, .session-sheet__row:focus-within .session-sheet__actions { opacity: 1; }
.session-sheet__empty { padding: var(--z-space-5) 0; font: 400 13px/1.5 var(--z-sans); color: var(--z-ink-4); }
</style>
