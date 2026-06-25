import { ApiError, apiRequest } from "@/lib/api-client";
import { getHallById, halls as mockHalls } from "@/features/halls/mock-data";
import type { HallSummary, VenueType } from "@/features/halls/types";

const STORAGE_KEY = "venue-aggregator-saved-halls";
export const SAVED_HALLS_CHANGED_EVENT = "venue-aggregator-saved-halls-changed";

const useMockCustomerSavedHalls = process.env.NEXT_PUBLIC_CUSTOMER_SAVED_HALLS_MODE === "mock";
const defaultSavedHalls = mockHalls.slice(0, 2);

export type CustomerSavedHallsResult = {
  halls: HallSummary[];
  source: "api" | "local";
};

export async function getCustomerSavedHalls(accessToken?: string | null): Promise<CustomerSavedHallsResult> {
  if (useMockCustomerSavedHalls || !accessToken) return { halls: getLocalSavedHalls(), source: "local" };

  try {
    const response = await apiRequest<unknown>("/customer/saved-halls", {
      token: accessToken
    });
    const savedHalls = extractSavedHalls(response);
    saveLocalSavedHalls(savedHalls);
    return { halls: savedHalls, source: "api" };
  } catch {
    return { halls: getLocalSavedHalls(), source: "local" };
  }
}

export async function getCustomerSavedHallIds(accessToken?: string | null) {
  const response = await getCustomerSavedHalls(accessToken);
  return response.halls.map((hall) => hall.id);
}

export async function isCustomerHallSaved(hallId: string, accessToken?: string | null) {
  const savedHallIds = await getCustomerSavedHallIds(accessToken);
  return savedHallIds.includes(hallId);
}

export async function saveCustomerHall(hall: HallSummary, accessToken?: string | null) {
  if (useMockCustomerSavedHalls || !accessToken) return saveLocalHall(hall);

  try {
    const response = await apiRequest<unknown>(`/customer/saved-halls/${encodeURIComponent(hall.id)}`, {
      method: "PUT",
      token: accessToken,
      body: JSON.stringify({ hallId: hall.id })
    });
    const savedHall = toHallSummary(response) ?? hall;
    saveLocalHall(savedHall);
    return savedHall;
  } catch (exception) {
    if (exception instanceof ApiError && [401, 403, 404].includes(exception.status)) {
      throw exception;
    }
    return saveLocalHall(hall);
  }
}

export async function removeCustomerSavedHall(hallId: string, accessToken?: string | null) {
  if (useMockCustomerSavedHalls || !accessToken) return removeLocalHall(hallId);

  try {
    await apiRequest<void>(`/customer/saved-halls/${encodeURIComponent(hallId)}`, {
      method: "DELETE",
      token: accessToken
    });
    return removeLocalHall(hallId);
  } catch (exception) {
    if (exception instanceof ApiError && [401, 403, 404].includes(exception.status)) {
      throw exception;
    }
    return removeLocalHall(hallId);
  }
}

export function subscribeToSavedHallChanges(listener: () => void) {
  if (typeof window === "undefined") return () => undefined;

  window.addEventListener(SAVED_HALLS_CHANGED_EVENT, listener);
  window.addEventListener("storage", listener);

  return () => {
    window.removeEventListener(SAVED_HALLS_CHANGED_EVENT, listener);
    window.removeEventListener("storage", listener);
  };
}

function saveLocalHall(hall: HallSummary) {
  const current = getLocalSavedHalls().filter((item) => item.id !== hall.id);
  saveLocalSavedHalls([hall, ...current]);
  notifySavedHallsChanged();
  return hall;
}

function removeLocalHall(hallId: string) {
  saveLocalSavedHalls(getLocalSavedHalls().filter((hall) => hall.id !== hallId));
  notifySavedHallsChanged();
  return hallId;
}

function getLocalSavedHalls() {
  if (typeof window === "undefined") return defaultSavedHalls;

  const stored = window.localStorage.getItem(STORAGE_KEY);
  if (stored === null) return defaultSavedHalls;

  try {
    const parsed = JSON.parse(stored) as unknown;
    return extractSavedHalls(parsed);
  } catch {
    return defaultSavedHalls;
  }
}

function saveLocalSavedHalls(savedHalls: HallSummary[]) {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(dedupeHalls(savedHalls)));
}

