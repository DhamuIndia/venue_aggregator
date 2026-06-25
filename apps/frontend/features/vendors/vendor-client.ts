import { apiRequest } from "@/lib/api-client";
import { getVendorById as getMockVendorById, vendors as mockVendors } from "./mock-data";
import type { VendorCategory, VendorPackage, VendorReview, VendorSummary } from "./types";

export type VendorSort = "recommended" | "rating" | "price-low";

export type VendorSearchFilters = {
  q?: string;
  location?: string;
  category?: VendorCategory;
  sort?: VendorSort;
};

export type VendorSearchResult = {
  vendors: VendorSummary[];
  total: number;
  source: "api" | "mock";
};

type ApiVendorRecord = Record<string, unknown>;

const useMockVendors = process.env.NEXT_PUBLIC_VENDORS_MODE === "mock";

export async function searchPublicVendors(filters: VendorSearchFilters = {}): Promise<VendorSearchResult> {
  if (useMockVendors) return searchMockVendors(filters);

  try {
    const response = await apiRequest<unknown>(`/public/vendors${toQueryString(filters)}`);
    const records = extractVendorRecords(response);
    const vendors = records.map(toVendorSummary).filter(Boolean) as VendorSummary[];
    return {
      vendors,
      total: extractTotal(response) ?? vendors.length,
      source: "api"
    };
  } catch {
    return searchMockVendors(filters);
  }
}

export async function getPublicVendor(vendorId: string): Promise<VendorSummary | undefined> {
  if (useMockVendors) return getMockVendorById(vendorId);

  try {
    const response = await apiRequest<unknown>(`/public/vendors/${encodeURIComponent(vendorId)}`);
    return toVendorSummary(response) ?? getMockVendorById(vendorId);
  } catch {
    return getMockVendorById(vendorId);
  }
}

function toQueryString(filters: VendorSearchFilters) {
  const params = new URLSearchParams();
  if (filters.q) params.set("q", filters.q);
  if (filters.location) {
    params.set("city", filters.location);
    params.set("area", filters.location);
  }
  if (filters.category) params.set("category", filters.category);
  if (filters.sort) params.set("sort", toBackendSort(filters.sort));
  params.set("size", "24");

  const query = params.toString();
  return query ? `?${query}` : "";
}

function searchMockVendors(filters: VendorSearchFilters): VendorSearchResult {
  const term = filters.q?.trim().toLowerCase() ?? "";
  const place = filters.location?.trim().toLowerCase() ?? "";
  const filtered = mockVendors.filter((vendor) => {
    const matchesTerm = !term || `${vendor.businessName} ${vendor.services.join(" ")} ${vendor.category}`.toLowerCase().includes(term);
    const matchesLocation = !place || `${vendor.city} ${vendor.area}`.toLowerCase().includes(place);
    return matchesTerm && matchesLocation && (!filters.category || vendor.category === filters.category);
  });

  const vendors = sortVendors(filtered, filters.sort);
  return { vendors, total: vendors.length, source: "mock" };
}

function sortVendors(vendors: VendorSummary[], sort: VendorSort = "recommended") {
  return [...vendors].sort((first, second) => {
    if (sort === "rating") return second.rating - first.rating;
    if (sort === "price-low") return first.startingPrice - second.startingPrice;
    return Number(second.verified) - Number(first.verified) || second.rating - first.rating;
  });
}

function extractVendorRecords(response: unknown): ApiVendorRecord[] {
  if (Array.isArray(response)) return response.filter(isRecord);
  if (!isRecord(response)) return [];

  const candidates = [response.items, response.content, response.data, response.results, response.vendors];
  const list = candidates.find(Array.isArray);
  return Array.isArray(list) ? list.filter(isRecord) : [];
}

function extractTotal(response: unknown) {
  if (!isRecord(response)) return undefined;
  return numberValue(response, ["total", "totalElements", "totalCount", "count"]);
}

function toVendorSummary(value: unknown): VendorSummary | undefined {
  if (!isRecord(value)) return undefined;

  const id = stringValue(value, ["id", "slug", "vendorId", "vendor_id"]);
  const businessName = stringValue(value, ["businessName", "business_name", "name"]);
  if (!id || !businessName) return undefined;

  const fallback = getMockVendorById(id) ?? mockVendors[0];
  const category = vendorCategory(value) ?? fallback.category;
  const imageUrl = stringValue(value, ["imageUrl", "coverImageUrl", "cover_image_url", "primaryImageUrl"]) ?? fallback.imageUrl;

  return {
    id,
    businessName,
    ownerName: stringValue(value, ["ownerName", "owner_name", "contactName"]) ?? fallback.ownerName,
    category,
    city: stringValue(value, ["city"]) ?? fallback.city,
    area: stringValue(value, ["area", "locality", "location"]) ?? fallback.area,
    rating: numberValue(value, ["rating", "ratings", "averageRating", "average_rating"]) ?? fallback.rating,
    reviewCount: numberValue(value, ["reviewCount", "reviewsCount", "totalReviews", "review_count"]) ?? fallback.reviewCount,
    startingPrice: numberValue(value, ["startingPrice", "starting_price", "price", "basePrice"], ["pricing", "startingPrice"]) ?? fallback.startingPrice,
    imageUrl,
    galleryUrls: galleryUrls(value, imageUrl, fallback.galleryUrls),
    verified: booleanValue(value, ["verified", "isVerified", "identityVerified"]) ?? statusIsApproved(value) ?? fallback.verified,
    responseTime: stringValue(value, ["responseTime", "response_time", "typicalResponseTime"]) ?? fallback.responseTime,
    completedEvents: numberValue(value, ["completedEvents", "completed_events", "eventsCompleted"]) ?? fallback.completedEvents,
    services: arrayOfStrings(value.services) ?? arrayOfStrings(value.serviceNames) ?? fallback.services,
    description: stringValue(value, ["description", "summary", "about"]) ?? fallback.description,
    packages: packages(value, fallback.packages),
    reviews: reviews(value, fallback.reviews)
  };
}

