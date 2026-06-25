import { ApiError, apiRequest } from "@/lib/api-client";
import type { HallSummary, VenueType } from "@/features/halls/types";

const STORAGE_KEY = "venue-aggregator-owner-listings";
const useMockOwnerListings = process.env.NEXT_PUBLIC_OWNER_LISTINGS_MODE === "mock";

export type OwnerListingStatus = "DRAFT" | "PENDING_APPROVAL" | "APPROVED" | "REJECTED";

export type OwnerHallListing = HallSummary & {
  status: OwnerListingStatus;
  rejectionReason?: string;
  updatedAt?: string;
  version?: number | string;
};

export type OwnerHallUpdatePayload = {
  name: string;
  city: string;
  area: string;
  capacity: number;
  startingPrice: number;
  venueType: VenueType;
  amenities: string[];
  description: string;
};

export async function getOwnerHallListing(hallId: string, accessToken: string | null | undefined, fallback: OwnerHallListing) {
  if (useMockOwnerListings || !accessToken) return getLocalOwnerListing(hallId, fallback);

  try {
    const response = await apiRequest<unknown>(`/owner/halls/${encodeURIComponent(hallId)}`, {
      token: accessToken
    });
    const listing = toOwnerHallListing(response, fallback);
    saveLocalOwnerListing(listing);
    return listing;
  } catch {
    return getLocalOwnerListing(hallId, fallback);
  }
}

export async function updateOwnerHallListing(
  hallId: string,
  payload: OwnerHallUpdatePayload,
  accessToken: string | null | undefined,
  fallback: OwnerHallListing
) {
  if (useMockOwnerListings || !accessToken) return saveLocalOwnerListing(mergeListing(fallback, payload, "DRAFT"));

  try {
    const response = await apiRequest<unknown>(`/owner/halls/${encodeURIComponent(hallId)}`, {
      method: "PUT",
      token: accessToken,
      body: JSON.stringify(toOwnerHallRequest(payload))
    });
    const listing = toOwnerHallListing(response, mergeListing(fallback, payload, "DRAFT"));
    saveLocalOwnerListing(listing);
    return listing;
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 409].includes(exception.status)) {
      throw exception;
    }
    return saveLocalOwnerListing(mergeListing(fallback, payload, "DRAFT"));
  }
}

export async function submitOwnerHallListing(hallId: string, accessToken: string | null | undefined, fallback: OwnerHallListing) {
  if (useMockOwnerListings || !accessToken) return saveLocalOwnerListing({ ...fallback, status: "PENDING_APPROVAL", updatedAt: new Date().toISOString() });

  try {
    const response = await apiRequest<unknown>(`/owner/halls/${encodeURIComponent(hallId)}/submit`, {
      method: "POST",
      token: accessToken
    });
    const listing = toOwnerHallListing(response, { ...fallback, status: "PENDING_APPROVAL" });
    saveLocalOwnerListing(listing);
    return listing;
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 409].includes(exception.status)) {
      throw exception;
    }
    return saveLocalOwnerListing({ ...fallback, status: "PENDING_APPROVAL", updatedAt: new Date().toISOString() });
  }
}

function getLocalOwnerListing(hallId: string, fallback: OwnerHallListing) {
  if (typeof window === "undefined") return fallback;
  const listing = readOwnerListingStore()[hallId];
  return listing ?? fallback;
}

function saveLocalOwnerListing(listing: OwnerHallListing) {
  if (typeof window === "undefined") return listing;
  const store = readOwnerListingStore();
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...store, [listing.id]: listing }));
  return listing;
}

function readOwnerListingStore(): Record<string, OwnerHallListing> {
  if (typeof window === "undefined") return {};

  try {
    const parsed = JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? "{}") as unknown;
    if (!isRecord(parsed)) return {};

    return Object.fromEntries(
      Object.entries(parsed)
        .map(([id, value]) => [id, toOwnerHallListing(value, undefined)])
        .filter((entry): entry is [string, OwnerHallListing] => Boolean(entry[1]))
    );
  } catch {
    return {};
  }
}

function mergeListing(fallback: OwnerHallListing, payload: OwnerHallUpdatePayload, status: OwnerListingStatus): OwnerHallListing {
  return {
    ...fallback,
    ...payload,
    status,
    updatedAt: new Date().toISOString()
  };
}

function toOwnerHallRequest(payload: OwnerHallUpdatePayload) {
  return {
    name: payload.name,
    city: payload.city,
    area: payload.area,
    venueType: toBackendVenueType(payload.venueType),
    capacity: payload.capacity,
    capacityMax: payload.capacity,
    startingPrice: payload.startingPrice,
    amount: payload.startingPrice,
    amenities: payload.amenities,
    description: payload.description,
    acAvailable: payload.amenities.includes("Air conditioned"),
    carParking: payload.amenities.includes("Parking") || payload.amenities.includes("Valet parking"),
    diningAvailable: payload.amenities.includes("Dining hall") || payload.amenities.includes("Dining area"),
    generatorAvailable: payload.amenities.includes("Generator") || payload.amenities.includes("Power backup"),
    liftAvailable: payload.amenities.includes("Lift")
  };
}

