# WhatsApp Cloud API Submission

Phase 3 submits queued `NEW_MATCHING_LEAD` jobs through Meta's official WhatsApp
Cloud API. Phase 4 adds signed delivery webhooks and controlled retries. Sending
remains disabled by default.

## Safety switch

The application and both production compose files default to:

```text
WHATSAPP_SENDING_ENABLED=false
WHATSAPP_ROLLOUT_ALLOWED_VENDOR_IDS=
```

When disabled:

- the scheduled queue worker is not created;
- no job is claimed or changed;
- no HTTP request is made to Meta; and
- Meta credentials are not required.

The empty rollout allowlist is a second fail-closed gate. Even when global
sending is enabled, no job is claimed and no Meta request is made until an
explicitly consented vendor id is allowlisted. See
[WhatsApp controlled rollout](whatsapp-controlled-rollout.md).

## Required configuration

Before explicitly enabling sending, configure:

```text
WHATSAPP_GRAPH_API_VERSION=<approved current Graph API version>
WHATSAPP_PHONE_NUMBER_ID=<VenueMart WhatsApp phone number id>
WHATSAPP_ACCESS_TOKEN=<system-user access token>
WHATSAPP_LEAD_TEMPLATE_NAME=<approved Meta template name>
WHATSAPP_LEAD_TEMPLATE_LANGUAGE=en
WHATSAPP_LEAD_TEMPLATE_URL_BUTTON_INDEX=0
WHATSAPP_ROLLOUT_ALLOWED_VENDOR_IDS=<comma-separated consented pilot vendor ids>
```

The Graph API version is intentionally not hard-coded because Meta versions it
independently of VenueMart.

## Meta request

The sender calls:

```http
POST https://graph.facebook.com/{version}/{phone-number-id}/messages
Authorization: Bearer <access-token>
Content-Type: application/json
```

The template body parameters are sent in this exact order:

1. vendor name;
2. service;
3. event date;
4. location; and
5. budget.

The approved template must contain a dynamic website button at the configured
index with this URL:

```text
https://bookvenuemart.in/vendor/leads/{{1}}
```

The sender adds the opaque `LEAD-...` reference as the URL button's dynamic
suffix. It never sends a customer name, phone number, email address, requirement
ID, vendor ID, or numeric database lead ID in that URL. See
[direct vendor lead links](direct-vendor-lead-links.md) for the access-control
contract.

## Consent recheck

Immediately before the Meta call, VenueMart confirms that:

- the vendor is still in the controlled rollout allowlist;
- lead notifications remain enabled;
- notifications are not paused;
- consent remains present;
- the vendor has not opted out; and
- the queued destination still equals the currently consented number.

If any check fails, the job becomes `CANCELLED` and no Meta call is made.

## Delivery states

- `QUEUED`: waiting while sending is disabled or for the next enabled batch.
- `PROCESSING`: safely claimed by one worker.
- `SENT`: Meta accepted the request and returned a message ID.
- `DELIVERED`: Meta says the message reached the vendor.
- `READ`: Meta says the vendor read the message.
- `FAILED`: submission or delivery failed; failure details and retry eligibility
  are recorded.
- `CANCELLED`: consent or destination was no longer valid before sending.

Meta's returned `wamid...` value is stored in `provider_message_id`.
Every initial send and retry also has an immutable attempt number and its own
provider message ID.

See [WhatsApp delivery tracking and retries](whatsapp-delivery-tracking.md) for
the webhook and retry contract.
