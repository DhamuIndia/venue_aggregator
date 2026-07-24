import { ApiError, apiRequest } from "@/lib/api-client";
import type { VendorCategory } from "./types";
import { workspaceVendor } from "./workspace-data";

const STORAGE_KEY_PREFIX = "venue-vendor-profile";
const useMockVendorProfile = process.env.NEXT_PUBLIC_VENDOR_PROFILE_MODE === "mock";

export type VendorProfileStatus = "DRAFT" | "PENDING_APPROVAL" | "APPROVED" | "REJECTED";

export type VendorProfileDraft = {
  id?: string;
  businessName: string;
  category: VendorCategory;
  city: string;
  area: string;
  serviceRadius: number;
  yearsInBusiness: number;
  description: string;
  instagramUrl: string;
  facebookUrl: string;
  whatsAppUrl: string;
  services: string[];
  packageName: string;
  startingPrice: number;
  packageDescription: string;
  status: VendorProfileStatus;
  pendingUpdate?: boolean;
  rejectionReason?: string;
  updatedAt?: string;
};

export const fallbackVendorProfile: VendorProfileDraft = {
  id: workspaceVendor.id,
  businessName: workspaceVendor.businessName,
  category: workspaceVendor.category,
  city: workspaceVendor.city,
  area: workspaceVendor.area,
  serviceRadius: 25,
  yearsInBusiness: 5,
  description: workspaceVendor.description,
  instagramUrl: "",
  facebookUrl: "",
  whatsAppUrl: "",
  services: [],
  packageName: workspaceVendor.packages[0]?.name ?? "",
  startingPrice: workspaceVendor.packages[0]?.price ?? workspaceVendor.startingPrice,
  packageDescription: workspaceVendor.packages[0]?.description ?? "",
  status: "DRAFT"
};

export async function getVendorProfile(accessToken?: string | null) {
  if (useMockVendorProfile || !accessToken) return getLocalVendorProfile(accessToken);

  try {
    const response = await apiRequest<unknown>("/vendor/profile", {
      token: accessToken
    });
    const profile = toVendorProfile(response) ?? getLocalVendorProfile(accessToken);
    saveLocalVendorProfile(profile, accessToken);
    return profile;
  } catch {
    return getLocalVendorProfile(accessToken);
  }
}

export async function saveVendorProfile(payload: VendorProfileDraft, accessToken?: string | null) {
  if (useMockVendorProfile || !accessToken) return saveLocalVendorProfile({ ...payload, status: "DRAFT" }, accessToken);

  try {
    const response = await apiRequest<unknown>("/vendor/profile", {
      method: "PUT",
      token: accessToken,
      body: JSON.stringify(toRequestPayload(payload))
    });
    const profile = toVendorProfile(response) ?? { ...payload, status: "DRAFT" as const, updatedAt: new Date().toISOString() };
    saveLocalVendorProfile(profile, accessToken);
    return profile;
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 409].includes(exception.status)) {
      throw exception;
    }
    return saveLocalVendorProfile({ ...payload, status: "DRAFT" }, accessToken);
  }
}

export async function submitVendorProfile(payload: VendorProfileDraft, accessToken?: string | null) {
  if (useMockVendorProfile || !accessToken) {
    return saveLocalVendorProfile({ ...payload, status: "PENDING_APPROVAL" }, accessToken);
  }

  try {
    const response = await apiRequest<unknown>("/vendor/profile/submit", {
      method: "POST",
      token: accessToken
    });
    const profile = toVendorProfile(response) ?? { ...payload, status: "PENDING_APPROVAL" as const, updatedAt: new Date().toISOString() };
    saveLocalVendorProfile(profile, accessToken);
    return profile;
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 409].includes(exception.status)) {
      throw exception;
    }
    return saveLocalVendorProfile({ ...payload, status: "PENDING_APPROVAL" }, accessToken);
  }
}

function getLocalVendorProfile(accessToken?: string | null) {
  if (typeof window === "undefined") return fallbackVendorProfile;
  try {
    const parsed = JSON.parse(window.localStorage.getItem(storageKey(accessToken)) ?? "null") as unknown;
    return toVendorProfile(parsed) ?? fallbackVendorProfile;
  } catch {
    return fallbackVendorProfile;
  }
}