function toOwnerHallListing(value: unknown, fallback?: OwnerHallListing): OwnerHallListing {
  if (!isRecord(value)) {
    if (fallback) return fallback;
    throw new Error("Invalid owner hall listing response.");
  }

  const record = unwrapRecord(value);
  const id = stringValue(record, ["id", "slug", "hallId"]) ?? fallback?.id;
  if (!id) throw new Error("Owner hall listing is missing an id.");

  const imageUrl = stringValue(record, ["imageUrl", "coverImageUrl", "cover_image_url", "primaryImageUrl"]) ?? fallback?.imageUrl ?? "";

  return {
    id,
    name: stringValue(record, ["name", "hallName", "title"]) ?? fallback?.name ?? "Untitled venue",
    city: stringValue(record, ["city"]) ?? fallback?.city ?? "",
    area: stringValue(record, ["area", "locality", "location"]) ?? fallback?.area ?? "",
    capacity: numberValue(record, ["capacity", "capacityMax", "capacity_max"]) ?? fallback?.capacity ?? 0,
    startingPrice: numberValue(record, ["startingPrice", "amount", "price"]) ?? fallback?.startingPrice ?? 0,
    rating: numberValue(record, ["rating", "averageRating"]) ?? fallback?.rating ?? 0,
    reviewCount: numberValue(record, ["reviewCount", "reviewsCount", "totalReviews"]) ?? fallback?.reviewCount ?? 0,
    imageUrl,
    galleryUrls: galleryUrls(record, imageUrl, fallback?.galleryUrls ?? []),
    venueType: venueType(record) ?? fallback?.venueType ?? "Marriage Hall",
    amenities: amenities(record, fallback?.amenities ?? []),
    isVerified: statusValue(record) === "APPROVED" || fallback?.isVerified || false,
    availableThisMonth: booleanValue(record, ["availableThisMonth", "hasAvailability"]) ?? fallback?.availableThisMonth ?? true,
    description: stringValue(record, ["description", "summary"]) ?? fallback?.description ?? "",
    status: statusValue(record) ?? fallback?.status ?? "DRAFT",
    rejectionReason: stringValue(record, ["rejectionReason", "rejection_reason"]),
    updatedAt: stringValue(record, ["updatedAt", "updated_at"]) ?? fallback?.updatedAt,
    version: numberValue(record, ["version"]) ?? stringValue(record, ["version"]) ?? fallback?.version
  };
}

function unwrapRecord(value: Record<string, unknown>) {
  if (isRecord(value.data)) return value.data;
  if (isRecord(value.hall)) return value.hall;
  if (isRecord(value.listing)) return value.listing;
  return value;
}

function galleryUrls(record: Record<string, unknown>, coverImageUrl: string, fallback: string[]) {
  const direct = arrayOfStrings(record.galleryUrls) ?? arrayOfStrings(record.gallery);
  if (direct?.length) return direct;

  const media = Array.isArray(record.media) ? record.media : [];
  const urls = media
    .filter(isRecord)
    .map((item) => stringValue(item, ["url", "mediaUrl", "imageUrl"]))
    .filter(Boolean) as string[];

  if (urls.length) return urls;
  if (fallback.length) return fallback;
  return coverImageUrl ? [coverImageUrl] : [];
}

function amenities(record: Record<string, unknown>, fallback: string[]) {
  const values = arrayOfStrings(record.amenities);
  if (values?.length) return values;

  const inferred = [
    booleanValue(record, ["acAvailable", "ac_available"]) ? "Air conditioned" : "",
    booleanValue(record, ["carParking", "car_parking"]) ? "Parking" : "",
    booleanValue(record, ["diningAvailable", "dining_available"]) ? "Dining hall" : "",
    booleanValue(record, ["generatorAvailable", "generator_available"]) ? "Generator" : "",
    booleanValue(record, ["liftAvailable", "lift_available"]) ? "Lift" : ""
  ].filter(Boolean);

  return inferred.length ? inferred : fallback;
}

function venueType(record: Record<string, unknown>): VenueType | undefined {
  const value = stringValue(record, ["venueType", "hallType", "type"]);
  if (!value) return undefined;

  const normalized = value.trim().toUpperCase().replace(/[\s-]+/g, "_");
  if (normalized === "MARRIAGE_HALL") return "Marriage Hall";
  if (normalized === "BANQUET_HALL") return "Banquet Hall";
  if (normalized === "MINI_HALL") return "Mini Hall";
  if (value === "Marriage Hall" || value === "Banquet Hall" || value === "Mini Hall") return value;
  return undefined;
}

function toBackendVenueType(value: VenueType) {
  return value.toUpperCase().replace(/\s+/g, "_");
}

function statusValue(record: Record<string, unknown>): OwnerListingStatus | undefined {
  const value = stringValue(record, ["status", "listingStatus"]);
  const normalized = value?.trim().toUpperCase();
  if (normalized === "DRAFT") return "DRAFT";
  if (normalized === "PENDING" || normalized === "PENDING_APPROVAL" || normalized === "UNDER_REVIEW") return "PENDING_APPROVAL";
  if (normalized === "APPROVED") return "APPROVED";
  if (normalized === "REJECTED") return "REJECTED";
  return undefined;
}

function arrayOfStrings(value: unknown) {
  return Array.isArray(value) && value.every((item) => typeof item === "string") ? value : undefined;
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

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}
