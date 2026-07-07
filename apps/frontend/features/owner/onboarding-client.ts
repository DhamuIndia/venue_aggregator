import { ApiError, apiRequest } from "@/lib/api-client";
import { toTitleCase } from "@/lib/display-format";

const STORAGE_KEY_PREFIX = "venue-owner-onboarding-draft";
const SHARED_STORAGE_KEY = "venue-owner-onboarding-draft";
const LEGACY_STORAGE_KEY = "venue-owner-onboarding";
const useMockOwnerOnboarding = process.env.NEXT_PUBLIC_OWNER_ONBOARDING_MODE === "mock";

export type OwnerOnboardingStatus = "DRAFT" | "PENDING_APPROVAL" | "APPROVED" | "REJECTED";

export type OwnerOnboardingDraft = {
  id?: string;
  hallName: string;
  venueType: string;
  description: string;
  addressLine: string;
  city: string;
  area: string;
  pincode: string;
  latitude?: number;
  longitude?: number;
  contactNumber: string;
  whatsappNumber: string;
  coverImageUrl: string;
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
  addressLine: "",
  city: "Chennai",
  area: "",
  pincode: "",
  latitude: undefined,
  longitude: undefined,
  contactNumber: "",
  whatsappNumber: "",
  coverImageUrl: "",
  capacity: 0,
  morningPrice: 0,
  eveningPrice: 0,
  fullDayPrice: 0,
  amenities: ["Air conditioned", "Parking", "Dining hall"],
  status: "DRAFT"
};

export async function getOwnerOnboardingDraft(accessToken?: string | null) {
  const localDraft = getLocalDraft(accessToken);
  if (!isEditableDraft(localDraft)) return clearLocalDraft(accessToken);
  if (useMockOwnerOnboarding || !accessToken || !localDraft.id) return localDraft;

  try {
    const response = await apiRequest<unknown>(`/owner/halls/${encodeURIComponent(localDraft.id)}`, {
      token: accessToken
    });
    const draft = toOwnerDraft(response) ?? localDraft;
    if (!isEditableDraft(draft)) return clearLocalDraft(accessToken);
    saveLocalDraft(draft, accessToken);
    return draft;
  } catch (exception) {
    if (exception instanceof ApiError && [403, 404].includes(exception.status)) {
      return clearLocalDraft(accessToken);
    }
    return localDraft;
  }
}

export async function saveOwnerOnboardingDraft(payload: OwnerOnboardingDraft, accessToken?: string | null) {
  if (useMockOwnerOnboarding || !accessToken) return saveLocalDraft({ ...payload, status: "DRAFT" }, accessToken);

  try {
    const response = await apiRequest<unknown>(payload.id ? `/owner/halls/${encodeURIComponent(payload.id)}` : "/owner/halls", {
      method: payload.id ? "PUT" : "POST",
      token: accessToken,
      body: JSON.stringify(toRequestPayload(payload))
    });
    const draft = toOwnerDraft(response) ?? { ...payload, id: payload.id ?? `HALL-${Date.now().toString().slice(-6)}`, status: "DRAFT" as const };
    saveLocalDraft(draft, accessToken);
    return draft;
  } catch (exception) {
    if (payload.id && exception instanceof ApiError && [403, 404].includes(exception.status)) {
      clearLocalDraft(accessToken);
      return saveOwnerOnboardingDraft({ ...payload, id: undefined }, accessToken);
    }
    if (exception instanceof ApiError && [400, 401, 403, 409].includes(exception.status)) {
      throw exception;
    }
    return saveLocalDraft({ ...payload, status: "DRAFT" }, accessToken);
  }
}

export async function submitOwnerOnboardingDraft(payload: OwnerOnboardingDraft, accessToken?: string | null) {
  const savedDraft = await saveOwnerOnboardingDraft(payload, accessToken);
  if (useMockOwnerOnboarding || !accessToken || !savedDraft.id) {
    return saveLocalDraft({ ...savedDraft, status: "PENDING_APPROVAL" }, accessToken);
  }

  try {
    const response = await apiRequest<unknown>(`/owner/halls/${encodeURIComponent(savedDraft.id)}/submit`, {
      method: "POST",
      token: accessToken
    });
    const draft = toOwnerDraft(response) ?? { ...savedDraft, status: "PENDING_APPROVAL" as const };
    clearLocalDraft(accessToken);
    return draft;
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 409].includes(exception.status)) {
      throw exception;
    }
    return saveLocalDraft({ ...savedDraft, status: "PENDING_APPROVAL" }, accessToken);
  }
}

