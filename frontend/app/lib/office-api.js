import { API_ROOT, readAuth } from './api.js';
import { parseJSON, stringifyJSON, createSSEParser } from './protocol.js';

export class OfficeApiError extends Error {
  constructor(message, status = 0, data = {}, retryAfter = 0) {
    super(message); this.name = 'OfficeApiError'; this.status = status;
    this.data = data || {}; this.errorKey = this.data.errorKey; this.traceId = this.data.traceId;
    this.retryAfter = retryAfter;
  }
}
export function retryDelay(value) {
  if (!value) return 0;
  const seconds = Number(value);
  return Number.isFinite(seconds) ? Math.max(0, seconds * 1000) : Math.max(0, Date.parse(value) - Date.now()) || 0;
}
export function safeOfficeUrl(path, root, origin) {
  const base = new URL(root, origin);
  const url = path.startsWith('/office/') ? new URL(base.href.replace(/\/$/, '') + path) : new URL(path, base.origin);
  if (url.origin !== base.origin || !url.pathname.startsWith(base.pathname.replace(/\/$/, '') + '/office/') || url.username || url.password)
    throw new OfficeApiError('服务返回了不允许访问的文件地址');
  return url;
}
export function safeFilename(name) { return String(name || '图表').replace(/[\x00-\x1f\x7f\\/:*?"<>|]/g, '_').slice(0, 180); }
export function downloadName(header, fallback) {
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(header || '');
  if (utf8) { try { return safeFilename(decodeURIComponent(utf8[1])); } catch { /* Use the versioned fallback. */ } }
  return safeFilename(fallback);
}
export function downloadBlob(blob, name) {
  const url = URL.createObjectURL(blob), link = document.createElement('a');
  link.href = url; link.download = safeFilename(name); document.body.append(link); link.click(); link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export function createOfficeApi({ root = API_ROOT, origin = () => window.location.origin, auth = readAuth,
  transport = (...args) => fetch(...args), unauthorized = () => window.dispatchEvent(new Event('miniagent:unauthorized')) } = {}) {
  async function request(path, { method = 'GET', body, key, query, signal, kind = 'json' } = {}) {
    const url = safeOfficeUrl(path, root, origin());
    for (const [k, v] of Object.entries(query || {})) if (v != null) url.searchParams.set(k, String(v));
    const token = auth()?.token;
    const headers = { Accept: kind === 'sse' ? 'text/event-stream' : kind === 'json' ? 'application/json' : 'application/xml' };
    if (token) headers.Authorization = `Bearer ${token}`;
    if (key) headers['Idempotency-Key'] = key;
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    let response;
    try { response = await transport(url, { method, headers, body: body === undefined ? undefined : stringifyJSON(body), signal, redirect: 'error', cache: 'no-store' }); }
    catch (e) { if (e.name === 'AbortError') throw e; throw new OfficeApiError('无法连接服务，请检查网络后确认原请求结果'); }
    const type = response.headers.get('Content-Type') || '';
    if (!response.ok || type.includes('json') || kind === 'json') {
      let result;
      try { result = parseJSON(await response.text()); } catch { /* Never show gateway HTML. */ }
      const status = [401, 403].includes(response.status) ? response.status : [401, 403].includes(result?.code) ? result.code : response.status;
      if (status === 401 && token === auth()?.token) unauthorized();
      if (!response.ok || result?.code !== 200 || kind !== 'json') {
        const message = status === 401 ? '登录已失效，请重新登录' : status === 403 ? '当前账号没有访问权限' : status === 404 ? '记录不存在或当前账号无法访问' : result?.info || '服务响应异常，请稍后重试';
        throw new OfficeApiError(message, status, result?.data, retryDelay(response.headers.get('Retry-After')));
      }
      return result.data;
    }
    if (kind === 'sse') {
      if (!type.includes('text/event-stream') || !response.body) throw new OfficeApiError('服务未返回有效的事件流');
      return response;
    }
    if (!/^(application|text)\/xml(?:;|$)/i.test(type)) throw new OfficeApiError('服务未返回图表文件，请重新读取');
    if (kind === 'xml') return response.text();
    return { blob: await response.blob(), disposition: response.headers.get('Content-Disposition') };
  }
  const id = encodeURIComponent;
  return {
    listSessions: (query, signal) => request('/office/sessions', { query, signal }),
    createSession: title => request('/office/sessions', { method: 'POST', body: { title } }),
    listRuns: (sessionId, query, signal) => request('/office/runs', { query: { sessionId, ...query }, signal }),
    createRun: envelope => request('/office/runs', { method: 'POST', body: envelope.body, key: envelope.key }),
    getRun: (runId, signal, includeReceipt = false) => request(`/office/runs/${id(runId)}`, { signal, query: { includeReceipt } }),
    cancelRun: runId => request(`/office/runs/${id(runId)}/cancel`, { method: 'POST' }),
    listArtifacts: (sessionId, query, signal) => request('/office/artifacts', { query: { sessionId, ...query }, signal }),
    getArtifact: (artifactId, signal) => request(`/office/artifacts/${id(artifactId)}`, { signal }),
    listRevisions: (artifactId, query, signal) => request(`/office/artifacts/${id(artifactId)}/revisions`, { query, signal }),
    readContent: (revision, signal) => request(revision.contentUrl, { signal, kind: 'xml' }),
    saveRevision: envelope => request(`/office/artifacts/${id(envelope.artifactId)}/revisions`, { method: 'POST', key: envelope.key, body: { baseVersion: envelope.baseVersion, xml: envelope.xml } }),
    downloadRevision: (revision, signal) => request(revision.downloadUrl, { signal, kind: 'blob' }),
    async subscribeRun(runId, afterSeq, onEvent, signal) {
      const response = await request(`/office/runs/${id(runId)}/events`, { query: { afterSeq }, signal, kind: 'sse' });
      const reader = response.body.getReader(), decoder = new TextDecoder(), parser = createSSEParser(onEvent);
      try {
        while (true) { const { value, done } = await reader.read(); if (done) break; parser.feed(decoder.decode(value, { stream: true })); }
        parser.feed(decoder.decode(), true);
      } finally { await reader.cancel().catch(() => {}); reader.releaseLock(); }
    }
  };
}
export const officeApi = createOfficeApi();
