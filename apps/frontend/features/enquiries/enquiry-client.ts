import type { CreateEnquiryPayload, EnquiryStatus, StoredEnquiry } from "./types";

const STORAGE_KEY = "venue-aggregator-enquiries";

export function getLocalEnquiries(): StoredEnquiry[] {
  if (typeof window === "undefined") return [];
  try {
    return JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? "[]") as StoredEnquiry[];
  } catch {
    return [];
  }
}

export function getLocalEnquiry(id: string) {
  return getLocalEnquiries().find((enquiry) => enquiry.id === id);
}

export function createLocalEnquiry(payload: CreateEnquiryPayload): StoredEnquiry {
  const enquiry: StoredEnquiry = {
    ...payload,
    id: `ENQ-${Date.now().toString().slice(-6)}`,
    status: "PENDING_OWNER_RESPONSE",
    submittedAt: new Date().toISOString()
  };

  window.localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify([enquiry, ...getLocalEnquiries()])
  );
  return enquiry;
}

export function updateLocalEnquiryStatus(id: string, status: EnquiryStatus) {
  const enquiries = getLocalEnquiries();
  const updated = enquiries.map((enquiry) => enquiry.id === id ? { ...enquiry, status } : enquiry);
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
  return updated.find((enquiry) => enquiry.id === id);
}
