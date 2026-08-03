import { apiRequest } from "@/lib/api-client";
import { HALL_BASE_SLOTS, isHallSlot, slotConflicts, type HallSlot } from "./slot-model";

const useMockAvailability = process.env.NEXT_PUBLIC_AVAILABILITY_MODE === "mock";

export type PublicHallUnavailableSlot = {
  id: string;
  date: string;
  slot: HallSlot;
  status: "BLOCKED" | "BOOKED";
};

export async function getPublicHallAvailability(hallId: string): Promise<PublicHallUnavailableSlot[]> {
  if (useMockAvailability) return mockUnavailableSlots();

  try {
    const response = await apiRequest<unknown>(`/public/halls/${encodeURIComponent(hallId)}/availability`);
    return extractUnavailableSlots(response);
  } catch {
    return [];
  }
}

export function slotStatusForDate(date: string, unavailableSlots: PublicHallUnavailableSlot[]) {
  const daySlots = unavailableSlots.filter((item) => item.date === date);

  return HALL_BASE_SLOTS.map((slot) => {
    const conflict = daySlots.find((item) => slotConflicts(item.slot, slot));
    return {
      slot,
      status: conflict?.status ?? "AVAILABLE" as const
    };
  });
}

function extractUnavailableSlots(response: unknown) {
  if (!isRecord(response)) return [];
  const record = isRecord(response.data) ? response.data : isRecord(response.availability) ? response.availability : response;

  const blockedDates = arrayValue(record, ["blockedDates", "blocked_dates", "blocks", "unavailableSlots"])
    .map((item) => toUnavailableSlot(item, "BLOCKED"))
    .filter(Boolean) as PublicHallUnavailableSlot[];

  const bookings = arrayValue(record, ["bookings", "confirmedBookings", "confirmed_bookings", "events"])
    .map((item) => toUnavailableSlot(item, "BOOKED"))
    .filter(Boolean) as PublicHallUnavailableSlot[];

  return dedupeUnavailableSlots([...blockedDates, ...bookings]);
}

function toUnavailableSlot(value: unknown, status: PublicHallUnavailableSlot["status"]) {
  if (!isRecord(value)) return undefined;
  const date = dateValue(value, ["date", "eventDate", "event_date", "blockedDate", "blocked_date"]);
  const slot = slotValue(value, ["slot", "slotType", "slot_type"]);
  if (!date || !slot) return undefined;

  return {
    id: stringValue(value, ["id", "blockId", "bookingId", "enquiryId"]) ?? `${status}-${date}-${slot}`,
    date,
    slot,
    status
  };
}

function dedupeUnavailableSlots(slots: PublicHallUnavailableSlot[]) {
  const byKey = new Map<string, PublicHallUnavailableSlot>();
  slots.forEach((slot) => byKey.set(`${slot.date}-${slot.slot}`, slot));
  return Array.from(byKey.values()).sort((first, second) => first.date.localeCompare(second.date));
}

function mockUnavailableSlots(): PublicHallUnavailableSlot[] {
  return [
    { id: "MOCK-BLOCK-1", date: "2026-07-15", slot: "FULL_DAY", status: "BLOCKED" },
    { id: "MOCK-BOOK-1", date: "2026-07-22", slot: "EVENING", status: "BOOKED" }
  ];
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

function slotValue(record: Record<string, unknown>, keys: string[]): HallSlot | undefined {
  const value = stringValue(record, keys);
  const normalized = value?.trim().toUpperCase().replace(/[\s-]+/g, "_");
  return isHallSlot(normalized) ? normalized : undefined;
}

function stringValue(record: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string" && value.trim()) return value;
    if (typeof value === "number") return String(value);
  }
  return undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}
