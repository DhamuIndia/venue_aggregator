#!/usr/bin/env bash
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "Usage: CONFIRM_RESTORE=yes $0 <backup-file.dump>" >&2
  exit 1
fi

if [ "${CONFIRM_RESTORE:-}" != "yes" ]; then
  echo "Restore is destructive. Set CONFIRM_RESTORE=yes to continue." >&2
  exit 1
fi

BACKUP_FILE="$1"
PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGDATABASE="${PGDATABASE:-venue_aggregator}"
PGUSER="${PGUSER:-venue_app}"

if [ ! -f "$BACKUP_FILE" ]; then
  echo "Backup file not found: $BACKUP_FILE" >&2
  exit 1
fi

if ! command -v pg_restore >/dev/null 2>&1; then
  echo "pg_restore is required. Install PostgreSQL client tools first." >&2
  exit 1
fi

pg_restore \
  --clean \
  --if-exists \
  --no-owner \
  --no-acl \
  --host="$PGHOST" \
  --port="$PGPORT" \
  --username="$PGUSER" \
  --dbname="$PGDATABASE" \
  "$BACKUP_FILE"

echo "PostgreSQL restore completed into $PGHOST:$PGPORT/$PGDATABASE"
