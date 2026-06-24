import { ApiError, apiRequest } from "@/lib/api-client";

const STORAGE_KEY = "venue-owner-onboarding-draft";
const LEGACY_STORAGE_KEY = "venue-owner-onboarding";
const useMockOwnerOnboarding = process.env.NEXT_PUBLIC_OWNER_ONBOARDING_MODE === "mock";

export type OwnerOnboardingStatus = "DRAFT" | "PENDING_APPROVAL" | "APPROVED" | "REJECTED";

export type OwnerOnboardingDraft = {
  id?: string;
  hallName: string;
  venueType: string;
  description: string;
  city: string;
  area: string;
  pincode: string;
  capacity: number;
  morningPrice: number;
  eveningPrice: number;
  fullDayPrice: number;
  amenities: string[];
  status: OwnerOnboardingStatus;
  updatedAt?: string;
};

export const emptyOwnerOnboardingDraft: OwnerOnboardingDraft = {
  hallName: "",
  venueType: "Marriage Hall",
  description: "",
  city: "Chennai",
  area: "",
  pincode: "",
  capacity: 0,
  morningPrice: 0,
  eveningPrice: 0,
  fullDayPrice: 0,
  amenities: ["Air conditioned", "Parking", "Dining hall"],
  status: "DRAFT"
};

export async function getOwnerOnboardingDraft(accessToken?: string | null) {
  const localDraft = getLocalDraft();
  if (useMockOwnerOnboarding || !accessToken || !localDraft.id) return localDraft;

  try {
    const response = await apiRequest<unknown>(`/owner/halls/${encodeURIComponent(localDraft.id)}`, {
      token: accessToken
    });
    const draft = toOwnerDraft(response) ?? localDraft;
    saveLocalDraft(draft);
    return draft;
  } catch {
    return localDraft;
  }
}

export async function saveOwnerOnboardingDraft(payload: OwnerOnboardingDraft, accessToken?: string | null) {
  if (useMockOwnerOnboarding || !accessToken) return saveLocalDraft({ ...payload, status: "DRAFT" });

  try {
    const response = await apiRequest<unknown>(payload.id ? `/owner/halls/${encodeURIComponent(payload.id)}` : "/owner/halls", {
      method: payload.id ? "PUT" : "POST",
      token: accessToken,
      body: JSON.stringify(toRequestPayload(payload))
    });
    const draft = toOwnerDraft(response) ?? { ...payload, id: payload.id ?? `HALL-${Date.now().toString().slice(-6)}`, status: "DRAFT" as const };
    saveLocalDraft(draft);
    return draft;
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 409].includes(exception.status)) {
      throw exception;
    }
    return saveLocalDraft({ ...payload, status: "DRAFT" });
  }
}

export async function submitOwnerOnboardingDraft(payload: OwnerOnboardingDraft, accessToken?: string | null) {
  const savedDraft = await saveOwnerOnboardingDraft(payload, accessToken);
  if (useMockOwnerOnboarding || !accessToken || !savedDraft.id) {
    return saveLocalDraft({ ...savedDraft, status: "PENDING_APPROVAL" });
  }

  try {
    const response = await apiRequest<unknown>(`/owner/halls/${encodeURIComponent(savedDraft.id)}/submit`, {
      method: "POST",
      token: accessToken
    });
    const draft = toOwnerDraft(response) ?? { ...savedDraft, status: "PENDING_APPROVAL" as const };
    saveLocalDraft(draft);
    return draft;
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 409].includes(exception.status)) {
      throw exception;
    }
    return saveLocalDraft({ ...savedDraft, status: "PENDING_APPROVAL" });
  }
}

function getLocalDraft() {
  if (typeof window === "undefined") return emptyOwnerOnboardingDraft;
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY) ?? window.localStorage.getItem(LEGACY_STORAGE_KEY) ?? "null";
    const parsed = JSON.parse(stored) as unknown;
    return toOwnerDraft(parsed) ?? emptyOwnerOnboardingDraft;
  } catch {
    return emptyOwnerOnboardingDraft;
  }
}

function saveLocalDraft(draft: OwnerOnboardingDraft) {
  const nextDraft = { ...draft, id: draft.id ?? `HALL-${Date.now().toString().slice(-6)}`, updatedAt: new Date().toISOString() };
  if (typeof window !== "undefined") window.localStorage.setItem(STORAGE_KEY, JSON.stringify(nextDraft));
  return nextDraft;
}

