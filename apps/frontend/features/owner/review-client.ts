import { apiRequest } from "@/lib/api-client";

const useMockOwnerReviews = process.env.NEXT_PUBLIC_OWNER_REVIEWS_MODE === "mock";

export type OwnerReview = {
  id: string;
  customerName: string;
  rating: number;
  eventType: string;
  eventDate: string;
  comment: string;
  verifiedService: boolean;
  reply?: string;
};

export type OwnerReviewsResult = {
  reviews: OwnerReview[];
  averageRating: number;
  totalReviews: number;
  source: "api" | "mock";
};

export async function getOwnerHallReviews(hallId: string, accessToken: string | null | undefined, fallback: OwnerReview[]): Promise<OwnerReviewsResult> {
  if (useMockOwnerReviews || !accessToken) return buildReviewResult(fallback, "mock");

  try {
    const response = await apiRequest<unknown>(`/owner/halls/${encodeURIComponent(hallId)}/reviews`, {
      token: accessToken
    });
    const reviews = extractReviews(response);
    return buildReviewResult(reviews.length ? reviews : fallback, "api");
  } catch {
    return buildReviewResult(fallback, "mock");
  }
}

function buildReviewResult(reviews: OwnerReview[], source: "api" | "mock"): OwnerReviewsResult {
  const totalRating = reviews.reduce((sum, review) => sum + review.rating, 0);
  return {
    reviews,
    averageRating: reviews.length ? Number((totalRating / reviews.length).toFixed(1)) : 0,
    totalReviews: reviews.length,
    source
  };
}

function extractReviews(response: unknown) {
  if (Array.isArray(response)) return response.map(toOwnerReview).filter(Boolean) as OwnerReview[];
  if (!isRecord(response)) return [];

  const candidates = [response.items, response.content, response.data, response.results, response.reviews];
  const list = candidates.find(Array.isArray);
  return Array.isArray(list) ? list.map(toOwnerReview).filter(Boolean) as OwnerReview[] : [];
}

function toOwnerReview(value: unknown): OwnerReview | undefined {
  if (!isRecord(value)) return undefined;

  const id = stringValue(value, ["id", "reviewId", "review_id"]);
  const customerName = stringValue(value, ["customerName", "customer_name", "name", "userName"]);
  const comment = stringValue(value, ["comment", "review", "message"]);
  const rating = numberValue(value, ["rating", "stars", "score"]);

  if (!id || !customerName || !comment || !rating) return undefined;

  return {
    id,
    customerName,
    rating,
    eventType: stringValue(value, ["eventType", "event_type", "event"]) ?? "Completed event",
    eventDate: stringValue(value, ["eventDate", "event_date", "date", "createdAt", "created_at"]) ?? "",
    comment,
    verifiedService: booleanValue(value, ["verifiedService", "verified_service", "verified"]) ?? true,
    reply: stringValue(value, ["reply", "ownerReply", "owner_reply"])
  };
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
