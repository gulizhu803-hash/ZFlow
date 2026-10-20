import { ApiError, checkResponse, consumeSSE, stringifyJSON } from './protocol.js';
const configured = import.meta.env?.VITE_API_ROOT || import.meta.env?.VITE_API_BASE || '/miniagent';
export const API_ROOT = configured.replace(/\/$/, '').replace(/\/api\/v1$/, '') + '/api/v1';
const AUTH_KEY = 'miniagent.studio.auth';
export function readAuth() { try { return JSON.parse(sessionStorage.getItem(AUTH_KEY)) || null; } catch { return null; } }
export function saveAuth(auth) { if (auth) sessionStorage.setItem(AUTH_KEY, JSON.stringify(auth)); else sessionStorage.removeItem(AUTH_KEY); }
async function request(path, { json, query, form, signal, stream = false } = {}) {
  if (json !== undefined && form) throw new Error('JSON 与 FormData 不能同时提交');
  const url = new URL(API_ROOT + path, window.location.origin);
  for (const [key, value] of Object.entries(query || {})) if (value != null) url.searchParams.set(key, String(value));
  const headers = { Accept: stream ? 'text/event-stream' : 'application/json' }, token = readAuth()?.token;
  if (token) headers.Authorization = `Bearer ${token}`;
  if (json !== undefined) headers['Content-Type'] = 'application/json';
  try { return await fetch(url, { method: 'POST', headers, body: form || (json !== undefined ? stringifyJSON(json) : undefined), signal }); }
  catch (error) { if (error.name === 'AbortError') throw error; throw new ApiError('无法连接服务，请确认后端已启动且地址配置正确'); }
}
async function guard(operation) {
  const token = readAuth()?.token;
  try { return await operation(); }
  catch (error) { if (error.status === 401 && token === readAuth()?.token) { saveAuth(null); window.dispatchEvent(new Event('miniagent:unauthorized')); } throw error; }
}
export function post(path, options) { return guard(async () => checkResponse(await request(path, options))); }
export function stream(path, json, onEvent, signal) { return guard(async () => consumeSSE(await request(path, { json, stream: true, signal }), onEvent)); }
