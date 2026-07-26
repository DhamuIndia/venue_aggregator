# Lead Notification Queue

Phase 2 creates durable WhatsApp notification jobs for newly matched marketplace
vendor leads. It does not call Meta or send messages.

## Flow

1. The customer requirement is saved.
2. Matching vendor leads are saved.
3. A lead-created event is registered inside the requirement transaction.
4. After that transaction commits, a separate transaction evaluates the vendor's
   current notification preference.
5. One `QUEUED` job is created when the vendor is subscribed and not paused.

The after-commit boundary ensures a queue failure cannot roll back a saved
customer requirement or vendor lead.

## Eligibility

A WhatsApp job is queued only when:

- the lead belongs to a marketplace requirement;
- the vendor preference exists;
- WhatsApp lead alerts are enabled;
- alerts are not paused;
- the destination number and consent record are complete; and
- the vendor has not opted out.

## Idempotency

The database allows only one job for a given combination of vendor lead,
channel and notification type:

```text
(vendor_lead_id, WHATSAPP, LEAD_MATCHED)
```

Repeated matching or event processing therefore cannot create a second job.

## Frozen template values

Each queue job stores the values needed by the logical
`NEW_MATCHING_LEAD` template:

- vendor name;
- service;
- event type;
- formatted event date;
- location;
- formatted budget; and
- opted-in WhatsApp destination number.

Customer name, phone and email are deliberately excluded. Phase 3 will map the
logical template key to the final Meta template name and send only after checking
the vendor's current preference again.
