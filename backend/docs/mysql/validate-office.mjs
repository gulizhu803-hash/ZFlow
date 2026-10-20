import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const backend = path.resolve(here, '../..');
const sql = readFileSync(path.join(here, '03-office-artifacts.sql'), 'utf8');
const baseline = readFileSync(path.join(here, '01-schema.sql'), 'utf8');
const mapper = readFileSync(path.join(backend, 'zflow-infrastructure/src/main/resources/mapper/office/OfficeDao.xml'), 'utf8');
const tables = new Map();
for (const match of (baseline + sql).matchAll(/CREATE TABLE (?:IF NOT EXISTS )?(ai_\w+)\s*\(([\s\S]*?)\) ENGINE=/g)) {
  const [, name, body] = match;
  tables.set(name, new Set([...body.matchAll(/^\s+(\w+)\s+(?:BIGINT|INT|VARCHAR|CHAR|JSON|DATETIME)\b/gm)].map(m => m[1])));
}
assert.equal((sql.match(/CREATE TABLE /g) || []).length, 4);
assert(!sql.includes('IF NOT EXISTS'), 'Migration must detect reapplication');
for (const key of ['owner_id, idempotency_key', 'artifact_id, version', 'artifact_id, save_request_key', 'run_id, output_key', 'run_id, seq']) {
  assert(sql.includes('(' + key + ')'), 'Missing uniqueness: ' + key);
}
const mappedTables = { Run: 'ai_office_run', Artifact: 'ai_artifact', Revision: 'ai_artifact_revision', Event: 'ai_office_run_event', Session: 'ai_session' };
let columns = 0;
for (const match of mapper.matchAll(/<resultMap id="(\w+)"[^>]*>([\s\S]*?)<\/resultMap>/g)) {
  const [, name, body] = match;
  const entity = readFileSync(path.join(backend, 'zflow-domain/src/main/java/com/zgl/zflow/domain/office/model/Office' + name + '.java'), 'utf8');
  for (const field of body.matchAll(/column="(\w+)" property="(\w+)"/g)) {
    assert(tables.get(mappedTables[name]).has(field[1]), 'Missing column: ' + field[1]);
    assert(new RegExp('private \\w+ ' + field[2] + ';').test(entity), 'Missing entity property: ' + field[2]);
    columns++;
  }
}
const contract = readFileSync(path.join(backend, 'zflow-domain/src/main/java/com/zgl/zflow/domain/office/repository/IOfficeRepository.java'), 'utf8');
for (const method of contract.matchAll(/^\s+(?:[\w<>]+)\s+(\w+)\([^;]*\);/gm)) {
  assert(mapper.includes('id="' + method[1] + '"'), 'Unmapped repository method: ' + method[1]);
}
assert(mapper.includes('UTC_TIMESTAMP(3)'));
assert(mapper.includes('FOR UPDATE'));
console.log('PASS: 4 office tables, ' + columns + ' mapped columns, repository statements, JSON fields and unique constraints.');
console.log('Migration SHA-256: ' + createHash('sha256').update(sql).digest('hex'));
