#!/usr/bin/env bash
set -euo pipefail

SERVER="${SERVER:-algo@138.199.208.60}"
REMOTE_DIR="${REMOTE_DIR:-/home/algo/bookvenuemart-preprod}"
ENV_FILE="infra/hetzner/.env.preprod"
COMPOSE_FILE="infra/hetzner/docker-compose.preprod-shared.yml"

ssh "$SERVER" "
set -euo pipefail
cd '$REMOTE_DIR'
docker compose -f '$COMPOSE_FILE' --env-file '$ENV_FILE' stop frontend backend
docker compose -f '$COMPOSE_FILE' --env-file '$ENV_FILE' ps
"

echo 'Preprod application containers are stopped. Shared production services were not changed.'
