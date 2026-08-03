# WhatsApp Controlled Rollout

Phase 7A adds a second, fail-closed production safety gate around WhatsApp
delivery. A vendor must pass both gates before VenueMart can submit a message:

1. the vendor has an active, unpaused notification subscription with valid
   consent; and
2. the vendor's database id is present in the rollout allowlist.

The global sending switch remains an independent emergency kill switch.

## Default production state

```text
WHATSAPP_SENDING_ENABLED=false
WHATSAPP_ROLLOUT_ALLOWED_VENDOR_IDS=
WHATSAPP_WEBHOOK_ENABLED=false
```

An empty `WHATSAPP_ROLLOUT_ALLOWED_VENDOR_IDS` value blocks every vendor. This
remains true even if `WHATSAPP_SENDING_ENABLED` is accidentally changed to
`true`.

With an empty allowlist:

- queued and retry-ready jobs remain unchanged;
- no job enters `PROCESSING`;
- no attempt record is created;
- no manual retry can be scheduled; and
- no request is made to Meta.

VenueMart currently has no official pilot vendors, so the production allowlist
must remain empty.

## Adding a pilot vendor later

Only add a vendor after confirming its database id, WhatsApp destination, and
recorded consent. Configure one or more comma-separated ids:

```text
WHATSAPP_ROLLOUT_ALLOWED_VENDOR_IDS=101,205
```

These are vendor database ids, not hall ids, user ids, phone numbers, or lead
ids. Never put WhatsApp phone numbers in this setting.

Changing the allowlist requires applying the environment configuration through
the normal deployment process. The Admin lead-notification page shows the
effective allowlist and marks each vendor as `Pilot allowlisted` or
`Outside pilot`.

## Dispatch enforcement

The queue query selects only jobs whose vendor id is allowlisted. A second
check runs immediately before the Meta API call. This protects against an
unexpected candidate or a rollout configuration change during processing.

Retries use the same gate. A failed notification for a vendor outside the
allowlist cannot be retried from Admin and cannot be claimed automatically.

## First live test checklist

1. Keep global sending disabled.
2. Create or select the controlled internal test-vendor account.
3. Record its explicit notification consent and verify its WhatsApp number.
4. Put only that vendor id in the rollout allowlist.
5. Verify the effective allowlist in Admin.
6. Configure and verify the signed Meta webhook.
7. Confirm the approved template and dynamic lead-button URL.
8. Enable sending for the test window.
9. Post one clearly identified test requirement.
10. Verify exactly one send, delivery tracking, login, and lead ownership.
11. Disable sending immediately if any unexpected recipient or duplicate is
    observed.

## Emergency rollback

Set:

```text
WHATSAPP_SENDING_ENABLED=false
```

This stops new queue claims. The webhook may remain enabled so VenueMart can
continue receiving final delivery updates for messages already submitted.
Removing every id from `WHATSAPP_ROLLOUT_ALLOWED_VENDOR_IDS` provides a second
independent block.
