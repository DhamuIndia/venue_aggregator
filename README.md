# Venue Aggregator

Marketplace platform for halls and event-service vendors.

## Stack

- Frontend: Next.js, React, TypeScript, Tailwind CSS, shadcn/ui
- Backend: Spring Boot modular monolith
- Database: PostgreSQL with Flyway migrations
- Media: S3-compatible object storage, MinIO for local/Hetzner and AWS S3 later
- Calendar: FullCalendar
- Payments: Razorpay subscriptions and webhooks

## Repository Layout

```text
apps/
  frontend/     Next.js customer, owner, vendor, and admin web app
  backend/      Spring Boot API and business logic
docs/
  api/          API contracts and endpoint planning
  database/     Schema and ERD planning
  product/      MVP scope, roles, and flows
infra/
  docker/       Docker-related configuration
scripts/        Local automation scripts
```

## Data Portability

The app should stay hosting-portable. PostgreSQL is the system of record for users, halls, vendors, bookings, enquiries, payments, reviews, and metadata. Media files live in S3-compatible object storage; the database stores only keys, public URLs, ownership, captions, cover flags, and sort order.

Start on Hetzner with PostgreSQL and MinIO. If the app later moves to AWS ECS, migrate PostgreSQL to RDS and copy MinIO media to S3 without changing the frontend contract.

Runbook: `docs/runbooks/hosting-data-portability.md`

Hetzner production deployment: `docs/runbooks/hetzner-bookvenuemart-deployment.md`

## Suggested First Milestones

1. Freeze MVP fields for halls, vendors, users, bookings, enquiries, and subscriptions.
2. Finalize the first database ER diagram.
3. Implement backend auth, roles, and JWT.
4. Build the frontend public search shell and dashboard route shells.
5. Add hall onboarding, media upload, approval, availability, and enquiries.
6. Add vendor subscriptions and Razorpay webhooks.

## Local Development

Frontend:

```bash
cd apps/frontend
npm install
npm run dev
```

Backend:

```bash
cd apps/backend
mvn spring-boot:run
```

Database:

```bash
cd infra
docker compose up -d
```

Local object storage:

```bash
cd infra
docker compose up -d minio minio-init
```

MinIO console: `http://localhost:9001`
