// Offline compatibility checks against the current MyBatis files; no database is started.
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const backend = path.resolve(here, '../..');
const read = (file) => readFileSync(file, 'utf8');
const schema = read(path.join(here, '01-schema.sql'));
const tables = new Map();
for (const match of schema.matchAll(/CREATE TABLE IF NOT EXISTS (ai_\w+)\s*\(([\s\S]*?)\) ENGINE=/g)) {
    const [, name, body] = match;
    assert(!tables.has(name), `Duplicate table: ${name}`);
    const definitions = new Map([...body.matchAll(/^\s+(\w+)\s+((?:BIGINT|INT|TINYINT|VARCHAR|TEXT|LONGTEXT|DATE|DATETIME)\b[^\n]*)/gm)]
        .map((m) => [m[1], m[2]]));
    const columns = [...body.matchAll(/^\s+(\w+)\s+(?:BIGINT|INT|TINYINT|VARCHAR|TEXT|LONGTEXT|DATE|DATETIME)\b/gm)].map((m) => m[1]);
    assert.equal(new Set(columns).size, columns.length, `Duplicate column in ${name}`);
    const columnSet = new Set(columns);
    for (const key of body.matchAll(/(?:PRIMARY KEY|(?:UNIQUE )?KEY \w+)\s*\(([^)]+)\)/g)) {
        let maxBytes = 0;
        for (const column of key[1].split(',').map((s) => s.trim())) {
            assert(columnSet.has(column), `${name}: index references missing ${column}`);
            const definition = definitions.get(column);
            const varchar = /^VARCHAR\((\d+)\)/.exec(definition);
            const fixedBytes = { BIGINT: 8, INT: 4, TINYINT: 1, DATE: 3, DATETIME: 8 };
            const bytes = varchar ? Number(varchar[1]) * 4 : fixedBytes[/^\w+/.exec(definition)[0]];
            assert(bytes, `${name}: unsupported unprefixed index column ${column}`);
            maxBytes += bytes;
        }
        assert(maxBytes <= 3072, `${name}: index exceeds default InnoDB 3072-byte limit`);
    }
    assert(/PRIMARY KEY \(id\)/.test(body), `${name}: missing primary key`);
    assert(columnSet.has('create_time') && columnSet.has('update_time'), `${name}: missing timestamps`);
    tables.set(name, { columns: columnSet, definitions, body });
}
assert.equal(tables.size, 20, 'Expected 20 business tables');

const mapperDir = path.join(backend, 'zflow-infrastructure/src/main/resources/mapper');
const referencedTables = new Set();
let mappedColumns = 0;
let insertStatements = 0;
const attr = (text, name) => new RegExp(`\\b${name}="([^"]+)"`).exec(text)?.[1];
for (const file of readdirSync(mapperDir).filter((name) => name.endsWith('.xml'))) {
    const xml = read(path.join(mapperDir, file));
    for (const match of xml.matchAll(/\b(?:FROM|JOIN|INTO|UPDATE)\s+(ai_\w+)\b/gi)) {
        assert(tables.has(match[1]), `${file}: missing table ${match[1]}`);
        referencedTables.add(match[1]);
    }
    for (const map of xml.matchAll(/<resultMap\b([^>]*)>([\s\S]*?)<\/resultMap>/g)) {
        const po = attr(map[1], 'type')?.split('.').at(-1);
        const tableName = po?.replace(/[A-Z]/g, (c, index) => (index ? '_' : '') + c.toLowerCase());
        assert(tables.has(tableName), `${file}: missing table for ${po}`);
        const poSource = read(path.join(backend, `zflow-infrastructure/src/main/java/com/zgl/zflow/infrastructure/persistent/po/${po}.java`));
        const properties = new Map([...poSource.matchAll(/private\s+(\w+)\s+(\w+)\s*[;=]/g)].map((m) => [m[2], m[1]]));
        const sqlTypes = { Long: /^BIGINT\b/, Integer: /^(?:INT|TINYINT)\b/, String: /^(?:VARCHAR|TEXT|LONGTEXT)\b/, LocalDate: /^DATE\b/, LocalDateTime: /^DATETIME\b/ };
        for (const tag of map[2].matchAll(/<(?:id|result)\b([^>]+)\/?\s*>/g)) {
            const column = attr(tag[1], 'column');
            assert(tables.get(tableName).columns.has(column), `${file}: missing ${tableName}.${column}`);
            const property = attr(tag[1], 'property');
            const type = properties.get(property);
            assert(sqlTypes[type]?.test(tables.get(tableName).definitions.get(column)), `${file}: incompatible Java/SQL type for ${property}/${column}`);
            mappedColumns++;
        }
    }
    for (const insert of xml.matchAll(/INSERT\s+(?:IGNORE\s+)?INTO\s+(ai_\w+)\s*\(([^)]+)\)/gi)) {
        const insertedColumns = insert[2].split(',').map((s) => s.trim());
        for (const column of insertedColumns) {
            assert(tables.get(insert[1]).columns.has(column), `${file}: INSERT references missing ${column}`);
        }
        for (const [column, definition] of tables.get(insert[1]).definitions) {
            if (/\bNOT NULL\b/.test(definition) && !/\b(?:DEFAULT|AUTO_INCREMENT)\b/.test(definition)) {
                assert(insertedColumns.includes(column), `${file}: INSERT omits mandatory column ${column}`);
            }
        }
        insertStatements++;
    }
    // Validate qualified columns in JOIN queries, including the AiApiModel projection.
    for (const statement of xml.matchAll(/<(?:select|update|delete)\b[^>]*>([\s\S]*?)<\/(?:select|update|delete)>/g)) {
        const aliases = new Map();
        for (const alias of statement[1].matchAll(/\b(?:FROM|JOIN)\s+(ai_\w+)\s+([a-z][a-z0-9_]*)\b/g)) {
            aliases.set(alias[2], alias[1]);
        }
        for (const column of statement[1].matchAll(/\b([a-z][a-z0-9_]*)\.(\w+)\b/g)) {
            if (aliases.has(column[1])) {
                const table = aliases.get(column[1]);
                assert(tables.get(table).columns.has(column[2]), `${file}: missing ${table}.${column[2]}`);
            }
        }
    }
}
assert.deepEqual([...referencedTables].sort(), [...tables.keys()].sort(), 'Schema/Mapper table coverage mismatch');

