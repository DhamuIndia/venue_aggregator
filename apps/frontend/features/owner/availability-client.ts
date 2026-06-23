import { ApiError, apiRequest } from "@/lib/api-client";
import type { EnquirySlot } from "@/features/enquiries/types";

const STORAGE_KEY = "venue-aggregator-owner-blocked-dates";
const useMockAvailability = process.env.NEXT_PUBLIC_AVAILABILITY_MODE === "mock";

export type BlockedDate = {
  id: string;
  date: string;
  slot: EnquirySlot;
  reason: string;
};

export type BlockDatePayload = Omit<BlockedDate, "id">;

export type AvailabilityBooking = {
  id: string;
  enquiryId?: string;
  eventDate: string;
  slot: EnquirySlot;
  eventType: string;
  guestCount: number;
  customerName?: string;
};

export type OwnerAvailabilityResult = {
  blockedDates: BlockedDate[];
  bookings: AvailabilityBooking[];
  source: "api" | "mock";
};

export async function getOwnerAvailability(
  hallId: string,
  accessToken: string | null | undefined,
  fallbackBlockedDates: BlockedDate[] = [],
  fallbackBookings: AvailabilityBooking[] = []
): Promise<OwnerAvailabilityResult> {
  if (useMockAvailability || !accessToken) {
    return {
      blockedDates: getLocalBlockedDates(hallId, fallbackBlockedDates),
      bookings: fallbackBookings,
      source: "mock"
    };
  }

  try {
    const response = await apiRequest<unknown>(`/owner/halls/${encodeURIComponent(hallId)}/availability`, {
      token: accessToken
    });
    const availability = extractAvailability(response, fallbackBlockedDates, fallbackBookings);
    saveLocalBlockedDates(hallId, availability.blockedDates);
    return { ...availability, source: "api" };
  } catch {
    return {
      blockedDates: getLocalBlockedDates(hallId, fallbackBlockedDates),
      bookings: fallbackBookings,
      source: "mock"
    };
  }
}

export async function createOwnerBlockedDate(
  hallId: string,
  payload: BlockDatePayload,
  accessToken?: string | null
) {
  if (useMockAvailability || !accessToken) return createLocalBlockedDate(hallId, payload);

  try {
    const response = await apiRequest<unknown>(`/owner/halls/${encodeURIComponent(hallId)}/blocked-dates`, {
      method: "POST",
      token: accessToken,
      body: JSON.stringify(payload)
    });
    const blockedDate = extractBlockedDate(response) ?? createLocalBlockedDate(hallId, payload);
    cacheLocalBlockedDate(hallId, blockedDate);
    return blockedDate;
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 409].includes(exception.status)) {
      throw exception;
    }
    return createLocalBlockedDate(hallId, payload);
  }
}

export async function deleteOwnerBlockedDate(hallId: string, blockId: string, accessToken?: string | null) {
  if (useMockAvailability || !accessToken) {
    deleteLocalBlockedDate(hallId, blockId);
    return;
  }

  try {
    await apiRequest<void>(`/owner/halls/${encodeURIComponent(hallId)}/blocked-dates/${encodeURIComponent(blockId)}`, {
      method: "DELETE",
      token: accessToken
    });
    deleteLocalBlockedDate(hallId, blockId);
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 404, 409].includes(exception.status)) {
      throw exception;
    }
    deleteLocalBlockedDate(hallId, blockId);
  }
}

function getLocalBlockedDates(hallId: string, fallback: BlockedDate[] = []) {
  if (typeof window === "undefined") return fallback;
  const store = readBlockedDateStore();
  return Object.prototype.hasOwnProperty.call(store, hallId) ? store[hallId] : fallback;
}

function createLocalBlockedDate(hallId: string, payload: BlockDatePayload) {
  const blockedDate: BlockedDate = {
    ...payload,
    id: `BLOCK-${Date.now().toString().slice(-6)}`
  };
  cacheLocalBlockedDate(hallId, blockedDate);
  return blockedDate;
}

function cacheLocalBlockedDate(hallId: string, blockedDate: BlockedDate) {
  const existing = getLocalBlockedDates(hallId).filter((item) => item.id !== blockedDate.id);
  saveLocalBlockedDates(hallId, [blockedDate, ...existing]);
}

function deleteLocalBlockedDate(hallId: string, blockId: string) {
  saveLocalBlockedDates(hallId, getLocalBlockedDates(hallId).filter((item) => item.id !== blockId));
}

