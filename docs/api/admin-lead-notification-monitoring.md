# Admin Lead Notification Monitoring

Phase 6 gives administrators a requirement-level view of vendor matching and
WhatsApp delivery.

## Admin endpoints

```http
GET /api/v1/admin/requirements/notification-monitoring
GET /api/v1/admin/requirements/{requirementId}/notification-monitoring
POST /api/v1/admin/notification-jobs/{jobId}/retry
Authorization: Bearer <admin-access-token>
```

Only active `ADMIN` and `SUPER_ADMIN` users can use these endpoints.

## Requirement summary

For every customer requirement, VenueMart reports:

- vendors matched;
- vendors subscribed when the notification decision was made;
- skipped and queued notifications;
- cumulative sent, delivered and read counts;
- current failed count; and
- whether WhatsApp sending is currently enabled; and
- the effective controlled-rollout vendor allowlist.

Sent, delivered and read are cumulative funnel counts. A notification that was
read is also included in delivered and sent.

## Vendor delivery detail

Each matched vendor row shows:

- the opaque lead reference;
- subscription status at evaluation and current eligibility;
- whether the vendor is included in the controlled rollout;
- exact future skip outcomes;
- job status and masked WhatsApp destination;
- failure code, title, reason and temporary/permanent classification;
- every persisted attempt in attempt-number order; and
- whether a manual retry is allowed.

Customer phone numbers and email addresses are not returned by the monitoring
API.

## Historical skipped decisions

Before Phase 6, VenueMart stored queued jobs but not skipped queue decisions.
Migration `V34__lead_notification_monitoring.sql` creates monitoring records for
all existing marketplace leads:

- existing notification jobs are marked as historically queued; and
- existing leads without a job are marked `LEGACY_NOT_RECORDED`.

Future matching stores the exact reason: not subscribed, paused, or invalid
consent. The migration inserts into the new monitoring table only; it does not
update vendor, hall, customer, requirement, lead, job, or attempt rows.

## Manual retry rules

Manual retry schedules the existing failed job for the next worker batch. It
does not call Meta from the admin HTTP request and does not create an attempt
until the queue worker safely claims the job.

A manual retry is allowed only when:

- the current job status is `FAILED`;
- the failure is explicitly temporary;
- the configured maximum attempt count has not been reached;
- the vendor remains in the controlled rollout allowlist;
- the vendor remains subscribed and not paused;
- consent has not been withdrawn;
- the destination still matches the consented WhatsApp number; and
- another retry is not already ready for dispatch.

Permanent failures and opted-out vendors can never be manually retried. Every
manual retry is written to the audit log. If
`WHATSAPP_SENDING_ENABLED=false`, the retry remains scheduled and no message is
sent.
