import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
const root = new URL('../', import.meta.url);
const schemaDoc = readFileSync(new URL('docs/API-SCHEMAS.md', root), 'utf8');
const endpointsDoc = readFileSync(new URL('docs/API-ENDPOINTS.md', root), 'utf8');
const schemas = {};
for (const block of schemaDoc.split(/(?=<a id=)/)) {
  const name = block.match(/### ([\w.]+)/)?.[1];
  if (!name) continue;
  schemas[name] = [...block.matchAll(/^\| `([^`]+)` \| ([^|]+) \| ([^|]+) \| ([^|]+) \|/gm)].map(([, key, type, rule, label]) => ({ key, type: type.trim(), required: /NotNull|NotBlank|NotEmpty/.test(rule), min: rule.match(/Min\((\d+)\)/)?.[1], max: rule.match(/Max\((\d+)\)/)?.[1], label: label.trim() }));
}
const endpoints = [...endpointsDoc.matchAll(/^\| \[`([^`]+)`\].+? \| ([^|]+) \| ([^|]+) \| ([^|]+) \|/gm)].map(([, path, params, response, description]) => ({ path, params: params.trim(), response: response.trim(), description: description.trim() }));
if (endpoints.length !== 125 || Object.keys(schemas).length !== 90) throw new Error(`Contract inventory changed: ${endpoints.length} endpoints, ${Object.keys(schemas).length} schemas. Review the documentation.`);
const admin = Object.fromEntries(Object.entries(schemas).filter(([key]) => key.startsWith('admin.') && key.endsWith('ManageDTO')).map(([key, value]) => [key.slice(6, -9).toLowerCase(), value]));
writeFileSync(new URL('app/lib/admin-schema.json', root), JSON.stringify(admin, null, 2) + '\n');
writeFileSync(new URL('docs/STUDIO-API-CONTRACT.json', root), JSON.stringify({ endpoints, schemas }, null, 2) + '\n');
console.log(`Generated ${endpoints.length} documented routes, ${Object.keys(schemas).length} schemas, ${Object.values(schemas).flat().length} fields; ${Object.keys(admin).length} admin forms.`);
