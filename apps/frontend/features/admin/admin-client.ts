import { ApiError, apiRequest } from "@/lib/api-client";
import {
  adminEnquiries,
  auditEvents,
  initialReportedReviews,
  initialVendorApplications,
  initialVenueApplications,
  type AdminEnquiry,
  type ModerationStatus,
  type ReportedReview,
  type VendorApplication,
  type VenueApplication
} from "./mock-data";

const useMockAdmin = process.env.NEXT_PUBLIC_ADMIN_MODE === "mock";

type AdminQueueResult = {
  venues: VenueApplication[];
  vendors: VendorApplication[];
  reviews: ReportedReview[];
  enquiries: AdminEnquiry[];
  auditEvents: typeof auditEvents;
  source: "api" | "mock";
};

export async function getAdminQueues(accessToken?: string | null): Promise<AdminQueueResult> {
  if (useMockAdmin || !accessToken) return mockResult();

  try {
    const [venues, vendors, reviews, enquiries, events] = await Promise.all([
      getAdminVenues(accessToken),
      getAdminVendors(accessToken),
      getAdminReviews(accessToken),
      getAdminEnquiries(accessToken),
      getAdminAuditEvents(accessToken)
    ]);

    return { venues, vendors, reviews, enquiries, auditEvents: events, source: "api" };
  } catch {
    return mockResult();
  }
}

export async function reviewAdminHall(id: string, decision: Exclude<ModerationStatus, "PENDING_APPROVAL">, reason: string, accessToken?: string | null) {
  if (useMockAdmin || !accessToken) return updateMockVenue(id, decision);

  try {
    const response = await apiRequest<unknown>(`/admin/halls/${encodeURIComponent(id)}/review`, {
      method: "PATCH",
      token: accessToken,
      body: JSON.stringify({ decision, reason })
    });
    return toVenueApplication(response) ?? updateMockVenue(id, decision);
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 404, 409].includes(exception.status)) {
      throw exception;
    }
    return updateMockVenue(id, decision);
  }
}

export async function reviewAdminVendor(id: string, decision: Exclude<ModerationStatus, "PENDING_APPROVAL">, reason: string, accessToken?: string | null) {
  if (useMockAdmin || !accessToken) return updateMockVendor(id, decision);

  try {
    const response = await apiRequest<unknown>(`/admin/vendors/${encodeURIComponent(id)}/review`, {
      method: "PATCH",
      token: accessToken,
      body: JSON.stringify({ decision, reason })
    });
    return toVendorApplication(response) ?? updateMockVendor(id, decision);
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 404, 409].includes(exception.status)) {
      throw exception;
    }
    return updateMockVendor(id, decision);
  }
}

export async function moderateAdminReview(id: string, status: ReportedReview["status"], reason: string, accessToken?: string | null) {
  if (useMockAdmin || !accessToken) return updateMockReview(id, status);

  try {
    const response = await apiRequest<unknown>(`/admin/reviews/${encodeURIComponent(id)}/moderation`, {
      method: "PATCH",
      token: accessToken,
      body: JSON.stringify({ status, reason })
    });
    return toReportedReview(response) ?? updateMockReview(id, status);
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 404, 409].includes(exception.status)) {
      throw exception;
    }
    return updateMockReview(id, status);
  }
}

async function getAdminVenues(accessToken: string) {
  const response = await apiRequest<unknown>("/admin/halls?status=PENDING_APPROVAL", { token: accessToken });
  const venues = extractList(response).map(toVenueApplication).filter(Boolean) as VenueApplication[];
  return venues.length ? venues : initialVenueApplications;
}

async function getAdminVendors(accessToken: string) {
  const response = await apiRequest<unknown>("/admin/vendors?status=PENDING_APPROVAL", { token: accessToken });
  const vendors = extractList(response).map(toVendorApplication).filter(Boolean) as VendorApplication[];
  return vendors.length ? vendors : initialVendorApplications;
}

async function getAdminReviews(accessToken: string) {
  const response = await apiRequest<unknown>("/admin/reviews?status=REPORTED", { token: accessToken });
  const reviews = extractList(response).map(toReportedReview).filter(Boolean) as ReportedReview[];
  return reviews.length ? reviews : initialReportedReviews;
}

