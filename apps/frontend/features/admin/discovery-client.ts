import { apiRequest } from "@/lib/api-client";

export type DiscoveryCandidateStatus =
  | "DISCOVERED" | "SHORTLISTED" | "INVITED" | "CLAIMED"
  | "DUPLICATE" | "OPTED_OUT" | "REJECTED" | "CLOSED";

export type ReviewableDiscoveryStatus = Extract<DiscoveryCandidateStatus, "DISCOVERED" | "SHORTLISTED" | "REJECTED" | "CLOSED">;

export type DiscoveryCandidate = {
  id: number;
  placeId: string;
  status: DiscoveryCandidateStatus;
  discoveredAt: string;
  sourceCheckedAt: string | null;
  statusChangedAt: string | null;
  linkedHallId: number | null;
};

export type DiscoveryRun = {
  id: number;
  city: string;
  area: string | null;
  venueType: string;
  status: "CREATED" | "RUNNING" | "COMPLETED" | "FAILED" | "CANCELLED";
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
  failureReason: string | null;
  resultCount: number;
};

export type DiscoveryPage<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type DiscoverySettings = {
  enabled: boolean;
  liveApiEnabled: boolean;
  ready: boolean;
  maxResults: number;
  dailyRequestLimit: number;
  requestsUsedToday: number;
  venueTypes: { value: string; label: string }[];
};

// Google content is transient: never persist these previews in browser storage.
export type DiscoveryPlacePreview = {
  placeId: string;
  displayName: string | null;
  formattedAddress: string | null;
  businessStatus: string | null;
  googleMapsUri: string | null;
  attributions: { displayName: string; uri: string | null }[];
};

export type DiscoveryRunDetail = { run: DiscoveryRun; candidates: DiscoveryCandidate[] };
export type DiscoverySearchResult = DiscoveryRunDetail & { previews: DiscoveryPlacePreview[] };
export type DiscoverySearchInput = { city: string; area: string; venueType: string };

const basePath = "/admin/venue-discovery";

function authenticatedToken(token?: string | null): string {
  if (!token) throw new Error("Please sign in with an admin account to use venue discovery.");
  return token;
}

export async function getDiscoverySettings(token?: string | null, signal?: AbortSignal) {
  return apiRequest<DiscoverySettings>(`${basePath}/settings`, {
    token: authenticatedToken(token), cache: "no-store", signal
  });
}

export async function searchDiscoveryVenues(input: DiscoverySearchInput, token?: string | null) {
  return apiRequest<DiscoverySearchResult>(`${basePath}/search`, {
    method: "POST", token: authenticatedToken(token), cache: "no-store", body: JSON.stringify(input)
  });
}

export async function getDiscoveryRuns(page: number, token?: string | null, signal?: AbortSignal) {
  return apiRequest<DiscoveryPage<DiscoveryRun>>(`${basePath}/runs?page=${page}&size=20`, {
    token: authenticatedToken(token), cache: "no-store", signal
  });
}

export async function getDiscoveryRun(id: number, token?: string | null) {
  return apiRequest<DiscoveryRunDetail>(`${basePath}/runs/${encodeURIComponent(String(id))}`, {
    token: authenticatedToken(token), cache: "no-store"
  });
}

export async function getDiscoveryCandidates(
  page: number, status: DiscoveryCandidateStatus | "ALL", token?: string | null, signal?: AbortSignal
) {
  const query = new URLSearchParams({ page: String(page), size: "20" });
  if (status !== "ALL") query.set("status", status);
  return apiRequest<DiscoveryPage<DiscoveryCandidate>>(`${basePath}/candidates?${query}`, {
    token: authenticatedToken(token), cache: "no-store", signal
  });
}

export async function getDiscoveryPreview(id: number, token?: string | null) {
  return apiRequest<DiscoveryPlacePreview>(`${basePath}/candidates/${encodeURIComponent(String(id))}/preview`, {
    method: "POST", token: authenticatedToken(token), cache: "no-store"
  });
}

export async function reviewDiscoveryCandidate(
  candidate: DiscoveryCandidate, status: ReviewableDiscoveryStatus, token?: string | null
) {
  return apiRequest<DiscoveryCandidate>(`${basePath}/candidates/${encodeURIComponent(String(candidate.id))}`, {
    method: "PATCH", token: authenticatedToken(token), cache: "no-store",
    body: JSON.stringify({ status, expectedStatus: candidate.status })
  });
}

export function safeDiscoveryExternalUrl(value: string | null | undefined): string | undefined {
  if (!value) return undefined;
  try {
    const url = new URL(value);
    if (url.protocol !== "https:" || url.username || url.password) return undefined;
    return url.href;
  } catch {
    return undefined;
  }
}