function getLocalDraft(accessToken?: string | null) {
  if (typeof window === "undefined") return emptyOwnerOnboardingDraft;
  try {
    if (accessToken) clearSharedDraftKeys();
    const key = storageKey(accessToken);
    const stored = window.localStorage.getItem(key) ?? (!accessToken ? window.localStorage.getItem(SHARED_STORAGE_KEY) ?? window.localStorage.getItem(LEGACY_STORAGE_KEY) : null) ?? "null";
    const parsed = JSON.parse(stored) as unknown;
    return toOwnerDraft(parsed) ?? emptyOwnerOnboardingDraft;
  } catch {
    return emptyOwnerOnboardingDraft;
  }
}

function saveLocalDraft(draft: OwnerOnboardingDraft, accessToken?: string | null) {
  const nextDraft = { ...draft, id: draft.id ?? `HALL-${Date.now().toString().slice(-6)}`, updatedAt: new Date().toISOString() };
  if (typeof window !== "undefined") {
    if (accessToken) clearSharedDraftKeys();
    window.localStorage.setItem(storageKey(accessToken), JSON.stringify(nextDraft));
  }
  return nextDraft;
}

function clearLocalDraft(accessToken?: string | null) {
  if (typeof window !== "undefined") {
    window.localStorage.removeItem(storageKey(accessToken));
    clearSharedDraftKeys();
  }
  return emptyOwnerOnboardingDraft;
}

function clearSharedDraftKeys() {
  window.localStorage.removeItem(SHARED_STORAGE_KEY);
  window.localStorage.removeItem(LEGACY_STORAGE_KEY);
}

function storageKey(accessToken?: string | null) {
  return `${STORAGE_KEY_PREFIX}:${tokenSubject(accessToken) ?? "demo"}`;
}

function tokenSubject(accessToken?: string | null) {
  if (!accessToken) return undefined;

  try {
    const [, payload] = accessToken.split(".");
    if (!payload || !globalThis.atob) return undefined;

    const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, "=");
    const decoded = JSON.parse(globalThis.atob(padded)) as Record<string, unknown>;
    const subject = decoded.sub;
    return typeof subject === "string" || typeof subject === "number" ? `user-${subject}` : undefined;
  } catch {
    return undefined;
  }
}

function isEditableDraft(draft: OwnerOnboardingDraft) {
  return draft.status === "DRAFT" || draft.status === "REJECTED";
}

function toRequestPayload(draft: OwnerOnboardingDraft) {
  const hallName = toTitleCase(draft.hallName);
  const addressLine = toTitleCase(draft.addressLine);
  const city = toTitleCase(draft.city);
  const area = toTitleCase(draft.area);

  return {
    name: hallName,
    hallName,
    venueType: toBackendVenueType(draft.venueType),
    description: draft.description,
    addressLine,
    city,
    area,
    pincode: draft.pincode,
    latitude: draft.latitude,
    longitude: draft.longitude,
    contactNumber: draft.contactNumber,
    whatsappNumber: draft.whatsappNumber,
    coverImageUrl: draft.coverImageUrl,
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
    hallName: toTitleCase(hallName),
    venueType: venueTypeValue(record) ?? "Marriage Hall",
    description: stringValue(record, ["description", "summary"]) ?? "",
    addressLine: toTitleCase(stringValue(record, ["addressLine", "address_line", "address"]) ?? ""),
    city: toTitleCase(stringValue(record, ["city"]) ?? "Chennai"),
    area: toTitleCase(stringValue(record, ["area", "locality", "location"]) ?? ""),
    pincode: stringValue(record, ["pincode", "pinCode", "postalCode"]) ?? "",
    latitude: numberValue(record, ["latitude", "lat"]),
    longitude: numberValue(record, ["longitude", "lng", "lon"]),
    contactNumber: stringValue(record, ["contactNumber", "contact_number", "phone"]) ?? "",
    whatsappNumber: stringValue(record, ["whatsappNumber", "whatsapp_number", "whatsAppNumber"]) ?? "",
    coverImageUrl: stringValue(record, ["coverImageUrl", "cover_image_url", "imageUrl", "primaryImageUrl"]) ?? "",
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
