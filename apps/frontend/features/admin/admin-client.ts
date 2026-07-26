import { ApiError, apiRequest } from "@/lib/api-client";
import {
  adminEnquiries,
  adminUsers,
  auditEvents,
  initialReportedReviews,
  initialVendorApplications,
  initialVenueApplications,
  type AdminEnquiry,
  type AdminUser,
  type AdminUserStatus,
  type ModerationStatus,
  type ReportedReview,
  type VendorApplication,
  type VenueApplication
} from "./mock-data";

const useMockAdmin = process.env.NEXT_PUBLIC_ADMIN_MODE === "mock";

export type AdminVendorReview = {
  id: string;
  vendorName: string;
  customerName: string;
  rating: number;
  comment: string;
  moderationReason: string;
  verifiedService: boolean;
  status: "PENDING" | "PUBLISHED" | "HIDDEN" | "REJECTED";
  createdAt: string;
  moderatedByAdminId?: number;
  moderatedAt?: string;
};

export type AdminRequirementNotificationSummary = {
  requirementId: number;
  customerName: string;
  eventType: string;
  eventDate: string;
  location: string;
  city?: string | null;
  requirementStatus?: string | null;
  services: string[];
  createdAt: string;
  matchedVendorCount: number;
  subscribedVendorCount: number;
  notificationSkippedCount: number;
  queuedCount: number;
  sentCount: number;
  deliveredCount: number;
  readCount: number;
  failedCount: number;
};

export type AdminWhatsAppAttempt = {
  attemptNumber: number;
  status: string;
  providerMessageId?: string | null;
  requestedAt: string;
  sentAt?: string | null;
  deliveredAt?: string | null;
  readAt?: string | null;
  failedAt?: string | null;
  cancelledAt?: string | null;
  failureCode?: number | null;
  failureTitle?: string | null;
  failureReason?: string | null;
  failureTemporary?: boolean | null;
};

export type AdminVendorNotificationDelivery = {
  vendorLeadId: number;
  leadReference: string;
  vendorId: number;
  vendorName: string;
  service: string;
  subscribedAtEvaluation: boolean;
  currentlySubscribed: boolean;
  currentlyEligible: boolean;
  rolloutAllowed: boolean;
  evaluationOutcome: string;
  skipReason?: string | null;
  evaluatedAt?: string | null;
  notificationJobId?: number | null;
  notificationStatus: string;
  maskedDestination?: string | null;
  attemptCount: number;
  queuedAt?: string | null;
  sentAt?: string | null;
  deliveredAt?: string | null;
  readAt?: string | null;
  failedAt?: string | null;
  nextRetryAt?: string | null;
  failureCode?: number | null;
  failureTitle?: string | null;
  failureReason?: string | null;
  failureTemporary?: boolean | null;
  canManualRetry: boolean;
  manualRetryBlockedReason?: string | null;
  retryHistory: AdminWhatsAppAttempt[];
};

export type AdminRequirementNotificationList = {
  content: AdminRequirementNotificationSummary[];
  sendingEnabled: boolean;
  maxAttempts: number;
  rolloutAllowedVendorIds: number[];
};

export type AdminRequirementNotificationDetail = {
  summary: AdminRequirementNotificationSummary;
  vendors: AdminVendorNotificationDelivery[];
  sendingEnabled: boolean;
  maxAttempts: number;
  rolloutAllowedVendorIds: number[];
};

export type AdminManualRetryResult = {
  notificationJobId: number;
  status: string;
  nextRetryAt: string;
  attemptCount: number;
  maxAttempts: number;
  sendingEnabled: boolean;
  message: string;
};

export type ManagedAdminRole = Extract<AdminUser["role"], "ADMIN" | "SUPER_ADMIN">;

export type CreateAdminUserPayload = {
  fullName: string;
  phone: string;
  email: string;
  password: string;
  role: ManagedAdminRole;
};

