#!/usr/bin/env bash
set -euo pipefail

finance_db_host="127.0.0.1"
finance_db_port="55432"
finance_db_name="expensely_finance"
export DB_USERNAME="${DB_USERNAME:-expensely_local}"
export DB_PASSWORD="${DB_PASSWORD:-expensely_local}"
if ! pg_isready -h "$finance_db_host" -p "$finance_db_port" -d "$finance_db_name" >/dev/null; then
  echo "Local finance PostgreSQL is unavailable on 127.0.0.1:55432" >&2
  exit 1
fi
finance_db_identity="$(PGPASSWORD="$DB_PASSWORD" psql -X -h "$finance_db_host" -p "$finance_db_port" -U "$DB_USERNAME" -d "$finance_db_name" -Atqc "SELECT current_database() || ':' || COALESCE(inet_server_addr()::text, '')" 2>/dev/null || true)"
if [[ "$finance_db_identity" != "expensely_finance:127.0.0.1" ]]; then
  echo "Refusing migrations: could not verify the isolated local expensely_finance database" >&2
  exit 1
fi
export DB_URL="jdbc:postgresql://${finance_db_host}:${finance_db_port}/${finance_db_name}"
export SPRING_PROFILES_ACTIVE="local-finance"
export JWT_SECRET="${JWT_SECRET:-local-finance-secret-change-before-sharing}"
export GOOGLE_CLIENT_ID="${GOOGLE_CLIENT_ID:-local-finance}"
export REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
export REDIS_PORT="${REDIS_PORT:-6379}"
export REDIS_USERNAME="${REDIS_USERNAME:-local}"
export REDIS_PASSWORD="${REDIS_PASSWORD:-local}"
exec ./gradlew bootRun --args='--server.port=18080' "$@"
