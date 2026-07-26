# Vendor Lead Notification Preferences

Phase 1 provides vendor-owned WhatsApp lead-alert preferences and consent storage.
It does not send messages or create notification jobs.

## Read current preferences

```http
GET /api/v1/vendor/notification-preferences
Authorization: Bearer <vendor-access-token>
```

Vendors without a saved preference receive a safe default response:

```json
{
  "vendorId": 501,
  "whatsAppLeadNotificationsEnabled": false,
  "whatsAppLeadNotificationsPaused": false,
  "canReceiveWhatsAppLeadNotifications": false,
  "whatsAppNumber": "9884012346",
  "whatsAppConsentedAt": null,
  "whatsAppConsentSource": null,
  "whatsAppOptedOutAt": null,
  "whatsAppPausedAt": null,
  "updatedAt": null
}
```

The profile phone may be returned as a suggested number, but the vendor is not
subscribed until the update endpoint receives explicit consent.

## Update preferences

```http
PUT /api/v1/vendor/notification-preferences
Authorization: Bearer <vendor-access-token>
Content-Type: application/json
```

Enable:

```json
{
  "whatsAppLeadNotificationsEnabled": true,
  "whatsAppLeadNotificationsPaused": false,
  "whatsAppNumber": "9884012346",
  "consentConfirmed": true
}
```

Pause an existing subscription:

```json
{
  "whatsAppLeadNotificationsEnabled": true,
  "whatsAppLeadNotificationsPaused": true,
  "whatsAppNumber": "+919884012346",
  "consentConfirmed": false
}
```

Opt out:

```json
{
  "whatsAppLeadNotificationsEnabled": false,
  "whatsAppLeadNotificationsPaused": false,
  "whatsAppNumber": "+919884012346",
  "consentConfirmed": false
}
```

## Rules

- Only an authenticated vendor can read or change their own preferences.
- First-time enablement requires `consentConfirmed: true`.
- Changing the subscribed number requires fresh consent.
- Indian 10-digit mobile numbers are stored in `+91` E.164 format.
- Pausing retains consent but makes the vendor temporarily ineligible.
- Opting out records the time and makes the vendor ineligible.
- Existing vendors are not backfilled or automatically subscribed.
