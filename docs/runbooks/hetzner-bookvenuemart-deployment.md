# BookVenueMart Hetzner Deployment Runbook

Use this runbook to deploy Venue Aggregator on the existing Hetzner server without mixing it into another product folder.

## Server Layout

Existing invoice app:

```text
/home/algo/INVOICE_AUTOMATION
```

New Venue app:

```text
/home/algo/bookvenuemart
```

The Venue app gets its own Docker compose project, database volume, MinIO volume, and env file.

If no other app owns ports `80` and `443`, use the standalone compose file. If another app already owns the reverse proxy, use the shared-Caddy compose file.

## Current Server Reality

As of July 2026, the Hetzner server already runs the Social CRM app:

```text
social-crm-live     app container
social-crm-caddy    shared Caddy reverse proxy on ports 80 and 443
```

For this setup, do not start another Caddy container for BookVenueMart. Run BookVenueMart behind the existing CRM Caddy using:

```text
infra/hetzner/docker-compose.shared-caddy.yml
infra/hetzner/Caddyfile.bookvenuemart.shared
scripts/ops/deploy-bookvenuemart-shared-caddy.sh
```

## Domains

DNS records should point to `138.199.208.60`:

```text
bookvenuemart.in
www.bookvenuemart.in
api.bookvenuemart.in
media.bookvenuemart.in
```

Live URLs:

```text
Frontend: https://bookvenuemart.in
API: https://api.bookvenuemart.in
Media: https://media.bookvenuemart.in
```

## First Deployment

SSH into the server:

```bash
ssh algo@138.199.208.60
```

## Retire Invoice Automation

Do this before starting BookVenueMart because the invoice Caddy container currently owns ports `80` and `443`.

Create a lightweight archive of the invoice deployment files:

```bash
cd /home/algo
mkdir -p retired-apps
tar -czf "retired-apps/invoice-automation-files-$(date +%Y%m%d_%H%M%S).tar.gz" INVOICE_AUTOMATION
```

Take database and media backups if you may need the old invoice data later. Then stop the invoice containers without deleting Docker volumes:

```bash
cd /home/algo/INVOICE_AUTOMATION
docker compose -f docker-compose.production.yml --env-file .env.production down
```

Check ports are free:

```bash
docker ps --format 'table {{.Names}}\t{{.Ports}}'
```

Do not run `docker compose down -v` unless the invoice data has been backed up and you are intentionally deleting the old PostgreSQL and MinIO volumes.

After BookVenueMart has been accepted in production, remove the old invoice app files:

```bash
cd /home/algo
mv INVOICE_AUTOMATION retired-apps/INVOICE_AUTOMATION.retired.$(date +%Y%m%d_%H%M%S)
```

Keep the archive for a few days. Delete old Docker volumes only after confirming invoice data is no longer needed:

```bash
docker volume ls | grep invoice_automation
```

Clone the repo into the new folder:

```bash
git clone https://github.com/DhamuIndia/venue_aggregator.git /home/algo/bookvenuemart
cd /home/algo/bookvenuemart
```

Create the production env file:

```bash
cp infra/hetzner/.env.prod.example infra/hetzner/.env.prod
nano infra/hetzner/.env.prod
```

Generate strong secrets:

```bash
openssl rand -base64 48
```

Replace these values in `infra/hetzner/.env.prod`:

```text
ACME_EMAIL
POSTGRES_PASSWORD
MINIO_ROOT_PASSWORD
JWT_SECRET
```

Start the Venue services when BookVenueMart owns ports `80` and `443`:

```bash
docker compose -f infra/hetzner/docker-compose.prod.yml --env-file infra/hetzner/.env.prod up -d --build
```

## Deploy Beside Existing CRM

Use this path on the current server because `social-crm-caddy` already owns ports `80` and `443`.

From your local machine:

```bash
cd /Users/dhamodharanr/Documents/VENUE_AGGREGATOR
BRANCH=main scripts/ops/deploy-bookvenuemart-shared-caddy.sh
```

What the script does:

- Clones or updates `/home/algo/bookvenuemart`.
- Starts Venue containers with `docker-compose.shared-caddy.yml`.
- Keeps Venue PostgreSQL and MinIO data separate from CRM.
- Connects `social-crm-caddy` to the `bookvenuemart_internal` Docker network.
- Appends the BookVenueMart domain routes to `/home/algo/social-crm/ops/Caddyfile`.
- Validates and reloads Caddy.

