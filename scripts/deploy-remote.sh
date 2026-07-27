#!/usr/bin/env bash
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/opt/penmate}"
ENV_FILE="${ENV_FILE:-.env}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-240}"

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
  echo "$DEPLOY_PATH/$ENV_FILE is missing." >&2
  echo "Run bash ./scripts/init-secrets.sh once, then fill the environment-specific values it reports." >&2
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

byte_length() {
  LC_ALL=C printf '%s' "$1" | wc -c | tr -d '[:space:]'
}

is_placeholder() {
  local value="$1"
  [[ "$value" == "<"*">" ]] || [[ "$value" == *"example.com"* ]] || [[ "$value" == *"OWNER/REPO"* ]]
}

validation_errors=0

validation_error() {
  echo "deployment configuration error: $1" >&2
  validation_errors=$((validation_errors + 1))
}

require_value() {
  local key="$1"
  local value
  value="$(read_env_value "$key")"
  if [ -z "$value" ]; then
    validation_error "$key is required"
  elif is_placeholder "$value"; then
    validation_error "$key still contains an example placeholder"
  fi
}

require_minimum_secret_length() {
  local key="$1"
  local minimum="$2"
  local value
  value="$(read_env_value "$key")"
  require_value "$key"
  if [ -n "$value" ] && ! is_placeholder "$value" && [ "$(byte_length "$value")" -lt "$minimum" ]; then
    validation_error "$key must contain at least $minimum bytes"
  fi
}

validate_model_encryption_key() {
  local value decoded_length
  value="$(read_env_value MODEL_KEY_ENCRYPTION_KEY_BASE64)"
  require_value MODEL_KEY_ENCRYPTION_KEY_BASE64
  if [ -z "$value" ] || is_placeholder "$value"; then
    return
  fi
  if ! decoded_length="$(printf '%s' "$value" | base64 -d 2>/dev/null | wc -c | tr -d '[:space:]')"; then
    validation_error "MODEL_KEY_ENCRYPTION_KEY_BASE64 must be valid Base64"
    return
  fi
  case "$decoded_length" in
    16|24|32) ;;
    *) validation_error "MODEL_KEY_ENCRYPTION_KEY_BASE64 must decode to 16, 24, or 32 bytes" ;;
  esac
}

validate_optional_group() {
  local prefix="$1"
  local keys=(PROVIDER BASE_URL API_KEY MODEL_NAME)
  local populated=0
  local suffix value
  for suffix in "${keys[@]}"; do
    value="$(read_env_value "${prefix}_${suffix}")"
    if [ -n "$value" ]; then
      populated=$((populated + 1))
      if is_placeholder "$value"; then
        validation_error "${prefix}_${suffix} still contains an example placeholder"
      fi
    fi
  done
  if [ "$populated" -ne 0 ] && [ "$populated" -ne 4 ]; then
    validation_error "$prefix must have all four values populated or all four values empty"
  fi
}

validate_image() {
  local key="$1"
  local value="$2"
  if [ -z "$value" ]; then
    validation_error "$key is required"
  elif is_placeholder "$value"; then
    validation_error "$key still contains an example placeholder"
  fi
}

validate_certificate_files() {
  local certificate_directory
  certificate_directory="$(read_env_value CERT_DIR)"
  certificate_directory="${certificate_directory:-./certs}"
  if [ ! -f "$certificate_directory/origin.pem" ]; then
    validation_error "$certificate_directory/origin.pem is missing"
  fi
  if [ ! -f "$certificate_directory/origin.key" ]; then
    validation_error "$certificate_directory/origin.key is missing"
  fi
}

BACKEND_IMAGE="${REQUESTED_BACKEND_IMAGE:-$(read_env_value BACKEND_IMAGE)}"
FRONTEND_IMAGE="${REQUESTED_FRONTEND_IMAGE:-$(read_env_value FRONTEND_IMAGE)}"
export BACKEND_IMAGE FRONTEND_IMAGE

PROJECT_NAME="${COMPOSE_PROJECT_NAME:-$(read_env_value COMPOSE_PROJECT_NAME)}"
PROJECT_NAME="${PROJECT_NAME:-penmate}"

