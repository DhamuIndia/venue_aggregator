import { apiRequest } from "@/lib/api-client";
import type { OverturePage, OvertureSource } from "./overture-client";

export type PublicationSummary = {
  hallId: number; name: string; city: string | null; area: string | null; hallStatus: string;
  publicationState: "UNPUBLISHED" | "PUBLISHED"; publicationVersion: number; reviewVersion: number; mediaVersion: number;
};
export type PublicationDetail = PublicationSummary & {
  ready: boolean; blockers: string[]; coverMediaId: number | null; approvedPhotoIds: number[];
  sourceAttribution: OvertureSource[]; sourceRelease: string;
  history: { publicationVersion: number; state: string; adminId: number; adminName: string; at: string; reason: string; reviewVersion: number; mediaVersion: number }[];
};
const base = "/admin/overture-onboarding/publications";
function auth(token?: string | null) { if (!token?.trim()) throw new Error("Sign in as an admin to manage application publication."); return token; }
export function getPublicationList(page: number, token?: string | null, signal?: AbortSignal) {
  return apiRequest<OverturePage<PublicationSummary>>(`${base}?page=${page}&size=20`, { token: auth(token), signal, cache: "no-store" });
}
export function getPublication(hallId: number, token?: string | null, signal?: AbortSignal) {
  return apiRequest<PublicationDetail>(`${base}/${hallId}`, { token: auth(token), signal, cache: "no-store" });
}
export function publishApplicationVenue(detail: PublicationDetail, reason: string, token?: string | null) {
  return apiRequest<PublicationDetail>(`${base}/${detail.hallId}/publish`, { method: "POST", token: auth(token), cache: "no-store", body: JSON.stringify({
    expectedPublicationVersion: detail.publicationVersion, expectedReviewVersion: detail.reviewVersion, expectedMediaVersion: detail.mediaVersion, reason: reason.trim()
  }) });
}
export function unpublishApplicationVenue(detail: PublicationDetail, reason: string, token?: string | null) {
  return apiRequest<PublicationDetail>(`${base}/${detail.hallId}/unpublish`, { method: "POST", token: auth(token), cache: "no-store", body: JSON.stringify({ expectedPublicationVersion: detail.publicationVersion, reason: reason.trim() }) });
}
