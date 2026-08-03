# Direct Vendor Lead Links

Phase 5 lets a subscribed vendor open the relevant VenueMart lead from the
WhatsApp notification.

## Link contract

The approved Meta template's website button URL must be:

```text
https://bookvenuemart.in/vendor/leads/{{1}}
```

VenueMart supplies only an opaque reference such as:

```text
LEAD-0123456789ABCDEF0123
```

The resulting link contains no customer name, phone number, email address,
requirement ID, vendor ID, or numeric database lead ID.

## Authorization

The reference is a locator, not an authentication token.

1. The frontend route sends a signed-out visitor to the VenueMart login page.
2. After login, only a `VENDOR` account may use the backend endpoint.
3. The backend resolves the reference together with the authenticated vendor ID.
4. A missing lead and a lead assigned to a different vendor both return the same
   `404 Lead not found` response.
5. Customer details are returned only through the authenticated response and
   retain the existing contact-privacy rules.

The vendor endpoint is:

```http
GET /api/v1/vendor/leads/reference/{leadReference}
Authorization: Bearer <vendor-access-token>
```

## Existing data

Migration `V33__secure_vendor_lead_references.sql` adds a random reference to
every existing vendor lead and copies that reference to existing notification
jobs. It does not modify vendor, hall, customer, or requirement business data.

WhatsApp sending remains disabled unless `WHATSAPP_SENDING_ENABLED=true`.
