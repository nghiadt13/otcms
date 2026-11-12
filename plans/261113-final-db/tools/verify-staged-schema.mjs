// Verify the consolidated scratch schema against plan.md, without touching app DBs.
// From otcms/: node plans/261113-final-db/tools/verify-staged-schema.mjs chk
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';
import { spawnSync } from 'node:child_process';

const db = process.argv[2] || 'chk';
if (!/^(chk|otcms_scratch|scratch_[a-z0-9_]+)$/.test(db)) {
  throw new Error('Only disposable scratch database names are allowed');
}
const planDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const stagedDir = resolve(planDir, '../../backend/src/main/resources/db/migration-staged');
const activeDir = resolve(planDir, '../../backend/src/main/resources/db/migration');
const expected = [...readFileSync(resolve(planDir, 'plan.md'), 'utf8')
  .matchAll(/^\| \d+[a-z]? \| (?:\*\*)?`(\w+)`/gm)].map(m => m[1]).sort();
if (expected.length !== 35 || new Set(expected).size !== 35) {
  throw new Error('plan.md must name exactly 35 distinct physical tables');
}
// A phase moves its migration into the active directory; keep checking its drops too.
const removed = [...new Set([stagedDir, activeDir].flatMap(dir => readdirSync(dir)
  .filter(f => f.endsWith('.sql') && Number(/^V(\d+)/.exec(f)?.[1]) >= 109)
  .flatMap(f => [...readFileSync(resolve(dir, f), 'utf8')
    .matchAll(/^DROP TABLE (\w+);/gm)].map(m => m[1]))))];
function query(sql) {
  const result = spawnSync('docker', ['exec', '-i', 'otcms-final-db',
    'psql', '-X', '-U', 'postgres', '-d', db, '-v', 'ON_ERROR_STOP=1', '-qAt'],
  { input: sql, encoding: 'utf8' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(result.stderr);
  return result.stdout.trim();
}
const actual = query(`SELECT tablename FROM pg_tables WHERE schemaname='public'
  AND tablename <> 'flyway_schema_history' ORDER BY tablename;`).split('\n').map(s => s.trim());
if (JSON.stringify(actual) !== JSON.stringify(expected)) {
  throw new Error(`Tables differ: missing=${expected.filter(t => !actual.includes(t))}; unexpected=${actual.filter(t => !expected.includes(t))}`);
}
const orphan = query(`SELECT p.proname FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
  WHERE n.nspname='public' AND p.prorettype='trigger'::regtype
  AND NOT EXISTS (SELECT 1 FROM pg_trigger t WHERE t.tgfoid=p.oid);`);
if (orphan) throw new Error(`Orphan trigger functions: ${orphan}`);
// Match SQL relation use, not legacy audit source names stored as string values.
const stale = query(`SELECT p.proname FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
  WHERE n.nspname='public' AND p.prosrc ~* '(from|join|update|into|references)[[:space:]]+(public[.])?(${removed.join('|')})([^a-z_0-9]|$)';`);
if (stale) throw new Error(`Functions still reference removed tables: ${stale}`);
query(`BEGIN;
INSERT INTO audit_logs (source, category, action, record_type, dedup_key)
VALUES ('schema_probe', 'SYSTEM', 'VERIFY', 'SYSTEM', 'schema_probe');
INSERT INTO audit_logs (source, category, action, record_type, dedup_key)
VALUES ('schema_probe', 'SYSTEM', 'VERIFY', 'SYSTEM', 'schema_probe')
ON CONFLICT (dedup_key) DO NOTHING;
DO $$ BEGIN
  IF (SELECT count(*) FROM audit_logs WHERE dedup_key='schema_probe') <> 1 THEN
    RAISE EXCEPTION 'Audit deduplication failed';
  END IF;
  BEGIN
    UPDATE audit_logs SET action='CHANGED' WHERE dedup_key='schema_probe';
    RAISE EXCEPTION 'Audit UPDATE was allowed';
  EXCEPTION WHEN SQLSTATE '55000' THEN NULL; END;
  BEGIN
    DELETE FROM audit_logs WHERE dedup_key='schema_probe';
    RAISE EXCEPTION 'Audit DELETE was allowed';
  EXCEPTION WHEN SQLSTATE '55000' THEN NULL; END;
  BEGIN
    TRUNCATE audit_logs;
    RAISE EXCEPTION 'Audit TRUNCATE was allowed';
  EXCEPTION WHEN SQLSTATE '55000' THEN NULL; END;
END $$;
ROLLBACK;`);
console.log('PASS: 35 expected tables; no orphan triggers or stale SQL references; audit deduplication and append-only guards work.');
