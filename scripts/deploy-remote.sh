#!/usr/bin/env bash
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/opt/penmate}"
ENV_FILE="${ENV_FILE:-.env}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"

cd "$DEPLOY_PATH"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required on the deployment host" >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "docker compose plugin is required on the deployment host" >&2
  exit 1
fi

if [ ! -f "$ENV_FILE" ]; then
  echo "$DEPLOY_PATH/$ENV_FILE is missing. Copy .env.example to $ENV_FILE and fill secrets first." >&2
  exit 1
fi

REQUESTED_BACKEND_IMAGE="${BACKEND_IMAGE:-}"
REQUESTED_FRONTEND_IMAGE="${FRONTEND_IMAGE:-}"

read_env_value() {
  local key="$1"
  awk -F '=' -v key="$key" '
    $0 ~ "^[[:space:]]*#" || $0 !~ "=" { next }
    {
      k=$1
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", k)
      if (k == key) {
        sub(/^[^=]*=/, "", $0)
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", $0)
        gsub(/^"|"$/, "", $0)
        gsub(/^'\''|'\''$/, "", $0)
        print $0
        exit
      }
    }
  ' "$ENV_FILE"
}

if [ -n "$REQUESTED_BACKEND_IMAGE" ]; then
  export BACKEND_IMAGE="$REQUESTED_BACKEND_IMAGE"
else
  unset BACKEND_IMAGE
fi

if [ -n "$REQUESTED_FRONTEND_IMAGE" ]; then
  export FRONTEND_IMAGE="$REQUESTED_FRONTEND_IMAGE"
else
  unset FRONTEND_IMAGE
fi

PROJECT_NAME="${COMPOSE_PROJECT_NAME:-$(read_env_value COMPOSE_PROJECT_NAME)}"
PROJECT_NAME="${PROJECT_NAME:-penmate}"

export FRONTEND_PUBLIC_PORT="${FRONTEND_PUBLIC_PORT:-$(read_env_value FRONTEND_PUBLIC_PORT)}"

echo "--- deploy-remote diagnostics ---"
echo "BACKEND_IMAGE=${BACKEND_IMAGE:-<empty>}"
echo "FRONTEND_IMAGE=${FRONTEND_IMAGE:-<empty>}"

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" -p "$PROJECT_NAME" pull
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" -p "$PROJECT_NAME" up -d --remove-orphans


docker image prune -f
