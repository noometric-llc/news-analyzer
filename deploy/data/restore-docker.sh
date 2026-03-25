#!/usr/bin/env bash
#
# Restore database dump into the production Docker Postgres container.
#
# This is the script to run ON the production server. It copies the dump
# file into the running Postgres container and restores it.
#
# Usage (on production server):
#   ./deploy/data/restore-docker.sh
#   ./deploy/data/restore-docker.sh --file /path/to/newsanalyzer.dump
#
# Prerequisites:
#   - Production docker-compose is running (newsanalyzer-postgres container up)
#   - newsanalyzer.dump file is accessible

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DUMP_FILE="${SCRIPT_DIR}/newsanalyzer.dump"
CONTAINER="newsanalyzer-postgres"
DB_NAME="${DB_NAME:-newsanalyzer}"
DB_USER="${DB_USER:-newsanalyzer}"

# Parse optional CLI args
while [[ $# -gt 0 ]]; do
  case $1 in
    --file) DUMP_FILE="$2"; shift 2 ;;
    --container) CONTAINER="$2"; shift 2 ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

if [[ ! -f "${DUMP_FILE}" ]]; then
  echo "ERROR: Dump file not found: ${DUMP_FILE}"
  echo "Run ./deploy/data/dump.sh on your dev machine first."
  exit 1
fi

# Verify container is running
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER}$"; then
  echo "ERROR: Container '${CONTAINER}' is not running."
  echo "Start it with: docker compose -f deploy/production/docker-compose.yml up -d postgres"
  exit 1
fi

DUMP_SIZE=$(du -h "${DUMP_FILE}" | cut -f1)

echo "========================================"
echo "  NewsAnalyzer Docker DB Restore"
echo "========================================"
echo "  Container: ${CONTAINER}"
echo "  Database: ${DB_NAME}"
echo "  Dump file: ${DUMP_FILE} (${DUMP_SIZE})"
echo "========================================"
echo ""
echo "WARNING: This will DROP all existing tables in '${DB_NAME}'"
echo ""
read -p "Continue? (y/N) " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
  echo "Aborted."
  exit 0
fi

echo ""
echo "Step 1: Copying dump into container..."
docker cp "${DUMP_FILE}" "${CONTAINER}:/tmp/newsanalyzer.dump"

echo "Step 2: Dropping existing tables..."
docker exec "${CONTAINER}" psql \
  -U "${DB_USER}" \
  -d "${DB_NAME}" \
  -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public; GRANT ALL ON SCHEMA public TO ${DB_USER};"

echo "Step 3: Restoring from dump..."
docker exec "${CONTAINER}" pg_restore \
  -U "${DB_USER}" \
  -d "${DB_NAME}" \
  --verbose \
  --no-owner \
  --no-privileges \
  --single-transaction \
  /tmp/newsanalyzer.dump

echo "Step 4: Cleaning up temp file..."
docker exec "${CONTAINER}" rm /tmp/newsanalyzer.dump

echo ""
echo "========================================"
echo "  Restore complete!"
echo "========================================"
echo ""
echo "Next steps:"
echo "  1. Restart backend so Flyway re-creates migration history:"
echo "     docker restart newsanalyzer-backend"
echo "  2. Verify:"
echo "     docker exec ${CONTAINER} psql -U ${DB_USER} -d ${DB_NAME} -c '\\dt'"
