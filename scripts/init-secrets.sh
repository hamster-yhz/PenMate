#!/usr/bin/env bash
set -euo pipefail

umask 077

DEPLOY_PATH="${DEPLOY_PATH:-/opt/penmate}"
ENV_FILE="${ENV_FILE:-.env}"
TEMPLATE_FILE="${TEMPLATE_FILE:-.env.example}"

cd "$DEPLOY_PATH"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "$1 is required to initialize PenMate secrets" >&2
    exit 1
  fi
}

require_command awk
require_command openssl
require_command mktemp

if [ ! -f "$TEMPLATE_FILE" ]; then
  echo "$DEPLOY_PATH/$TEMPLATE_FILE is missing" >&2
  exit 1
fi

if [ ! -f "$ENV_FILE" ]; then
  cp "$TEMPLATE_FILE" "$ENV_FILE"
  echo "Created $DEPLOY_PATH/$ENV_FILE from $TEMPLATE_FILE"
fi
chmod 600 "$ENV_FILE"

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

write_env_value() {
  local key="$1"
  local value="$2"
  local temporary
  temporary="$(mktemp "${ENV_FILE}.XXXXXX")"
  awk -v key="$key" -v value="$value" '
    BEGIN { replaced=0 }
    {
      line=$0
      candidate=line
      sub(/=.*/, "", candidate)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", candidate)
      if (!replaced && candidate == key && line !~ "^[[:space:]]*#") {
        print key "=" value
        replaced=1
      } else {
        print line
      }
    }
    END {
      if (!replaced) print key "=" value
    }
  ' "$ENV_FILE" > "$temporary"
  chmod 600 "$temporary"
  mv "$temporary" "$ENV_FILE"
}

is_generated_placeholder() {
  local value="$1"
  [ -z "$value" ] || [[ "$value" == "<"*">" ]] || [[ "$value" == change-me-* ]]
}

project_name="$(read_env_value COMPOSE_PROJECT_NAME)"
project_name="${project_name:-penmate}"
postgres_volume="${project_name}_postgres-data"
database_volume_exists=false
if command -v docker >/dev/null 2>&1 && docker volume inspect "$postgres_volume" >/dev/null 2>&1; then
  database_volume_exists=true
fi

database_secret_blocked=false

ensure_random_secret() {
  local key="$1"
  local bytes="$2"
  local stateful="${3:-false}"
  local current
  current="$(read_env_value "$key")"
  if ! is_generated_placeholder "$current"; then
    echo "Keeping existing $key"
    return
  fi
  if [ "$stateful" = true ] && [ "$database_volume_exists" = true ]; then
    echo "Cannot generate $key because Docker volume $postgres_volume already exists." >&2
    echo "Rotate the PostgreSQL role password first, then update $ENV_FILE with the same value." >&2
    database_secret_blocked=true
    return
  fi
  write_env_value "$key" "$(openssl rand -base64 "$bytes" | tr -d '\r\n')"
  echo "Generated $key"
}

ensure_random_secret DB_PASS 32 true
ensure_random_secret REDIS_PASS 32
ensure_random_secret JWT_SECRET 32
ensure_random_secret MODEL_KEY_ENCRYPTION_KEY_BASE64 32
ensure_random_secret RATE_LIMIT_KEY_SECRET 32
ensure_random_secret BOOTSTRAP_ADMIN_PASSWORD 24

disable_optional_bootstrap_if_placeholder() {
  local prefix="$1"
  local api_key
  api_key="$(read_env_value "${prefix}_API_KEY")"
  if [[ "$api_key" == "<"*">" ]]; then
    write_env_value "${prefix}_PROVIDER" ""
    write_env_value "${prefix}_BASE_URL" ""
    write_env_value "${prefix}_API_KEY" ""
    write_env_value "${prefix}_MODEL_NAME" ""
    echo "Disabled optional ${prefix#BOOTSTRAP_} bootstrap configuration; fill all four values to enable it"
  fi
}

disable_optional_bootstrap_if_placeholder BOOTSTRAP_CHAT
disable_optional_bootstrap_if_placeholder BOOTSTRAP_EMBEDDING

manual_keys="$(awk -F '=' '
  $0 ~ "^[[:space:]]*#" || $0 !~ "=" { next }
  {
    key=$1
    value=$0
    sub(/^[^=]*=/, "", value)
    gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
    if (value ~ /^<.*>$/ || value ~ /example\.com/ || value ~ /OWNER\/REPO/) print key
  }
' "$ENV_FILE")"

echo
echo "Internal secret initialization finished."
if [ -n "$manual_keys" ]; then
  echo "Review these external or environment-specific values before deployment:"
  while IFS= read -r key; do
    [ -n "$key" ] && echo "  - $key"
  done <<< "$manual_keys"
fi
echo "Install TLS files at CERT_DIR/origin.pem and CERT_DIR/origin.key (CERT_DIR defaults to ./certs)."
echo "Secrets were saved to $DEPLOY_PATH/$ENV_FILE with mode 600 and were not printed."

if [ "$database_secret_blocked" = true ]; then
  exit 1
fi
