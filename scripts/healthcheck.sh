#!/usr/bin/env bash
set -euo pipefail

BACKEND_HEALTH_URL="${BACKEND_HEALTH_URL:-http://127.0.0.1:8080/actuator/health}"
FRONTEND_PORT="${FRONTEND_PUBLIC_PORT:-8443}"
FRONTEND_HEALTH_URL="${FRONTEND_URL:-https://127.0.0.1:${FRONTEND_PORT}/}"
MAX_ATTEMPTS="${HEALTHCHECK_ATTEMPTS:-30}"
SLEEP_SECONDS="${HEALTHCHECK_SLEEP_SECONDS:-3}"

wait_for_url() {
  local name="$1"
  local url="$2"
  local attempt=1
  while [ "$attempt" -le "$MAX_ATTEMPTS" ]; do
    if curl -fsSk "$url" >/dev/null; then
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

wait_for_url "backend health" "$BACKEND_HEALTH_URL"
wait_for_url "frontend" "$FRONTEND_HEALTH_URL"