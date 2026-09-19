# LeadCat WhatsApp callback forwarding

BookVenueMart retains the Meta callback at `/api/v1/integrations/meta/whatsapp/webhook` and its existing delivery processing. After signature verification and existing processing, matching sender delivery receipts and text STOP/UNSUBSCRIBE/CANCEL events are committed to a PostgreSQL outbox before acknowledgement. Ordinary messages, contact profiles and pricing fields are excluded. LeadCat ignores receipts unrelated to its delivery records. Opt-outs apply to LeadCat notification subscriptions for that recipient; this change does not implement BookVenueMart opt-out behavior.

Enable only when LeadCat has the same Meta app signing secret configured:

```
WHATSAPP_LEADCAT_FORWARDING_ENABLED=true
WHATSAPP_LEADCAT_FORWARDING_PHONE_NUMBER_ID=1216271864900770
WHATSAPP_LEADCAT_FORWARDING_URL=https://crm.staminal.in/webhooks/notifications/whatsapp
```

The default is disabled. No access token is used by forwarding. The filtered event is signed using the existing server-side WhatsApp app secret. HTTPS redirects are disabled; connect and total request timeouts are 3 and 5 seconds. One event is claimed per worker tick with PostgreSQL `FOR UPDATE SKIP LOCKED`. Failed requests retry with exponential delay capped at one hour, without a discard limit. A crash can replay an event; the LeadCat receiver is idempotent. Meta retries if durable enqueue fails. Existing BookVenueMart delivery processing is idempotent on callback replay.

The provider routing table is global, not tenant-owned. It contains only the minimal event until successful delivery, then clears the payload immediately. Successful digests expire after seven days; pending events are retained. Do not log or expose payloads. Queue health can be inspected without customer content:

```sql
SELECT count(*) FILTER (WHERE delivered_at IS NULL) AS pending,
       min(created_at) FILTER (WHERE delivered_at IS NULL) AS oldest_pending,
       max(attempts) AS maximum_attempts
FROM whatsapp_webhook_forwards;
```

Failures log only a fixed reason/status and attempt count. Investigate a growing queue or repeated HTTP 403 before enabling LeadCat sending. Disabling forwarding pauses dispatch and new enqueue; pending rows remain.

Production uses an immutable backend image built from the verified production commit plus this change. A backend-only Compose override supplies its image and forwarding configuration. Keep the original env file and Compose project; use `up -d --no-deps --no-build backend`. Never run the broad deployment script or recreate shared Caddy for this feature. Back up PostgreSQL first and retain the previous backend image for rollback. The additive V38 migration can remain on rollback. Future releases must include this branch and preserve the forwarding configuration.

Validation: complete backend suite (245 tests) passed; focused PostgreSQL integration test checks migration, durable deduplication, concurrent claims, delayed retry, successful payload removal and digest expiry. Existing callback security tests verify unsigned events reach neither processor nor forwarder.
