import { ApiError, apiRequest } from "@/lib/api-client";
import type { CreateEnquiryPayload, EnquiryStatus, StoredEnquiry } from "./types";

const STORAGE_KEY = "venue-aggregator-enquiries";
const useMockEnquiries = process.env.NEXT_PUBLIC_ENQUIRIES_MODE === "mock";
type EnquiryFallback = Partial<CreateEnquiryPayload>;

export type EnquiryListResult = {
  enquiries: StoredEnquiry[];
  source: "api" | "mock";
};

export function getLocalEnquiries(): StoredEnquiry[] {
  if (typeof window === "undefined") return [];
  try {
    return JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? "[]") as StoredEnquiry[];
  } catch {
    return [];
  }
}

export function getLocalEnquiry(id: string) {
  return getLocalEnquiries().find((enquiry) => enquiry.id === id);
}

export async function createEnquiry(payload: CreateEnquiryPayload, accessToken?: string | null): Promise<StoredEnquiry> {
  if (useMockEnquiries || !accessToken) return createLocalEnquiry(payload);

  try {
    const response = await apiRequest<unknown>("/public/enquiries", {
      method: "POST",
      token: accessToken,
      body: JSON.stringify({
        hallId: payload.hallId,
        eventDate: payload.eventDate,
        eventType: payload.eventType,
        guestCount: payload.guestCount,
        slot: payload.slot,
        notes: payload.notes
      })
    });

    const enquiry = toStoredEnquiry(response, payload) ?? createStoredFromFallback(payload);
    cacheLocalEnquiry(enquiry);
    return enquiry;
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403].includes(exception.status)) {
      throw exception;
    }
    return createLocalEnquiry(payload);
  }
}

export async function getEnquiry(id: string, accessToken?: string | null): Promise<StoredEnquiry | undefined> {
  if (useMockEnquiries || !accessToken) return getLocalEnquiry(id);

  try {
    const response = await apiRequest<unknown>(`/customer/enquiries/${encodeURIComponent(id)}`, {
      token: accessToken
    });
    const enquiry = toStoredEnquiry(response);
    if (enquiry) cacheLocalEnquiry(enquiry);
    return enquiry;
  } catch {
    return getLocalEnquiry(id);
  }
}

export async function getCustomerEnquiries(accessToken?: string | null): Promise<EnquiryListResult> {
  if (useMockEnquiries || !accessToken) {
    return { enquiries: getLocalEnquiries(), source: "mock" };
  }

  try {
    const response = await apiRequest<unknown>("/customer/enquiries", {
      token: accessToken
    });
    const enquiries = extractEnquiries(response);
    enquiries.forEach(cacheLocalEnquiry);
    return { enquiries, source: "api" };
  } catch {
    return { enquiries: getLocalEnquiries(), source: "mock" };
  }
}

export async function getOwnerHallEnquiries(hallId: string, hallName: string, accessToken?: string | null): Promise<EnquiryListResult> {
  if (useMockEnquiries || !accessToken) {
    return { enquiries: getLocalEnquiries().filter((enquiry) => enquiry.hallId === hallId), source: "mock" };
  }

  try {
    const response = await apiRequest<unknown>(`/owner/halls/${encodeURIComponent(hallId)}/enquiries`, {
      token: accessToken
    });
    const enquiries = extractEnquiries(response, { hallId, hallName, eventType: "Event", guestCount: 0, slot: "FULL_DAY" });
    enquiries.forEach(cacheLocalEnquiry);
    return { enquiries, source: "api" };
  } catch {
    return { enquiries: getLocalEnquiries().filter((enquiry) => enquiry.hallId === hallId), source: "mock" };
  }
}

export async function updateOwnerEnquiryStatus(enquiryId: string, status: EnquiryStatus, accessToken?: string | null) {
  if (useMockEnquiries || !accessToken) return updateLocalEnquiryStatus(enquiryId, status);

  try {
    const response = await apiRequest<unknown>(`/owner/enquiries/${encodeURIComponent(enquiryId)}/status`, {
      method: "PATCH",
      token: accessToken,
      body: JSON.stringify({ status })
    });
    const enquiry = toStoredEnquiry(response) ?? updateLocalEnquiryStatus(enquiryId, status);
    if (enquiry) cacheLocalEnquiry(enquiry);
    return enquiry;
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 409].includes(exception.status)) {
      throw exception;
    }
    return updateLocalEnquiryStatus(enquiryId, status);
  }
}

