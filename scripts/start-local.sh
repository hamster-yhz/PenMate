#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKEND_DIR="${ROOT_DIR}/penmate-backend"
FRONTEND_DIR="${ROOT_DIR}/penmate-frontend"

BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-8091}"
BACKEND_HEALTH_URL="${BACKEND_HEALTH_URL:-http://localhost:${BACKEND_PORT}/actuator/health}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:${FRONTEND_PORT}/}"
WAIT_SECONDS="${WAIT_SECONDS:-90}"
LOG_DIR="${LOG_DIR:-${ROOT_DIR}/.codex-runtime/logs/start-local}"
BACKEND_LOG="${LOG_DIR}/backend.log"
FRONTEND_LOG="${LOG_DIR}/frontend.log"
PID_DIR="${ROOT_DIR}/.codex-runtime/pids"

usage() {
  cat <<EOF
Usage: scripts/start-local.sh

Starts PenMate local development services without Docker.

Environment overrides:
  BACKEND_PORT       default: 8080
  FRONTEND_PORT      default: 8091
  WAIT_SECONDS       default: 90
  LOG_DIR            default: .codex-runtime/logs/start-local

Prerequisites:
  PostgreSQL 18.4 and Redis are already running locally.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

require_command() {
  local name="$1"
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "Missing required command: ${name}" >&2
    exit 1
  fi
}

is_wsl() {
  [[ -r /proc/version ]] && grep -qi microsoft /proc/version
}

http_ok() {
  local url="$1"
  if command -v curl >/dev/null 2>&1; then
    curl -fsS --max-time 3 "$url" >/dev/null 2>&1
    return $?
  fi

  if command -v powershell.exe >/dev/null 2>&1; then
    powershell.exe -NoProfile -Command \
      "try { Invoke-WebRequest -UseBasicParsing -Uri '${url}' -TimeoutSec 3 | Out-Null; exit 0 } catch { exit 1 }" \
      >/dev/null 2>&1
    return $?
  fi

  return 1
}

backend_healthy() {
  if command -v curl >/dev/null 2>&1; then
    curl -fsS --max-time 3 "$BACKEND_HEALTH_URL" 2>/dev/null | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'
    return $?
  fi

  if command -v powershell.exe >/dev/null 2>&1; then
    powershell.exe -NoProfile -Command \
      "try { \$r = Invoke-WebRequest -UseBasicParsing -Uri '${BACKEND_HEALTH_URL}' -TimeoutSec 3; if (\$r.Content -match '\"status\"\s*:\s*\"UP\"') { exit 0 } else { exit 1 } } catch { exit 1 }" \
      >/dev/null 2>&1
    return $?
  fi

  return 1
}

port_in_use() {
  local port="$1"
  if is_wsl && command -v powershell.exe >/dev/null 2>&1; then
    powershell.exe -NoProfile -Command \
      "if (Get-NetTCPConnection -LocalPort ${port} -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }" \
      >/dev/null 2>&1
    return $?
  fi

  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
    return $?
  fi

  if command -v netstat >/dev/null 2>&1; then
    netstat -an 2>/dev/null | grep -E "[:.]${port}[[:space:]].*(LISTEN|LISTENING)" >/dev/null 2>&1
    return $?
  fi

  return 1
}

wait_for_backend() {
  local waited=0
  until backend_healthy; do
    if (( waited >= WAIT_SECONDS )); then
      echo "Backend did not become healthy within ${WAIT_SECONDS}s. See ${BACKEND_LOG}" >&2
      exit 1
    fi
    sleep 2
    waited=$((waited + 2))
  done
}

start_backend() {
  mkdir -p "$LOG_DIR" "$PID_DIR"
  echo "Starting backend on port ${BACKEND_PORT}..."
  (
    cd "$BACKEND_DIR"
    nohup mvn -Dspring-boot.run.profiles=local spring-boot:run >"$BACKEND_LOG" 2>&1 &
    echo $! >"${PID_DIR}/penmate-backend.pid"
  )
}

start_frontend() {
  mkdir -p "$LOG_DIR" "$PID_DIR"
  echo "Starting frontend on port ${FRONTEND_PORT}..."
  (
    cd "$FRONTEND_DIR"
    nohup npm run dev >"$FRONTEND_LOG" 2>&1 &
    echo $! >"${PID_DIR}/penmate-frontend.pid"
  )
}

echo "Starting PenMate local development services without Docker."
echo "Assuming local PostgreSQL 18.4 and Redis are already running."

[[ -d "$BACKEND_DIR" ]] || { echo "Backend directory not found: ${BACKEND_DIR}" >&2; exit 1; }
[[ -d "$FRONTEND_DIR" ]] || { echo "Frontend directory not found: ${FRONTEND_DIR}" >&2; exit 1; }
require_command mvn
require_command npm

if backend_healthy; then
  echo "Backend is already healthy at ${BACKEND_HEALTH_URL}; reusing it."
elif port_in_use "$BACKEND_PORT"; then
  echo "Port ${BACKEND_PORT} is already in use, but ${BACKEND_HEALTH_URL} is not healthy." >&2
  echo "Stop that process or set BACKEND_PORT before running this script." >&2
  exit 1
else
  start_backend
  wait_for_backend
  echo "Backend is healthy at ${BACKEND_HEALTH_URL}."
fi

if http_ok "$FRONTEND_URL"; then
  echo "Frontend is already reachable at ${FRONTEND_URL}; reusing it."
elif port_in_use "$FRONTEND_PORT"; then
  echo "Port ${FRONTEND_PORT} is already in use, but ${FRONTEND_URL} is not reachable." >&2
  echo "Stop that process or set FRONTEND_PORT before running this script." >&2
  exit 1
else
  start_frontend
  echo "Frontend is starting at ${FRONTEND_URL}."
fi

echo "Logs:"
echo "  Backend:  ${BACKEND_LOG}"
echo "  Frontend: ${FRONTEND_LOG}"
echo "URLs:"
echo "  Backend health: ${BACKEND_HEALTH_URL}"
echo "  Frontend:       ${FRONTEND_URL}"
