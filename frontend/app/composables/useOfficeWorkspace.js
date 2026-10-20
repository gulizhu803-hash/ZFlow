import { reactive, onBeforeUnmount } from 'vue';
import { state as app, ownerId, replaceRoute } from '../lib/workspace.js';
import { useOfficeRunStream } from './useOfficeRunStream.js';
import { newKey, unknownOutcome } from '../lib/office-drafts.js';

export function useOfficeWorkspace(api, canReveal) {
  const owner = ownerId(app.auth), pendingKey = 'zflow.office.submit.' + owner;
  const view = reactive({ sessions: [], sessionsPage: 0, sessionsTotal: 0, session: null, runs: [], runsPage: 0, runsTotal: 0,
    files: [], filesPage: 0, filesTotal: 0, run: null, artifact: null, revision: null, xml: '', revisions: [], revisionsPage: 0, revisionsTotal: 0,
    busy: false, listsBusy: {}, error: '', errorTraceId: '', notice: '', submitting: false, pending: null, rateLimited: false, sessionCreating: false, sessionUnknown: false, cancelling: false, receipt: null });
  let epoch = 0, controller, alive = true, rateTimer;
  const listRequests = {};
  const valid = ticket => alive && ticket === epoch && owner === ownerId(app.auth);
  const fail = e => { if (e.name !== 'AbortError' && alive && owner === ownerId(app.auth)) { view.error = e.message; view.errorTraceId = e.traceId || ''; } };
  try { view.pending = JSON.parse(sessionStorage.getItem(pendingKey)); } catch {}
  function persistPending() { try { if (view.pending) sessionStorage.setItem(pendingKey, JSON.stringify(view.pending)); else sessionStorage.removeItem(pendingKey); } catch { view.notice = '无法保存请求记录；确认提交结果前请保持此页打开。'; } }
  const stream = useOfficeRunStream(api, snapshot => {
    if (!alive || owner !== ownerId(app.auth) || snapshot.sessionId !== view.session?.sessionId || snapshot.runId !== app.route.params.run) return;
    view.run = snapshot;
    const i = view.runs.findIndex(r => r.runId === snapshot.runId); if (i >= 0) view.runs[i] = snapshot;
    if (snapshot.status === 'SUCCEEDED' && snapshot.artifacts?.length && !app.route.params.artifact && canReveal()) {
      const first = snapshot.artifacts[0]; replaceRoute('office', { ...app.route.params, artifact: first.artifactId, version: first.version });
    }
  }, fail);
  async function list(name, more, fetchPage, accepted) {
    if (listRequests[name]?.epoch === epoch) return;
    const request = { epoch }, page = more ? view[name + 'Page'] + 1 : 1;
    listRequests[name] = request; view.listsBusy[name] = true;
    try {
      const result = await fetchPage({ page, pageSize: 20 }); if (!valid(request.epoch)) return;
      view[name] = more ? [...view[name], ...result.items] : result.items;
      view[name + 'Page'] = page; view[name + 'Total'] = result.total; accepted?.();
    } catch (e) { if (valid(request.epoch)) fail(e); }
    finally { if (listRequests[name] === request) { delete listRequests[name]; view.listsBusy[name] = false; } }
  }
  function listSessions(more = false) {
    return list('sessions', more, paging => api.listSessions(paging), () => { app.officeSessions = [...view.sessions]; });
  }
  async function createSession(title) {
    if (view.sessionCreating || view.sessionUnknown || !title.trim()) return;
    const ticket = epoch; view.sessionCreating = true; view.error = '';
    try {
      const session = await api.createSession(title.trim()); if (!valid(ticket)) return;
      view.sessions.unshift(session); app.officeSessions = [...view.sessions];
      replaceRoute('office', { session: session.sessionId }); return session;
    } catch (e) { if (valid(ticket)) { view.sessionUnknown = unknownOutcome(e); fail(e); await listSessions(); } }
    finally { if (alive) view.sessionCreating = false; }
  }
  function listRuns(more = false) {
    if (!view.session) return; const id = view.session.sessionId;
    return list('runs', more, paging => api.listRuns(id, paging));
  }
  function listFiles(more = false) {
    if (!view.session) return; const id = view.session.sessionId;
    return list('files', more, paging => api.listArtifacts(id, paging));
  }
  function listRevisions(more = false) {
    if (!view.artifact) return; const id = view.artifact.artifactId;
    return list('revisions', more, paging => api.listRevisions(id, paging));
  }
  async function load(params) {
    const ticket = ++epoch; controller?.abort(); controller = new AbortController(); const signal = controller.signal;
    stream.stop(); stream.state.model = null; view.error = ''; view.busy = true; view.receipt = null;
    view.artifact = null; view.revision = null; view.xml = ''; view.run = null;
    view.session = null;
    for (const name of ['runs', 'files', 'revisions']) { view[name] = []; view[name + 'Page'] = 0; view[name + 'Total'] = 0; view.listsBusy[name] = false; }
    try {
      if (params.version && (!/^[1-9]\d*$/.test(String(params.version)) || !Number.isSafeInteger(Number(params.version)))) throw new Error('版本号须为正整数');
      if (!params.session) {
        if (params.run || params.artifact || params.version) throw new Error('记录与会话不匹配');
        view.session = null; view.runs = []; view.files = []; await listSessions(); return;
      }
      let session = view.sessions.find(s => s.sessionId === params.session);
      if (!session) {
        let page = 1, count = 0, total = 1;
        while (!session && count < total) {
          const data = await api.listSessions({ page, pageSize: 100 }, signal); if (!valid(ticket)) return;
          session = data.items.find(s => s.sessionId === params.session); count += data.items.length; total = data.total; page++;
          if (!data.items.length) break;
        }
      }
      if (!session) throw new Error('记录不存在或当前账号无法访问');
      if (!valid(ticket)) return; view.session = session;
      if (!app.officeSessions.some(s => s.sessionId === session.sessionId)) app.officeSessions.push(session);
      await Promise.all([listRuns(), listFiles()]);
      if (!valid(ticket)) return;
      if (params.run) {
        const run = await api.getRun(params.run, signal); if (!valid(ticket)) return;
        if (run.sessionId !== params.session) throw new Error('记录与会话不匹配'); view.run = run;
      }
      if (params.artifact) {
        const artifact = await api.getArtifact(params.artifact, signal); if (!valid(ticket)) return;
        if (artifact.sessionId !== params.session || (view.run && !view.run.artifacts.some(a => a.artifactId === params.artifact))) throw new Error('记录与会话不匹配');
        let revision = artifact.revision;
        const target = Number(params.version || revision.version);
        if (target !== revision.version) {
          let page = 1, count = 0, total = 1; revision = null;
          while (!revision && count < total) {
            const data = await api.listRevisions(params.artifact, { page, pageSize: 100 }, signal); if (!valid(ticket)) return;
            revision = data.items.find(r => r.version === target); count += data.items.length; total = data.total; page++;
            if (!data.items.length) break;
          }
        }
        if (!revision) throw new Error('该版本不存在或不可访问');
        view.artifact = artifact; view.revision = revision;
        const xml = await api.readContent(revision, signal); if (!valid(ticket)) return;
        view.xml = xml;
      } else if (params.version) throw new Error('版本号缺少对应文件');
      if (!valid(ticket)) return;
      if (params.run) stream.start(params.run);
    } catch (e) { if (valid(ticket)) fail(e); }
    finally { if (valid(ticket)) view.busy = false; }
  }
  async function submit(body) {
    if (view.submitting || view.rateLimited) return; const ticket = epoch;
    if (!view.pending) { view.pending = { key: newKey(), body: JSON.parse(JSON.stringify(body)) }; persistPending(); }
    view.submitting = true; view.error = '';
    try {
      const envelope = view.pending, accepted = await api.createRun(envelope);
      if (!valid(ticket)) return;
      view.pending = null; persistPending();
      replaceRoute('office', { session: envelope.body.sessionId, run: accepted.runId });
    } catch (e) {
      if (!valid(ticket)) return;
      if (!unknownOutcome(e)) { view.pending = null; persistPending(); }
      if (e.status === 429) { view.rateLimited = true; clearTimeout(rateTimer); rateTimer = setTimeout(() => { view.rateLimited = false; }, Math.max(1000, e.retryAfter || 2000)); }
      fail(e);
    } finally { if (alive) view.submitting = false; }
  }
  async function cancel() {
    if (!view.run || view.cancelling) return; const ticket = epoch, id = view.run.runId; view.cancelling = true;
    try { const result = await api.cancelRun(id); if (valid(ticket)) { Object.assign(view.run, result); if (stream.state.model?.run.runId === id) Object.assign(stream.state.model.run, result); stream.refresh(); } }
    catch (e) { if (valid(ticket)) { fail(e); stream.refresh(); } }
    finally { if (alive) view.cancelling = false; }
  }
  async function receipt() {
    if (!view.run) return; const ticket = epoch;
    try { const result = await api.getRun(view.run.runId, undefined, true); if (valid(ticket)) view.receipt = result.generationReceipt; } catch (e) { if (valid(ticket)) fail(e); }
  }
  function suspend() { persistPending(); alive = false; epoch++; clearTimeout(rateTimer); controller?.abort(); stream.stop(); }
  onBeforeUnmount(suspend);
  return { view, stream, listSessions, createSession, listRuns, listFiles, listRevisions, load, submit, cancel, receipt, suspend, fail };
}
