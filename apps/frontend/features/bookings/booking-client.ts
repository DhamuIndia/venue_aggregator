import { ApiError, apiRequest } from "@/lib/api-client";
import type { EnquirySlot, StoredEnquiry } from "@/features/enquiries/types";

const STORAGE_KEY = "venue-aggregator-bookings";
const useMockBookings = process.env.NEXT_PUBLIC_BOOKINGS_MODE === "mock";

export type BookingStatus = "REQUESTED" | "CONFIRMED" | "CANCELLED" | "COMPLETED";

export type BookingPaymentStatus = "NOT_STARTED" | "ADVANCE_PENDING" | "ADVANCE_PAID" | "REFUNDED";

export type BookingItem = {
  id: string;
  enquiryId?: string;
  hallId: string;
  hallName: string;
  customerId?: string;
  customerName?: string;
  eventDate: string;
  eventType: string;
  guestCount: number;
  slot: EnquirySlot;
  status: BookingStatus;
  amount?: number;
  paymentStatus: BookingPaymentStatus;
  notes?: string;
  confirmedAt?: string;
  updatedAt: string;
};

export type BookingListResult = {
  bookings: BookingItem[];
  source: "api" | "mock";
};

export function bookingFromEnquiry(enquiry: StoredEnquiry): BookingItem {
  return {
    id: `BOOK-${enquiry.id.replace(/^ENQ-/, "")}`,
    enquiryId: enquiry.id,
    hallId: enquiry.hallId,
    hallName: enquiry.hallName,
    customerId: enquiry.customerId,
    eventDate: enquiry.eventDate,
    eventType: enquiry.eventType,
    guestCount: enquiry.guestCount,
    slot: enquiry.slot,
    status: enquiry.status === "COMPLETED" ? "COMPLETED" : enquiry.status === "DECLINED" ? "CANCELLED" : "CONFIRMED",
    paymentStatus: "ADVANCE_PENDING",
    notes: enquiry.notes,
    confirmedAt: enquiry.status === "CONFIRMED" || enquiry.status === "COMPLETED" ? enquiry.submittedAt : undefined,
    updatedAt: enquiry.submittedAt
  };
}

export async function getCustomerBookings(accessToken?: string | null, fallbackBookings: BookingItem[] = []): Promise<BookingListResult> {
  if (useMockBookings || !accessToken) return { bookings: getLocalBookings(fallbackBookings), source: "mock" };

  try {
    const response = await apiRequest<unknown>("/customer/bookings", {
      token: accessToken
    });
    const bookings = extractBookings(response);
    bookings.forEach(cacheLocalBooking);
    return { bookings, source: "api" };
  } catch {
    return { bookings: getLocalBookings(fallbackBookings), source: "mock" };
  }
}

export async function getOwnerBookings(hallId: string, accessToken?: string | null, fallbackBookings: BookingItem[] = []): Promise<BookingListResult> {
  if (useMockBookings || !accessToken) return { bookings: getLocalBookings(fallbackBookings).filter((booking) => booking.hallId === hallId), source: "mock" };

  try {
    const response = await apiRequest<unknown>(`/owner/halls/${encodeURIComponent(hallId)}/bookings`, {
      token: accessToken
    });
    const bookings = extractBookings(response).filter((booking) => booking.hallId === hallId);
    bookings.forEach(cacheLocalBooking);
    return { bookings, source: "api" };
  } catch {
    return { bookings: getLocalBookings(fallbackBookings).filter((booking) => booking.hallId === hallId), source: "mock" };
  }
}

export async function updateOwnerBookingStatus(bookingId: string, status: BookingStatus, accessToken?: string | null) {
  if (useMockBookings || !accessToken) return updateLocalBookingStatus(bookingId, status);

  try {
    const response = await apiRequest<unknown>(`/owner/bookings/${encodeURIComponent(bookingId)}/status`, {
      method: "PATCH",
      token: accessToken,
      body: JSON.stringify({ status })
    });
    const booking = toBookingItem(response) ?? updateLocalBookingStatus(bookingId, status);
    if (booking) cacheLocalBooking(booking);
    return booking;
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 404, 409].includes(exception.status)) {
      throw exception;
    }
    return updateLocalBookingStatus(bookingId, status);
  }
}

export function upsertLocalBooking(booking: BookingItem) {
  cacheLocalBooking(booking);
  return booking;
}

function getLocalBookings(fallbackBookings: BookingItem[] = []) {
  if (typeof window === "undefined") return fallbackBookings;
  const stored = readBookingStore();
  return stored.length ? stored : fallbackBookings;
}

function updateLocalBookingStatus(bookingId: string, status: BookingStatus) {
  const bookings = readBookingStore();
  const updated = bookings.map((booking) => booking.id === bookingId ? {
    ...booking,
    status,
    updatedAt: new Date().toISOString()
  } : booking);
  saveBookings(updated);
  return updated.find((booking) => booking.id === bookingId);
}

function cacheLocalBooking(booking: BookingItem) {
  const existing = readBookingStore().filter((item) => item.id !== booking.id && item.enquiryId !== booking.enquiryId);
  saveBookings([booking, ...existing]);
}

function saveBookings(bookings: BookingItem[]) {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(dedupeBookings(bookings)));
}