If the script creates `infra/hetzner/.env.prod` and exits, SSH into the server and fill secrets:

```bash
ssh algo@138.199.208.60
cd /home/algo/bookvenuemart
nano infra/hetzner/.env.prod
```

Generate secrets on the server:

```bash
openssl rand -base64 48
```

Then rerun:

```bash
BRANCH=main scripts/ops/deploy-bookvenuemart-shared-caddy.sh
```

## Smoke Test

```bash
docker compose -f /home/algo/bookvenuemart/infra/hetzner/docker-compose.prod.yml --env-file /home/algo/bookvenuemart/infra/hetzner/.env.prod ps
curl -I https://bookvenuemart.in
curl -sS https://api.bookvenuemart.in/api/actuator/health
curl -I https://media.bookvenuemart.in/venue-media/
```

For the shared CRM server, use:

```bash
docker compose -f /home/algo/bookvenuemart/infra/hetzner/docker-compose.shared-caddy.yml --env-file /home/algo/bookvenuemart/infra/hetzner/.env.prod ps
curl -I https://bookvenuemart.in
curl -sS https://api.bookvenuemart.in/api/actuator/health
curl -I https://media.bookvenuemart.in/venue-media/
```

Expected:

- Frontend returns `200` or `307`.
- Backend health returns JSON.
- Media domain is reachable.

## Deploy Updates

```bash
cd /home/algo/bookvenuemart
git pull --ff-only
docker compose -f infra/hetzner/docker-compose.prod.yml --env-file infra/hetzner/.env.prod up -d --build
```

For the shared CRM server:

```bash
cd /home/algo/bookvenuemart
git pull --ff-only
docker compose -f infra/hetzner/docker-compose.shared-caddy.yml --env-file infra/hetzner/.env.prod up -d --build
docker network connect bookvenuemart_internal social-crm-caddy 2>/dev/null || true
docker exec social-crm-caddy caddy reload --config /etc/caddy/Caddyfile
```

## Logs

```bash
cd /home/algo/bookvenuemart
docker compose -f infra/hetzner/docker-compose.prod.yml --env-file infra/hetzner/.env.prod logs -f frontend
docker compose -f infra/hetzner/docker-compose.prod.yml --env-file infra/hetzner/.env.prod logs -f backend
docker compose -f infra/hetzner/docker-compose.prod.yml --env-file infra/hetzner/.env.prod logs -f reverse-proxy
docker compose -f infra/hetzner/docker-compose.prod.yml --env-file infra/hetzner/.env.prod logs -f postgres
docker compose -f infra/hetzner/docker-compose.prod.yml --env-file infra/hetzner/.env.prod logs -f minio
```

For the shared CRM server, replace `docker-compose.prod.yml` with `docker-compose.shared-caddy.yml` and check Caddy logs from the CRM compose:

```bash
docker logs -f social-crm-caddy
```

## Restart

```bash
cd /home/algo/bookvenuemart
docker compose -f infra/hetzner/docker-compose.prod.yml --env-file infra/hetzner/.env.prod restart reverse-proxy backend frontend
```

## Backups

PostgreSQL:

```bash
cd /home/algo/bookvenuemart
mkdir -p backups/postgres
docker compose -f infra/hetzner/docker-compose.prod.yml --env-file infra/hetzner/.env.prod exec -T postgres pg_dump -U venue_app -d venue_aggregator -Fc --no-owner --no-acl > "backups/postgres/venue_aggregator_$(date +%Y%m%d_%H%M%S).dump"
```

MinIO media should be backed up with `rclone` or by syncing the `venue-media` bucket to another S3-compatible provider.

## Rollback

Rollback code:

```bash
cd /home/algo/bookvenuemart
git log --oneline -5
git checkout <previous-commit-sha>
docker compose -f infra/hetzner/docker-compose.prod.yml --env-file infra/hetzner/.env.prod up -d --build
```

Rollback to the retired invoice app:

```bash
cd /home/algo/bookvenuemart
docker compose -f infra/hetzner/docker-compose.prod.yml --env-file infra/hetzner/.env.prod down
cd /home/algo/INVOICE_AUTOMATION
docker compose -f docker-compose.production.yml --env-file .env.production up -d
```
