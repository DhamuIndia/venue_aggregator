#!/usr/bin/env bash
set -euo pipefail

SERVER="${SERVER:-algo@138.199.208.60}"
REMOTE_DIR="${REMOTE_DIR:-/home/algo/bookvenuemart-preprod}"
PRODUCTION_DIR="${PRODUCTION_DIR:-/home/algo/bookvenuemart}"
REPO_URL="${REPO_URL:-https://github.com/DhamuIndia/venue_aggregator.git}"
BRANCH="${BRANCH:-develop}"
ENV_FILE="infra/hetzner/.env.preprod"
ENV_EXAMPLE="infra/hetzner/.env.preprod.example"
PRODUCTION_ENV_FILE="${PRODUCTION_DIR}/infra/hetzner/.env.prod"
COMPOSE_FILE="infra/hetzner/docker-compose.preprod-shared.yml"
CADDY_FILE="${CADDY_FILE:-/home/algo/social-crm/ops/Caddyfile}"
CADDY_CONTAINER="${CADDY_CONTAINER:-social-crm-caddy}"
CADDY_MARKER="# bookvenuemart preprod shared caddy routes"

if ! curl -fsS https://api.bookvenuemart.in/api/actuator/health >/dev/null; then
  echo 'Production API health is failing; refusing to touch the host.' >&2
  exit 1
fi

ssh "$SERVER" "mkdir -p '$REMOTE_DIR'"

ssh "$SERVER" "
set -euo pipefail
if [ -d '$REMOTE_DIR/.git' ]; then
  cd '$REMOTE_DIR'
  git fetch origin '$BRANCH'
  git checkout '$BRANCH'
  git pull --ff-only origin '$BRANCH'
else
  git clone --branch '$BRANCH' '$REPO_URL' '$REMOTE_DIR'
fi
"

ssh "$SERVER" "
set -euo pipefail
cd '$REMOTE_DIR'

if [ ! -f '$ENV_FILE' ]; then
  cp '$ENV_EXAMPLE' '$ENV_FILE'
  chmod 600 '$ENV_FILE'
  echo 'Created $REMOTE_DIR/$ENV_FILE. Fill its secrets, then rerun.'
  exit 2
fi

if grep -q 'replace-with-' '$ENV_FILE'; then
  echo 'Preprod env file still contains placeholder secrets.' >&2
  exit 2
fi

if [ ! -f '$PRODUCTION_ENV_FILE' ]; then
  echo 'Production env file was not found; refusing to bootstrap shared services.' >&2
  exit 2
fi

