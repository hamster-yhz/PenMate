#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

COMPOSE_FILE="${COMPOSE_FILE:-${ROOT_DIR}/docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env}"
PULL_IMAGES=1
WITH_RABBITMQ=0

usage() {
  cat <<EOF
Usage: scripts/start-prod.sh [options]

Starts PenMate production services with Docker Compose.

Options:
  --env-file PATH       Env file to pass to Docker Compose. Default: .env
  --compose-file PATH   Compose file to use. Default: docker-compose.prod.yml
  --no-pull             Skip docker compose pull before startup.
  --with-rabbitmq       Enable the optional rabbitmq compose profile.
  -h, --help            Show this help.

Environment overrides:
  ENV_FILE              Same as --env-file.
  COMPOSE_FILE          Same as --compose-file.

Expected production env:
  Copy .env.example to .env and fill all required image, database, JWT,
  storage, Milvus S3, and LLM settings before running this script.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      [[ $# -ge 2 ]] || { echo "--env-file requires a path" >&2; exit 1; }
      ENV_FILE="$2"
      shift 2
      ;;
    --compose-file)
      [[ $# -ge 2 ]] || { echo "--compose-file requires a path" >&2; exit 1; }
      COMPOSE_FILE="$2"
      shift 2
      ;;
    --no-pull)
      PULL_IMAGES=0
      shift
      ;;
    --with-rabbitmq)
      WITH_RABBITMQ=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

require_command() {
  local name="$1"
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "Missing required command: ${name}" >&2
    exit 1
  fi
}

compose_args() {
  local args=(--env-file "$ENV_FILE" -f "$COMPOSE_FILE")
  if [[ "$WITH_RABBITMQ" -eq 1 ]]; then
    args=(--profile rabbitmq "${args[@]}")
  fi
  printf '%s\n' "${args[@]}"
}

run_compose() {
  local args=()
  local item
  while IFS= read -r item; do
    args+=("$item")
  done < <(compose_args)
  docker compose "${args[@]}" "$@"
}

require_command docker

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose v2 is required: docker compose version failed." >&2
  exit 1
fi

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "Compose file not found: ${COMPOSE_FILE}" >&2
  echo "Set COMPOSE_FILE or run from a checkout that contains docker-compose.prod.yml." >&2
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Env file not found: ${ENV_FILE}" >&2
  echo "Copy .env.example to .env and fill production values before deployment." >&2
  exit 1
fi

echo "Starting PenMate production stack with Docker Compose."
echo "Compose file: ${COMPOSE_FILE}"
echo "Env file:     ${ENV_FILE}"
if [[ "$WITH_RABBITMQ" -eq 1 ]]; then
  echo "Profile:      rabbitmq"
fi

echo "Validating compose configuration..."
run_compose config --quiet

if [[ "$PULL_IMAGES" -eq 1 ]]; then
  echo "Pulling production images..."
  run_compose pull
fi

echo "Starting services..."
run_compose up -d --remove-orphans

echo "Current service status:"
run_compose ps