async function getAdminEnquiries(accessToken: string) {
  const response = await apiRequest<unknown>("/admin/enquiries", { token: accessToken });
  const enquiries = extractList(response).map(toAdminEnquiry).filter(Boolean) as AdminEnquiry[];
  return enquiries.length ? enquiries : adminEnquiries;
}

async function getAdminAuditEvents(accessToken: string) {
  const response = await apiRequest<unknown>("/admin/audit-events", { token: accessToken });
  const events = extractList(response).map(toAuditEvent).filter(Boolean) as typeof auditEvents;
  return events.length ? events : auditEvents;
}

function mockResult(): AdminQueueResult {
  return {
    venues: initialVenueApplications,
    vendors: initialVendorApplications,
    reviews: initialReportedReviews,
    enquiries: adminEnquiries,
    auditEvents,
    source: "mock"
  };
}

function updateMockVenue(id: string, status: Exclude<ModerationStatus, "PENDING_APPROVAL">) {
  return initialVenueApplications.find((venue) => venue.id === id)
    ? { ...initialVenueApplications.find((venue) => venue.id === id)!, status }
    : undefined;
}

function updateMockVendor(id: string, status: Exclude<ModerationStatus, "PENDING_APPROVAL">) {
  return initialVendorApplications.find((vendor) => vendor.id === id)
    ? { ...initialVendorApplications.find((vendor) => vendor.id === id)!, status }
    : undefined;
}

function updateMockReview(id: string, status: ReportedReview["status"]) {
  return initialReportedReviews.find((review) => review.id === id)
    ? { ...initialReportedReviews.find((review) => review.id === id)!, status }
    : undefined;
}

function extractList(response: unknown) {
  if (Array.isArray(response)) return response;
  if (!isRecord(response)) return [];
  const candidates = [response.items, response.content, response.data, response.results, response.venues, response.halls, response.vendors, response.reviews, response.enquiries, response.auditEvents];
  const list = candidates.find(Array.isArray);
  return Array.isArray(list) ? list : [];
}

function toVenueApplication(value: unknown): VenueApplication | undefined {
  if (!isRecord(value)) return undefined;
  const id = stringValue(value, ["id", "hallId", "hall_id", "slug"]);
  const name = stringValue(value, ["name", "hallName", "hall_name", "title"]);
  if (!id || !name) return undefined;

  return {
    id,
    name,
    ownerName: stringValue(value, ["ownerName", "owner_name", "contactName"]) ?? "Owner",
    ownerPhone: stringValue(value, ["ownerPhone", "owner_phone", "phone"]) ?? "",
    location: stringValue(value, ["location", "area", "city"]) ?? "",
    venueType: stringValue(value, ["venueType", "venue_type", "type"]) ?? "Venue",
    capacity: numberValue(value, ["capacity", "capacityMax", "capacity_max"]) ?? 0,
    startingPrice: numberValue(value, ["startingPrice", "starting_price", "price"]) ?? 0,
    submittedAt: stringValue(value, ["submittedAt", "createdAt", "created_at", "updatedAt"]) ?? new Date().toISOString(),
    imageUrl: stringValue(value, ["imageUrl", "coverImageUrl", "cover_image_url"]) ?? initialVenueApplications[0].imageUrl,
    status: moderationStatus(value) ?? "PENDING_APPROVAL",
    documents: documentStatus(value)
  };
}

function toVendorApplication(value: unknown): VendorApplication | undefined {
  if (!isRecord(value)) return undefined;
  const id = stringValue(value, ["id", "vendorId", "vendor_id", "slug"]);
  const businessName = stringValue(value, ["businessName", "business_name", "name"]);
  if (!id || !businessName) return undefined;

  return {
    id,
    businessName,
    contactName: stringValue(value, ["contactName", "contact_name", "ownerName", "owner_name"]) ?? "Vendor",
    category: stringValue(value, ["category", "vendorCategory", "vendor_category"]) ?? "Service",
    city: stringValue(value, ["city", "serviceCity"]) ?? "",
    submittedAt: stringValue(value, ["submittedAt", "createdAt", "created_at", "updatedAt"]) ?? "",
    status: moderationStatus(value) ?? "PENDING_APPROVAL"
  };
}