function toRequestPayload(draft: OwnerOnboardingDraft) {
  return {
    name: draft.hallName,
    hallName: draft.hallName,
    venueType: toBackendVenueType(draft.venueType),
    description: draft.description,
    city: draft.city,
    area: draft.area,
    pincode: draft.pincode,
    capacity: draft.capacity,
    capacityMax: draft.capacity,
    startingPrice: draft.fullDayPrice,
    pricing: {
      morningPrice: draft.morningPrice,
      eveningPrice: draft.eveningPrice,
      fullDayPrice: draft.fullDayPrice
    },
    amenities: draft.amenities,
    acAvailable: draft.amenities.includes("Air conditioned"),
    carParking: draft.amenities.includes("Parking"),
    diningAvailable: draft.amenities.includes("Dining hall"),
    generatorAvailable: draft.amenities.includes("Generator"),
    liftAvailable: draft.amenities.includes("Lift")
  };
}

function toOwnerDraft(value: unknown): OwnerOnboardingDraft | undefined {
  if (!isRecord(value)) return undefined;
  const record = unwrapRecord(value);

  const hallName = stringValue(record, ["hallName", "name", "title"]);
  if (!hallName) return undefined;

  return {
    id: stringValue(record, ["id", "hallId", "hall_id", "slug"]),
    hallName,
    venueType: venueTypeValue(record) ?? "Marriage Hall",
    description: stringValue(record, ["description", "summary"]) ?? "",
    city: stringValue(record, ["city"]) ?? "Chennai",
    area: stringValue(record, ["area", "locality", "location"]) ?? "",
    pincode: stringValue(record, ["pincode", "pinCode", "postalCode"]) ?? "",
    capacity: numberValue(record, ["capacity", "capacityMax", "capacity_max"]) ?? 0,
    morningPrice: numberValue(record, ["morningPrice", "morning_price"], ["pricing", "morningPrice"]) ?? 0,
    eveningPrice: numberValue(record, ["eveningPrice", "evening_price"], ["pricing", "eveningPrice"]) ?? 0,
    fullDayPrice: numberValue(record, ["fullDayPrice", "full_day_price", "startingPrice", "amount"], ["pricing", "fullDayPrice"]) ?? 0,
    amenities: arrayOfStrings(record.amenities) ?? inferredAmenities(record),
    status: statusValue(record) ?? "DRAFT",
    updatedAt: stringValue(record, ["updatedAt", "updated_at"])
  };
}

function unwrapRecord(value: Record<string, unknown>) {
  if (isRecord(value.data)) return value.data;
  if (isRecord(value.hall)) return value.hall;
  if (isRecord(value.listing)) return value.listing;
  return value;
}

function inferredAmenities(record: Record<string, unknown>) {
  return [
    booleanValue(record, ["acAvailable", "ac_available"]) ? "Air conditioned" : "",
    booleanValue(record, ["carParking", "car_parking"]) ? "Parking" : "",
    booleanValue(record, ["diningAvailable", "dining_available"]) ? "Dining hall" : "",
    booleanValue(record, ["generatorAvailable", "generator_available"]) ? "Generator" : "",
    booleanValue(record, ["liftAvailable", "lift_available"]) ? "Lift" : ""
  ].filter(Boolean);
}

function venueTypeValue(record: Record<string, unknown>) {
  const value = stringValue(record, ["venueType", "venue_type", "hallType", "type"]);
  if (!value) return undefined;
  const normalized = value.trim().toUpperCase().replace(/[\s-]+/g, "_");
  if (normalized === "MARRIAGE_HALL") return "Marriage Hall";
  if (normalized === "BANQUET_HALL") return "Banquet Hall";
  if (normalized === "MINI_HALL") return "Mini Hall";
  if (normalized === "CONVENTION_CENTRE" || normalized === "CONVENTION_CENTER") return "Convention Centre";
  return value;
}

function toBackendVenueType(value: string) {
  return value.toUpperCase().replace(/\s+/g, "_");
}

function statusValue(record: Record<string, unknown>): OwnerOnboardingStatus | undefined {
  const value = stringValue(record, ["status", "listingStatus", "listing_status", "approvalStatus"]);
  const normalized = value?.trim().toUpperCase();
  if (normalized === "DRAFT") return "DRAFT";
  if (normalized === "PENDING" || normalized === "PENDING_APPROVAL" || normalized === "SUBMITTED") return "PENDING_APPROVAL";
  if (normalized === "APPROVED") return "APPROVED";
  if (normalized === "REJECTED") return "REJECTED";
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

function numberValue(record: Record<string, unknown>, keys: string[], nested?: [string, string]) {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "number") return value;
    if (typeof value === "string" && value.trim() && !Number.isNaN(Number(value))) return Number(value);
  }
  const nestedRecord = nested ? record[nested[0]] : undefined;
  if (nested && isRecord(nestedRecord)) return numberValue(nestedRecord, [nested[1]]);
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

function arrayOfStrings(value: unknown) {
  if (!Array.isArray(value)) return undefined;
  const strings = value.filter((item): item is string => typeof item === "string" && item.trim().length > 0);
  return strings.length ? strings : undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}
