# WhatsApp Delivery Tracking and Retries

Phase 4 receives Meta WhatsApp status webhooks, records the delivery lifecycle,
and retries only failures that are safe to retry.

## Callback

Configure the Meta app's WhatsApp callback as:

```text
https://api.bookvenuemart.in/v1/integrations/meta/whatsapp/webhook
```

Subscribe the WhatsApp Business Account to the `messages` webhook field.

The same URL supports:

- Meta's `GET` verification request using `hub.mode`, `hub.verify_token`, and
  `hub.challenge`; and
- signed `POST` event notifications.

VenueMart verifies every POST against the exact raw request bytes using the
Meta app secret and the `X-Hub-Signature-256` HMAC-SHA256 header. Invalid or
unsigned requests are rejected before parsing.

## Configuration

Webhook receipt and message sending have separate safety switches:

```text
WHATSAPP_WEBHOOK_ENABLED=false
WHATSAPP_WEBHOOK_VERIFY_TOKEN=
WHATSAPP_APP_SECRET=

WHATSAPP_SENDING_ENABLED=false
WHATSAPP_ROLLOUT_ALLOWED_VENDOR_IDS=
WHATSAPP_MAX_ATTEMPTS=3
WHATSAPP_RETRY_INITIAL_DELAY_MS=60000
WHATSAPP_RETRY_MAX_DELAY_MS=3600000
```

The webhook can be enabled for Meta verification while sending remains off.
Retries are not dispatched unless `WHATSAPP_SENDING_ENABLED=true` and the
vendor remains in `WHATSAPP_ROLLOUT_ALLOWED_VENDOR_IDS`.

## Tracked states

The vendor-notification lifecycle is:

```text
QUEUED -> SENT -> DELIVERED -> READ
                    \
                     -> FAILED
```

`PROCESSING` and `CANCELLED` are internal safety states. A successful Cloud API
response records `SENT` and the returned `wamid`. Meta webhooks then advance the
same attempt to `DELIVERED` or `READ`.

State changes are monotonic:

- duplicate webhook events are idempotent;
- `SENT` cannot replace `DELIVERED` or `READ`;
- `FAILED` cannot replace `DELIVERED` or `READ`; and
- a late event for an older attempt cannot change the current attempt.

## Failure records

VenueMart stores, on both the lead notification and its individual attempt:

- Meta error code;
- error title;
- detailed failure reason;
- whether the failure is temporary; and
- failure timestamp.

Each attempt has a unique `(job_id, attempt_number)` pair and each Meta message
ID is unique. This retains the complete history when a retry receives a new
`wamid`.

## Retry rules

Temporary failures use bounded exponential backoff. The default is at most
three total attempts, beginning after one minute and capped at one hour.

A retry is scheduled only when:

- Meta explicitly marks the error transient, or its error code is in the
  conservative temporary-error allowlist;
- the maximum attempt count has not been reached;
- the vendor still has WhatsApp lead notifications enabled;
- notifications are not paused;
- consent still exists;
- the vendor has not opted out; and
- the consented destination still matches the queued destination.

Unknown, malformed, permanent, and ambiguous network failures are never
automatically retried. A network timeout may occur after Meta accepted a
message, so treating it as retryable could send a duplicate.

Before an actual retry, VenueMart checks the vendor preference again. If the
vendor opted out or changed the number after the retry was scheduled, the
attempt is cancelled without calling Meta.

Administrators can inspect the requirement-level funnel, failure details and
attempt history, and schedule a safe manual retry. See
[admin lead notification monitoring](admin-lead-notification-monitoring.md).

## Duplicate-send protection

Ready jobs are claimed with a database row lock and moved to `PROCESSING` before
the network call. A unique persisted attempt is created in the same transaction.
Only `FAILED` jobs with an explicit future retry time can be claimed again.

If the application stops after calling Meta but before recording the response,
the attempt remains `PROCESSING` and is not automatically retried. This favors
avoiding duplicate vendor messages; such an ambiguous attempt requires manual
review.