function saveLocalVendorProfile(profile: VendorProfileDraft, accessToken?: string | null) {
  const nextProfile = { ...profile, updatedAt: new Date().toISOString() };
  if (typeof window !== "undefined") window.localStorage.setItem(storageKey(accessToken), JSON.stringify(nextProfile));
  return nextProfile;
}

function toRequestPayload(profile: VendorProfileDraft) {
  return {
    businessName: profile.businessName,
    category: profile.category,
    city: profile.city,
    area: profile.area,
    serviceRadius: profile.serviceRadius,
    yearsInBusiness: profile.yearsInBusiness,
    description: profile.description,
    instagramUrl: profile.instagramUrl,
    facebookUrl: profile.facebookUrl,
    whatsAppUrl: profile.whatsAppUrl,
    services: profile.services,
    packageName: profile.packageName,
    startingPrice: profile.startingPrice,
    packageDescription: profile.packageDescription
  };
}

function toVendorProfile(value: unknown): VendorProfileDraft | undefined {
  if (!isRecord(value)) return undefined;
  const record = isRecord(value.data) ? value.data : isRecord(value.profile) ? value.profile : value;

  const businessName = stringValue(record, ["businessName", "business_name", "name"]);
  const category = categoryValue(record);
  if (!businessName || !category) return undefined;

  return {
    id: stringValue(record, ["id", "vendorId", "vendor_id"]),
    businessName,
    category,
    city: stringValue(record, ["city"]) ?? fallbackVendorProfile.city,
    area: stringValue(record, ["area", "locality", "location"]) ?? "",
    serviceRadius: numberValue(record, ["serviceRadius", "service_radius"]) ?? fallbackVendorProfile.serviceRadius,
    yearsInBusiness: numberValue(record, ["yearsInBusiness", "years_in_business", "experienceYears"]) ?? fallbackVendorProfile.yearsInBusiness,
    description: stringValue(record, ["description", "summary", "about"]) ?? "",
    instagramUrl: stringValue(record, ["instagramUrl", "instagram_url"]) ?? "",
    facebookUrl: stringValue(record, ["facebookUrl", "facebook_url"]) ?? "",
    whatsAppUrl: stringValue(record, ["whatsAppUrl", "whatsappUrl", "whatsapp_url"]) ?? "",
    services: arrayOfStrings(record.services) ?? [],
    packageName: stringValue(record, ["packageName", "package_name"]) ?? firstPackageValue(record, "name") ?? "",
    startingPrice: numberValue(record, ["startingPrice", "starting_price"]) ?? firstPackageNumber(record, "price") ?? fallbackVendorProfile.startingPrice,
    packageDescription: stringValue(record, ["packageDescription", "package_description"]) ?? firstPackageValue(record, "description") ?? "",
    status: statusValue(record) ?? "DRAFT",
    pendingUpdate: record.pendingUpdate === true,
    rejectionReason: stringValue(record, ["rejectionReason", "rejection_reason"]) ?? "",
    updatedAt: stringValue(record, ["updatedAt", "updated_at"])
  };
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

function firstPackageValue(record: Record<string, unknown>, key: string) {
  const packages = Array.isArray(record.packages) ? record.packages : [];
  const first = packages.find(isRecord);
  return first ? stringValue(first, [key]) : undefined;
}

function firstPackageNumber(record: Record<string, unknown>, key: string) {
  const packages = Array.isArray(record.packages) ? record.packages : [];
  const first = packages.find(isRecord);
  return first ? numberValue(first, [key]) : undefined;
}

function categoryValue(record: Record<string, unknown>): VendorCategory | undefined {
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

function statusValue(record: Record<string, unknown>): VendorProfileStatus | undefined {
  const value = stringValue(record, ["status", "listingStatus", "listing_status", "approvalStatus"]);
  if (value === "DRAFT" || value === "PENDING_APPROVAL" || value === "APPROVED" || value === "REJECTED") return value;
  if (value === "PENDING" || value === "SUBMITTED") return "PENDING_APPROVAL";
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

function arrayOfStrings(value: unknown) {
  if (!Array.isArray(value)) return undefined;
  const strings = value.filter((item): item is string => typeof item === "string" && item.trim().length > 0);
  return strings;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}
