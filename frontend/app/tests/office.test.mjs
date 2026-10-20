import test from 'node:test';
import assert from 'node:assert/strict';
import { createOfficeApi, safeOfficeUrl, OfficeApiError, downloadName } from '../lib/office-api.js';
import { eventState, applySnapshot, reduceOfficeEvent } from '../lib/office-events.js';
import { persistDraft, draftsFor, saveEnvelope, acknowledgeSave } from '../lib/office-drafts.js';
import { editorMessage, editorUrl } from '../lib/drawio-protocol.js';
import { useArtifactDraft } from '../composables/useArtifactDraft.js';
const json = (status, data, code = 200) => new Response(JSON.stringify({ code, data, info: 'test' }), { status, headers: { 'Content-Type': 'application/json' } });
const apiWith = transport => createOfficeApi({ transport, origin: () => 'http://localhost:5173', root: '/miniagent/api/v1', auth: () => ({ token: 'test' }), unauthorized: () => {} });
const snapshot = { runId: 'r', status: 'SUCCEEDED', stage: 'COMPLETED', lastEventSeq: 4, artifacts: [{ artifactId: 'a', version: 1 }] };
const event = (seq, name, data = {}) => ({ id: String(seq), event: name, data: JSON.stringify({ schemaVersion: 1, runId: 'r', seq, ...data }) });
class MemoryStorage {
  data = new Map(); get length() { return this.data.size; } key(n) { return [...this.data.keys()][n]; }
  setItem(k, v) { this.data.set(k, String(v)); } getItem(k) { return this.data.get(k) ?? null; } removeItem(k) { this.data.delete(k); }
}

