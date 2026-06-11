# Venue Aggregator

Marketplace platform for halls and event-service vendors.

## Stack

- Frontend: Next.js, React, TypeScript, Tailwind CSS, shadcn/ui
- Backend: Spring Boot modular monolith
- Database: PostgreSQL with Flyway migrations
- Media: Cloudinary initially, S3 later if needed
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
