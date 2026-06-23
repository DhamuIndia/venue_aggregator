import { ApiError, apiRequest } from "@/lib/api-client";
import type { VendorLead, VendorLeadStatus } from "./types";

const storageKey = "venue-vendor-leads";
const useMockVendorLeads = process.env.NEXT_PUBLIC_VENDOR_LEADS_MODE === "mock";

export type CreateVendorLeadPayload = {
  vendorId: string;
  vendorName: string;
  customerId: string;
  customerName: string;
  eventDate: string;
  eventType: string;
  location: string;
  service: string;
  budget: number;
  notes?: string;
};

export type VendorLeadListResult = {
  leads: VendorLead[];
  source: "api" | "mock";
};

export function getLocalVendorLeads(): VendorLead[] {
  if (typeof window === "undefined") return [];
  try {
    const parsed = JSON.parse(window.localStorage.getItem(storageKey) ?? "[]") as unknown;
    return Array.isArray(parsed) ? parsed.map((item) => toVendorLead(item)).filter(Boolean) as VendorLead[] : [];
  } catch {
    return [];
  }
}

export async function createVendorLead(payload: CreateVendorLeadPayload, accessToken?: string | null): Promise<VendorLead> {
  if (useMockVendorLeads || !accessToken) return createLocalVendorLead(payload);

  try {
    const response = await apiRequest<unknown>("/public/vendor-leads", {
      method: "POST",
      token: accessToken,
      body: JSON.stringify({
        vendorId: payload.vendorId,
        eventDate: payload.eventDate,
        eventType: payload.eventType,
        location: payload.location,
        service: payload.service,
        budget: payload.budget,
        notes: payload.notes
      })
    });
    const lead = toVendorLead(response, payload) ?? createStoredFromFallback(payload);
    cacheLocalVendorLead(lead);
    return lead;
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403].includes(exception.status)) {
      throw exception;
    }
    return createLocalVendorLead(payload);
  }
}

export async function getVendorLeads(vendorId: string, accessToken?: string | null): Promise<VendorLeadListResult> {
  if (useMockVendorLeads || !accessToken) {
    return { leads: getLocalVendorLeads().filter((lead) => lead.vendorId === vendorId), source: "mock" };
  }

  try {
    const response = await apiRequest<unknown>("/vendor/leads", {
      token: accessToken
    });
    const leads = extractVendorLeads(response).filter((lead) => lead.vendorId === vendorId || !lead.vendorId);
    leads.forEach(cacheLocalVendorLead);
    return { leads, source: "api" };
  } catch {
    return { leads: getLocalVendorLeads().filter((lead) => lead.vendorId === vendorId), source: "mock" };
  }
}

export async function updateVendorLeadStatus(id: string, status: VendorLeadStatus, accessToken?: string | null) {
  if (useMockVendorLeads || !accessToken) return updateLocalVendorLeadStatus(id, status);

  try {
    const response = await apiRequest<unknown>(`/vendor/leads/${encodeURIComponent(id)}/status`, {
      method: "PATCH",
      token: accessToken,
      body: JSON.stringify({ status })
    });
    const lead = toVendorLead(response) ?? updateLocalVendorLeadStatus(id, status);
    if (lead) cacheLocalVendorLead(lead);
    return lead;
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 409].includes(exception.status)) {
      throw exception;
    }
    return updateLocalVendorLeadStatus(id, status);
  }
}

export function createLocalVendorLead(payload: CreateVendorLeadPayload): VendorLead {
  const lead = createStoredFromFallback(payload);
  cacheLocalVendorLead(lead);
  return lead;
}

export function updateLocalVendorLeadStatus(id: string, status: VendorLeadStatus) {
  const updated = getLocalVendorLeads().map((lead) => lead.id === id ? { ...lead, status } : lead);
  if (typeof window !== "undefined") window.localStorage.setItem(storageKey, JSON.stringify(updated));
  return updated.find((lead) => lead.id === id);
}

