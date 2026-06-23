import { ApiError, apiRequest } from "@/lib/api-client";
import type { OwnerHallListing } from "./listing-client";

const STORAGE_KEY = "venue-aggregator-owner-media";
const useMockOwnerMedia = process.env.NEXT_PUBLIC_OWNER_MEDIA_MODE === "mock";

export type OwnerMediaItem = {
  id: string;
  url: string;
  caption?: string;
  isCover: boolean;
  sortOrder: number;
};

export type OwnerMediaPayload = {
  url: string;
  fileName?: string;
  caption?: string;
  isCover?: boolean;
  sortOrder?: number;
};

export type OwnerMediaPatch = {
  caption?: string;
  isCover?: boolean;
  sortOrder?: number;
};

export function mediaFromListing(listing: OwnerHallListing): OwnerMediaItem[] {
  const urls = [listing.imageUrl, ...listing.galleryUrls].filter(Boolean);
  return Array.from(new Set(urls)).map((url, index) => ({
    id: index === 0 ? "MEDIA-COVER" : `MEDIA-${index}`,
    url,
    isCover: index === 0,
    sortOrder: index
  }));
}

export function getLocalOwnerMedia(hallId: string, fallback: OwnerMediaItem[]) {
  if (typeof window === "undefined") return fallback;
  const store = readMediaStore();
  return Object.prototype.hasOwnProperty.call(store, hallId) ? store[hallId] : fallback;
}

export async function createOwnerMedia(hallId: string, payload: OwnerMediaPayload, accessToken?: string | null) {
  if (useMockOwnerMedia || !accessToken) return createLocalMedia(hallId, payload);

  try {
    const response = await apiRequest<unknown>(`/owner/halls/${encodeURIComponent(hallId)}/media`, {
      method: "POST",
      token: accessToken,
      body: JSON.stringify(toMediaRequest(payload))
    });
    const media = toMediaItem(response, payload) ?? createLocalMedia(hallId, payload);
    cacheLocalMedia(hallId, media);
    return media;
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 409].includes(exception.status)) {
      throw exception;
    }
    return createLocalMedia(hallId, payload);
  }
}

export async function updateOwnerMedia(hallId: string, mediaId: string, patch: OwnerMediaPatch, accessToken?: string | null) {
  if (useMockOwnerMedia || !accessToken) return updateLocalMedia(hallId, mediaId, patch);

  try {
    const response = await apiRequest<unknown>(`/owner/halls/${encodeURIComponent(hallId)}/media/${encodeURIComponent(mediaId)}`, {
      method: "PATCH",
      token: accessToken,
      body: JSON.stringify(toMediaRequest(patch))
    });
    const media = toMediaItem(response) ?? updateLocalMedia(hallId, mediaId, patch);
    if (media) cacheLocalMedia(hallId, media);
    return media;
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 404, 409].includes(exception.status)) {
      throw exception;
    }
    return updateLocalMedia(hallId, mediaId, patch);
  }
}

export async function deleteOwnerMedia(hallId: string, mediaId: string, accessToken?: string | null) {
  if (useMockOwnerMedia || !accessToken) {
    deleteLocalMedia(hallId, mediaId);
    return;
  }

  try {
    await apiRequest<void>(`/owner/halls/${encodeURIComponent(hallId)}/media/${encodeURIComponent(mediaId)}`, {
      method: "DELETE",
      token: accessToken
    });
    deleteLocalMedia(hallId, mediaId);
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 404, 409].includes(exception.status)) {
      throw exception;
    }
    deleteLocalMedia(hallId, mediaId);
  }
}

function createLocalMedia(hallId: string, payload: OwnerMediaPayload) {
  const media: OwnerMediaItem = {
    id: `MEDIA-${Date.now().toString().slice(-6)}`,
    url: payload.url,
    caption: payload.caption ?? payload.fileName,
    isCover: Boolean(payload.isCover),
    sortOrder: payload.sortOrder ?? getLocalOwnerMedia(hallId, []).length
  };
  cacheLocalMedia(hallId, media);
  return media;
}

