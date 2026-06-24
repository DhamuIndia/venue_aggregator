import { ApiError, apiRequest } from "@/lib/api-client";
import type { AuthRole } from "@/features/auth/types";

const STORAGE_KEY = "venue-aggregator-notifications";
export const NOTIFICATIONS_CHANGED_EVENT = "venue-aggregator-notifications-changed";

const useMockNotifications = process.env.NEXT_PUBLIC_NOTIFICATIONS_MODE === "mock";

export type NotificationType = "ENQUIRY" | "BOOKING" | "PAYMENT" | "REVIEW" | "SYSTEM";

export type NotificationItem = {
  id: string;
  type: NotificationType;
  title: string;
  message: string;
  createdAt: string;
  readAt?: string | null;
  actionHref?: string;
};

export type NotificationListResult = {
  notifications: NotificationItem[];
  unreadCount: number;
  source: "api" | "mock";
};

export async function getNotifications(accessToken?: string | null, role: AuthRole = "CUSTOMER"): Promise<NotificationListResult> {
  if (useMockNotifications || !accessToken) return localNotificationResult(role);

  try {
    const response = await apiRequest<unknown>("/notifications", {
      token: accessToken
    });
    const notifications = extractNotifications(response);
    saveLocalNotifications(role, notifications);
    return {
      notifications,
      unreadCount: unreadCount(notifications),
      source: "api"
    };
  } catch {
    return localNotificationResult(role);
  }
}

export async function markNotificationRead(notificationId: string, accessToken?: string | null, role: AuthRole = "CUSTOMER") {
  if (useMockNotifications || !accessToken) return markLocalNotificationRead(role, notificationId);

  try {
    const response = await apiRequest<unknown>(`/notifications/${encodeURIComponent(notificationId)}/read`, {
      method: "PATCH",
      token: accessToken
    });
    const notification = toNotificationItem(response) ?? markLocalNotificationRead(role, notificationId);
    cacheLocalNotification(role, notification);
    notifyNotificationsChanged();
    return notification;
  } catch (exception) {
    if (exception instanceof ApiError && [401, 403, 404].includes(exception.status)) throw exception;
    return markLocalNotificationRead(role, notificationId);
  }
}

export async function markAllNotificationsRead(accessToken?: string | null, role: AuthRole = "CUSTOMER") {
  if (useMockNotifications || !accessToken) return markAllLocalNotificationsRead(role);

  try {
    await apiRequest<void>("/notifications/read-all", {
      method: "PATCH",
      token: accessToken
    });
    return markAllLocalNotificationsRead(role);
  } catch (exception) {
    if (exception instanceof ApiError && [401, 403].includes(exception.status)) throw exception;
    return markAllLocalNotificationsRead(role);
  }
}

export function subscribeToNotificationChanges(listener: () => void) {
  if (typeof window === "undefined") return () => undefined;

  window.addEventListener(NOTIFICATIONS_CHANGED_EVENT, listener);
  window.addEventListener("storage", listener);

  return () => {
    window.removeEventListener(NOTIFICATIONS_CHANGED_EVENT, listener);
    window.removeEventListener("storage", listener);
  };
}

function localNotificationResult(role: AuthRole): NotificationListResult {
  const notifications = getLocalNotifications(role);
  return {
    notifications,
    unreadCount: unreadCount(notifications),
    source: "mock"
  };
}

function getLocalNotifications(role: AuthRole) {
  if (typeof window === "undefined") return seededNotifications(role);
  const store = readNotificationStore();
  if (!Object.prototype.hasOwnProperty.call(store, role)) {
    const seeded = seededNotifications(role);
    saveLocalNotifications(role, seeded);
    return seeded;
  }
  return store[role];
}

function markLocalNotificationRead(role: AuthRole, notificationId: string) {
  const notifications = getLocalNotifications(role).map((notification) => notification.id === notificationId ? {
    ...notification,
    readAt: notification.readAt ?? new Date().toISOString()
  } : notification);
  saveLocalNotifications(role, notifications);
  notifyNotificationsChanged();
  return notifications.find((notification) => notification.id === notificationId) ?? seededNotifications(role)[0];
}

function markAllLocalNotificationsRead(role: AuthRole) {
  const now = new Date().toISOString();
  const notifications = getLocalNotifications(role).map((notification) => ({ ...notification, readAt: notification.readAt ?? now }));
  saveLocalNotifications(role, notifications);
  notifyNotificationsChanged();
  return notifications;
}

function cacheLocalNotification(role: AuthRole, notification: NotificationItem) {
  const current = getLocalNotifications(role).filter((item) => item.id !== notification.id);
  saveLocalNotifications(role, [notification, ...current]);
}

function saveLocalNotifications(role: AuthRole, notifications: NotificationItem[]) {
  if (typeof window === "undefined") return;
  const store = readNotificationStore();
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify({
    ...store,
    [role]: sortNotifications(dedupeNotifications(notifications))
  }));
}

function readNotificationStore(): Record<AuthRole, NotificationItem[]> {
  if (typeof window === "undefined") return {} as Record<AuthRole, NotificationItem[]>;

  try {
    const parsed = JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? "{}") as unknown;
    if (!isRecord(parsed)) return {} as Record<AuthRole, NotificationItem[]>;

    return Object.fromEntries(
      Object.entries(parsed).map(([role, value]) => [
        role,
        Array.isArray(value) ? sortNotifications(value.map(toNotificationItem).filter(Boolean) as NotificationItem[]) : []
      ])
    ) as Record<AuthRole, NotificationItem[]>;
  } catch {
    return {} as Record<AuthRole, NotificationItem[]>;
  }
}

