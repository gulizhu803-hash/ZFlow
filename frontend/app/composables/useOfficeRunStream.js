import { reactive, onBeforeUnmount } from 'vue';
import { eventState, applySnapshot, reduceOfficeEvent, terminal } from '../lib/office-events.js';

// A stream belongs to one selected run. Disconnecting never cancels that run.
export function useOfficeRunStream(api, onSnapshot, onError) {
  const state = reactive({ model: null, connection: 'idle', warning: '' });
  let epoch = 0, controller, timer, runId = '', stopped = false, polling = false, failures = 0;
  const available = () => navigator.onLine && !document.hidden;
  function stop() { epoch++; stopped = true; clearTimeout(timer); controller?.abort(); state.connection = 'idle'; }
  function schedule(ticket, delay) {
    if (ticket !== epoch || stopped) return;
    clearTimeout(timer); timer = setTimeout(() => cycle(ticket), delay);
  }
  async function cycle(ticket) {
    if (ticket !== epoch || stopped) return;
    if (!available()) { state.connection = navigator.onLine ? 'idle' : 'offline'; return; }
    controller = new AbortController();
    try {
      state.connection = polling ? 'polling' : failures ? 'reconnecting' : 'connecting';
      const snapshot = await api.getRun(runId, controller.signal);
      if (ticket !== epoch) return;
      if (!state.model) state.model = eventState(snapshot); else applySnapshot(state.model, snapshot);
      onSnapshot(snapshot);
      if (terminal(snapshot.status) && (polling || state.model.confirmedSeq >= Number(snapshot.lastEventSeq || 0))) { state.connection = 'idle'; return; }
      if (polling) { failures = 0; schedule(ticket, 5000); return; }
      state.connection = 'streaming'; let issue = ''; const beforeSeq = state.model.confirmedSeq;
      await api.subscribeRun(runId, state.model.confirmedSeq, event => {
        if (ticket !== epoch || issue) return;
        const result = reduceOfficeEvent(state.model, event);
        if (result === 'gap' || result === 'protocol') { issue = result; controller.abort(); }
        else if (result === 'applied') failures = 0;
      }, controller.signal).catch(error => { if (!issue) throw error; });
      if (ticket !== epoch) return;
      if (issue === 'protocol') { polling = true; state.warning = '事件协议暂不兼容，已改为定期查询任务状态。'; }
      failures = issue === 'gap' || beforeSeq === state.model.confirmedSeq ? failures + 1 : 0;
      // Repeated holes must not create an endless replay loop.
      if (failures >= 3) { polling = true; state.warning = '部分进度记录暂不可用，已改为查询任务状态。'; }
      schedule(ticket, failures ? Math.min(15000, 1000 * 2 ** failures) : 250);
    } catch (error) {
      if (ticket !== epoch || stopped || error.name === 'AbortError') return;
      if ([401, 403, 404].includes(error.status)) { state.connection = error.status === 401 ? 'authRequired' : 'idle'; onError(error); return; }
      failures++; state.connection = navigator.onLine ? 'reconnecting' : 'offline';
      state.warning = error.message;
      schedule(ticket, Math.max(error.retryAfter || 0, Math.min(15000, 1000 * 2 ** Math.min(failures - 1, 4)) + Math.random() * 300));
    }
  }
  function start(id) { stop(); runId = id; state.model = null; state.warning = ''; polling = false; failures = 0; stopped = !id; if (id) cycle(epoch); }
  function resume() {
    if (stopped || !runId) return;
    epoch++; clearTimeout(timer); controller?.abort();
    if (available()) cycle(epoch); else state.connection = navigator.onLine ? 'idle' : 'offline';
  }
  document.addEventListener('visibilitychange', resume); window.addEventListener('online', resume); window.addEventListener('offline', resume);
  onBeforeUnmount(() => { stop(); document.removeEventListener('visibilitychange', resume); window.removeEventListener('online', resume); window.removeEventListener('offline', resume); });
  return { state, start, stop, refresh: resume };
}