swap_kb=\$(awk '/SwapTotal:/ {print \$2}' /proc/meminfo)
available_kb=\$(awk '/MemAvailable:/ {print \$2}' /proc/meminfo)
if [ \"\$swap_kb\" -lt 2097152 ]; then
  echo 'At least 2 GiB of swap is required before building preprod on this host.' >&2
  exit 3
fi
if [ \"\$available_kb\" -lt 786432 ]; then
  echo 'Less than 768 MiB RAM is currently available; refusing to start a build.' >&2
  exit 3
fi

for container in bookvenuemart-postgres bookvenuemart-minio bookvenuemart-backend bookvenuemart-frontend '$CADDY_CONTAINER'; do
  if [ \"\$(docker inspect -f '{{.State.Running}}' \"\$container\" 2>/dev/null || true)\" != true ]; then
    echo \"Required production container is not running: \$container\" >&2
    exit 4
  fi
done

set -a
source '$PRODUCTION_ENV_FILE'
source '$ENV_FILE'
set +a

if [ \"\$PREPROD_POSTGRES_DB\" = \"\$POSTGRES_DB\" ] || [ \"\$PREPROD_POSTGRES_USER\" = \"\$POSTGRES_USER\" ]; then
  echo 'Preprod database name and user must differ from production.' >&2
  exit 4
fi
if [ \"\$PREPROD_MINIO_BUCKET\" != venue-media-preprod ]; then
  echo 'PREPROD_MINIO_BUCKET must remain venue-media-preprod to match its restricted policy.' >&2
  exit 4
fi

docker exec -i bookvenuemart-postgres psql \
  -v ON_ERROR_STOP=1 \
  -U \"\$POSTGRES_USER\" \
  -d postgres \
  --set=preprod_db=\"\$PREPROD_POSTGRES_DB\" \
  --set=preprod_user=\"\$PREPROD_POSTGRES_USER\" \
  --set=preprod_password=\"\$PREPROD_POSTGRES_PASSWORD\" \
  < infra/hetzner/preprod-db-bootstrap.sql

docker run --rm \
  --network bookvenuemart_internal \
  --env-file '$PRODUCTION_ENV_FILE' \
  --env-file '$REMOTE_DIR/$ENV_FILE' \
  -v '$REMOTE_DIR/infra/hetzner/minio-preprod-policy.json:/tmp/preprod-policy.json:ro' \
  -v '$REMOTE_DIR/infra/hetzner/minio-preprod-cors.json:/tmp/minio-cors.json:ro' \
  --entrypoint /bin/sh \
  minio/mc:latest \
  -c '
    set -eu
    mc alias set shared http://bookvenuemart-minio:9000 \"\$MINIO_ROOT_USER\" \"\$MINIO_ROOT_PASSWORD\"
    mc mb --ignore-existing shared/venue-media-preprod
    mc anonymous set download shared/venue-media-preprod
    mc cors set shared/venue-media-preprod /tmp/minio-cors.json
    mc admin user add shared \"\$PREPROD_MINIO_ACCESS_KEY\" \"\$PREPROD_MINIO_SECRET_KEY\" || true
    mc admin policy create shared bookvenuemart-preprod /tmp/preprod-policy.json || mc admin policy info shared bookvenuemart-preprod >/dev/null
    mc admin policy attach shared bookvenuemart-preprod --user \"\$PREPROD_MINIO_ACCESS_KEY\"
  '

docker compose -f '$COMPOSE_FILE' --env-file '$ENV_FILE' build
docker compose -f '$COMPOSE_FILE' --env-file '$ENV_FILE' up -d

if ! docker inspect '$CADDY_CONTAINER' --format '{{json .NetworkSettings.Networks}}' | grep -q 'bookvenuemart_preprod_internal'; then
  docker network connect bookvenuemart_preprod_internal '$CADDY_CONTAINER'
fi

caddy_backup=''
if ! grep -q '$CADDY_MARKER' '$CADDY_FILE'; then
  caddy_backup=\"$CADDY_FILE.preprod-backup.\$(date +%Y%m%d_%H%M%S)\"
  cp '$CADDY_FILE' \"\$caddy_backup\"
  {
    printf '\\n%s\\n' '$CADDY_MARKER'
    cat infra/hetzner/Caddyfile.bookvenuemart.preprod.shared
  } >> '$CADDY_FILE'
fi

if ! docker exec '$CADDY_CONTAINER' caddy validate --config /etc/caddy/Caddyfile; then
  if [ -n \"\$caddy_backup\" ]; then
    cp \"\$caddy_backup\" '$CADDY_FILE'
  fi
  docker compose -f '$COMPOSE_FILE' --env-file '$ENV_FILE' stop frontend backend
  echo 'Caddy validation failed. Original config restored and preprod stopped.' >&2
  exit 5
fi
docker exec '$CADDY_CONTAINER' caddy reload --config /etc/caddy/Caddyfile
docker compose -f '$COMPOSE_FILE' --env-file '$ENV_FILE' ps
"

curl -fsS https://api.bookvenuemart.in/api/actuator/health >/dev/null
curl -fsS https://api-preprod.bookvenuemart.in/api/actuator/health

echo 'Preprod is healthy and production health still passes.'