test('GET, POST, 201 and 202 preserve the contract and frozen request headers', async () => {
  const requests = [], api = apiWith(async (url, init) => { requests.push({ url, init }); return json(init.method === 'POST' ? 202 : 200, { runId: 'r' }); });
  await api.getRun('r'); const envelope = { key: 'original-key', body: { sessionId: 's', prompt: '图' } }; await api.createRun(envelope); await api.createRun(envelope);
  assert.equal(requests[0].init.method, 'GET'); assert.equal(requests[0].init.headers.Authorization, 'Bearer test');
  assert.equal(requests[1].init.body, requests[2].init.body); assert.equal(requests[1].init.headers['Idempotency-Key'], 'original-key');
  assert.equal(requests[0].init.redirect, 'error');
  assert.deepEqual(await apiWith(async () => json(201, { version: 2 })).saveRevision({ artifactId: 'a', baseVersion: 1, key: 'save-key', xml: '<mxfile/>' }), { version: 2 });
});
test('409 and 422 keep conflict/version and node report details', async () => {
  for (const [status, data] of [[409, { errorKey: 'ARTIFACT_VERSION_CONFLICT', currentVersion: 3 }], [422, { errorKey: 'INVALID_DIAGRAM_XML', validationReport: { issues: [{ cellId: 'node1' }] } }]]) {
    await assert.rejects(apiWith(async () => json(status, data, status)).getArtifact('a'), e => e instanceof OfficeApiError && e.status === status && JSON.stringify(e.data) === JSON.stringify(data));
  }
});
test('authentication JSON and gateway HTML cannot become downloaded XML', async () => {
  for (const response of [json(401, {}, 200), json(200, {}, 401), new Response('<html>proxy</html>', { status: 502 }), new Response('<html/>', { headers: { 'Content-Type': 'text/html' } })]) {
    await assert.rejects(apiWith(async () => response).downloadRevision({ downloadUrl: '/miniagent/api/v1/office/artifacts/a/download?version=1' }));
  }
});
test('resource URLs cannot exfiltrate bearer tokens or duplicate the API root', () => {
  const root = 'https://api.test/miniagent/api/v1';
  assert.equal(safeOfficeUrl('/miniagent/api/v1/office/artifacts/a/content?version=1', root, 'http://local').href, root + '/office/artifacts/a/content?version=1');
  for (const url of ['https://evil.test/miniagent/api/v1/office/a', '//evil.test/a', '/miniagent/api/v1/office/../../user', 'https://user@api.test/miniagent/api/v1/office/a']) assert.throws(() => safeOfficeUrl(url, root, 'http://local'));
  assert.equal(downloadName("attachment; filename*=UTF-8''%E5%9B%BE.drawio", 'fallback'), '图.drawio');
});
test('replay fills the timeline without regressing a terminal snapshot', () => {
  const state = eventState(snapshot);
  assert.equal(reduceOfficeEvent(state, event(1, 'run.started', { status: 'RUNNING' })), 'applied');
  assert.equal(state.run.status, 'SUCCEEDED'); assert.equal(state.confirmedSeq, 1);
  assert.equal(reduceOfficeEvent(state, event(1, 'run.started', { status: 'RUNNING' })), 'duplicate');
  assert.equal(reduceOfficeEvent(state, event(3, 'step.started', { stage: 'SAVING' })), 'gap'); assert.equal(state.confirmedSeq, 1);
  applySnapshot(state, { ...snapshot, lastEventSeq: 1, status: 'RUNNING' }); assert.equal(state.run.status, 'SUCCEEDED');
});
test('bad schemas, event names and cursor mismatches never acknowledge progress', () => {
  const state = eventState(snapshot);
  for (const e of [event(1, 'future.event'), { ...event(1, 'run.started'), id: '2' }, { ...event(1, 'run.started'), data: '{bad' }, { ...event(1, 'run.started'), data: JSON.stringify({ schemaVersion: 2, runId: 'r', seq: 1 }) }]) assert.equal(reduceOfficeEvent(state, e), 'protocol');
  assert.equal(state.confirmedSeq, 0);
});
test('editing during save advances only the acknowledged base, preserving newer changes', () => {
  const draft = { artifactId: 'a', baseVersion: 1, xml: 'old', editEpoch: 2, dirty: true };
  const envelope = saveEnvelope(draft, 'submitted', 2); draft.editEpoch = 3; draft.xml = 'newer';
  acknowledgeSave(draft, envelope, { version: 2 }); assert.equal(draft.baseVersion, 2); assert.equal(draft.xml, 'newer'); assert.equal(draft.dirty, true);
  assert.equal(envelope.baseVersion, 1); assert.equal(envelope.xml, 'submitted');
});
test('draft persistence isolates owners and does not evict unresolved copies at capacity', () => {
  const storage = new MemoryStorage();
  for (const artifactId of ['a', 'b', 'c']) persistDraft(storage, 'owner', { artifactId, baseVersion: 1, xml: '<mxfile/>', pending: { key: 'pending' } });
  assert.throws(() => persistDraft(storage, 'owner', { artifactId: 'd', baseVersion: 1, xml: '<mxfile/>' }), /容量/);
  assert.equal(draftsFor(storage, 'owner').length, 3); assert.equal(draftsFor(storage, 'another').length, 0);
  persistDraft(storage, 'owner', { artifactId: 'a', baseVersion: 2, xml: 'updated' }); assert.equal(draftsFor(storage, 'owner').length, 3);
});
test('unknown save outcomes replay the identical envelope and acknowledge once', async () => {
  globalThis.sessionStorage = new MemoryStorage(); const requests = []; let failures = 1;
  const api = { saveRevision: async envelope => { requests.push({ ...envelope }); if (failures--) throw new OfficeApiError('lost'); return { version: 2 }; } };
  const instance = useArtifactDraft(api, 'owner', async () => {});
  instance.begin('a', 1, '<mxfile/>'); instance.changed('<mxfile>edited</mxfile>');
  assert.equal(await instance.save(async () => '<mxfile>edited</mxfile>'), false); assert.equal(instance.draft.saveState, 'outcomeUnknown');
  assert.equal(await instance.save(async () => { throw new Error('must not export again'); }), true);
  assert.deepEqual(requests[0], requests[1]); assert.equal(instance.draft.dirty, false); assert.equal(instance.draft.baseVersion, 2);
});
test('iframe origin, source and configured host are all validated', () => {
  const frame = {}, data = JSON.stringify({ event: 'save', xml: 'test' });
  assert.equal(editorMessage({ data, origin: 'https://evil.test', source: frame }, 'https://embed.diagrams.net', frame), null);
  assert.equal(editorMessage({ data, origin: 'https://embed.diagrams.net', source: {} }, 'https://embed.diagrams.net', frame), null);
  assert.equal(editorMessage({ data, origin: 'https://embed.diagrams.net', source: frame }, 'https://embed.diagrams.net', frame).event, 'save');
  assert.throws(() => editorUrl('https://evil.test/', 'http://localhost')); assert.equal(editorUrl(null, 'http://localhost').origin, 'https://embed.diagrams.net');
});
