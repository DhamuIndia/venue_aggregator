import { apiRequest } from "@/lib/api-client";
import type { VendorLead } from "@/features/vendors/types";
import type { UpsertVendorQuoteInput, VendorQuote, VendorQuoteStatus } from "./types";

const useMockQuotes = process.env.NEXT_PUBLIC_VENDOR_LEADS_MODE === "mock";
const storageKey = "venuemart-vendor-quotes";

export async function getVendorQuotes(accessToken?: string | null): Promise<VendorQuote[]> {
  if (useMockQuotes || !accessToken) return readMockQuotes();
  return normalizeQuoteList(await apiRequest<unknown>("/vendor/quotes", { token: accessToken }));
}

export async function getCustomerQuotes(accessToken?: string | null): Promise<VendorQuote[]> {
  if (useMockQuotes || !accessToken) return readMockQuotes();
  return normalizeQuoteList(await apiRequest<unknown>("/customer/quotes", { token: accessToken }));
}

export async function saveVendorQuote(
  lead: VendorLead,
  payload: UpsertVendorQuoteInput,
  accessToken?: string | null
): Promise<VendorQuote> {
  if (useMockQuotes || !accessToken) return saveMockQuote(lead, payload);
  const quote = normalizeQuote(await apiRequest<unknown>(`/vendor/leads/${encodeURIComponent(lead.id)}/quote`, {
    method: "PUT",
    token: accessToken,
    body: JSON.stringify(payload)
  }));
  if (!quote.id || !quote.leadId) throw new Error("The quotation service returned an invalid response.");
  return quote;
}

export async function updateCustomerQuoteShortlist(
  quoteId: string,
  shortlisted: boolean,
  accessToken?: string | null
): Promise<VendorQuote> {
  if (useMockQuotes || !accessToken) return updateMockQuoteShortlist(quoteId, shortlisted);
  const quote = normalizeQuote(await apiRequest<unknown>(`/customer/quotes/${encodeURIComponent(quoteId)}/shortlist`, {
    method: "PATCH",
    token: accessToken,
    body: JSON.stringify({ shortlisted })
  }));
  if (!quote.id || !quote.leadId) throw new Error("The shortlist service returned an invalid response.");
  return quote;
}

function saveMockQuote(lead: VendorLead, payload: UpsertVendorQuoteInput) {
  const quotes = readMockQuotes();
  const existing = quotes.find((quote) => quote.leadId === lead.id);
  const now = new Date().toISOString();
  const quote: VendorQuote = {
    id: existing?.id ?? `QUOTE-${Date.now().toString().slice(-7)}`,
    leadId: lead.id,
    requirementId: lead.requirementId,
    vendorId: lead.vendorId,
    vendorName: lead.vendorName || "VenueMart vendor",
    service: lead.service,
    ...payload,
    inclusions: payload.inclusions.map((item) => item.trim()).filter(Boolean),
    additionalCharges: payload.additionalCharges ?? 0,
    totalAmount: payload.amount + (payload.additionalCharges ?? 0),
    status: "SENT",
    shortlisted: false,
    shortlistedAt: undefined,
    createdAt: existing?.createdAt ?? now,
    updatedAt: now
  };
  writeMockQuotes([quote, ...quotes.filter((item) => item.leadId !== lead.id)]);
  return quote;
}

function updateMockQuoteShortlist(quoteId: string, shortlisted: boolean) {
  const quotes = readMockQuotes();
  const existing = quotes.find((quote) => quote.id === quoteId);
  if (!existing) throw new Error("Quote not found.");
  if (shortlisted && existing.status !== "SENT") {
    throw new Error("Only active quotes can be shortlisted.");
  }
  if (shortlisted && isExpired(existing.validUntil)) {
    throw new Error("Expired quotes cannot be shortlisted.");
  }
  const updated: VendorQuote = {
    ...existing,
    shortlisted,
    shortlistedAt: shortlisted ? new Date().toISOString() : undefined,
    updatedAt: new Date().toISOString()
  };
  writeMockQuotes(quotes.map((quote) => quote.id === quoteId ? updated : quote));
  return updated;
}

function normalizeQuoteList(value: unknown): VendorQuote[] {
  if (!Array.isArray(value)) return [];
  return value.map(normalizeQuote).filter((quote) => quote.id && quote.leadId);
}

function normalizeQuote(value: unknown): VendorQuote {
  const record = asRecord(value);
  const amount = numberValue(record.amount) ?? 0;
  const additionalCharges = numberValue(record.additionalCharges) ?? 0;
  return {
    id: stringValue(record.id) ?? "",
    leadId: stringValue(record.leadId) ?? "",
    requirementId: stringValue(record.requirementId),
    vendorId: stringValue(record.vendorId) ?? "",
    vendorName: stringValue(record.vendorName) ?? "Vendor",
    service: stringValue(record.service) ?? "Event service",
    amount,
    packageName: stringValue(record.packageName) ?? "Custom quotation",
    serviceDescription: stringValue(record.serviceDescription) ?? "",
    inclusions: Array.isArray(record.inclusions)
      ? record.inclusions.filter((item): item is string => typeof item === "string" && Boolean(item.trim()))
      : [],
    additionalCharges,
    additionalChargesDescription: stringValue(record.additionalChargesDescription),
    totalAmount: numberValue(record.totalAmount) ?? amount + additionalCharges,
    notes: stringValue(record.notes),
    validUntil: stringValue(record.validUntil) ?? "",
    status: quoteStatus(record.status),
    shortlisted: record.shortlisted === true,
    shortlistedAt: stringValue(record.shortlistedAt),
    createdAt: stringValue(record.createdAt) ?? new Date().toISOString(),
    updatedAt: stringValue(record.updatedAt) ?? new Date().toISOString()
  };
}

function readMockQuotes(): VendorQuote[] {
  if (typeof window === "undefined") return [];
  try {
    const parsed = JSON.parse(window.localStorage.getItem(storageKey) ?? "[]") as unknown;
    return Array.isArray(parsed) ? parsed.map(normalizeQuote).filter((quote) => quote.id) : [];
  } catch {
    return [];
  }
}

function writeMockQuotes(quotes: VendorQuote[]) {
  if (typeof window !== "undefined") {
    window.localStorage.setItem(storageKey, JSON.stringify(quotes));
  }
}

function quoteStatus(value: unknown): VendorQuoteStatus {
  return value === "WITHDRAWN" || value === "EXPIRED" ? value : "SENT";
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

function isExpired(validUntil: string) {
  const today = new Date();
  const localToday = new Date(today.getTime() - today.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
  return Boolean(validUntil && validUntil < localToday);
}