function notifyNotificationsChanged() {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new CustomEvent(NOTIFICATIONS_CHANGED_EVENT));
}

function extractNotifications(response: unknown) {
  if (Array.isArray(response)) return sortNotifications(response.map(toNotificationItem).filter(Boolean) as NotificationItem[]);
  if (!isRecord(response)) return [];
  if (isRecord(response.data)) return extractNotifications(response.data);

  const candidates = [response.items, response.content, response.results, response.notifications];
  const list = candidates.find(Array.isArray);
  return Array.isArray(list) ? sortNotifications(list.map(toNotificationItem).filter(Boolean) as NotificationItem[]) : [];
}

function toNotificationItem(value: unknown): NotificationItem | undefined {
  const record = unwrapRecord(value);
  if (!record) return undefined;

  const id = stringValue(record, ["id", "notificationId", "notification_id"]);
  const title = stringValue(record, ["title", "subject"]);
  const message = stringValue(record, ["message", "body", "description"]);
  if (!id || !title || !message) return undefined;

  return {
    id,
    type: notificationType(record) ?? "SYSTEM",
    title,
    message,
    createdAt: stringValue(record, ["createdAt", "created_at", "sentAt", "sent_at"]) ?? new Date().toISOString(),
    readAt: stringValue(record, ["readAt", "read_at"]) ?? (booleanValue(record, ["read", "isRead", "is_read"]) ? new Date().toISOString() : null),
    actionHref: stringValue(record, ["actionHref", "action_href", "url", "link"])
  };
}

function seededNotifications(role: AuthRole): NotificationItem[] {
  const now = Date.now();
  if (role === "HALL_OWNER") {
    return [
      {
        id: "NOTIF-OWNER-3",
        type: "PAYMENT",
        title: "Advance payment received",
        message: "A customer advance payment was marked paid for Emerald Convention Centre.",
        createdAt: new Date(now - 1000 * 60 * 18).toISOString(),
        actionHref: "/owner?tab=bookings"
      },
      {
        id: "NOTIF-OWNER-2",
        type: "BOOKING",
        title: "Booking needs completion update",
        message: "Wedding on 18 July 2026 is confirmed. Mark it completed after the event.",
        createdAt: new Date(now - 1000 * 60 * 60 * 4).toISOString(),
        actionHref: "/owner?tab=bookings"
      },
      {
        id: "NOTIF-OWNER-1",
        type: "ENQUIRY",
        title: "New enquiry received",
        message: "Reception enquiry for 620 guests is waiting for your response.",
        createdAt: new Date(now - 1000 * 60 * 60 * 8).toISOString(),
        readAt: new Date(now - 1000 * 60 * 60 * 7).toISOString(),
        actionHref: "/owner?tab=enquiries"
      }
    ];
  }

  if (role === "VENDOR") {
    return [
      {
        id: "NOTIF-VENDOR-1",
        type: "ENQUIRY",
        title: "New vendor lead",
        message: "A customer requested a quote for a reception service.",
        createdAt: new Date(now - 1000 * 60 * 45).toISOString(),
        actionHref: "/vendor?tab=leads"
      }
    ];
  }

  return [
    {
      id: "NOTIF-CUSTOMER-3",
      type: "PAYMENT",
      title: "Advance payment pending",
      message: "Pay the advance for Emerald Convention Centre to secure your booking.",
      createdAt: new Date(now - 1000 * 60 * 15).toISOString(),
      actionHref: "/customer?tab=bookings"
    },
    {
      id: "NOTIF-CUSTOMER-2",
      type: "BOOKING",
      title: "Booking confirmed",
      message: "Emerald Convention Centre confirmed your Wedding booking for 18 July 2026.",
      createdAt: new Date(now - 1000 * 60 * 60 * 3).toISOString(),
      actionHref: "/customer?tab=bookings"
    },
    {
      id: "NOTIF-CUSTOMER-1",
      type: "REVIEW",
      title: "Review your completed service",
      message: "Your completed event at Marigold Mini Hall is eligible for a verified review.",
      createdAt: new Date(now - 1000 * 60 * 60 * 12).toISOString(),
      readAt: new Date(now - 1000 * 60 * 60 * 6).toISOString(),
      actionHref: "/customer?tab=reviews"
    }
  ];
}

function unreadCount(notifications: NotificationItem[]) {
  return notifications.filter((notification) => !notification.readAt).length;
}

function dedupeNotifications(notifications: NotificationItem[]) {
  const byId = new Map<string, NotificationItem>();
  notifications.forEach((notification) => byId.set(notification.id, notification));
  return Array.from(byId.values());
}

function sortNotifications(notifications: NotificationItem[]) {
  return [...notifications].sort((first, second) => new Date(second.createdAt).getTime() - new Date(first.createdAt).getTime());
}

function unwrapRecord(value: unknown): Record<string, unknown> | undefined {
  if (!isRecord(value)) return undefined;
  if (isRecord(value.notification)) return value.notification;
  if (isRecord(value.data)) return unwrapRecord(value.data);
  return value;
}

function notificationType(record: Record<string, unknown>): NotificationType | undefined {
  const value = stringValue(record, ["type", "notificationType", "notification_type"]);
  const normalized = value?.trim().toUpperCase();
  if (normalized === "ENQUIRY" || normalized === "BOOKING" || normalized === "PAYMENT" || normalized === "REVIEW" || normalized === "SYSTEM") return normalized;
  return undefined;
}

function stringValue(record: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string" && value.trim()) return value;
    if (typeof value === "number") return String(value);
  }
  return undefined;
}

function booleanValue(record: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "boolean") return value;
    if (typeof value === "string") return value.toLowerCase() === "true";
  }
  return undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}