function notifySavedHallsChanged() {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new CustomEvent(SAVED_HALLS_CHANGED_EVENT));
}

function extractSavedHalls(response: unknown): HallSummary[] {
  if (Array.isArray(response)) return dedupeHalls(response.map(toHallSummary).filter(Boolean) as HallSummary[]);
  if (!isRecord(response)) return [];

  const candidates = [response.items, response.content, response.data, response.results, response.halls, response.savedHalls];
  const list = candidates.find(Array.isArray);
  if (Array.isArray(list)) return dedupeHalls(list.map(toHallSummary).filter(Boolean) as HallSummary[]);
  if (isRecord(response.data)) return extractSavedHalls(response.data);

  const single = toHallSummary(response);
  return single ? [single] : [];
}

function toHallSummary(value: unknown): HallSummary | undefined {
  const record = unwrapHallRecord(value);
  if (!record) return undefined;

  const id = stringValue(record, ["id", "slug", "hallId", "hall_id"]);
  if (!id) return undefined;

  const fallback = getHallById(id) ?? mockHalls[0];
  const name = stringValue(record, ["name", "hallName", "hall_name", "title"]) ?? fallback.name;
  const imageUrl = stringValue(record, ["imageUrl", "image_url", "coverImageUrl", "cover_image_url", "primaryImageUrl", "mediaUrl"])
    ?? fallback.imageUrl;

  return {
    id,
    name,
    city: stringValue(record, ["city"]) ?? fallback.city,
    area: stringValue(record, ["area", "locality", "location"]) ?? fallback.area,
    capacity: numberValue(record, ["capacity", "capacityMax", "maxCapacity", "capacity_max"]) ?? fallback.capacity,
    startingPrice: numberValue(record, ["startingPrice", "starting_price", "amount", "price"]) ?? fallback.startingPrice,
    rating: numberValue(record, ["rating", "ratings", "averageRating", "average_rating"]) ?? fallback.rating,
    reviewCount: numberValue(record, ["reviewCount", "review_count", "reviewsCount", "totalReviews"]) ?? fallback.reviewCount,
    imageUrl,
    galleryUrls: arrayOfStrings(record.galleryUrls) ?? arrayOfStrings(record.gallery) ?? fallback.galleryUrls,
    venueType: venueType(record) ?? fallback.venueType,
    amenities: arrayOfStrings(record.amenities) ?? fallback.amenities,
    isVerified: booleanValue(record, ["isVerified", "is_verified", "verified"]) ?? fallback.isVerified,
    availableThisMonth: booleanValue(record, ["availableThisMonth", "available_this_month", "hasAvailability"]) ?? fallback.availableThisMonth,
    description: stringValue(record, ["description", "summary"]) ?? fallback.description
  };
}

function unwrapHallRecord(value: unknown): Record<string, unknown> | undefined {
  if (!isRecord(value)) return undefined;
  if (isRecord(value.hall)) return value.hall;
  if (isRecord(value.venue)) return value.venue;
  if (isRecord(value.savedHall)) return value.savedHall;
  if (isRecord(value.data)) return unwrapHallRecord(value.data);
  return value;
}

function dedupeHalls(savedHalls: HallSummary[]) {
  const byId = new Map<string, HallSummary>();
  savedHalls.forEach((hall) => byId.set(hall.id, hall));
  return Array.from(byId.values());
}

function venueType(record: Record<string, unknown>): VenueType | undefined {
  const value = stringValue(record, ["venueType", "venue_type", "hallType", "type"]);
  if (!value) return undefined;

  const normalized = value.trim().toUpperCase().replace(/[\s-]+/g, "_");
  if (normalized === "MARRIAGE_HALL") return "Marriage Hall";
  if (normalized === "BANQUET_HALL") return "Banquet Hall";
  if (normalized === "MINI_HALL") return "Mini Hall";
  if (value === "Marriage Hall" || value === "Banquet Hall" || value === "Mini Hall") return value;
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

function numberValue(record: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "number") return value;
    if (typeof value === "string" && value.trim() && !Number.isNaN(Number(value))) return Number(value);
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

function arrayOfStrings(value: unknown) {
  if (!Array.isArray(value)) return undefined;
  const strings = value.filter((item): item is string => typeof item === "string" && item.trim().length > 0);
  return strings.length ? strings : undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}
