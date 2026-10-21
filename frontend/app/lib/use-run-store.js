// use-run-store.js
// ZFlow「Quiet Canvas 2.0」P0：把 run-store 提升为模块级单例。
// App.vue 用 :key 会在切换页面 / 账号时重建 Conversation.vue，但 run 必须归
// store 所有、不随组件销毁；本文件只做「单例 + 响应式快照」的组合式封装。
import { ref } from 'vue';
import { createRunStore } from '../../ui-v2-p0/src/run-store.js';

// 模块级单例：整个 SPA 生命周期只有这一份。
const store = createRunStore();

// 与 store 同步的响应式快照，供 Vue 计算属性派生 UI（busy / canSend / statusText）。
const runState = ref(store.getState());
store.subscribe(state => {
  runState.value = state;
});

export function useRunStore() {
  return { store, runState };
}
