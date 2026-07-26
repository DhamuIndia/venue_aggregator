# WhatsApp Cloud API Submission

Phase 3 submits queued `NEW_MATCHING_LEAD` jobs through Meta's official WhatsApp
Cloud API. Sending is disabled by default and no webhook or retry behavior is
included in this phase.

## Safety switch

The application and both production compose files default to:

```text
WHATSAPP_SENDING_ENABLED=false
```

When disabled:

- the scheduled queue worker is not created;
- no job is claimed or changed;
- no HTTP request is made to Meta; and
- Meta credentials are not required.

## Required configuration

Before explicitly enabling sending, configure:

```text
WHATSAPP_GRAPH_API_VERSION=<approved current Graph API version>
WHATSAPP_PHONE_NUMBER_ID=<VenueMart WhatsApp phone number id>
WHATSAPP_ACCESS_TOKEN=<system-user access token>
WHATSAPP_LEAD_TEMPLATE_NAME=<approved Meta template name>
WHATSAPP_LEAD_TEMPLATE_LANGUAGE=en
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

The approved template owns the static `https://bookvenuemart.in/vendor` website
button, so the sender does not add a button component.

## Consent recheck

Immediately before the Meta call, VenueMart confirms that:

- lead notifications remain enabled;
- notifications are not paused;
- consent remains present;
- the vendor has not opted out; and
- the queued destination still equals the currently consented number.

If any check fails, the job becomes `CANCELLED` and no Meta call is made.

## Submission states

- `QUEUED`: waiting while sending is disabled or for the next enabled batch.
- `PROCESSING`: safely claimed by one worker.
- `SUBMITTED`: Meta accepted the request and returned a message ID.
- `SEND_FAILED`: the API submission failed; Phase 3 does not automatically retry it.
- `CANCELLED`: consent or destination was no longer valid before sending.

Meta's returned `wamid...` value is stored in `provider_message_id`.
Delivered/read status webhooks and controlled retries belong to Phase 4.
