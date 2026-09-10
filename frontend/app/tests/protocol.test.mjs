import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { parseJSON, stringifyJSON, createSSEParser, checkResponse, consumeSSE } from '../lib/protocol.js';

test('Java Long IDs survive parse, update serialization and roundtrip without rounding', () => {
  const text = '{"flowId":9223372036854775807,"userId":-9223372036854775808,"messageSeq":7,"content":"9223372036854775807","fraction":0.6}';
  const result = parseJSON(text);
  assert.equal(result.flowId, 9223372036854775807n);
  assert.equal(result.userId, -9223372036854775808n);
  assert.equal(result.content, '9223372036854775807');
  assert.equal(result.messageSeq, 7);
  assert.deepEqual(parseJSON(stringifyJSON(result)), result);
  assert.ok(stringifyJSON(result).includes('"flowId":9223372036854775807'));
});

test('strings containing escaped JSON and numeric literals remain untouched', () => {
  const value = { message: 'data:  123\n{"flowId":9223372036854775807}', quoted: 'x\\"y', empty: '', scientific: 1.5e4 };
  assert.deepEqual(parseJSON(JSON.stringify(value)), value);
});

test('SSE preserves whitespace, multiline data, names and IDs at every split boundary', () => {
  const fixture = ': keepalive\r\nid:1789\r\nevent:message\r\ndata:  你好 \r\ndata: next\r\n\r\nevent:error\ndata:模型不可用\n\nevent:complete\rdata:{}\r\r';
  for (let split = 0; split <= fixture.length; split++) {
    const events = [], parser = createSSEParser(event => events.push(event));
    parser.feed(fixture.slice(0, split)); parser.feed(fixture.slice(split)); parser.feed('', true);
    assert.deepEqual(events, [
      { event: 'message', id: '1789', data: ' 你好 \nnext' },
      { event: 'error', id: '1789', data: '模型不可用' },
      { event: 'complete', id: '1789', data: '{}' }
    ], `split at ${split}`);
  }
});

test('incomplete final SSE event is not falsely delivered as completion', () => {
  const events = [], parser = createSSEParser(event => events.push(event));
  parser.feed('data:hello\n\nevent:complete\ndata:{}'); parser.feed('', true);
  assert.deepEqual(events, [{ event: 'message', id: '', data: 'hello' }]);
});

test('empty data and one protocol space are different from trimmed content', () => {
  const events = [], parser = createSSEParser(e => events.push(e));
  parser.feed('data:\n\ndata:   \n\ndata:\t\n\n', true);
  assert.deepEqual(events.map(e => e.data), ['', '  ', '\t']);
});

test('UTF-8 decoder handles one-byte network chunks, CRLF and Chinese characters', async () => {
  const bytes = new TextEncoder().encode('data: 你好，世界 🌿\r\n\r\ndata: second\n\n');
  const body = new ReadableStream({ start(controller) { for (const byte of bytes) controller.enqueue(Uint8Array.of(byte)); controller.close(); } });
  const events = [];
  await consumeSSE(new Response(body, { headers: { 'content-type': 'text/event-stream; charset=utf-8' } }), e => events.push(e));
  assert.deepEqual(events.map(e => e.data), ['你好，世界 🌿', 'second']);
});

test('HTTP authentication status wins over misleading JSON business code', async () => {
  await assert.rejects(checkResponse(new Response('{"code":500,"info":"错误"}', { status: 401 })), e => e.status === 401);
  await assert.rejects(checkResponse(new Response('{"code":500}', { status: 403 })), e => e.status === 403);
});

test('HTTP 200 business failure, non-JSON proxy errors and unexpected stream content fail visibly', async () => {
  await assert.rejects(checkResponse(new Response('{"code":500,"info":"模型配置不存在"}', { headers: { 'content-type': 'application/json' } })), /模型配置不存在/);
  await assert.rejects(checkResponse(new Response('<html>Bad Gateway</html>', { status: 502 })), /502/);
  await assert.rejects(consumeSSE(new Response('{"code":500,"info":"参数不正确"}', { headers: { 'content-type': 'application/json' } }), () => assert.fail()), /参数不正确/);
  await assert.rejects(consumeSSE(new Response('plain error', { headers: { 'content-type': 'text/plain' } }), () => assert.fail()), /SSE/);
});

test('success unpacks list/page and successful Void does not become an error', async () => {
  assert.equal(await checkResponse(new Response('{"code":200,"data":null}')), null);
  const page = { list: [{ sessionId: 's1' }], pageNum: 1, pageSum: 2, pageSize: 10, total: 12 };
  assert.deepEqual(await checkResponse(new Response(JSON.stringify({ code: 200, data: page }))), page);
});

test('consumer callback errors cancel the stream instead of marking it done', async () => {
  let cancelled = false;
  const body = new ReadableStream({ start(c) { c.enqueue(new TextEncoder().encode('event:message\ndata:not-json\n\n')); }, cancel() { cancelled = true; } });
  await assert.rejects(consumeSSE(new Response(body, { headers: { 'content-type': 'text/event-stream' } }), e => JSON.parse(e.data)), SyntaxError);
  assert.equal(cancelled, true);
});

test('contract inventory and user/admin field spellings match the supplied handoff', () => {
  const contract = JSON.parse(readFileSync(new URL('../../docs/STUDIO-API-CONTRACT.json', import.meta.url), 'utf8'));
  assert.equal(contract.endpoints.length, 125);
  assert.equal(Object.keys(contract.schemas).length, 90);
  assert.equal(Object.values(contract.schemas).flat().length, 506);
  assert.ok(contract.schemas['admin.PromptManageDTO'].some(f => f.key === 'systenPrompt'));
  assert.ok(contract.schemas['user.SettingApiModelDTO'].some(f => f.key === 'apiCompletionPath'));
  assert.ok(contract.schemas['admin.ApiManageDTO'].some(f => f.key === 'apiCompletionsPath'));
  const admin = JSON.parse(readFileSync(new URL('../lib/admin-schema.json', import.meta.url), 'utf8'));
  assert.equal(Object.keys(admin).length, 13);
  assert.ok(admin.flow.some(f => f.key === 'originClientId'));
});
