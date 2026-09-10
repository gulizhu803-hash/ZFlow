import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import { checkResponse, consumeSSE, stringifyJSON } from '../app/lib/protocol.js';

// Credentials stay in memory. All requests go through the same proxy as the UI.
const root = (process.env.LIVE_API_ROOT || 'http://127.0.0.1:5180/miniagent/api/v1').replace(/\/$/, '');
const userName = process.env.LIVE_USER, password = process.env.LIVE_PASSWORD;
assert.ok(userName && password, 'Set LIVE_USER and LIVE_PASSWORD before running test:live');
const checks = [], sessions = [];
let token;
async function request(path, { json, query, stream = false, anonymous = false } = {}) {
  const url = new URL(root + path);
  for (const [key, value] of Object.entries(query || {})) url.searchParams.set(key, String(value));
  return fetch(url, {
    method: 'POST',
    headers: { Accept: stream ? 'text/event-stream' : 'application/json', ...(!anonymous && token ? { Authorization: 'Bearer ' + token } : {}), ...(json === undefined ? {} : { 'Content-Type': 'application/json' }) },
    body: json === undefined ? undefined : stringifyJSON(json), signal: AbortSignal.timeout(240000)
  });
}
async function post(path, options) { return checkResponse(await request(path, options)); }
function pass(name) { checks.push(name); console.log('PASS ' + name); }
async function session(type) {
  const item = await post('/session/insert', { query: { sessionTitle: '自动联调-' + Date.now(), sessionType: type } });
  sessions.push(item); return item.sessionId;
}
async function chat(clientId, prompt, expected, ragTag) {
  const sessionId = await session('chat');
  let answer = '', count = 0;
  await consumeSSE(await request('/ai/chat/stream', { stream: true, json: { clientId, sessionId, userMessage: prompt, maxCompletionTokens: 512, ...(ragTag ? { ragTag } : {}) } }), event => { answer += event.data; count++; });
  assert.ok(count > 0 && answer.includes(expected), 'Expected text missing from model response');
  const history = await post('/session/message/chat', { query: { sessionId } });
  assert.ok(history.some(m => m.messageRole === 'assistant' && m.messageContent.includes(expected)), 'Assistant response was not persisted');
  await post('/session/delete', { query: { sessionId } });
  sessions.splice(sessions.findIndex(s => s.sessionId === sessionId), 1);
}
let failure;
try {
  const health = await fetch(root + '/actuator/health');
  assert.equal(health.status, 200); assert.equal((await health.json()).status, 'UP'); pass('proxy.health');
  assert.equal((await request('/user/profile/query', { anonymous: true })).status, 401); pass('auth.401');
  const auth = await post('/user/auth/login', { json: { userName, password }, anonymous: true }); token = auth.token; pass('auth.login');
  if (auth.userRole !== 'admin') { assert.equal((await request('/admin/dashboard')).status, 403); pass('auth.403'); }
  assert.equal((await post('/user/profile/query')).userName, userName); pass('profile');
  await assert.rejects(() => post('/user/model/insert', { json: {} })); pass('validation.business-error');
  await assert.rejects(() => post('/session/insert', { query: { sessionTitle: 'invalid-type-probe', sessionType: 'invalid' } })); pass('validation.session-type');
  const clients = await post('/user/query/chat-client');
  const client = clients.find(c => c.clientId === process.env.LIVE_CLIENT_ID) || clients[0];
  assert.ok(client, 'No available chat client');
  await post('/ai/armory', { json: { armoryType: 'chat', armoryId: client.clientId } }); pass('chat.armory');
  await chat(client.clientId, '连通性测试，请只回复：ZFlow联调成功。', '联调成功'); pass('chat.SSE-history-cleanup');
  if (process.env.LIVE_RAG_TAG) {
    assert.ok((await post('/user/query/rag')).some(r => r.ragTag === process.env.LIVE_RAG_TAG), 'Configured LIVE_RAG_TAG was not found');
    assert.ok(process.env.LIVE_RAG_QUESTION && process.env.LIVE_RAG_EXPECTED, 'Set LIVE_RAG_QUESTION and LIVE_RAG_EXPECTED');
    await chat(client.clientId, process.env.LIVE_RAG_QUESTION, process.env.LIVE_RAG_EXPECTED, process.env.LIVE_RAG_TAG); pass('rag.retrieval-SSE-history');
  }
  if (process.env.LIVE_AGENT_ID) {
    const agent = await post('/workspace/agent/detail', { query: { agentId: process.env.LIVE_AGENT_ID } });
    assert.ok(agent.clientInfoList.length); pass('agent.detail');
    if (process.env.LIVE_WORK_PROMPT) {
      assert.ok(process.env.LIVE_WORK_EXPECTED, 'Set LIVE_WORK_EXPECTED');
      await post('/ai/armory', { json: { armoryType: 'work', armoryId: agent.agentId } });
      const sessionId = await session('work'); const events = [];
      await consumeSSE(await request('/ai/work/execute', { stream: true, json: { agentId: agent.agentId, agentDesc: agent.agentDesc, sessionId, userMessage: process.env.LIVE_WORK_PROMPT, maxRound: 1, maxRetry: 1, maxPace: 2 } }), event => events.push(event));
      assert.ok(events.some(e => e.event === 'complete') && !events.some(e => e.event === 'error'));
      const history = await post('/session/message/work-answer', { query: { sessionId } });
      assert.ok(history.some(m => m.messageRole === 'assistant' && m.messageContent.includes(process.env.LIVE_WORK_EXPECTED)));
      assert.ok((await post('/session/message/work-sse', { query: { sessionId } })).length > 0); pass('work.SSE-complete-and-history');
    }
  }
} catch (error) {
  // Never print request bodies, AuthVOs or raw provider errors containing secrets.
  failure = error.name; console.error('FAIL ' + (error.code === 'ERR_ASSERTION' ? error.message : error.name) + '; last passed check: ' + (checks.at(-1) || 'none')); process.exitCode = 1;
} finally {
  for (const item of sessions) {
    try { await post('/session/delete', { query: { sessionId: item.sessionId } }); }
    catch { failure ||= 'CleanupError'; process.exitCode = 1; console.error('Cleanup failed for test session ' + item.sessionId); }
  }
  const report = { time: new Date().toISOString(), root, passed: checks, success: !failure, ...(failure ? { failure } : {}) };
  if (process.env.LIVE_REPORT) await fs.writeFile(process.env.LIVE_REPORT, JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report));
}