function saveLocalBlockedDates(hallId: string, blockedDates: BlockedDate[]) {
  if (typeof window === "undefined") return;
  const store = readBlockedDateStore();
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...store, [hallId]: blockedDates }));
}

function readBlockedDateStore(): Record<string, BlockedDate[]> {
  if (typeof window === "undefined") return {};

  try {
    const parsed = JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? "{}") as unknown;
    if (!isRecord(parsed)) return {};

    return Object.fromEntries(
      Object.entries(parsed).map(([hallId, value]) => [
        hallId,
        Array.isArray(value) ? value.map(toBlockedDate).filter(Boolean) as BlockedDate[] : []
      ])
    );
  } catch {
    return {};
  }
}

function extractAvailability(response: unknown, fallbackBlockedDates: BlockedDate[], fallbackBookings: AvailabilityBooking[]) {
  const record = unwrapRecord(response);
  if (!record) return { blockedDates: fallbackBlockedDates, bookings: fallbackBookings };

  const blockedDates = arrayValue(record, ["blockedDates", "blocked_dates", "blocks", "unavailableSlots"])
    .map(toBlockedDate)
    .filter(Boolean) as BlockedDate[];
  const bookings = arrayValue(record, ["confirmedBookings", "bookings", "confirmedEnquiries", "events"])
    .map(toAvailabilityBooking)
    .filter(Boolean) as AvailabilityBooking[];

  return {
    blockedDates: blockedDates.length ? blockedDates : fallbackBlockedDates,
    bookings: bookings.length ? bookings : fallbackBookings
  };
}

function extractBlockedDate(response: unknown) {
  const direct = toBlockedDate(response);
  if (direct) return direct;

  const record = unwrapRecord(response);
  if (!record) return undefined;

  return toBlockedDate(record.blockedDate) ?? toBlockedDate(record.block) ?? toBlockedDate(record.data);
}

function toBlockedDate(value: unknown): BlockedDate | undefined {
  if (!isRecord(value)) return undefined;

  const date = dateValue(value, ["date", "blockedDate", "blocked_date"]);
  const slot = slotValue(value, ["slot", "slotType", "slot_type"]);
  if (!date || !slot) return undefined;

  return {
    id: stringValue(value, ["id", "blockId", "block_id"]) ?? `BLOCK-${date}-${slot}`,
    date,
    slot,
    reason: stringValue(value, ["reason", "notes", "message"]) ?? "Owner blocked"
  };
}

function toAvailabilityBooking(value: unknown): AvailabilityBooking | undefined {
  if (!isRecord(value)) return undefined;

  const eventDate = dateValue(value, ["eventDate", "event_date", "date"]);
  if (!eventDate) return undefined;

  const slot = slotValue(value, ["slot", "slotType", "slot_type"]) ?? "FULL_DAY";
  const enquiryId = stringValue(value, ["enquiryId", "enquiry_id"]);

  return {
    id: stringValue(value, ["id", "bookingId", "booking_id"]) ?? enquiryId ?? `BOOKING-${eventDate}-${slot}`,
    enquiryId,
    eventDate,
    slot,
    eventType: stringValue(value, ["eventType", "event_type", "title"]) ?? "Confirmed event",
    guestCount: numberValue(value, ["guestCount", "guest_count", "guests"]) ?? 0,
    customerName: stringValue(value, ["customerName", "customer_name", "name"])
  };
}

function unwrapRecord(value: unknown): Record<string, unknown> | undefined {
  if (!isRecord(value)) return undefined;
  if (isRecord(value.data)) return value.data;
  if (isRecord(value.availability)) return value.availability;
  return value;
}

function arrayValue(record: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = record[key];
    if (Array.isArray(value)) return value;
  }
  return [];
}

function dateValue(record: Record<string, unknown>, keys: string[]) {
  const value = stringValue(record, keys);
  return value?.match(/^\d{4}-\d{2}-\d{2}/)?.[0];
}

function slotValue(record: Record<string, unknown>, keys: string[]): EnquirySlot | undefined {
  const value = stringValue(record, keys);
  const normalized = value?.trim().toUpperCase().replace(/[\s-]+/g, "_");
  return isSlot(normalized) ? normalized : undefined;
}

function isSlot(value: unknown): value is EnquirySlot {
  return value === "MORNING" || value === "EVENING" || value === "FULL_DAY";
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
