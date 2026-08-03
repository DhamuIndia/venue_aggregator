import { apiRequest } from "@/lib/api-client";

export type VendorReview = {
  id: string;
  customerName: string;
  rating: number;
  eventType: string;
  eventDate: string;
  comment: string;
  verifiedService: boolean;
};

export type VendorReviewsResult = {
  reviews: VendorReview[];
  reviewCount: number;
  averageRating: number;
};

export async function getVendorReviews(accessToken?: string | null): Promise<VendorReviewsResult> {
  if (!accessToken) return { reviews: [], reviewCount: 0, averageRating: 0 };
  const response = await apiRequest<unknown>("/vendor/reviews", { token: accessToken });
  const record = isRecord(response) ? response : {};
  const source = Array.isArray(record.reviews) ? record.reviews : [];
  const reviews = source.map(toVendorReview).filter(Boolean) as VendorReview[];
  return {
    reviews,
    reviewCount: numberValue(record.reviewCount) ?? reviews.length,
    averageRating: numberValue(record.averageRating) ?? (reviews.length ? Number((reviews.reduce((sum, review) => sum + review.rating, 0) / reviews.length).toFixed(1)) : 0)
  };
}

function toVendorReview(value: unknown): VendorReview | undefined {
  if (!isRecord(value)) return undefined;
  const id = stringValue(value.id);
  const customerName = stringValue(value.customerName);
  const comment = stringValue(value.comment);
  const rating = numberValue(value.rating);
  if (!id || !customerName || !comment || !rating) return undefined;
  return { id, customerName, comment, rating, eventType: stringValue(value.eventType) ?? "Completed event", eventDate: stringValue(value.eventDate) ?? "", verifiedService: value.verifiedService !== false };
}

function isRecord(value: unknown): value is Record<string, unknown> { return Boolean(value) && typeof value === "object" && !Array.isArray(value); }
function stringValue(value: unknown) { return typeof value === "string" && value.trim() ? value : typeof value === "number" ? String(value) : undefined; }
function numberValue(value: unknown) { return typeof value === "number" ? value : typeof value === "string" && value.trim() && !Number.isNaN(Number(value)) ? Number(value) : undefined; }
