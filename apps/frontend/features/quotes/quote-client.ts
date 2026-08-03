import { apiRequest } from "@/lib/api-client";
import { cacheVendorServiceBooking, normalizeVendorServiceBooking } from "@/features/bookings/vendor-service-booking-client";
import { getLocalVendorLeads, updateLocalVendorLeadStatus } from "@/features/vendors/lead-client";
import type { VendorLead } from "@/features/vendors/types";
import type { QuoteAcceptanceResult, UpsertVendorQuoteInput, VendorQuote, VendorQuoteStatus } from "./types";

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

export async function acceptCustomerQuote(
  quoteId: string,
  accessToken?: string | null
): Promise<QuoteAcceptanceResult> {
  if (useMockQuotes || !accessToken) return acceptMockQuote(quoteId);
  const record = asRecord(await apiRequest<unknown>(`/customer/quotes/${encodeURIComponent(quoteId)}/accept`, {
    method: "POST",
    token: accessToken
  }));
  const acceptedQuote = normalizeQuote(record.acceptedQuote);
  const requirementQuotes = normalizeQuoteList(record.requirementQuotes);
  const booking = normalizeVendorServiceBooking(record.booking);
  if (!acceptedQuote.id || !booking.id) {
    throw new Error("The booking service returned an invalid response.");
  }
  cacheVendorServiceBooking(booking);
  return { acceptedQuote, requirementQuotes, booking };
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

function acceptMockQuote(quoteId: string): QuoteAcceptanceResult {
  const quotes = readMockQuotes();
  const selected = quotes.find((quote) => quote.id === quoteId);
  if (!selected) throw new Error("Quote not found.");
  if (selected.status !== "SENT" || isExpired(selected.validUntil)) {
    throw new Error("Only an active quote can be accepted.");
  }
  const updatedAt = new Date().toISOString();
  const requirementQuotes = quotes
    .filter((quote) => selected.requirementId && quote.requirementId === selected.requirementId)
    .map((quote): VendorQuote => quote.id === quoteId
      ? { ...quote, status: "ACCEPTED", shortlisted: false, shortlistedAt: undefined, updatedAt }
      : quote.status === "SENT"
        ? { ...quote, status: "NOT_SELECTED", shortlisted: false, shortlistedAt: undefined, updatedAt }
        : quote);
  const acceptedQuote = requirementQuotes.find((quote) => quote.id === quoteId)
    ?? { ...selected, status: "ACCEPTED", shortlisted: false, shortlistedAt: undefined, updatedAt };
  const changedById = new Map(requirementQuotes.map((quote) => [quote.id, quote]));
  changedById.set(acceptedQuote.id, acceptedQuote);
  writeMockQuotes(quotes.map((quote) => changedById.get(quote.id) ?? quote));
  const selectedLead = getLocalVendorLeads().find((lead) => lead.id === selected.leadId);
  updateLocalVendorLeadStatus(selected.leadId, "BOOKED");
  requirementQuotes
    .filter((quote) => quote.id !== quoteId)
    .forEach((quote) => updateLocalVendorLeadStatus(quote.leadId, "NOT_SELECTED"));
  const booking = normalizeVendorServiceBooking({
    id: `VBOOK-${Date.now().toString().slice(-6)}`,
    quoteId: acceptedQuote.id,
    leadId: acceptedQuote.leadId,
    requirementId: acceptedQuote.requirementId,
    vendorId: acceptedQuote.vendorId,
    vendorName: acceptedQuote.vendorName,
    customerId: selectedLead?.customerId ?? "customer",
    customerName: selectedLead?.customerName ?? "Customer",
    service: acceptedQuote.service,
    packageName: acceptedQuote.packageName,
    eventType: selectedLead?.eventType ?? "Event",
    eventDate: selectedLead?.eventDate ?? acceptedQuote.validUntil,
    location: selectedLead?.location ?? "",
    amount: acceptedQuote.totalAmount,
    status: "CONFIRMED",
    paymentStatus: "NOT_STARTED",
    confirmedAt: updatedAt,
    createdAt: updatedAt,
    updatedAt
  });
  cacheVendorServiceBooking(booking);
  return {
    acceptedQuote,
    requirementQuotes: requirementQuotes.length ? requirementQuotes : [acceptedQuote],
    booking
  };
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
  return value === "ACCEPTED"
    || value === "NOT_SELECTED"
    || value === "WITHDRAWN"
    || value === "EXPIRED"
    ? value
    : "SENT";
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
