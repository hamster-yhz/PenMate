#!/usr/bin/env bash
set -euo pipefail

FRONTEND_PUBLIC_PORT="${FRONTEND_PUBLIC_PORT:-80}"
PUBLIC_BASE_URL="${PUBLIC_BASE_URL:-http://127.0.0.1:${FRONTEND_PUBLIC_PORT}}"
HEALTH_URL="${HEALTH_URL:-${PUBLIC_BASE_URL%/}/actuator/health}"
FRONTEND_URL="${FRONTEND_URL:-${PUBLIC_BASE_URL%/}/}"
MAX_ATTEMPTS="${HEALTHCHECK_ATTEMPTS:-60}"
SLEEP_SECONDS="${HEALTHCHECK_SLEEP_SECONDS:-5}"

wait_for_url() {
  local name="$1"
  local url="$2"
  local attempt=1

  while [ "$attempt" -le "$MAX_ATTEMPTS" ]; do
    if curl -fsS "$url" >/dev/null; then
      echo "$name is reachable: $url"
      return 0
    fi

    echo "Waiting for $name ($attempt/$MAX_ATTEMPTS): $url"
    attempt=$((attempt + 1))
    sleep "$SLEEP_SECONDS"
  done

  echo "$name did not become reachable: $url" >&2
  return 1
}

wait_for_url "backend health" "$HEALTH_URL"
wait_for_url "frontend" "$FRONTEND_URL"
