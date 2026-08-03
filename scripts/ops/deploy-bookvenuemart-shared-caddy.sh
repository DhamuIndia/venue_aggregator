#!/usr/bin/env bash
set -euo pipefail

SERVER="${SERVER:-algo@138.199.208.60}"
REMOTE_DIR="${REMOTE_DIR:-/home/algo/bookvenuemart}"
REPO_URL="${REPO_URL:-https://github.com/DhamuIndia/venue_aggregator.git}"
BRANCH="${BRANCH:-main}"
ENV_FILE="infra/hetzner/.env.prod"
COMPOSE_FILE="infra/hetzner/docker-compose.shared-caddy.yml"
CADDY_FILE="${CADDY_FILE:-/home/algo/social-crm/ops/Caddyfile}"
CADDY_CONTAINER="${CADDY_CONTAINER:-social-crm-caddy}"
CADDY_MARKER="# bookvenuemart shared caddy routes"

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
  cp infra/hetzner/.env.prod.example '$ENV_FILE'
  echo 'Created $REMOTE_DIR/$ENV_FILE. Fill secrets, then rerun this script.'
  exit 2
fi

docker compose -f '$COMPOSE_FILE' --env-file '$ENV_FILE' up -d --build

if ! docker inspect '$CADDY_CONTAINER' --format '{{json .NetworkSettings.Networks}}' | grep -q 'bookvenuemart_internal'; then
  docker network connect bookvenuemart_internal '$CADDY_CONTAINER'
fi

if ! grep -q '$CADDY_MARKER' '$CADDY_FILE'; then
  {
    printf '\\n%s\\n' '$CADDY_MARKER'
    cat infra/hetzner/Caddyfile.bookvenuemart.shared
  } >> '$CADDY_FILE'
fi

docker exec '$CADDY_CONTAINER' caddy validate --config /etc/caddy/Caddyfile
docker exec '$CADDY_CONTAINER' caddy reload --config /etc/caddy/Caddyfile
docker compose -f '$COMPOSE_FILE' --env-file '$ENV_FILE' ps
"
