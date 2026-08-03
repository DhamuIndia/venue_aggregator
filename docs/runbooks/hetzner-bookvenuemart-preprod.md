# VenueMart Preprod on the Existing Hetzner Host

This design runs a small, on-demand preprod application stack from `develop`
without copying or exposing production customer data.

## Isolation model

Preprod has its own:

- repository directory: `/home/algo/bookvenuemart-preprod`;
- Docker project and frontend/backend containers;
- PostgreSQL database, login, password, and Flyway history;
- MinIO bucket and bucket-restricted access key;
- JWT secret, test users, and application data; and
- frontend, API, and media domains.

It shares the existing PostgreSQL and MinIO *processes* to conserve memory, but
it does not share the production database, database login, bucket, or media
credentials. The preprod backend is the only preprod container connected to the
production-services Docker network.

WhatsApp sending and webhooks are hard-disabled in the compose file. Meta
credentials are not passed to preprod.

## Resource safeguards

The server currently has limited free RAM, so preprod is intentionally
on-demand:

- frontend limit: 192 MiB and 0.50 CPU;
- backend limit: 512 MiB and 0.75 CPU;
- backend Java heap: 256 MiB;
- no preprod PostgreSQL or MinIO containers; and
- no automatic restart after a server reboot.

The deploy script refuses to build unless the server has at least 2 GiB swap,
768 MiB available RAM, and every required production container is running.

## Required DNS

Point these records to `138.199.208.60` before deployment:

```text
preprod.bookvenuemart.in
api-preprod.bookvenuemart.in
media-preprod.bookvenuemart.in
```

## One-time host prerequisite

Adding swap changes the live host and requires explicit production approval.
After approval, create a root-owned 2 GiB swapfile and verify `free -h` reports
it. Do not deploy preprod while production health is degraded.

## Configure secrets

The first deployment attempt creates this file and exits:

```text
/home/algo/bookvenuemart-preprod/infra/hetzner/.env.preprod
```

Replace every placeholder with a new secret. Do not copy production database,
MinIO, or JWT secrets into it.

## Deploy

Preprod always defaults to `develop`:

```bash
scripts/ops/deploy-bookvenuemart-preprod-shared.sh
```

The script creates only the dedicated preprod database/user and media
bucket/user, builds the two capped application containers, backs up the shared
Caddyfile before appending routes, validates Caddy, reloads it, and verifies
both production and preprod API health.

## Stop when testing is finished

```bash
scripts/ops/stop-bookvenuemart-preprod.sh
```

This stops only the preprod frontend and backend. It does not delete the
preprod database or bucket and never runs `docker compose down -v`.

## Promotion rule

Test the exact `develop` commit in preprod. Production remains deployed from
`main` only, and a production deployment still requires explicit approval.
