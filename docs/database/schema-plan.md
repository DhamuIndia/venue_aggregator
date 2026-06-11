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

Do not store images or videos inside PostgreSQL. Store files in Cloudinary or S3, and store only URL, public ID, media type, sort order, and ownership metadata in the database.
