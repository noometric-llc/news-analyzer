#!/usr/bin/env bash
#
# Dump the local dev database for deployment to production.
#
# Creates a compressed custom-format dump file that can be restored
# on the production server. Excludes volatile tables (Spring sessions,
# Flyway history — those are re-created on startup).
#
# Usage:
#   ./deploy/data/dump.sh                          # Uses defaults
#   ./deploy/data/dump.sh --host localhost --port 5432  # Custom connection
#
# Prerequisites:
#   - pg_dump installed locally (comes with PostgreSQL)
#   - Dev database running and populated
#
# Output:
#   deploy/data/newsanalyzer.dump  (binary, custom format)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DUMP_FILE="${SCRIPT_DIR}/newsanalyzer.dump"

# Dev database defaults (match docker-compose.dev.yml)
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-newsanalyzer_dev}"
DB_USER="${DB_USER:-newsanalyzer}"

# Parse optional CLI args
while [[ $# -gt 0 ]]; do
  case $1 in
    --host) DB_HOST="$2"; shift 2 ;;
    --port) DB_PORT="$2"; shift 2 ;;
    --db)   DB_NAME="$2"; shift 2 ;;
    --user) DB_USER="$2"; shift 2 ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

echo "========================================"
echo "  NewsAnalyzer Database Dump"
echo "========================================"
echo "  Host: ${DB_HOST}:${DB_PORT}"
echo "  Database: ${DB_NAME}"
echo "  User: ${DB_USER}"
echo "  Output: ${DUMP_FILE}"
echo "========================================"
echo ""

# Exclude Flyway migration history (re-created on startup)
# and any Spring session tables
EXCLUDE_TABLES=(
  --exclude-table=flyway_schema_history
  --exclude-table=spring_session
  --exclude-table=spring_session_attributes
)

echo "Running pg_dump..."
pg_dump \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --username="${DB_USER}" \
  --dbname="${DB_NAME}" \
  --format=custom \
  --compress=6 \
  --verbose \
  --no-owner \
  --no-privileges \
  "${EXCLUDE_TABLES[@]}" \
  --file="${DUMP_FILE}"

DUMP_SIZE=$(du -h "${DUMP_FILE}" | cut -f1)
echo ""
echo "========================================"
echo "  Dump complete: ${DUMP_FILE} (${DUMP_SIZE})"
echo "========================================"
echo ""
echo "Next steps:"
echo "  1. Copy to production server:"
echo "     scp ${DUMP_FILE} user@server:~/newsanalyzer/"
echo "  2. Restore on production:"
echo "     ./deploy/data/restore.sh"