type AdminQueueResult = {
  venues: VenueApplication[];
  vendors: VendorApplication[];
  reviews: ReportedReview[];
  enquiries: AdminEnquiry[];
  users: AdminUser[];
  auditEvents: typeof auditEvents;
  source: "api" | "mock";
};

export async function getAdminQueues(accessToken?: string | null): Promise<AdminQueueResult> {
  if (useMockAdmin) return mockResult();
  if (!accessToken) return emptyResult();

  const [venues, vendors, reviews, enquiries, users, events] = await Promise.all([
    getAdminVenues(accessToken).catch(() => []),
    getAdminVendors(accessToken).catch(() => []),
    getAdminReviews(accessToken).catch(() => []),
    getAdminEnquiries(accessToken).catch(() => []),
    getAdminUsers(accessToken).catch(() => []),
    getAdminAuditEvents(accessToken).catch(() => [])
  ]);

  return { venues, vendors, reviews, enquiries, users, auditEvents: events, source: "api" };
}

export async function getAdminRequirementNotificationMonitoring(
  accessToken?: string | null
): Promise<AdminRequirementNotificationList> {
  if (!accessToken) throw new Error("Authentication required");
  return apiRequest<AdminRequirementNotificationList>(
    "/admin/requirements/notification-monitoring",
    { token: accessToken }
  );
}

export async function getAdminRequirementNotificationDetail(
  requirementId: number,
  accessToken?: string | null
): Promise<AdminRequirementNotificationDetail> {
  if (!accessToken) throw new Error("Authentication required");
  return apiRequest<AdminRequirementNotificationDetail>(
    `/admin/requirements/${encodeURIComponent(String(requirementId))}/notification-monitoring`,
    { token: accessToken }
  );
}

export async function retryAdminLeadNotification(
  notificationJobId: number,
  accessToken?: string | null
): Promise<AdminManualRetryResult> {
  if (!accessToken) throw new Error("Authentication required");
  return apiRequest<AdminManualRetryResult>(
    `/admin/notification-jobs/${encodeURIComponent(String(notificationJobId))}/retry`,
    {
      method: "POST",
      token: accessToken
    }
  );
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

export async function moderateAdminVendorReview(
  id: string,
  status: "PUBLISHED" | "HIDDEN" | "REJECTED",
  reason: string,
  accessToken?: string | null
) {

  const response = await apiRequest<unknown>(
    `/admin/vendor-reviews/${encodeURIComponent(id)}`,
    {
      method: "PATCH",
      token: accessToken ?? undefined,
      body: JSON.stringify({
        status,
        reason
      })
    }
  );

  const review = toAdminVendorReview(response);

  if (!review) {
    throw new Error("Failed to moderate vendor review.");
  }

  return review;
}

export async function updateAdminUserStatus(id: string, status: Exclude<AdminUserStatus, "PENDING_VERIFICATION">, reason: string, accessToken?: string | null) {
  if (useMockAdmin || !accessToken) return updateMockUser(id, status);

  try {
    const response = await apiRequest<unknown>(`/admin/users/${encodeURIComponent(id)}/status`, {
      method: "PATCH",
      token: accessToken,
      body: JSON.stringify({ status, reason })
    });
    return toAdminUser(response) ?? updateMockUser(id, status);
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 404, 409].includes(exception.status)) {
      throw exception;
    }
    return updateMockUser(id, status);
  }
}

export async function createAdminUser(payload: CreateAdminUserPayload, accessToken?: string | null) {
  if (useMockAdmin) return createMockAdminUser(payload);
  if (!accessToken) throw new Error("Authentication required");

  const response = await apiRequest<unknown>("/admin/users", {
    method: "POST",
    token: accessToken,
    body: JSON.stringify(payload)
  });

  const adminUser = toAdminUser(response);
  if (!adminUser) throw new Error("Could not create admin user.");
  return adminUser;
}