export FRONTEND_PUBLIC_PORT="${FRONTEND_PUBLIC_PORT:-$(read_env_value FRONTEND_PUBLIC_PORT)}"

require_value DB_NAME
require_value DB_USER
require_minimum_secret_length DB_PASS 24
require_minimum_secret_length REDIS_PASS 24
require_minimum_secret_length JWT_SECRET 32
require_minimum_secret_length RATE_LIMIT_KEY_SECRET 32
validate_model_encryption_key
require_value STORAGE_ENDPOINT
require_value STORAGE_PUBLIC_ENDPOINT
require_value STORAGE_ACCESS_KEY
require_value STORAGE_SECRET_KEY
require_value STORAGE_BUCKET
require_value BOOTSTRAP_ADMIN_EMAIL
require_value BOOTSTRAP_ADMIN_PASSWORD
validate_optional_group BOOTSTRAP_CHAT
validate_optional_group BOOTSTRAP_EMBEDDING
validate_image BACKEND_IMAGE "$BACKEND_IMAGE"
validate_image FRONTEND_IMAGE "$FRONTEND_IMAGE"
validate_certificate_files

if { [ -n "${GHCR_USERNAME:-}" ] && [ -z "${GHCR_TOKEN:-}" ]; } || \
   { [ -z "${GHCR_USERNAME:-}" ] && [ -n "${GHCR_TOKEN:-}" ]; }; then
  validation_error "GHCR_USERNAME and GHCR_TOKEN must be provided together"
fi

if [ "$validation_errors" -ne 0 ]; then
  echo "Deployment stopped before changing containers. Fix $DEPLOY_PATH/$ENV_FILE and retry." >&2
  exit 1
fi

COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" -p "$PROJECT_NAME")

if ! "${COMPOSE[@]}" config --quiet; then
  echo "Deployment stopped because the Docker Compose configuration is invalid." >&2
  exit 1
fi

echo "--- deploy-remote diagnostics ---"
echo "PROJECT_NAME=$PROJECT_NAME"
echo "BACKEND_IMAGE=$BACKEND_IMAGE"
echo "FRONTEND_IMAGE=$FRONTEND_IMAGE"

if [ -n "${GHCR_USERNAME:-}" ]; then
  printf '%s' "$GHCR_TOKEN" | docker login ghcr.io --username "$GHCR_USERNAME" --password-stdin
fi

print_deployment_diagnostics() {
  echo "--- deployment service status ---" >&2
  "${COMPOSE[@]}" ps >&2 || true
  echo "--- recent deployment logs ---" >&2
  "${COMPOSE[@]}" logs --no-color --tail=200 postgres redis backend frontend >&2 || true
}

wait_for_service() {
  local service="$1"
  local container_id state health
  while [ "$SECONDS" -lt "$DEPLOY_DEADLINE" ]; do
    container_id="$("${COMPOSE[@]}" ps -q "$service")"
    if [ -n "$container_id" ]; then
      state="$(docker inspect --format '{{.State.Status}}' "$container_id" 2>/dev/null || true)"
      health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container_id" 2>/dev/null || true)"
      if [ "$state" = running ] && { [ "$health" = healthy ] || [ "$health" = none ]; }; then
        echo "$service is $state ($health)"
        return 0
      fi
      if [ "$state" = exited ] || [ "$state" = dead ] || [ "$health" = unhealthy ]; then
        echo "$service failed readiness: state=${state:-unknown}, health=${health:-unknown}" >&2
        return 1
      fi
    fi
    sleep 5
  done
  echo "$service did not become healthy within ${HEALTH_TIMEOUT_SECONDS}s" >&2
  return 1
}

"${COMPOSE[@]}" pull
if ! "${COMPOSE[@]}" up -d; then
  echo "Docker Compose failed to start the deployment." >&2
  print_deployment_diagnostics
  exit 1
fi

DEPLOY_DEADLINE=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
for service in postgres redis backend frontend; do
  if ! wait_for_service "$service"; then
    print_deployment_diagnostics
    exit 1
  fi
done

docker image prune -f
echo "PenMate deployment completed successfully."
