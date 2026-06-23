import { apiRequest } from "@/lib/api-client";
import { getHallById as getMockHallById, halls as mockHalls } from "./mock-data";
import type { HallSummary, VenueType } from "./types";

export type HallSort = "recommended" | "rating" | "price-low" | "capacity";

export type HallSearchFilters = {
  q?: string;
  eventDate?: string;
  minCapacity?: number;
  venueType?: VenueType;
  sort?: HallSort;
};

export type HallSearchResult = {
  halls: HallSummary[];
  total: number;
  source: "api" | "mock";
};

type ApiHallRecord = Record<string, unknown>;

const useMockHalls = process.env.NEXT_PUBLIC_HALLS_MODE === "mock";

export async function searchPublicHalls(filters: HallSearchFilters = {}): Promise<HallSearchResult> {
  if (useMockHalls) return searchMockHalls(filters);

  try {
    const response = await apiRequest<unknown>(`/public/halls${toQueryString(filters)}`);
    const records = extractHallRecords(response);
    const halls = records.map(toHallSummary).filter(Boolean) as HallSummary[];
    return {
      halls,
      total: extractTotal(response) ?? halls.length,
      source: "api"
    };
  } catch {
    return searchMockHalls(filters);
  }
}

export async function getPublicHall(hallId: string): Promise<HallSummary | undefined> {
  if (useMockHalls) return getMockHallById(hallId);

  try {
    const response = await apiRequest<unknown>(`/public/halls/${encodeURIComponent(hallId)}`);
    return toHallSummary(response);
  } catch {
    return getMockHallById(hallId);
  }
}

function toQueryString(filters: HallSearchFilters) {
  const params = new URLSearchParams();
  if (filters.q) params.set("q", filters.q);
  if (filters.eventDate) params.set("eventDate", filters.eventDate);
  if (filters.minCapacity) params.set("minCapacity", String(filters.minCapacity));
  if (filters.venueType) params.set("venueType", toBackendVenueType(filters.venueType));
  if (filters.sort) params.set("sort", toBackendSort(filters.sort));
  params.set("size", "24");

  const query = params.toString();
  return query ? `?${query}` : "";
}

function searchMockHalls(filters: HallSearchFilters): HallSearchResult {
  const query = filters.q?.trim().toLowerCase() ?? "";
  const filtered = mockHalls.filter((hall) => {
    const matchesLocation =
      !query ||
      `${hall.name} ${hall.city} ${hall.area}`.toLowerCase().includes(query);
    const matchesGuests = !filters.minCapacity || hall.capacity >= filters.minCapacity;
    const matchesType = !filters.venueType || hall.venueType === filters.venueType;
    return matchesLocation && matchesGuests && matchesType;
  });

  const halls = sortHalls(filtered, filters.sort);
  return { halls, total: halls.length, source: "mock" };
}

function sortHalls(halls: HallSummary[], sort: HallSort = "recommended") {
  return [...halls].sort((first, second) => {
    if (sort === "rating") return second.rating - first.rating;
    if (sort === "price-low") return first.startingPrice - second.startingPrice;
    if (sort === "capacity") return second.capacity - first.capacity;
    return Number(second.isVerified) - Number(first.isVerified) || second.rating - first.rating;
  });
}

function extractHallRecords(response: unknown): ApiHallRecord[] {
  if (Array.isArray(response)) return response.filter(isRecord);
  if (!isRecord(response)) return [];

  const candidates = [response.items, response.content, response.data, response.results, response.halls];
  const list = candidates.find(Array.isArray);
  return Array.isArray(list) ? list.filter(isRecord) : [];
}

function extractTotal(response: unknown) {
  if (!isRecord(response)) return undefined;
  return numberValue(response, ["total", "totalElements", "totalCount", "count"]);
}