function readBookingStore() {
  if (typeof window === "undefined") return [];

  try {
    const parsed = JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? "[]") as unknown;
    return Array.isArray(parsed) ? dedupeBookings(parsed.map(toBookingItem).filter(Boolean) as BookingItem[]) : [];
  } catch {
    return [];
  }
}

function extractBookings(response: unknown): BookingItem[] {
  if (Array.isArray(response)) return dedupeBookings(response.map(toBookingItem).filter(Boolean) as BookingItem[]);
  if (!isRecord(response)) return [];
  if (isRecord(response.data)) return extractBookings(response.data);

  const candidates = [response.items, response.content, response.results, response.bookings, response.events];
  const list = candidates.find(Array.isArray);
  if (Array.isArray(list)) return dedupeBookings(list.map(toBookingItem).filter(Boolean) as BookingItem[]);

  const booking = toBookingItem(response);
  return booking ? [booking] : [];
}

function toBookingItem(value: unknown): BookingItem | undefined {
  const record = unwrapBookingRecord(value);
  if (!record) return undefined;

  const id = stringValue(record, ["id", "bookingId", "booking_id"]);
  const hallId = stringValue(record, ["hallId", "hall_id"]);
  const hallName = stringValue(record, ["hallName", "hall_name", "venue", "venueName"]);
  const eventDate = dateValue(record, ["eventDate", "event_date", "date"]);
  const eventType = stringValue(record, ["eventType", "event_type", "title"]);
  const slot = slotValue(record, ["slot", "slotType", "slot_type"]) ?? "FULL_DAY";

  if (!id || !hallId || !hallName || !eventDate || !eventType) return undefined;

  return {
    id,
    enquiryId: stringValue(record, ["enquiryId", "enquiry_id"]),
    hallId,
    hallName,
    customerId: stringValue(record, ["customerId", "customer_id"]),
    customerName: stringValue(record, ["customerName", "customer_name", "name"]),
    eventDate,
    eventType,
    guestCount: numberValue(record, ["guestCount", "guest_count", "guests"]) ?? 0,
    slot,
    status: bookingStatus(record) ?? "CONFIRMED",
    amount: numberValue(record, ["amount", "totalAmount", "total_amount", "bookingAmount"]),
    paymentStatus: paymentStatus(record) ?? "NOT_STARTED",
    notes: stringValue(record, ["notes", "message"]),
    confirmedAt: stringValue(record, ["confirmedAt", "confirmed_at"]),
    updatedAt: stringValue(record, ["updatedAt", "updated_at", "createdAt", "created_at"]) ?? new Date().toISOString()
  };
}

function unwrapBookingRecord(value: unknown): Record<string, unknown> | undefined {
  if (!isRecord(value)) return undefined;
  if (isRecord(value.booking)) return value.booking;
  if (isRecord(value.event)) return value.event;
  if (isRecord(value.data)) return unwrapBookingRecord(value.data);
  return value;
}

function dedupeBookings(bookings: BookingItem[]) {
  const byId = new Map<string, BookingItem>();
  bookings.forEach((booking) => byId.set(booking.id, booking));
  return Array.from(byId.values()).sort((first, second) => first.eventDate.localeCompare(second.eventDate));
}

function bookingStatus(record: Record<string, unknown>): BookingStatus | undefined {
  const value = stringValue(record, ["status", "bookingStatus", "booking_status"]);
  if (!value) return undefined;
  const normalized = value.trim().toUpperCase();
  if (normalized === "REQUESTED" || normalized === "CONFIRMED" || normalized === "CANCELLED" || normalized === "COMPLETED") return normalized;
  if (normalized === "PENDING" || normalized === "PENDING_CONFIRMATION") return "REQUESTED";
  if (normalized === "CANCELED") return "CANCELLED";
  return undefined;
}

function paymentStatus(record: Record<string, unknown>): BookingPaymentStatus | undefined {
  const value = stringValue(record, ["paymentStatus", "payment_status"]);
  if (!value) return undefined;
  const normalized = value.trim().toUpperCase();
  if (normalized === "NOT_STARTED" || normalized === "ADVANCE_PENDING" || normalized === "ADVANCE_PAID" || normalized === "REFUNDED") return normalized;
  if (normalized === "PENDING") return "ADVANCE_PENDING";
  if (normalized === "PAID") return "ADVANCE_PAID";
  return undefined;
}

function dateValue(record: Record<string, unknown>, keys: string[]) {
  const value = stringValue(record, keys);
  return value?.match(/^\d{4}-\d{2}-\d{2}/)?.[0];
}

function slotValue(record: Record<string, unknown>, keys: string[]): EnquirySlot | undefined {
  const value = stringValue(record, keys);
  const normalized = value?.trim().toUpperCase().replace(/[\s-]+/g, "_");
  return normalized === "MORNING" || normalized === "EVENING" || normalized === "FULL_DAY" ? normalized : undefined;
}

function stringValue(record: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string" && value.trim()) return value;
    if (typeof value === "number") return String(value);
  }
  return undefined;
}

function numberValue(record: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "number") return value;
    if (typeof value === "string" && value.trim() && !Number.isNaN(Number(value))) return Number(value);
  }
  return undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}