function galleryUrls(record: ApiVendorRecord, coverImageUrl: string, fallback: string[]) {
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

function packages(record: ApiVendorRecord, fallback: VendorPackage[]) {
  const list = Array.isArray(record.packages) ? record.packages : Array.isArray(record.servicePackages) ? record.servicePackages : [];
  const mapped = list.map(toVendorPackage).filter(Boolean) as VendorPackage[];
  return mapped.length ? mapped : fallback;
}

function toVendorPackage(value: unknown): VendorPackage | undefined {
  if (!isRecord(value)) return undefined;
  const id = stringValue(value, ["id", "packageId", "package_id"]);
  const name = stringValue(value, ["name", "packageName", "package_name"]);
  if (!id || !name) return undefined;

  return {
    id,
    name,
    description: stringValue(value, ["description", "summary"]) ?? "",
    price: numberValue(value, ["price", "amount", "startingPrice"]) ?? 0,
    includes: arrayOfStrings(value.includes) ?? arrayOfStrings(value.features) ?? []
  };
}

function reviews(record: ApiVendorRecord, fallback: VendorReview[]) {
  const list = Array.isArray(record.reviews) ? record.reviews : [];
  const mapped = list.map(toVendorReview).filter(Boolean) as VendorReview[];
  return mapped.length ? mapped : fallback;
}

function toVendorReview(value: unknown): VendorReview | undefined {
  if (!isRecord(value)) return undefined;
  const id = stringValue(value, ["id", "reviewId", "review_id"]);
  const customerName = stringValue(value, ["customerName", "customer_name", "name"]);
  const comment = stringValue(value, ["comment", "review", "message"]);
  const rating = numberValue(value, ["rating", "stars", "score"]);
  if (!id || !customerName || !comment || !rating) return undefined;

  return {
    id,
    customerName,
    rating,
    eventType: stringValue(value, ["eventType", "event_type", "event"]) ?? "Completed event",
    comment,
    eventDate: stringValue(value, ["eventDate", "event_date", "date", "createdAt", "created_at"]) ?? "",
    verifiedService: booleanValue(value, ["verifiedService", "verified_service", "verified"]) ?? true
  };
}

function vendorCategory(record: ApiVendorRecord): VendorCategory | undefined {
  const value = stringValue(record, ["category", "vendorCategory", "vendor_category"]);
  if (!value) return undefined;

  const normalized = value.trim().toUpperCase().replace(/[\s-]+/g, "_").replace(/&/g, "AND");
  if (normalized === "CATERING") return "CATERING";
  if (normalized === "DECORATION" || normalized === "DECOR") return "DECORATION";
  if (normalized === "PHOTOGRAPHY") return "PHOTOGRAPHY";
  if (normalized === "BRIDAL_MAKEUP" || normalized === "MAKEUP") return "BRIDAL_MAKEUP";
  if (normalized === "MUSIC_AND_DJ" || normalized === "MUSIC_DJ" || normalized === "DJ") return "MUSIC_AND_DJ";
  if (normalized === "EVENT_PLANNING" || normalized === "PLANNING") return "EVENT_PLANNING";
  return undefined;
}

function statusIsApproved(record: ApiVendorRecord) {
  const status = stringValue(record, ["status", "listingStatus", "listing_status", "approvalStatus"]);
  return status ? status.toUpperCase() === "APPROVED" : undefined;
}

function toBackendSort(value: VendorSort) {
  if (value === "rating") return "RATING_DESC";
  if (value === "price-low") return "PRICE_ASC";
  return "RELEVANCE";
}

function stringValue(record: ApiVendorRecord, keys: string[]) {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string" && value.trim()) return value;
    if (typeof value === "number") return String(value);
  }
  return undefined;
}

function numberValue(record: ApiVendorRecord, keys: string[], nested?: [string, string]) {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "number") return value;
    if (typeof value === "string" && value.trim() && !Number.isNaN(Number(value))) return Number(value);
  }

  const nestedRecord = nested ? record[nested[0]] : undefined;
  if (isRecord(nestedRecord) && nested) return numberValue(nestedRecord, [nested[1]]);

  return undefined;
}

function booleanValue(record: ApiVendorRecord, keys: string[]) {
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

function isRecord(value: unknown): value is ApiVendorRecord {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}
