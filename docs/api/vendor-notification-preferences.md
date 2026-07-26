# Vendor Lead Notification Preferences

This module provides vendor-owned WhatsApp lead-alert preferences and consent
storage. In the vendor workspace, the separate **Notifications** tab lets a
vendor:

- enter the WhatsApp number used for lead alerts;
- explicitly opt in (consent is never preselected);
- pause and resume alerts without withdrawing consent;
- change the number after providing fresh consent; and
- opt out through a confirmation step.

The commercial **Subscription** tab is separate and does not control WhatsApp
consent.

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
- The UI reads the existing preference from the API and fails closed if it
  cannot be loaded; it does not create a local or mock consent record.
- First-time enablement requires `consentConfirmed: true`.
- Changing the subscribed number requires fresh consent.
- Indian 10-digit mobile numbers are stored in `+91` E.164 format.
- Pausing retains consent but makes the vendor temporarily ineligible.
- Opting out records the time and makes the vendor ineligible.
- Existing vendors are not backfilled or automatically subscribed.
- Vendor consent alone does not cause messages to be sent. The vendor must also
  be included in the controlled rollout allowlist, and global WhatsApp sending
  must be enabled by an administrator.
