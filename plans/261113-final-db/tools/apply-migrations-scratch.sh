#!/usr/bin/env bash
# Apply active migrations plus the given staged final-db migrations to a fresh scratch DB in Docker.
# Usage: apply-migrations.sh [staged-file-name ...]   (staged names relative to db/migration-staged)
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)
REPO="$ROOT/backend/src/main/resources/db"
DB=${SCRATCH_DB:-otcms_scratch}
# This command recreates a disposable DB. Never accept an application DB name
# or SQL metacharacters through SCRATCH_DB.
if [[ ! "$DB" =~ ^(chk|otcms_scratch|scratch_[a-z0-9_]+)$ ]]; then
  echo "SCRATCH_DB must be chk, otcms_scratch or scratch_<lowercase identifier>" >&2
  exit 2
fi
docker exec otcms-final-db psql -U postgres -qtAc "DROP DATABASE IF EXISTS $DB WITH (FORCE)" >/dev/null
docker exec otcms-final-db psql -U postgres -qtAc "CREATE DATABASE $DB" >/dev/null
files=$(ls "$REPO/migration" | grep '^V.*\.sql$' | awk -F__ '{v=substr($1,2); gsub("_",".",v); print v" "$0}' | sort -V | cut -d' ' -f2-)
for f in $files; do
  docker exec -i otcms-final-db psql -U postgres -d $DB -v ON_ERROR_STOP=1 -q < "$REPO/migration/$f" >/dev/null 2>"/tmp/mig-err-$DB.txt" || { echo "FAIL $f"; cat "/tmp/mig-err-$DB.txt"; exit 1; }
done
echo "base ok ($(echo "$files" | wc -l) files)"
for s in "$@"; do
  docker exec -i otcms-final-db psql -U postgres -d $DB -v ON_ERROR_STOP=1 -q -1 < "$REPO/migration-staged/$s" >/dev/null 2>"/tmp/mig-err-$DB.txt" || { echo "FAIL $s"; cat "/tmp/mig-err-$DB.txt"; exit 1; }
  echo "ok $s"
done
docker exec otcms-final-db psql -U postgres -d $DB -tAc "select count(*) from information_schema.tables where table_schema='public' and table_type='BASE TABLE'"