export function createLocalEnquiry(payload: CreateEnquiryPayload): StoredEnquiry {
  const enquiry: StoredEnquiry = {
    ...payload,
    id: `ENQ-${Date.now().toString().slice(-6)}`,
    status: "PENDING_OWNER_RESPONSE",
    submittedAt: new Date().toISOString()
  };

  cacheLocalEnquiry(enquiry);
  return enquiry;
}

export function updateLocalEnquiryStatus(id: string, status: EnquiryStatus) {
  const enquiries = getLocalEnquiries();
  const updated = enquiries.map((enquiry) => enquiry.id === id ? { ...enquiry, status } : enquiry);
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
  return updated.find((enquiry) => enquiry.id === id);
}

function cacheLocalEnquiry(enquiry: StoredEnquiry) {
  if (typeof window === "undefined") return;
  const existing = getLocalEnquiries().filter((item) => item.id !== enquiry.id);
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify([enquiry, ...existing]));
}

function extractEnquiries(response: unknown, fallback?: EnquiryFallback) {
  if (Array.isArray(response)) return response.map((item) => toStoredEnquiry(item, fallback)).filter(Boolean) as StoredEnquiry[];
  if (!isRecord(response)) return [];

  const candidates = [response.items, response.content, response.data, response.results, response.enquiries];
  const list = candidates.find(Array.isArray);
  return Array.isArray(list) ? list.map((item) => toStoredEnquiry(item, fallback)).filter(Boolean) as StoredEnquiry[] : [];
}

function toStoredEnquiry(value: unknown, fallback?: EnquiryFallback): StoredEnquiry | undefined {
  if (!isRecord(value)) {
    return hasCompleteFallback(fallback) ? createStoredFromFallback(fallback) : undefined;
  }

  const hallId = stringValue(value, ["hallId", "hall_id"]) ?? fallback?.hallId;
  const hallName = stringValue(value, ["hallName", "venue", "venueName"]) ?? fallback?.hallName;
  const eventDate = stringValue(value, ["eventDate", "event_date"]) ?? fallback?.eventDate;
  const eventType = stringValue(value, ["eventType", "event_type"]) ?? fallback?.eventType;
  const slot = stringValue(value, ["slot", "slotType", "slot_type"]) ?? fallback?.slot;
  const id = stringValue(value, ["id", "enquiryId", "enquiry_id"]);

  if (!id || !hallId || !hallName || !eventDate || !eventType || !isSlot(slot)) {
    return hasCompleteFallback(fallback) ? createStoredFromFallback(fallback) : undefined;
  }

  return {
    id,
    hallId,
    hallName,
    customerId: stringValue(value, ["customerId", "customer_id"]) ?? fallback?.customerId ?? "",
    eventDate,
    eventType,
    guestCount: numberValue(value, ["guestCount", "guest_count"]) ?? fallback?.guestCount ?? 0,
    slot,
    notes: stringValue(value, ["notes", "message"]) ?? fallback?.notes,
    status: statusValue(value) ?? "PENDING_OWNER_RESPONSE",
    submittedAt: stringValue(value, ["submittedAt", "createdAt", "created_at"]) ?? new Date().toISOString()
  };
}

function createStoredFromFallback(fallback: CreateEnquiryPayload): StoredEnquiry {
  return {
    ...fallback,
    id: `ENQ-${Date.now().toString().slice(-6)}`,
    status: "PENDING_OWNER_RESPONSE",
    submittedAt: new Date().toISOString()
  };
}

function hasCompleteFallback(fallback?: EnquiryFallback): fallback is CreateEnquiryPayload {
  return Boolean(
    fallback?.hallId &&
    fallback.hallName &&
    fallback.customerId &&
    fallback.eventDate &&
    fallback.eventType &&
    typeof fallback.guestCount === "number" &&
    isSlot(fallback.slot)
  );
}

function statusValue(record: Record<string, unknown>): EnquiryStatus | undefined {
  const value = stringValue(record, ["status"]);
  if (!value) return undefined;

  if (value === "NEW" || value === "PENDING_OWNER_RESPONSE" || value === "CONFIRMED" || value === "DECLINED" || value === "COMPLETED") {
    return value;
  }
  if (value === "AWAITING_RESPONSE") return "PENDING_OWNER_RESPONSE";
  if (value === "CONTACTED") return "PENDING_OWNER_RESPONSE";
  if (value === "CLOSED") return "COMPLETED";
  return undefined;
}

function isSlot(value: unknown): value is StoredEnquiry["slot"] {
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
