import { apiRequest } from "@/lib/api-client";

export type OvertureSource = { dataset: string; license: string; recordId: string | null };
export type OvertureVenue = {
  id: string;
  name: string;
  category: string;
  address: string | null;
  city: string | null;
  area: string | null;
  postcode: string | null;
  latitude: number;
  longitude: number;
  phone: string | null;
  website: string | null;
  confidence: number | null;
  operatingStatus: string | null;
  sources: OvertureSource[];
};
export type OvertureSettings = {
  enabled: boolean;
  ready: boolean;
  catalogVersion: string | null;
  release: string | null;
  city: string | null;
  generatedAt: string | null;
  recordCount: number;
  maxBatchSize: number;
};
export type OverturePage<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };
export type OvertureCatalog = OverturePage<OvertureVenue> & {
  catalogVersion: string;
  release: string;
  city: string;
  generatedAt: string;
};
export type OverturePreviewOutcome = "READY" | "ALREADY_IMPORTED" | "POSSIBLE_DUPLICATE" | "INCOMPLETE";
export type OverturePreview = {
  catalogVersion: string;
  release: string;
  items: {
    venue: OvertureVenue;
    outcome: OverturePreviewOutcome;
    hallId: number | null;
    duplicateHallIds: number[];
    issues: string[];
  }[];
};
export type OvertureImport = {
  createdCount: number;
  skippedCount: number;
  items: {
    sourceId: string;
    name: string;
    outcome: "CREATED" | Exclude<OverturePreviewOutcome, "READY">;
    hallId: number | null;
    issues: string[];
  }[];
};
export type OvertureDraft = {
  hallId: number;
  sourceId: string;
  name: string;
  city: string | null;
  area: string | null;
  address: string | null;
  category: string;
  release: string;
  importedAt: string;
  sources: OvertureSource[];
  missingFields: string[];
  status: "DRAFT";
};

const basePath = "/admin/overture-onboarding";
function authenticatedToken(token?: string | null): string {
  if (!token?.trim()) throw new Error("Please sign in with an admin account to use application onboarding.");
  return token;
}

export async function getOvertureSettings(token?: string | null, signal?: AbortSignal) {
  return apiRequest<OvertureSettings>(`${basePath}/settings`, { token: authenticatedToken(token), cache: "no-store", signal });
}

export async function getOvertureCatalog(page: number, query: string, token?: string | null, signal?: AbortSignal) {
  const parameters = new URLSearchParams({ page: String(page), size: "20" });
  if (query.trim()) parameters.set("query", query.trim());
  return apiRequest<OvertureCatalog>(`${basePath}/catalog?${parameters}`, { token: authenticatedToken(token), cache: "no-store", signal });
}

export async function previewOvertureVenues(ids: string[], token?: string | null) {
  return apiRequest<OverturePreview>(`${basePath}/preview`, {
    method: "POST", token: authenticatedToken(token), cache: "no-store", body: JSON.stringify({ ids })
  });
}

export async function importOvertureVenues(catalogVersion: string, ids: string[], token?: string | null) {
  return apiRequest<OvertureImport>(`${basePath}/import`, {
    method: "POST", token: authenticatedToken(token), cache: "no-store", body: JSON.stringify({ catalogVersion, ids })
  });
}

export async function getOvertureDrafts(page: number, token?: string | null, signal?: AbortSignal) {
  return apiRequest<OverturePage<OvertureDraft>>(`${basePath}/drafts?page=${page}&size=20`, {
    token: authenticatedToken(token), cache: "no-store", signal
  });
}

// Only rows from the reviewed response and the current selection can be imported.
export function readyOvertureIds(preview: OverturePreview | null, selected: string[]): string[] {
  const selection = new Set(selected);
  return [...new Set(preview?.items.filter((item) => item.outcome === "READY" && selection.has(item.venue.id)).map((item) => item.venue.id) ?? [])];
}

function suppliedWebsiteUrl(value: string | null | undefined): URL | undefined {
  if (!value) return undefined;
  try {
    const url = new URL(value);
    if (!["https:", "http:"].includes(url.protocol) || url.username || url.password) return undefined;
    // Venue websites are displayed separately from the open-data source; do not
    // turn Maps URLs into another source of listing content in this workflow.
    if (/(^|\.)(google\.com|google\.co\.in|goo\.gl|googleusercontent\.com)$/.test(url.hostname)) return undefined;
    return url;
  } catch {
    return undefined;
  }
}

export function safeOvertureWebsite(value: string | null | undefined): string | undefined {
  const url = suppliedWebsiteUrl(value);
  return url?.protocol === "https:" ? url.href : undefined;
}

// Source-provided HTTP websites remain visible as text without making an
// insecure URL clickable or pretending the source omitted the website.
export function overtureWebsiteText(value: string | null | undefined): string | undefined {
  return suppliedWebsiteUrl(value)?.href;
}
