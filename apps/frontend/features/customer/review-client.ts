import { ApiError, apiRequest } from "@/lib/api-client";

const STORAGE_KEY = "venue-aggregator-customer-reviews";
const useMockCustomerReviews = process.env.NEXT_PUBLIC_CUSTOMER_REVIEWS_MODE === "mock";

export type ReviewEligibility = {
  eligible: boolean;
  enquiryId: string;
  hallId: string;
  hallName?: string;
  eventDate: string;
  eventType?: string;
  reason?: string | null;
  submittedReviewId?: string;
};

export type CreateCustomerReviewPayload = {
  enquiryId: string;
  hallId: string;
  rating: number;
  comment: string;
};

export type CustomerReview = CreateCustomerReviewPayload & {
  id: string;
  submittedAt: string;
  verifiedService: boolean;
  status: "PENDING_MODERATION" | "PUBLISHED";
};

export async function getCustomerReviewEligibility(enquiryId: string, accessToken: string | null | undefined, fallback: ReviewEligibility) {
  if (useMockCustomerReviews || !accessToken) return localEligibility(fallback);

  try {
    const response = await apiRequest<unknown>(`/customer/review-eligibility?enquiryId=${encodeURIComponent(enquiryId)}`, {
      token: accessToken
    });
    return toReviewEligibility(response, fallback);
  } catch {
    return localEligibility(fallback);
  }
}

export async function submitCustomerReview(payload: CreateCustomerReviewPayload, accessToken?: string | null) {
  if (useMockCustomerReviews || !accessToken) return createLocalReview(payload);

  try {
    const response = await apiRequest<unknown>("/customer/reviews", {
      method: "POST",
      token: accessToken,
      body: JSON.stringify(payload)
    });
    const review = toCustomerReview(response, payload) ?? createLocalReview(payload);
    cacheLocalReview(review);
    return review;
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 409].includes(exception.status)) {
      throw exception;
    }
    return createLocalReview(payload);
  }
}

function localEligibility(fallback: ReviewEligibility): ReviewEligibility {
  const existingReview = getLocalReviews().find((review) => review.enquiryId === fallback.enquiryId);
  if (!existingReview) return fallback;

  return {
    ...fallback,
    eligible: false,
    reason: "You already submitted a review for this completed service.",
    submittedReviewId: existingReview.id
  };
}

function createLocalReview(payload: CreateCustomerReviewPayload): CustomerReview {
  const review: CustomerReview = {
    ...payload,
    id: `REV-${Date.now().toString().slice(-6)}`,
    submittedAt: new Date().toISOString(),
    verifiedService: true,
    status: "PENDING_MODERATION"
  };
  cacheLocalReview(review);
  return review;
}

function cacheLocalReview(review: CustomerReview) {
  if (typeof window === "undefined") return;
  const existing = getLocalReviews().filter((item) => item.id !== review.id && item.enquiryId !== review.enquiryId);
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify([review, ...existing]));
}

function getLocalReviews(): CustomerReview[] {
  if (typeof window === "undefined") return [];
  try {
    const parsed = JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? "[]") as unknown;
    return Array.isArray(parsed) ? parsed.map((item) => toCustomerReview(item)).filter(Boolean) as CustomerReview[] : [];
  } catch {
    return [];
  }
}

function toReviewEligibility(value: unknown, fallback: ReviewEligibility): ReviewEligibility {
  const record = unwrapRecord(value);
  if (!record) return localEligibility(fallback);

  const eligible = booleanValue(record, ["eligible"]) ?? fallback.eligible;
  const enquiryId = stringValue(record, ["enquiryId", "enquiry_id"]) ?? fallback.enquiryId;

  return localEligibility({
    eligible,
    enquiryId,
    hallId: stringValue(record, ["hallId", "hall_id"]) ?? fallback.hallId,
    hallName: stringValue(record, ["hallName", "hall_name", "venue"]) ?? fallback.hallName,
    eventDate: stringValue(record, ["eventDate", "event_date"]) ?? fallback.eventDate,
    eventType: stringValue(record, ["eventType", "event_type"]) ?? fallback.eventType,
    reason: stringValue(record, ["reason", "message"]) ?? fallback.reason ?? null,
    submittedReviewId: stringValue(record, ["submittedReviewId", "reviewId", "review_id"])
  });
}

function toCustomerReview(value: unknown, fallback?: CreateCustomerReviewPayload): CustomerReview | undefined {
  const record = unwrapRecord(value);
  if (!record) return undefined;

  const id = stringValue(record, ["id", "reviewId", "review_id"]);
  const enquiryId = stringValue(record, ["enquiryId", "enquiry_id"]) ?? fallback?.enquiryId;
  const hallId = stringValue(record, ["hallId", "hall_id"]) ?? fallback?.hallId;
  const rating = numberValue(record, ["rating", "stars", "score"]) ?? fallback?.rating;
  const comment = stringValue(record, ["comment", "review", "message"]) ?? fallback?.comment;

  if (!id || !enquiryId || !hallId || !rating || !comment) return undefined;

  return {
    id,
    enquiryId,
    hallId,
    rating,
    comment,
    submittedAt: stringValue(record, ["submittedAt", "createdAt", "created_at"]) ?? new Date().toISOString(),
    verifiedService: booleanValue(record, ["verifiedService", "verified_service", "verified"]) ?? true,
    status: statusValue(record) ?? "PENDING_MODERATION"
  };
}

function statusValue(record: Record<string, unknown>): CustomerReview["status"] | undefined {
  const value = stringValue(record, ["status"]);
  if (value === "PENDING_MODERATION" || value === "PUBLISHED") return value;
  if (value === "PENDING" || value === "UNDER_REVIEW") return "PENDING_MODERATION";
  return undefined;
}

function unwrapRecord(value: unknown): Record<string, unknown> | undefined {
  if (!isRecord(value)) return undefined;
  if (isRecord(value.data)) return value.data;
  if (isRecord(value.review)) return value.review;
  if (isRecord(value.eligibility)) return value.eligibility;
  return value;
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