function cacheLocalVendorLead(lead: VendorLead) {
  if (typeof window === "undefined") return;
  const existing = getLocalVendorLeads().filter((item) => item.id !== lead.id);
  window.localStorage.setItem(storageKey, JSON.stringify([lead, ...existing]));
}

function extractVendorLeads(response: unknown) {
  if (Array.isArray(response)) return response.map((item) => toVendorLead(item)).filter(Boolean) as VendorLead[];
  if (!isRecord(response)) return [];

  const candidates = [response.items, response.content, response.data, response.results, response.leads];
  const list = candidates.find(Array.isArray);
  return Array.isArray(list) ? list.map((item) => toVendorLead(item)).filter(Boolean) as VendorLead[] : [];
}

function toVendorLead(value: unknown, fallback?: Partial<CreateVendorLeadPayload>): VendorLead | undefined {
  if (!isRecord(value)) return hasCompleteFallback(fallback) ? createStoredFromFallback(fallback) : undefined;

  const id = stringValue(value, ["id", "leadId", "lead_id"]);
  const vendorId = stringValue(value, ["vendorId", "vendor_id"]) ?? fallback?.vendorId ?? "";
  const vendorName = stringValue(value, ["vendorName", "vendor_name", "businessName", "business_name"]) ?? fallback?.vendorName ?? "";
  const customerId = stringValue(value, ["customerId", "customer_id"]) ?? fallback?.customerId ?? "";
  const customerName = stringValue(value, ["customerName", "customer_name", "name"]) ?? fallback?.customerName ?? "Customer";
  const eventDate = stringValue(value, ["eventDate", "event_date"]) ?? fallback?.eventDate;
  const eventType = stringValue(value, ["eventType", "event_type"]) ?? fallback?.eventType;
  const location = stringValue(value, ["location", "eventLocation", "event_location"]) ?? fallback?.location;
  const service = stringValue(value, ["service", "serviceName", "service_name"]) ?? fallback?.service;
  const budget = numberValue(value, ["budget", "expectedBudget", "expected_budget"]) ?? fallback?.budget;

  if (!id || !eventDate || !eventType || !location || !service || typeof budget !== "number") {
    return hasCompleteFallback(fallback) ? createStoredFromFallback(fallback) : undefined;
  }

  return {
    id,
    vendorId,
    vendorName,
    customerId,
    customerName,
    eventDate,
    eventType,
    location,
    service,
    budget,
    notes: stringValue(value, ["notes", "message"]) ?? fallback?.notes,
    status: statusValue(value) ?? "NEW",
    submittedAt: stringValue(value, ["submittedAt", "createdAt", "created_at"]) ?? new Date().toISOString()
  };
}

function createStoredFromFallback(fallback: CreateVendorLeadPayload): VendorLead {
  return {
    ...fallback,
    id: `LEAD-${Date.now().toString().slice(-6)}`,
    status: "NEW",
    submittedAt: new Date().toISOString()
  };
}

function hasCompleteFallback(fallback?: Partial<CreateVendorLeadPayload>): fallback is CreateVendorLeadPayload {
  return Boolean(
    fallback?.vendorId &&
    fallback.vendorName &&
    fallback.customerId &&
    fallback.customerName &&
    fallback.eventDate &&
    fallback.eventType &&
    fallback.location &&
    fallback.service &&
    typeof fallback.budget === "number"
  );
}

function statusValue(record: Record<string, unknown>): VendorLeadStatus | undefined {
  const value = stringValue(record, ["status"]);
  if (!value) return undefined;
  if (value === "NEW" || value === "CONTACTED" || value === "QUOTE_SENT" || value === "BOOKED" || value === "DECLINED") return value;
  if (value === "IN_PROGRESS") return "CONTACTED";
  if (value === "QUOTED") return "QUOTE_SENT";
  if (value === "CONFIRMED") return "BOOKED";
  if (value === "REJECTED") return "DECLINED";
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

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}
