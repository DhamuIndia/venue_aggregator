import { apiRequest } from "@/lib/api-client";

export type VendorNotificationPreference = {
  vendorId: number;
  whatsAppLeadNotificationsEnabled: boolean;
  whatsAppLeadNotificationsPaused: boolean;
  canReceiveWhatsAppLeadNotifications: boolean;
  whatsAppNumber: string | null;
  whatsAppConsentedAt: string | null;
  whatsAppConsentSource: "VENDOR_SETTINGS" | null;
  whatsAppOptedOutAt: string | null;
  whatsAppPausedAt: string | null;
  updatedAt: string | null;
};

export type UpdateVendorNotificationPreferencePayload = {
  whatsAppLeadNotificationsEnabled: boolean;
  whatsAppLeadNotificationsPaused: boolean;
  whatsAppNumber: string;
  consentConfirmed: boolean;
};

function requireAccessToken(accessToken?: string | null) {
  if (!accessToken) {
    throw new Error("Sign in as a vendor to manage WhatsApp notifications.");
  }
  return accessToken;
}

export function getVendorNotificationPreference(accessToken?: string | null) {
  return apiRequest<VendorNotificationPreference>("/vendor/notification-preferences", {
    token: requireAccessToken(accessToken)
  });
}

export function updateVendorNotificationPreference(
  payload: UpdateVendorNotificationPreferencePayload,
  accessToken?: string | null
) {
  return apiRequest<VendorNotificationPreference>("/vendor/notification-preferences", {
    method: "PUT",
    token: requireAccessToken(accessToken),
    body: JSON.stringify(payload)
  });
}
