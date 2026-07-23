import { apiRequest } from "@/lib/api-client";
import type {
  CreateCustomerRequirementInput,
  CustomerRequirement,
  CustomerRequirementStatus,
  PreferredContactChannel,
  RequirementCategory,
  RequirementOptions
} from "./types";

const useMockRequirements = process.env.NEXT_PUBLIC_AUTH_MODE !== "api"
  || process.env.NEXT_PUBLIC_REQUIREMENTS_MODE === "mock";
const mockStorageKey = "venuemart-customer-requirements";

const mockCategories: RequirementCategory[] = [
  { id: 1, name: "Photography" },
  { id: 2, name: "DJ" },
  { id: 3, name: "Food" },
  { id: 5, name: "Decoration" },
  { id: 6, name: "Makeup" },
  { id: 7, name: "Mehendi" },
  { id: 8, name: "Catering" },
  { id: 9, name: "Balloon Decoration" },
  { id: 10, name: "Live Music" },
  { id: 11, name: "Wedding Planner" }
];

export async function getRequirementOptions(): Promise<RequirementOptions> {
  if (useMockRequirements) return { enabled: true, categories: mockCategories };
  return normalizeOptions(await apiRequest<unknown>("/public/requirements/options"));
}

export async function createCustomerRequirement(
  payload: CreateCustomerRequirementInput,
  accessToken: string
): Promise<CustomerRequirement> {
  if (useMockRequirements) {
    const now = new Date().toISOString();
    const requirement: CustomerRequirement = {
      id: `REQ-${Date.now().toString().slice(-8)}`,
      ...payload,
      services: mockCategories.filter((category) => payload.categoryIds.includes(category.id)),
      status: "OPEN",
      matchedVendorCount: Math.min(payload.categoryIds.length * 2, 6),
      createdAt: now,
      updatedAt: now
    };
    saveMockRequirements([requirement, ...readMockRequirements()]);
    return requirement;
  }

  return normalizeRequirement(await apiRequest<unknown>("/customer/requirements", {
    method: "POST",
    token: accessToken,
    body: JSON.stringify(payload)
  }));
}

export async function getCustomerRequirements(accessToken: string | null): Promise<CustomerRequirement[]> {
  if (useMockRequirements) return readMockRequirements();
  if (!accessToken) return [];
  const response = await apiRequest<unknown>("/customer/requirements", { token: accessToken });
  return Array.isArray(response) ? response.map(normalizeRequirement) : [];
}

function normalizeOptions(value: unknown): RequirementOptions {
  const record = asRecord(value);
  return {
    enabled: record.enabled === true,
    categories: Array.isArray(record.categories) ? record.categories.map(normalizeCategory).filter(Boolean) as RequirementCategory[] : []
  };
}

function normalizeRequirement(value: unknown): CustomerRequirement {
  const record = asRecord(value);
  const services = Array.isArray(record.services)
    ? record.services.map(normalizeCategory).filter(Boolean) as RequirementCategory[]
    : [];
  return {
    id: stringValue(record.id) ?? "",
    eventType: stringValue(record.eventType) ?? "Event",
    eventDate: stringValue(record.eventDate) ?? "",
    location: stringValue(record.location) ?? "",
    city: stringValue(record.city),
    pincode: stringValue(record.pincode),
    budgetMin: numberValue(record.budgetMin),
    budgetMax: numberValue(record.budgetMax),
    guestCount: numberValue(record.guestCount),
    details: stringValue(record.details),
    preferredContactChannel: contactChannel(record.preferredContactChannel),
    shareContactDetails: record.shareContactDetails === true,
    status: requirementStatus(record.status),
    services,
    matchedVendorCount: numberValue(record.matchedVendorCount) ?? 0,
    createdAt: stringValue(record.createdAt) ?? new Date().toISOString(),
    updatedAt: stringValue(record.updatedAt) ?? new Date().toISOString()
  };
}

function normalizeCategory(value: unknown): RequirementCategory | undefined {
  const record = asRecord(value);
  const id = numberValue(record.id);
  const name = stringValue(record.name) ?? stringValue(record.categoryName);
  return id !== undefined && name ? { id, name } : undefined;
}

function contactChannel(value: unknown): PreferredContactChannel {
  return value === "PHONE" || value === "WHATSAPP" || value === "EMAIL" ? value : "IN_APP";
}

function requirementStatus(value: unknown): CustomerRequirementStatus {
  return value === "CLOSED" || value === "CANCELLED" || value === "EXPIRED" ? value : "OPEN";
}

function readMockRequirements(): CustomerRequirement[] {
  if (typeof window === "undefined") return [];
  try {
    const value = window.localStorage.getItem(mockStorageKey);
    const parsed = value ? JSON.parse(value) as unknown : [];
    return Array.isArray(parsed) ? parsed.map(normalizeRequirement) : [];
  } catch {
    return [];
  }
}

function saveMockRequirements(requirements: CustomerRequirement[]) {
  if (typeof window !== "undefined") {
    window.localStorage.setItem(mockStorageKey, JSON.stringify(requirements));
  }
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? value as Record<string, unknown> : {};
}

function stringValue(value: unknown) {
  if (typeof value === "string" && value.trim()) return value;
  if (typeof value === "number") return String(value);
  return undefined;
}

function numberValue(value: unknown) {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim() && Number.isFinite(Number(value))) return Number(value);
  return undefined;
}