function updateLocalMedia(hallId: string, mediaId: string, patch: OwnerMediaPatch) {
  const updated = normalizeCover(
    getLocalOwnerMedia(hallId, []).map((item) => item.id === mediaId ? { ...item, ...patch } : item)
  );
  saveLocalMedia(hallId, updated);
  return updated.find((item) => item.id === mediaId);
}

function deleteLocalMedia(hallId: string, mediaId: string) {
  const updated = getLocalOwnerMedia(hallId, []).filter((item) => item.id !== mediaId);
  saveLocalMedia(hallId, normalizeCover(updated));
}

function cacheLocalMedia(hallId: string, media: OwnerMediaItem) {
  const current = getLocalOwnerMedia(hallId, []).filter((item) => item.id !== media.id);
  saveLocalMedia(hallId, normalizeCover([media, ...current].sort((first, second) => first.sortOrder - second.sortOrder)));
}

function saveLocalMedia(hallId: string, media: OwnerMediaItem[]) {
  if (typeof window === "undefined") return;
  const store = readMediaStore();
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...store, [hallId]: media }));
}

function readMediaStore(): Record<string, OwnerMediaItem[]> {
  if (typeof window === "undefined") return {};

  try {
    const parsed = JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? "{}") as unknown;
    if (!isRecord(parsed)) return {};
    return Object.fromEntries(
      Object.entries(parsed).map(([hallId, value]) => [
        hallId,
        Array.isArray(value) ? normalizeCover(value.map((item) => toMediaItem(item)).filter(Boolean) as OwnerMediaItem[]) : []
      ])
    );
  } catch {
    return {};
  }
}

function normalizeCover(media: OwnerMediaItem[]) {
  if (media.length === 0) return media;
  const coverIndex = media.findIndex((item) => item.isCover);
  return media.map((item, index) => ({
    ...item,
    sortOrder: index,
    isCover: coverIndex >= 0 ? index === coverIndex : index === 0
  }));
}

function toMediaRequest(payload: OwnerMediaPayload | OwnerMediaPatch) {
  const url = "url" in payload ? payload.url : undefined;
  const isCover = "isCover" in payload ? payload.isCover : undefined;

  return {
    url,
    mediaUrl: url,
    fileName: "fileName" in payload ? payload.fileName : undefined,
    caption: payload.caption,
    isCover,
    primary: isCover,
    sortOrder: payload.sortOrder,
    mediaType: "IMAGE",
    type: "IMAGE"
  };
}

function toMediaItem(value: unknown, fallback?: OwnerMediaPayload): OwnerMediaItem | undefined {
  const record = unwrapRecord(value);
  if (!record) {
    return fallback ? {
      id: `MEDIA-${Date.now().toString().slice(-6)}`,
      url: fallback.url,
      caption: fallback.caption ?? fallback.fileName,
      isCover: Boolean(fallback.isCover),
      sortOrder: fallback.sortOrder ?? 0
    } : undefined;
  }

  const url = stringValue(record, ["url", "mediaUrl", "media_url", "imageUrl"]) ?? fallback?.url;
  if (!url) return undefined;

  return {
    id: stringValue(record, ["id", "mediaId", "media_id"]) ?? `MEDIA-${Date.now().toString().slice(-6)}`,
    url,
    caption: stringValue(record, ["caption", "fileName", "file_name"]) ?? fallback?.caption ?? fallback?.fileName,
    isCover: booleanValue(record, ["isCover", "primary", "cover"]) ?? Boolean(fallback?.isCover),
    sortOrder: numberValue(record, ["sortOrder", "displayOrder", "order"]) ?? fallback?.sortOrder ?? 0
  };
}

function unwrapRecord(value: unknown): Record<string, unknown> | undefined {
  if (!isRecord(value)) return undefined;
  if (isRecord(value.data)) return value.data;
  if (isRecord(value.media)) return value.media;
  return value;
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

function booleanValue(record: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "boolean") return value;
    if (typeof value === "string") return value.toLowerCase() === "true";
  }
  return undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}
