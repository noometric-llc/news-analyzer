#!/usr/bin/env bash
#
# Dump the dev database from the Docker Postgres container.
#
# Use this when pg_dump is not installed locally — runs pg_dump
# inside the dev container and copies the dump file out.
#
# Usage:
#   ./deploy/data/dump-docker.sh
#
# Prerequisites:
#   - Dev docker-compose is running (newsanalyzer-postgres-dev container up)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DUMP_FILE="${SCRIPT_DIR}/newsanalyzer.dump"
CONTAINER="newsanalyzer-postgres-dev"
DB_NAME="${DB_NAME:-newsanalyzer_dev}"
DB_USER="${DB_USER:-newsanalyzer}"

# Verify container is running
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER}$"; then
  echo "ERROR: Container '${CONTAINER}' is not running."
  echo "Start it with: docker compose -f docker-compose.dev.yml up -d postgres"
  exit 1
fi

echo "========================================"
echo "  NewsAnalyzer Dev Database Dump"
echo "========================================"
echo "  Container: ${CONTAINER}"
echo "  Database: ${DB_NAME}"
echo "  Output: ${DUMP_FILE}"
echo "========================================"
echo ""

echo "Running pg_dump inside container (streaming to local file)..."
docker exec "${CONTAINER}" pg_dump \
  -U "${DB_USER}" \
  -d "${DB_NAME}" \
  --format=custom \
  --compress=6 \
  --no-owner \
  --no-privileges \
  --exclude-table=flyway_schema_history \
  --exclude-table=spring_session \
  --exclude-table=spring_session_attributes \
  > "${DUMP_FILE}"

DUMP_SIZE=$(du -h "${DUMP_FILE}" | cut -f1)

echo ""
echo "========================================"
echo "  Dump complete: ${DUMP_FILE} (${DUMP_SIZE})"
echo "========================================"
echo ""
echo "Next steps:"
echo "  1. Copy dump to production server:"
echo "     scp ${DUMP_FILE} user@server:~/newsanalyzer/deploy/data/"
echo "  2. On production server, restore:"
echo "     ./deploy/data/restore-docker.sh"