function toReportedReview(value: unknown): ReportedReview | undefined {
  if (!isRecord(value)) return undefined;
  const id = stringValue(value, ["id", "reviewId", "review_id"]);
  const comment = stringValue(value, ["comment", "review", "message"]);
  if (!id || !comment) return undefined;

  return {
    id,
    hallName: stringValue(value, ["hallName", "hall_name", "venueName", "vendorName"]) ?? "Marketplace listing",
    customerName: stringValue(value, ["customerName", "customer_name", "name"]) ?? "Customer",
    rating: numberValue(value, ["rating", "stars", "score"]) ?? 0,
    comment,
    reportReason: stringValue(value, ["reportReason", "report_reason", "reason"]) ?? "Reported by user",
    verifiedService: booleanValue(value, ["verifiedService", "verified_service", "verified"]) ?? false,
    status: reviewStatus(value) ?? "REPORTED"
  };
}

function toAdminEnquiry(value: unknown): AdminEnquiry | undefined {
  if (!isRecord(value)) return undefined;
  const id = stringValue(value, ["id", "enquiryId", "enquiry_id"]);
  if (!id) return undefined;

  return {
    id,
    hallName: stringValue(value, ["hallName", "hall_name", "venue", "venueName"]) ?? "Venue",
    customerName: stringValue(value, ["customerName", "customer_name", "name"]) ?? "Customer",
    eventDate: stringValue(value, ["eventDate", "event_date"]) ?? "",
    submittedAt: stringValue(value, ["submittedAt", "createdAt", "created_at"]) ?? "",
    status: enquiryStatus(value) ?? "PENDING_OWNER_RESPONSE"
  };
}

function toAuditEvent(value: unknown): (typeof auditEvents)[number] | undefined {
  if (!isRecord(value)) return undefined;
  const id = stringValue(value, ["id", "auditId", "audit_id"]);
  const action = stringValue(value, ["action", "event"]);
  if (!id || !action) return undefined;
  return {
    id,
    action,
    subject: stringValue(value, ["subject", "resourceName", "resource_name"]) ?? "",
    actor: stringValue(value, ["actor", "actorName", "actor_name"]) ?? "Admin",
    timestamp: stringValue(value, ["timestamp", "createdAt", "created_at"]) ?? ""
  };
}

function documentStatus(record: Record<string, unknown>): VenueApplication["documents"] {
  const documents = isRecord(record.documents) ? record.documents : record;
  return {
    ownership: booleanValue(documents, ["ownership", "ownershipDocument", "ownership_document"]) ?? false,
    identity: booleanValue(documents, ["identity", "identityDocument", "identity_document"]) ?? false,
    address: booleanValue(documents, ["address", "addressDocument", "address_document"]) ?? false
  };
}

function moderationStatus(record: Record<string, unknown>): ModerationStatus | undefined {
  const value = stringValue(record, ["status", "listingStatus", "listing_status", "approvalStatus"]);
  if (value === "PENDING_APPROVAL" || value === "APPROVED" || value === "REJECTED") return value;
  if (value === "PENDING" || value === "SUBMITTED") return "PENDING_APPROVAL";
  return undefined;
}

function reviewStatus(record: Record<string, unknown>): ReportedReview["status"] | undefined {
  const value = stringValue(record, ["status", "moderationStatus", "moderation_status"]);
  if (value === "REPORTED" || value === "PUBLISHED" || value === "HIDDEN") return value;
  if (value === "APPROVED") return "PUBLISHED";
  if (value === "REJECTED") return "HIDDEN";
  return undefined;
}

function enquiryStatus(record: Record<string, unknown>): AdminEnquiry["status"] | undefined {
  const value = stringValue(record, ["status"]);
  if (value === "NEW" || value === "PENDING_OWNER_RESPONSE" || value === "CONFIRMED" || value === "DECLINED" || value === "COMPLETED") return value;
  if (value === "AWAITING_RESPONSE" || value === "CONTACTED") return "PENDING_OWNER_RESPONSE";
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

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}