function toHallSummary(value: unknown): HallSummary | undefined {
  if (!isRecord(value)) return undefined;

  const id = stringValue(value, ["id", "slug", "hallId"]);
  const name = stringValue(value, ["name", "hallName", "title"]);
  if (!id || !name) return undefined;

  const fallback = getMockHallById(id) ?? mockHalls[0];
  const city = stringValue(value, ["city"]) ?? fallback.city;
  const area = stringValue(value, ["area", "locality", "location"]) ?? fallback.area;
  const imageUrl = stringValue(value, ["imageUrl", "coverImageUrl", "cover_image_url", "primaryImageUrl"])
    ?? fallback.imageUrl;

  return {
    id,
    name,
    city,
    area,
    capacity: numberValue(value, ["capacity", "capacityMax", "maxCapacity", "capacity_max"]) ?? fallback.capacity,
    startingPrice: numberValue(value, ["startingPrice", "amount", "price"], ["pricing", "startingPrice"])
      ?? fallback.startingPrice,
    rating: numberValue(value, ["rating", "ratings", "averageRating"]) ?? fallback.rating,
    reviewCount: numberValue(value, ["reviewCount", "reviewsCount", "totalReviews"]) ?? fallback.reviewCount,
    imageUrl,
    galleryUrls: galleryUrls(value, imageUrl, fallback.galleryUrls),
    venueType: venueType(value) ?? fallback.venueType,
    amenities: amenities(value, fallback.amenities),
    isVerified: booleanValue(value, ["isVerified", "verified"]) ?? statusIsApproved(value) ?? fallback.isVerified,
    availableThisMonth: booleanValue(value, ["availableThisMonth", "hasAvailability"]) ?? fallback.availableThisMonth,
    description: stringValue(value, ["description", "summary"]) ?? fallback.description
  };
}

function galleryUrls(record: ApiHallRecord, coverImageUrl: string, fallback: string[]) {
  const direct = arrayOfStrings(record.galleryUrls) ?? arrayOfStrings(record.gallery);
  if (direct?.length) return direct;

  const media = Array.isArray(record.media) ? record.media : [];
  const urls = media
    .filter(isRecord)
    .map((item) => stringValue(item, ["url", "mediaUrl", "imageUrl"]))
    .filter(Boolean) as string[];

  if (urls.length) return urls;
  return fallback.length ? fallback : [coverImageUrl];
}

function venueType(record: ApiHallRecord): VenueType | undefined {
  const value = stringValue(record, ["venueType", "hallType", "type"]);
  if (!value) return undefined;

  const normalized = value.trim().toUpperCase().replace(/[\s-]+/g, "_");
  if (normalized === "MARRIAGE_HALL") return "Marriage Hall";
  if (normalized === "BANQUET_HALL") return "Banquet Hall";
  if (normalized === "MINI_HALL") return "Mini Hall";
  if (value === "Marriage Hall" || value === "Banquet Hall" || value === "Mini Hall") return value;
  return undefined;
}

function amenities(record: ApiHallRecord, fallback: string[]) {
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

function statusIsApproved(record: ApiHallRecord) {
  const status = stringValue(record, ["status", "listingStatus"]);
  return status ? status.toUpperCase() === "APPROVED" : undefined;
}

function toBackendVenueType(value: VenueType) {
  return value.toUpperCase().replace(/\s+/g, "_");
}

function toBackendSort(value: HallSort) {
  if (value === "rating") return "RATING_DESC";
  if (value === "price-low") return "PRICE_ASC";
  if (value === "capacity") return "CAPACITY_DESC";
  return "RELEVANCE";
}

function stringValue(record: ApiHallRecord, keys: string[]) {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string" && value.trim()) return value;
    if (typeof value === "number") return String(value);
  }
  return undefined;
}

function numberValue(record: ApiHallRecord, keys: string[], nested?: [string, string]) {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "number") return value;
    if (typeof value === "string" && value.trim() && !Number.isNaN(Number(value))) return Number(value);
  }

  const nestedRecord = nested ? record[nested[0]] : undefined;
  if (isRecord(nestedRecord) && nested) {
    return numberValue(nestedRecord, [nested[1]]);
  }

  return undefined;
}

function booleanValue(record: ApiHallRecord, keys: string[]) {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "boolean") return value;
  }
  return undefined;
}

function arrayOfStrings(value: unknown) {
  if (!Array.isArray(value)) return undefined;
  const strings = value.filter((item): item is string => typeof item === "string" && item.trim().length > 0);
  return strings.length ? strings : undefined;
}

function isRecord(value: unknown): value is ApiHallRecord {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}
