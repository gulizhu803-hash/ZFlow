export class ApiError extends Error {
  constructor(message, status = 0) { super(message); this.name = 'ApiError'; this.status = status; }
}
// Protect Java Long precision before JSON.parse sees the number token.
export function parseJSON(text) {
  const longs = [], nonce = `__long_${Math.random().toString(36).slice(2)}_`;
  const safe = text.replace(/"(?:[^"\\]|\\.)*"|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/g, token => {
    if (token[0] === '"' || /[.eE]/.test(token) || Number.isSafeInteger(Number(token))) return token;
    longs.push(BigInt(token)); return JSON.stringify(nonce + (longs.length - 1));
  });
  return JSON.parse(safe, (_, v) => typeof v === 'string' && v.startsWith(nonce) ? longs[Number(v.slice(nonce.length))] : v);
}
export function stringifyJSON(value) {
  if (typeof value === 'bigint') return value.toString();
  if (Array.isArray(value)) return '[' + value.map(v => stringifyJSON(v) ?? 'null').join(',') + ']';
  if (value && typeof value === 'object') return '{' + Object.entries(value).filter(([, v]) => v !== undefined).map(([k, v]) => JSON.stringify(k) + ':' + stringifyJSON(v)).join(',') + '}';
  return JSON.stringify(value);
}
export function createSSEParser(onEvent) {
  let buffer = '', data = [], event = '', id = '';
  function line(value) {
    if (!value) { if (data.length) onEvent({ event: event || 'message', id, data: data.join('\n') }); data = []; event = ''; return; }
    if (value.startsWith(':')) return;
    const colon = value.indexOf(':'), key = colon < 0 ? value : value.slice(0, colon);
    let content = colon < 0 ? '' : value.slice(colon + 1);
    if (content.startsWith(' ')) content = content.slice(1);
    if (key === 'data') data.push(content);
    if (key === 'event') event = content;
    if (key === 'id' && !content.includes('\0')) id = content;
  }
  return { feed(chunk, final = false) {
    buffer += chunk; let start = 0;
    for (let i = 0; i < buffer.length; i++) {
      if (buffer[i] !== '\r' && buffer[i] !== '\n') continue;
      if (buffer[i] === '\r' && i === buffer.length - 1 && !final) break;
      line(buffer.slice(start, i));
      if (buffer[i] === '\r' && buffer[i + 1] === '\n') i++;
      start = i + 1;
    }
    buffer = buffer.slice(start);
    // SSE requires a blank line; discard incomplete final events.
    if (final) { buffer = ''; data = []; event = ''; }
  } };
}
export async function checkResponse(response, stream = false) {
  if (response.status === 401) throw new ApiError('登录已失效，请重新登录', 401);
  if (response.status === 403) throw new ApiError('当前账号没有访问权限', 403);
  const isJSON = (response.headers.get('content-type') || '').includes('json');
  if (!response.ok || isJSON || !stream) {
    const text = await response.text(); let result;
    try { result = parseJSON(text); } catch { /* A proxy may return HTML. */ }
    if (!response.ok) throw new ApiError(result?.info || `服务暂时不可用（HTTP ${response.status}）`, response.status);
    if (!result || result.code !== 200) throw new ApiError(result?.info || '服务返回了无法识别的响应');
    if (stream) throw new ApiError('服务未返回流式响应，请检查模型配置');
    return result.data;
  }
  if (!(response.headers.get('content-type') || '').includes('text/event-stream')) throw new ApiError('服务未返回 SSE 数据，请检查服务与代理配置');
  if (!response.body) throw new ApiError('浏览器无法读取流式响应');
}
export async function consumeSSE(response, onEvent) {
  await checkResponse(response, true);
  const reader = response.body.getReader(), decoder = new TextDecoder(), parser = createSSEParser(onEvent);
  try {
    while (true) { const { done, value } = await reader.read(); if (done) break; parser.feed(decoder.decode(value, { stream: true })); }
    parser.feed(decoder.decode(), true);
  } catch (error) { await reader.cancel().catch(() => {}); throw error; }
  finally { reader.releaseLock(); }
}