const requireUnique = (table, columns) => {
    const keys = [...tables.get(table).body.matchAll(/UNIQUE KEY \w+\s*\(([^)]+)\)/g)]
        .map((m) => m[1].split(',').map((s) => s.trim()).join(','));
    assert(keys.includes(columns.join(',')), `${table}: missing unique key (${columns})`);
};
requireUnique('ai_user', ['user_name']);
requireUnique('ai_plaza_like', ['plaza_id', 'user_id']);
requireUnique('ai_plaza_favor', ['plaza_id', 'user_id']);
requireUnique('ai_stat', ['stat_date', 'stat_category', 'stat_key', 'stat_value']);
requireUnique('ai_config', ['client_id', 'config_type', 'config_value']);
requireUnique('ai_repo', ['user_id', 'agent_id', 'repo_type']);
requireUnique('ai_repo', ['user_id', 'template_id', 'repo_type']);
for (const [table, column] of [['ai_agent', 'template_id'], ['ai_repo', 'agent_id'], ['ai_repo', 'template_id']]) {
    const definition = tables.get(table).body.split('\n').find((line) => line.trim().startsWith(column + ' '));
    assert(/\bNULL\b/.test(definition) && !/NOT NULL/.test(definition), `${table}.${column} must allow null`);
}
assert(!/UNIQUE KEY/.test(tables.get('ai_message').body), 'Current MAX(seq)+1 writer cannot guarantee unique sequences');
const seed = read(path.join(here, '02-seed.sql'));
const workspace = read(path.join(backend, 'zflow-infrastructure/src/main/java/com/zgl/zflow/infrastructure/repository/WorkspaceRepository.java'));
const advisorId = /DEFAULT_ADVISOR_ID\s*=\s*"([^"]+)"/.exec(workspace)?.[1];
assert(advisorId && seed.includes(`'${advisorId}'`), 'Seed must supply the advisor referenced by WorkspaceRepository');
assert(seed.includes("'Memory'") && seed.includes('"maxMessages":20'), 'Invalid default advisor parameters');
const taskDto = read(path.join(backend, 'zflow-domain/src/main/java/com/zgl/zflow/domain/user/model/dto/SettingTaskDTO.java'));
const defaultTaskStatus = /private Integer taskStatus\s*=\s*(\d+)/.exec(taskDto)?.[1];
assert(defaultTaskStatus && new RegExp(`\\bDEFAULT ${defaultTaskStatus}\\b`).test(tables.get('ai_task').definitions.get('task_status')), 'Task default must match SettingTaskDTO');
assert(!/\b(?:DROP|TRUNCATE)\s+(?:TABLE|DATABASE)\b/i.test(schema + seed), 'Initialization must preserve existing data');
console.log(`PASS: ${tables.size} tables, ${mappedColumns} resultMap columns, ${insertStatements} INSERT statements, JOIN columns, required unique keys, nullable links, and default advisor seed.`);
console.log('PASS: Java/SQL field types, mandatory INSERT columns, InnoDB index sizes, and task default state.');
console.log('Static checks only; MySQL import and application integration have not been executed.');
