#!/usr/bin/env node
// Compare two OpenAPI JSON documents for the final-db refactor (API must not change).
// Usage: node compare-openapi.mjs <baseline.json> <current.json> [--allow <regex>]... [--allow-schema <regex>]...
//   --allow: path regex whose operations may differ or disappear (e.g. removed features).
//   --allow-schema: component schema name regex that may differ or disappear (DTOs of removed features).
// Prints every difference; prints "no differences" and exits 0 when the contracts match.
import { readFileSync } from 'node:fs';

const args = process.argv.slice(2);
const allows = [];
const schemaAllows = [];
const files = [];
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--allow') allows.push(new RegExp(args[++i]));
  else if (args[i] === '--allow-schema') schemaAllows.push(new RegExp(args[++i]));
  else files.push(args[i]);
}
if (files.length !== 2) {
  console.error('usage: node compare-openapi.mjs <baseline.json> <current.json> [--allow <regex>]...');
  process.exit(2);
}
const [base, curr] = files.map((f) => JSON.parse(readFileSync(f, 'utf8')));
const allowed = (path) => allows.some((re) => re.test(path));

// Stable JSON: sort object keys so key order never counts as a difference.
const canon = (v) => {
  if (Array.isArray(v)) return v.map(canon);
  if (v && typeof v === 'object') {
    return Object.fromEntries(Object.keys(v).sort().map((k) => [k, canon(v[k])]));
  }
  return v;
};
const same = (a, b) => JSON.stringify(canon(a)) === JSON.stringify(canon(b));

const diffs = [];
const METHODS = ['get', 'put', 'post', 'delete', 'patch', 'options', 'head'];
// Only the contract-relevant parts of an operation; summaries, tags and operationIds are ignored.
const contract = (op) => ({
  parameters: (op.parameters || []).map((p) => ({ name: p.name, in: p.in, required: !!p.required, schema: p.schema })),
  requestBody: op.requestBody?.content,
  requestRequired: !!op.requestBody?.required,
  responses: Object.fromEntries(Object.entries(op.responses || {}).map(([code, r]) => [code, r.content || null])),
});

const paths = new Set([...Object.keys(base.paths || {}), ...Object.keys(curr.paths || {})]);
for (const path of [...paths].sort()) {
  if (allowed(path)) continue;
  const b = base.paths?.[path] || {};
  const c = curr.paths?.[path] || {};
  for (const m of METHODS) {
    if (!b[m] && !c[m]) continue;
    if (!c[m]) { diffs.push(`REMOVED   ${m.toUpperCase()} ${path}`); continue; }
    if (!b[m]) { diffs.push(`ADDED     ${m.toUpperCase()} ${path}`); continue; }
    const cb = contract(b[m]);
    const cc = contract(c[m]);
    for (const part of Object.keys(cb)) {
      if (!same(cb[part], cc[part])) diffs.push(`CHANGED   ${m.toUpperCase()} ${path} -> ${part}`);
    }
  }
}

const bs = base.components?.schemas || {};
const cs = curr.components?.schemas || {};
for (const name of [...new Set([...Object.keys(bs), ...Object.keys(cs)])].sort()) {
  if (schemaAllows.some((re) => re.test(name))) continue;
  if (!cs[name]) diffs.push(`SCHEMA REMOVED ${name}`);
  else if (!bs[name]) diffs.push(`SCHEMA ADDED   ${name}`);
  else if (!same(bs[name], cs[name])) diffs.push(`SCHEMA CHANGED ${name}`);
}

if (diffs.length === 0) {
  console.log('no differences');
} else {
  console.log(diffs.join('\n'));
  console.log(`\n${diffs.length} difference(s)`);
  process.exit(1);
}