export async function updateAdminUserRole(id: string, role: ManagedAdminRole, accessToken?: string | null) {
  if (useMockAdmin) return updateMockUserRole(id, role);
  if (!accessToken) throw new Error("Authentication required");

  const response = await apiRequest<unknown>(`/admin/users/${encodeURIComponent(id)}/role`, {
    method: "PATCH",
    token: accessToken,
    body: JSON.stringify({ role })
  });

  const adminUser = toAdminUser(response);
  if (!adminUser) throw new Error("Could not update admin role.");
  return adminUser;
}

export async function resetAdminUserPassword(id: string, password: string, accessToken?: string | null) {
  if (useMockAdmin) return adminUsers.find((adminUser) => adminUser.id === id);
  if (!accessToken) throw new Error("Authentication required");

  const response = await apiRequest<unknown>(`/admin/users/${encodeURIComponent(id)}/password`, {
    method: "PATCH",
    token: accessToken,
    body: JSON.stringify({ password })
  });

  const adminUser = toAdminUser(response);
  if (!adminUser) throw new Error("Could not reset admin password.");
  return adminUser;
}

async function getAdminVenues(accessToken: string) {
  const response = await apiRequest<unknown>("/admin/halls?status=PENDING_APPROVAL", { token: accessToken });
  const venues = extractList(response).map(toVenueApplication).filter(Boolean) as VenueApplication[];
  return venues;
}

async function getAdminVendors(accessToken: string) {
  const response = await apiRequest<unknown>("/admin/vendors?status=PENDING_APPROVAL", { token: accessToken });
  const vendors = extractList(response).map(toVendorApplication).filter(Boolean) as VendorApplication[];
  return vendors;
}

async function getAdminReviews(accessToken: string) {
  const response = await apiRequest<unknown>("/admin/reviews?status=REPORTED", { token: accessToken });
  const reviews = extractList(response).map(toReportedReview).filter(Boolean) as ReportedReview[];
  return reviews;
}

export async function getAdminVendorReviews(accessToken: string | null) {

  const response = await apiRequest<unknown>(
    "/admin/vendor-reviews?status=PENDING",
    {
      token: accessToken ?? undefined
    }
  );

  return extractList(response)
    .map(toAdminVendorReview)
    .filter(Boolean) as AdminVendorReview[];
}

async function getAdminEnquiries(accessToken: string) {
  const response = await apiRequest<unknown>("/admin/enquiries", { token: accessToken });
  const enquiries = extractList(response).map(toAdminEnquiry).filter(Boolean) as AdminEnquiry[];
  return enquiries;
}

async function getAdminUsers(accessToken: string) {
  const response = await apiRequest<unknown>("/admin/users", { token: accessToken });
  const users = extractList(response).map(toAdminUser).filter(Boolean) as AdminUser[];
  return users;
}

async function getAdminAuditEvents(accessToken: string) {
  const response = await apiRequest<unknown>("/admin/audit-events", { token: accessToken });
  const events = extractList(response).map(toAuditEvent).filter(Boolean) as typeof auditEvents;
  return events;
}

function mockResult(): AdminQueueResult {
  return {
    venues: initialVenueApplications,
    vendors: initialVendorApplications,
    reviews: initialReportedReviews,
    enquiries: adminEnquiries,
    users: adminUsers,
    auditEvents,
    source: "mock"
  };
}

