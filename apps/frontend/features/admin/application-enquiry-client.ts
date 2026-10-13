import { apiRequest } from "@/lib/api-client";
import { toStoredEnquiry } from "@/features/enquiries/enquiry-client";
import type { StoredEnquiry } from "@/features/enquiries/types";

export type ApplicationEnquiry = StoredEnquiry & { routingTarget: "VENUEMART"; version: number; status: "NEW" | "CONTACTED" | "CLOSED" };
export type ApplicationEnquiryPage = { items: ApplicationEnquiry[]; page: number; size: number; totalItems: number; totalPages: number };
const base = "/admin/application-venue-enquiries";
function auth(token?: string | null) { if (!token?.trim()) throw new Error("Sign in as an admin to manage VenueMart enquiries."); return token; }
function record(value: unknown): ApplicationEnquiry {
  const enquiry = toStoredEnquiry(value);
  if (!enquiry || enquiry.routingTarget !== "VENUEMART" || enquiry.version === undefined || !["NEW", "CONTACTED", "CLOSED"].includes(enquiry.status)) throw new Error("The server returned an invalid VenueMart enquiry.");
  return enquiry as ApplicationEnquiry;
}
export async function getApplicationEnquiries(page: number, status: string, token?: string | null, signal?: AbortSignal): Promise<ApplicationEnquiryPage> {
  const params = new URLSearchParams({ page: String(page), size: "20" }); if (status !== "ALL") params.set("status", status);
  const response = await apiRequest<Omit<ApplicationEnquiryPage, "items"> & { items: unknown[] }>(`${base}?${params}`, { token: auth(token), signal, cache: "no-store" });
  return { ...response, items: response.items.map(record) };
}
export async function updateApplicationEnquiry(enquiry: ApplicationEnquiry, responseMessage: string, reason: string, token?: string | null) {
  if (enquiry.status === "CLOSED") throw new Error("Closed enquiries cannot be reopened.");
  return record(await apiRequest<unknown>(`${base}/${encodeURIComponent(enquiry.id)}`, { method: "PUT", token: auth(token), cache: "no-store", body: JSON.stringify({
    expectedVersion: enquiry.version, status: enquiry.status === "NEW" ? "CONTACTED" : "CLOSED", responseMessage: responseMessage.trim(), reason: reason.trim()
  }) }));
}
