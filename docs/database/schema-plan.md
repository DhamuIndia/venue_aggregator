# Database Schema Plan

Use PostgreSQL as the source of truth for marketplace data.

## Core Tables

- `users`
- `roles`
- `user_roles`
- `halls`
- `hall_pricing`
- `hall_amenities`
- `hall_media`
- `hall_blocked_dates`
- `bookings`
- `vendors`
- `vendor_categories`
- `vendor_media`
- `subscription_plans`
- `vendor_subscriptions`
- `payments`
- `razorpay_events`
- `enquiries`
- `reviews`
- `locations`

## Booking Rule

For each hall, event date, and slot type, only one confirmed booking should exist.

Slot types:

- `MORNING`
- `EVENING`
- `FULL_DAY`

The first baseline migration includes a partial unique index for confirmed bookings:

```sql
create unique index uq_bookings_confirmed_hall_date_slot
    on bookings(hall_id, event_date, slot_type)
    where status = 'CONFIRMED';
```

## Media Rule

Do not store images or videos inside PostgreSQL. Store files in S3-compatible object storage, using MinIO on Hetzner/local development and AWS S3 if the app moves to AWS.

Store only portable metadata in PostgreSQL:

- `storage_key`
- `public_url`
- `media_type`
- `caption`
- `is_cover`
- `sort_order`
- ownership fields such as `hall_id`, `vendor_id`, and `uploaded_by`

Avoid provider-specific IDs in business flows. A media migration should only need to copy object keys to a new bucket and update `public_url` if the CDN/base URL changes.
