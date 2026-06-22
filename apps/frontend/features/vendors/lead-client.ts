import type { VendorLead } from "./types";

const storageKey = "venue-vendor-leads";

export function getLocalVendorLeads(): VendorLead[] {
  if (typeof window === "undefined") return [];
  try {
    return JSON.parse(window.localStorage.getItem(storageKey) ?? "[]") as VendorLead[];
  } catch {
    return [];
  }
}

export function createLocalVendorLead(payload: Omit<VendorLead, "id" | "status" | "submittedAt">): VendorLead {
  const lead: VendorLead = {
    ...payload,
    id: `LEAD-${String(Date.now()).slice(-6)}`,
    status: "NEW",
    submittedAt: new Date().toISOString()
  };
  window.localStorage.setItem(storageKey, JSON.stringify([lead, ...getLocalVendorLeads()]));
  return lead;
}

export function updateLocalVendorLeadStatus(id: string, status: VendorLead["status"]) {
  const updated = getLocalVendorLeads().map((lead) => lead.id === id ? { ...lead, status } : lead);
  window.localStorage.setItem(storageKey, JSON.stringify(updated));
}
