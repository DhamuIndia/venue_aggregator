"use client";

import { Bell, CheckCheck, Circle, LoaderCircle } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import {
  getNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  subscribeToNotificationChanges,
  type NotificationItem,
  type NotificationType
} from "@/features/notifications/notification-client";

const typeStyles: Record<NotificationType, string> = {
  ENQUIRY: "bg-blue-50 text-blue-700",
  BOOKING: "bg-emerald-50 text-emerald-700",
  PAYMENT: "bg-amber-50 text-amber-700",
  REVIEW: "bg-violet-50 text-violet-700",
  SYSTEM: "bg-slate-100 text-slate-700"
};

export function NotificationBell() {
  const { accessToken, user } = useAuth();
  const [open, setOpen] = useState(false);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (!user) return;
    let isCurrent = true;
    const role = user.role;

    async function loadNotifications() {
      setIsLoading(true);
      const response = await getNotifications(accessToken, role);
      if (!isCurrent) return;
      setNotifications(response.notifications);
      setUnreadCount(response.unreadCount);
      setIsLoading(false);
    }

    loadNotifications();
    const unsubscribe = subscribeToNotificationChanges(loadNotifications);

    return () => {
      isCurrent = false;
      unsubscribe();
    };
  }, [accessToken, user]);

  if (!user) return null;

  return (
    <div className="relative">
      <button
        aria-label="Notifications"
        className="relative grid size-10 place-items-center rounded-md border border-border bg-white text-muted-foreground hover:border-primary hover:text-primary"
        onClick={() => setOpen((current) => !current)}
        type="button"
      >
        <Bell size={18} />
        {unreadCount > 0 && <span className="absolute -right-1 -top-1 grid min-w-5 place-items-center rounded-full bg-primary px-1.5 text-[11px] font-semibold text-white">{unreadCount}</span>}
      </button>

      {open && (
        <div className="absolute right-0 top-12 z-50 w-[min(22rem,calc(100vw-2rem))] rounded-lg border border-border bg-white shadow-xl">
          <div className="flex items-center justify-between gap-3 border-b border-border px-4 py-3">
            <div>
              <h2 className="font-semibold">Notifications</h2>
              <p className="text-xs text-muted-foreground">{unreadCount} unread</p>
            </div>
            <button className="inline-flex h-8 items-center gap-1 rounded-md px-2 text-xs font-semibold text-primary hover:bg-muted" onClick={() => markAllNotificationsRead(accessToken, user.role)} type="button">
              <CheckCheck size={14} /> Read all
            </button>
          </div>
          <NotificationList isLoading={isLoading} notifications={notifications.slice(0, 5)} onRead={(id) => markNotificationRead(id, accessToken, user.role)} />
          <div className="border-t border-border px-4 py-3">
            <Link className="text-sm font-semibold text-primary" href={dashboardActivityHref(user.role)} onClick={() => setOpen(false)}>Open activity center</Link>
          </div>
        </div>
      )}
    </div>
  );
}

export function NotificationActivity() {
  const { accessToken, user } = useAuth();
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!user) return;
    let isCurrent = true;
    const role = user.role;

    async function loadNotifications() {
      setIsLoading(true);
      setError("");

      try {
        const response = await getNotifications(accessToken, role);
        if (!isCurrent) return;
        setNotifications(response.notifications);
        setUnreadCount(response.unreadCount);
      } catch {
        if (!isCurrent) return;
        setError("Could not load notifications.");
      } finally {
        if (isCurrent) setIsLoading(false);
      }
    }

    loadNotifications();
    const unsubscribe = subscribeToNotificationChanges(loadNotifications);

    return () => {
      isCurrent = false;
      unsubscribe();
    };
  }, [accessToken, user]);

  if (!user) return null;

  return (
    <section className="py-7">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="text-xl font-semibold">Activity center</h2>
          <p className="mt-1 text-sm text-muted-foreground">Important updates across enquiries, bookings, payments, and reviews.</p>
        </div>
        <button className="inline-flex h-10 items-center gap-2 rounded-md border border-border bg-white px-3 text-sm font-semibold hover:border-primary disabled:opacity-60" disabled={unreadCount === 0} onClick={() => markAllNotificationsRead(accessToken, user.role)} type="button">
          <CheckCheck size={17} /> Mark all read
        </button>
      </div>

      <div className="mt-5 grid gap-4 lg:grid-cols-[220px_1fr]">
        <div className="h-fit rounded-lg border border-border bg-white p-5">
          <p className="text-sm text-muted-foreground">Unread</p>
          <p className="mt-2 text-3xl font-semibold">{unreadCount}</p>
          <p className="mt-4 text-sm text-muted-foreground">{notifications.length} total updates</p>
        </div>
        <div className="rounded-lg border border-border bg-white">
          {error && <p className="m-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p>}
          <NotificationList isLoading={isLoading} notifications={notifications} onRead={(id) => markNotificationRead(id, accessToken, user.role)} />
        </div>
      </div>
    </section>
  );
}

function NotificationList({
  isLoading,
  notifications,
  onRead
}: {
  isLoading: boolean;
  notifications: NotificationItem[];
  onRead: (id: string) => void;
}) {
  if (isLoading) {
    return (
      <div className="grid gap-2 p-3">
        {[1, 2, 3].map((item) => <div className="h-20 animate-pulse rounded-md bg-muted" key={item} />)}
      </div>
    );
  }

  if (notifications.length === 0) {
    return (
      <div className="grid min-h-48 place-items-center px-6 py-10 text-center">
        <div>
          <Bell className="mx-auto text-muted-foreground" size={28} />
          <h3 className="mt-4 font-semibold">No notifications</h3>
          <p className="mt-2 text-sm text-muted-foreground">New updates will appear here.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="divide-y divide-border">
      {notifications.map((notification) => (
        <article className="flex gap-3 p-4" key={notification.id}>
          <span className={`mt-1 grid size-8 shrink-0 place-items-center rounded-md ${typeStyles[notification.type]}`}>
            <Circle fill="currentColor" size={9} />
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex items-start justify-between gap-3">
              <div>
                <h3 className="font-semibold">{notification.title}</h3>
                <p className="mt-1 text-sm leading-6 text-muted-foreground">{notification.message}</p>
              </div>
              {!notification.readAt && <span className="mt-1 size-2 shrink-0 rounded-full bg-primary" />}
            </div>
            <div className="mt-3 flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
              <span>{formatNotificationTime(notification.createdAt)}</span>
              {notification.actionHref && <a className="font-semibold text-primary" href={notification.actionHref} onClick={() => onRead(notification.id)}>View</a>}
              {!notification.readAt && <button className="font-semibold text-primary" onClick={() => onRead(notification.id)} type="button">Mark read</button>}
            </div>
          </div>
        </article>
      ))}
    </div>
  );
}

function dashboardActivityHref(role: string) {
  if (role === "HALL_OWNER") return "/owner?tab=activity";
  if (role === "VENDOR") return "/vendor";
  if (role === "ADMIN" || role === "SUPER_ADMIN") return "/admin";
  return "/customer?tab=activity";
}

function formatNotificationTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("en-IN", {
    day: "2-digit",
    month: "short",
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
}
