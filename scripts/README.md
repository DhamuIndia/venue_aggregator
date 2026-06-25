# Scripts

Place local developer scripts here as the project grows.

Suggested future scripts:

- `dev-up`: start PostgreSQL, backend, and frontend
- `db-reset`: reset the local database
- `seed-dev-data`: insert sample halls, vendors, and enquiries
- `generate-openapi-client`: generate frontend API types from OpenAPI

Current ops scripts:

- `ops/backup-postgres.sh`: create a portable PostgreSQL custom-format dump
- `ops/restore-postgres.sh`: restore a dump into another PostgreSQL database
- `ops/sync-media-rclone.sh`: copy or sync S3-compatible media between providers
- `ops/deploy-bookvenuemart-hetzner.sh`: deploy the production compose stack to `/home/algo/bookvenuemart`
