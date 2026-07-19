#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
cleanup_file="${repo_root}/penmate-backend/src/test/resources/db/cases/cleanup.sql"
psql_bin="${PSQL_BIN:-psql}"
db_host="${DB_HOST:-localhost}"

case "${db_host}" in
  localhost|127.0.0.1|::1) ;;
  *)
    if [[ "${PENMATE_ALLOW_REMOTE_DB:-false}" != "true" ]]; then
      echo "Refusing to clean non-local PostgreSQL host '${db_host}'. Set PENMATE_ALLOW_REMOTE_DB=true to confirm." >&2
      exit 2
    fi
    ;;
esac

export PGPASSWORD="${DB_PASS:-postgres}"
trap 'unset PGPASSWORD' EXIT
"${psql_bin}" \
  -v ON_ERROR_STOP=1 \
  -h "${db_host}" \
  -p "${DB_PORT:-5432}" \
  -U "${DB_USER:-postgres}" \
  -d "${DB_NAME:-penmate}" \
  -f "${cleanup_file}"