function emptyResult(): AdminQueueResult {
  return {
    venues: [],
    vendors: [],
    reviews: [],
    enquiries: [],
    users: [],
    auditEvents: [],
    source: "api"
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

function updateMockUser(id: string, status: Exclude<AdminUserStatus, "PENDING_VERIFICATION">) {
  return adminUsers.find((user) => user.id === id)
    ? { ...adminUsers.find((user) => user.id === id)!, status }
    : undefined;
}

function createMockAdminUser(payload: CreateAdminUserPayload) {
  return {
    id: `admin-${Date.now()}`,
    fullName: payload.fullName,
    phone: payload.phone,
    email: payload.email,
    role: payload.role,
    status: "ACTIVE",
    joinedAt: new Date().toISOString()
  } satisfies AdminUser;
}

function updateMockUserRole(id: string, role: ManagedAdminRole) {
  return adminUsers.find((user) => user.id === id)
    ? { ...adminUsers.find((user) => user.id === id)!, role }
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
  const coverImageUrl = usableImageUrl(stringValue(value, ["imageUrl", "coverImageUrl", "cover_image_url", "primaryImageUrl", "url"])) ?? "";
  const galleryImageUrls = imageUrlList(value, ["imageUrls", "image_urls", "galleryUrls", "gallery_urls", "gallery", "media"]);
  const imageUrls = [coverImageUrl, ...galleryImageUrls].filter((url, index, urls) => Boolean(url) && urls.indexOf(url) === index);

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
    imageUrl: imageUrls[0] ?? "",
    imageUrls,
    status: moderationStatus(value) ?? "PENDING_APPROVAL",
    documents: documentStatus(value).documents,
    documentReviewRequired: documentStatus(value).required
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

    contactName:
      stringValue(value, [
        "contactName",
        "contact_name",
        "ownerName",
        "owner_name",
        "vendorName",
      ]) ?? "Vendor",

    category:
      stringValue(value, [
        "category",
        "vendorCategory",
        "vendor_category",
      ]) ?? "Service",

    city: stringValue(value, ["city", "serviceCity"]) ?? "",

    submittedAt:
      stringValue(value, [
        "submittedAt",
        "createdAt",
        "created_at",
        "updatedAt",
      ]) ?? "",

    status: moderationStatus(value) ?? "PENDING_APPROVAL",

    description: stringValue(value, ["description"]),

    coverImageUrl: usableImageUrl(stringValue(value, ["coverImageUrl", "cover_image_url", "imageUrl", "image_url"])),

    addressLine: stringValue(value, ["addressLine", "address_line", "address"]),

    area: stringValue(value, ["area", "locality"]),

    pincode: stringValue(value, ["pincode"]),

    phone: stringValue(value, ["contactNumber", "contact_number", "phone", "mobile"]),

    whatsAppNumber: stringValue(value, ["whatsAppNumber", "whatsappNumber", "whats_app_number", "whatsapp_number"]),

    instagramUrl: stringValue(value, ["instagramUrl", "instagram_url"]),

    facebookUrl: stringValue(value, ["facebookUrl", "facebook_url"]),

    whatsAppUrl: stringValue(value, ["whatsAppUrl", "whatsappUrl", "whatsapp_url"]),

    email: stringValue(value, ["email"]),

    yearsInBusiness: numberValue(value, ["yearsInBusiness"]),

    serviceRadius: numberValue(value, ["serviceRadius"]),

    packageName: stringValue(value, ["packageName"]),

    startingPrice: numberValue(value, ["startingPrice"]),

    packageDescription: stringValue(value, ["packageDescription"]),

    rejectionReason: stringValue(value, ["rejectionReason"]),

    services: Array.isArray(value.services)
      ? value.services.map(String)
      : [],

    categories: Array.isArray(value.categories)
      ? value.categories.map(String)
      : [],
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

function toAdminVendorReview(
  value: unknown
): AdminVendorReview | undefined {

  if (!isRecord(value)) return undefined;

  const id = stringValue(value, ["id"]);

  if (!id) return undefined;

  return {

    id,

    vendorName:
      stringValue(value, ["vendorName"]) ?? "",

    customerName:
      stringValue(value, ["customerName"]) ?? "",

    rating:
      numberValue(value, ["rating"]) ?? 0,

    comment:
      stringValue(value, ["comment"]) ?? "",

    moderationReason:
      stringValue(value, ["moderationReason"]) ?? "",

    verifiedService:
      booleanValue(value, ["verifiedService"]) ?? true,

    status:
      stringValue(value, ["status"]) as
      | "PENDING"
      | "PUBLISHED"
      | "HIDDEN"
      | "REJECTED",

    createdAt:
      stringValue(value, ["createdAt"]) ?? "",

    moderatedByAdminId:
      numberValue(value, ["moderatedByAdminId"]),

    moderatedAt:
      stringValue(value, ["moderatedAt"]),
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

function toAdminUser(value: unknown): AdminUser | undefined {
  if (!isRecord(value)) return undefined;
  const record = isRecord(value.user) ? value.user : isRecord(value.data) ? value.data : value;
  const id = stringValue(record, ["id", "userId", "user_id"]);
  const fullName = stringValue(record, ["fullName", "full_name", "name"]);
  const phone = stringValue(record, ["phone", "mobile", "phoneNumber"]);
  if (!id || !fullName || !phone) return undefined;

  return {
    id,
    fullName,
    phone,
    email: stringValue(record, ["email"]),
    role: userRole(record) ?? "CUSTOMER",
    status: userStatus(record) ?? "ACTIVE",
    joinedAt: stringValue(record, ["joinedAt", "joined_at", "createdAt", "created_at"]) ?? new Date().toISOString(),
    lastActiveAt: stringValue(record, ["lastActiveAt", "last_active_at", "lastLoginAt", "last_login_at"]),
    city: stringValue(record, ["city", "location"])
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

function documentStatus(record: Record<string, unknown>): { documents: VenueApplication["documents"]; required: boolean } {
  const documents = isRecord(record.documents) ? record.documents : record;
  const ownership = booleanValue(documents, ["ownership", "ownershipDocument", "ownership_document"]);
  const identity = booleanValue(documents, ["identity", "identityDocument", "identity_document"]);
  const address = booleanValue(documents, ["address", "addressDocument", "address_document"]);
  const required = ownership !== undefined || identity !== undefined || address !== undefined;

  if (!required) {
    return {
      documents: { ownership: true, identity: true, address: true },
      required: false
    };
  }

  return {
    documents: {
      ownership: ownership ?? false,
      identity: identity ?? false,
      address: address ?? false
    },
    required: true
  };
}

function usableImageUrl(value: string | undefined) {
  if (!value) return undefined;
  const trimmed = value.trim();
  if (/^(https?:|blob:|data:image\/)/i.test(trimmed) || trimmed.startsWith("/")) return trimmed;
  return undefined;
}

function imageUrlList(record: Record<string, unknown>, keys: string[]) {
  const candidate = keys.map((key) => record[key]).find(Array.isArray);
  if (!Array.isArray(candidate)) return [];

  return candidate
    .map((item) => {
      if (typeof item === "string") return usableImageUrl(item);
      if (isRecord(item)) return usableImageUrl(stringValue(item, ["url", "imageUrl", "image_url"]));
      return undefined;
    })
    .filter((url): url is string => Boolean(url));
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

function userRole(record: Record<string, unknown>): AdminUser["role"] | undefined {
  const value = stringValue(record, ["role", "userRole", "user_role"]);
  if (value === "CUSTOMER" || value === "HALL_OWNER" || value === "VENDOR" || value === "ADMIN" || value === "SUPER_ADMIN") return value;
  return undefined;
}

function userStatus(record: Record<string, unknown>): AdminUserStatus | undefined {
  const value = stringValue(record, ["status", "userStatus", "user_status"]);
  if (value === "ACTIVE" || value === "SUSPENDED" || value === "PENDING_VERIFICATION") return value;
  if (value === "PENDING") return "PENDING_VERIFICATION";
  if (value === "DISABLED" || value === "BLOCKED") return "SUSPENDED";
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

export async function getAdminVendor(
  id: string,
  accessToken?: string | null
): Promise<VendorApplication> {

  const response = await apiRequest<unknown>(
    `/admin/vendors/${encodeURIComponent(id)}`,
    {
      token: accessToken ?? undefined,
    }
  );

  const vendor = toVendorApplication(response);

  if (!vendor) {
    throw new Error("Failed to load vendor details");
  }

  return vendor;
}
