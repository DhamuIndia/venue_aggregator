# Hosting And Data Portability Runbook

This runbook keeps the Venue Aggregator deployable on Hetzner today and movable to AWS ECS/RDS/S3 later.

## Target Architecture

Today on Hetzner:

- Spring Boot backend container
- Next.js frontend container
- PostgreSQL database
- MinIO S3-compatible object storage
- Reverse proxy with HTTPS

Later on AWS:

- Spring Boot backend on ECS
- Frontend on ECS, Vercel, or Amplify
- PostgreSQL on RDS
- Media on S3
- Secrets in AWS Secrets Manager or Parameter Store

ECS runs containers only. Persistent data must stay outside ECS tasks.

## Data Ownership

PostgreSQL stores:

- users and roles
- halls and hall metadata
- vendors and vendor metadata
- bookings, enquiries, reviews, payments, subscriptions
- media metadata such as `storage_key`, `public_url`, `caption`, `is_cover`, and `sort_order`

Object storage stores:

- hall images and videos
- vendor portfolio media
- future invoices, documents, and generated assets if required

Do not store media files in PostgreSQL or inside application container folders.

## Environment Contract

Backend variables:

```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/venue_aggregator
DATABASE_USERNAME=venue_app
DATABASE_PASSWORD=venue_app_password
STORAGE_PROVIDER=s3
S3_ENDPOINT=http://localhost:9000
S3_PRESIGN_ENDPOINT=http://localhost:9000
S3_REGION=us-east-1
S3_BUCKET=venue-media
S3_ACCESS_KEY=venue_minio
S3_SECRET_KEY=venue_minio_password
S3_PATH_STYLE_ACCESS=true
S3_PUBLIC_BASE_URL=http://localhost:9000/venue-media
MEDIA_MAX_UPLOAD_SIZE_BYTES=10485760
```

Provider-specific values change by environment, but endpoint behavior should not.

| Environment | `S3_ENDPOINT` | `S3_PRESIGN_ENDPOINT` | `S3_BUCKET` | `S3_PATH_STYLE_ACCESS` | `S3_PUBLIC_BASE_URL` |
| --- | --- | --- | --- | --- | --- |
| Local | `http://localhost:9000` | `http://localhost:9000` | `venue-media` | `true` | `http://localhost:9000/venue-media` |
| Hetzner | `http://minio:9000` | `https://media.bookvenuemart.in` | `venue-media` | `true` | `https://media.bookvenuemart.in/venue-media` |
| AWS | AWS S3 endpoint or blank if SDK default is used | AWS S3 or CloudFront upload endpoint | production bucket name | `false` | CloudFront or S3 public base URL |

## Local Storage

Start PostgreSQL and MinIO:

```bash
cd /Users/dhamodharanr/Documents/VENUE_AGGREGATOR/infra
docker compose up -d postgres minio minio-init
```

MinIO console:

```text
http://localhost:9001
```

Default local credentials:

```text
User: venue_minio
Password: venue_minio_password
Bucket: venue-media
```

## Backup Commands

PostgreSQL backup:

```bash
cd /Users/dhamodharanr/Documents/VENUE_AGGREGATOR
PGHOST=138.199.208.60 PGPORT=5432 PGDATABASE=venue_aggregator PGUSER=venue_app PGPASSWORD='password' ./scripts/ops/backup-postgres.sh
```

Media copy:

```bash
SOURCE_REMOTE=hetzner-minio:venue-media TARGET_REMOTE=aws-s3:venue-media DRY_RUN=yes ./scripts/ops/sync-media-rclone.sh
```

Change `DRY_RUN=no` after reviewing the rclone plan.

## Hetzner To AWS Migration

1. Freeze writes for a short maintenance window.
2. Run a final PostgreSQL backup from Hetzner.
3. Restore the backup into AWS RDS PostgreSQL.
4. Copy media from MinIO to S3 with rclone.
5. Update backend environment variables to point to RDS and S3.
6. Deploy backend and frontend containers.
7. Run smoke tests for login, search, hall detail, media, enquiry, owner, vendor, and admin flows.
8. Switch DNS to the new frontend and API.
9. Keep the Hetzner database and MinIO bucket read-only until the AWS deployment is accepted.

## Restore To AWS RDS

Example:

```bash
CONFIRM_RESTORE=yes \
PGHOST=aws-rds-endpoint \
PGPORT=5432 \
PGDATABASE=venue_aggregator \
PGUSER=venue_app \
PGPASSWORD='password' \
./scripts/ops/restore-postgres.sh ./backups/postgres/venue_aggregator_20260625_101500.dump
```

Use `pg_restore --list` on the dump before restoring if you need to inspect contents.

## Media Migration Rules

- Preserve object keys during migration.
- Use bucket paths like `halls/{hallId}/...` and `vendors/{vendorId}/...`.
- If only the domain changes, update `S3_PUBLIC_BASE_URL`.
- If stored `public_url` values must change, run a DB migration that rewrites the base URL only.
- Do not use storage provider IDs as business IDs.

## Validation Checklist

- `GET /api/actuator/health` returns healthy.
- Login works for customer, owner, vendor, and admin test users.
- Public hall and vendor images load from the configured public base URL.
- Owner hall media upload creates an object and saves metadata.
- Vendor portfolio upload creates an object and saves metadata.
- Bookings, enquiries, reviews, and payments survive the DB restore.
- Flyway has no pending failed migration.
- Backups exist before and after the move.

## Minimum Production Rules

- Take daily PostgreSQL backups.
- Take daily object storage sync or snapshot backups.
- Keep secrets outside Git.
- Use Flyway for every schema change.
- Keep Docker images stateless.
- Never manually edit production data without a backup.
