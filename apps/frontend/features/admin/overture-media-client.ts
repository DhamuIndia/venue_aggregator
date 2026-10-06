import { API_BASE_URL, ApiError, apiRequest } from "@/lib/api-client";

export type OvertureMediaSourceKind = "TEAM_PHOTO" | "BUSINESS_PROVIDED" | "LICENSED_IMAGE";
export type OvertureMediaRightsBasis = "TEAM_OWNED" | "BUSINESS_PERMISSION" | "OPEN_LICENSE";
export type OvertureMediaStatus = "PENDING" | "APPROVED" | "REJECTED" | "ARCHIVED";
export type OvertureMediaActor = { adminId: number; adminName: string };
export type OvertureMediaItem = {
  id: number; hallId: number; sizeBytes: number; width: number; height: number; sha256: string;
  caption: string | null; sourceKind: OvertureMediaSourceKind; sourceReference: string | null;
  rightsBasis: OvertureMediaRightsBasis; licenseName: string | null; permissionEvidence: string; rightsConfirmed: true;
  status: OvertureMediaStatus; sortOrder: number; isCover: boolean;
  uploadedBy: OvertureMediaActor; uploadedAt: string;
  reviewedBy: OvertureMediaActor | null; reviewedAt: string | null; reviewReason: string | null;
  archivedBy: OvertureMediaActor | null; archivedAt: string | null; archiveReason: string | null;
};
export type OvertureMediaGallery = {
  mediaVersion: number; coverMediaId: number | null; items: OvertureMediaItem[];
  limits: { maxInputBytes: number; maxOutputBytes: number; maxActivePhotos: number; maxRetainedBytes: number; maxLifetimePhotos: number };
  activeCount: number; retainedBytes: number;
};
export type OvertureMediaUploadMetadata = {
  expectedVersion: number; caption: string | null; sourceKind: OvertureMediaSourceKind;
  sourceReference: string | null; rightsBasis: OvertureMediaRightsBasis; licenseName: string | null;
  permissionEvidence: string; rightsConfirmed: true;
};

function authenticatedToken(token?: string | null) {
  if (!token?.trim()) throw new Error("Please sign in with an admin account to manage private venue photos.");
  return token;
}
const path = (hallId: number) => `/admin/overture-onboarding/drafts/${hallId}/media`;

export async function getOvertureMedia(hallId: number, token?: string | null, signal?: AbortSignal) {
  return apiRequest<OvertureMediaGallery>(path(hallId), { token: authenticatedToken(token), cache: "no-store", signal });
}
export async function uploadOvertureMedia(hallId: number, file: File, metadata: OvertureMediaUploadMetadata, token?: string | null) {
  const accessToken = authenticatedToken(token);
  const body = new FormData();
  body.append("file", file, file.name);
  body.append("metadata", new Blob([JSON.stringify(metadata)], { type: "application/json" }), "metadata.json");
  return apiRequest<OvertureMediaGallery>(`${path(hallId)}/upload`, { method: "POST", token: accessToken, cache: "no-store", body });
}
export async function reviewOvertureMedia(hallId: number, id: number, expectedVersion: number, status: "APPROVED" | "REJECTED", reason: string, token?: string | null) {
  return apiRequest<OvertureMediaGallery>(`${path(hallId)}/${id}/review`, {
    method: "PUT", token: authenticatedToken(token), cache: "no-store", body: JSON.stringify({ expectedVersion, status, reason })
  });
}
export async function arrangeOvertureMedia(hallId: number, expectedVersion: number, coverMediaId: number | null, orderedMediaIds: number[], token?: string | null) {
  return apiRequest<OvertureMediaGallery>(`${path(hallId)}/arrangement`, {
    method: "PUT", token: authenticatedToken(token), cache: "no-store", body: JSON.stringify({ expectedVersion, coverMediaId, orderedMediaIds })
  });
}
export async function archiveOvertureMedia(hallId: number, id: number, expectedVersion: number, reason: string, token?: string | null) {
  return apiRequest<OvertureMediaGallery>(`${path(hallId)}/${id}/archive`, {
    method: "POST", token: authenticatedToken(token), cache: "no-store", body: JSON.stringify({ expectedVersion, reason })
  });
}
// Private bytes are fetched with bearer auth. No storage URL, public fallback,
// browser persistence or image-optimizer request is part of this workflow.
export async function getOvertureMediaContent(hallId: number, id: number, token?: string | null, signal?: AbortSignal): Promise<Blob> {
  const accessToken = authenticatedToken(token);
  const response = await fetch(`${API_BASE_URL}${path(hallId)}/${id}/content`, {
    headers: { Authorization: `Bearer ${accessToken}` }, cache: "no-store", redirect: "error", signal
  });
  if (!response.ok) {
    let details: unknown;
    try { details = JSON.parse(await (await boundedResponseBlob(response, 16 * 1024)).text()); } catch { details = undefined; }
    const detail = details && typeof details === "object" && "detail" in details && typeof details.detail === "string" ? details.detail : undefined;
    throw new ApiError(response.status, detail || "The private photo preview could not be loaded.", details);
  }
  if (response.headers.get("content-type")?.split(";")[0].trim().toLowerCase() !== "image/jpeg") {
    await cancelResponseBody(response);
    throw new Error("The preview service did not return a normalized JPEG photo.");
  }
  const maxOutputBytes = 4 * 1024 * 1024;
  const declaredLength = response.headers.get("content-length");
  if (declaredLength !== null && (!/^\d+$/.test(declaredLength) || Number(declaredLength) > maxOutputBytes)) {
    await cancelResponseBody(response);
    throw new Error("The private photo preview exceeds the supported size.");
  }
  return boundedResponseBlob(response, maxOutputBytes);
}

async function cancelResponseBody(response: Response) {
  try { await response.body?.cancel(); } catch { /* The fetch may already be aborted. */ }
}

// Content-Length is only an early check, never the memory bound. Count actual
// streamed bytes and cancel before accumulating an over-limit response.
async function boundedResponseBlob(response: Response, limit: number): Promise<Blob> {
  const reader = response.body?.getReader();
  if (!reader) throw new Error("The private photo response is empty.");
  const parts: BlobPart[] = []; let received = 0; let completed = false;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) { completed = true; break; }
      received += value.byteLength;
      if (received > limit) throw new Error("The private photo response exceeds the supported size.");
      parts.push(new Uint8Array(value));
    }
  } finally {
    if (!completed) { try { await reader.cancel(); } catch { /* Preserve the original error. */ } }
    reader.releaseLock();
  }
  if (received < 1) throw new Error("The private photo response is empty.");
  return new Blob(parts, { type: response.headers.get("content-type")?.split(";")[0].trim().toLowerCase() || "application/octet-stream" });
}
