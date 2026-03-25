#!/usr/bin/env bash
#
# Restore the database dump to production.
#
# This script:
#   1. Drops all existing tables (clean slate)
#   2. Restores from the dump file
#   3. Flyway re-creates its migration history on next backend startup
#
# Usage (on production server):
#   ./deploy/data/restore.sh                         # Uses defaults
#   ./deploy/data/restore.sh --host localhost --port 5432  # Custom
#
# Or run against the prod Docker container:
#   docker exec -i newsanalyzer-postgres pg_restore ...
#
# Prerequisites:
#   - pg_restore installed (comes with PostgreSQL)
#   - newsanalyzer.dump file present in deploy/data/
#   - Target database exists and is accessible

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DUMP_FILE="${SCRIPT_DIR}/newsanalyzer.dump"

# Production database defaults (match deploy/production/docker-compose.yml)
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-newsanalyzer}"
DB_USER="${DB_USER:-newsanalyzer}"

# Parse optional CLI args
while [[ $# -gt 0 ]]; do
  case $1 in
    --host) DB_HOST="$2"; shift 2 ;;
    --port) DB_PORT="$2"; shift 2 ;;
    --db)   DB_NAME="$2"; shift 2 ;;
    --user) DB_USER="$2"; shift 2 ;;
    --file) DUMP_FILE="$2"; shift 2 ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

if [[ ! -f "${DUMP_FILE}" ]]; then
  echo "ERROR: Dump file not found: ${DUMP_FILE}"
  echo "Run ./deploy/data/dump.sh first to create it."
  exit 1
fi

DUMP_SIZE=$(du -h "${DUMP_FILE}" | cut -f1)

echo "========================================"
echo "  NewsAnalyzer Database Restore"
echo "========================================"
echo "  Host: ${DB_HOST}:${DB_PORT}"
echo "  Database: ${DB_NAME}"
echo "  User: ${DB_USER}"
echo "  Dump file: ${DUMP_FILE} (${DUMP_SIZE})"
echo "========================================"
echo ""
echo "WARNING: This will DROP all existing tables in '${DB_NAME}'"
echo "         and replace them with data from the dump file."
echo ""
read -p "Continue? (y/N) " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
  echo "Aborted."
  exit 0
fi

echo ""
echo "Step 1: Dropping existing tables..."
# Drop all tables in public schema
psql \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --username="${DB_USER}" \
  --dbname="${DB_NAME}" \
  --command="DROP SCHEMA public CASCADE; CREATE SCHEMA public; GRANT ALL ON SCHEMA public TO ${DB_USER};"

echo "Step 2: Restoring from dump..."
pg_restore \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --username="${DB_USER}" \
  --dbname="${DB_NAME}" \
  --verbose \
  --no-owner \
  --no-privileges \
  --single-transaction \
  "${DUMP_FILE}"

echo ""
echo "========================================"
echo "  Restore complete!"
echo "========================================"
echo ""
echo "Next steps:"
echo "  1. Restart the backend service so Flyway re-creates its history:"
echo "     docker restart newsanalyzer-backend"
echo "  2. Verify data:"
echo "     docker exec newsanalyzer-postgres psql -U ${DB_USER} -d ${DB_NAME} -c '\\dt'"
