// use-run-store.js
// ZFlow「Quiet Canvas 2.0」P0：把 run-store 提升为模块级单例。
// App.vue 用 :key 会在切换页面 / 账号时重建 Conversation.vue，但 run 必须归
// store 所有、不随组件销毁；本文件只做「单例 + 响应式快照」的组合式封装。
import { ref } from 'vue';
import { createRunStore } from '../../ui-v2-p0/src/run-store.js';
import { state } from './workspace.js';

// 模块级单例：整个 SPA 生命周期只有这一份。
const store = createRunStore();

// 与 store 同步的响应式快照，供 Vue 计算属性派生 UI（busy / canSend / statusText）。
const runState = ref(store.getState());
store.subscribe(state => {
  runState.value = state;
});

// 运行事件总线：页面重建后，旧 adapter 继续跑，但回调通过这里交给新页面。
const runEventListeners = new Set();

export function subscribeRunEvents(listener) {
  runEventListeners.add(listener);
  return () => runEventListeners.delete(listener);
}

export function emitRunEvent(event) {
  for (const listener of [...runEventListeners]) {
    try { listener(event); } catch (error) { console.error('run event listener failed', error); }
  }
}

const ownerOf = () => String(state.auth?.userId ?? state.auth?.userName ?? '');
let currentOwner = ownerOf();

export function useRunStore() {
  // 账号切换必须切断旧 run，避免上一个账号的 prompt / stage 残留。
  const nextOwner = ownerOf();
  if (nextOwner !== currentOwner) {
    currentOwner = nextOwner;
    store.reset();
  }
  return { store, runState };
}
