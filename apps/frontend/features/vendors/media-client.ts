import { ApiError, apiRequest } from "@/lib/api-client";
import { uploadImageFile } from "@/features/uploads/upload-client";
import type { VendorSummary } from "./types";

const STORAGE_KEY = "venue-vendor-media";
const useMockVendorMedia = process.env.NEXT_PUBLIC_VENDOR_MEDIA_MODE === "mock";

export type VendorMediaItem = {
  id: string;
  url: string;
  caption?: string;
  isCover: boolean;
  sortOrder: number;
  storageKey?: string;
};

export type VendorMediaPayload = {
  url: string;
  fileName?: string;
  caption?: string;
  isCover?: boolean;
  sortOrder?: number;
  storageKey?: string;
};

export function mediaFromVendor(vendor: VendorSummary): VendorMediaItem[] {
  const urls = [vendor.imageUrl, ...vendor.galleryUrls].filter(Boolean);
  return Array.from(new Set(urls)).map((url, index) => ({
    id: index === 0 ? "MEDIA-COVER" : `MEDIA-${index}`,
    url,
    isCover: index === 0,
    sortOrder: index
  }));
}

export async function getVendorMedia(accessToken: string | null | undefined, fallback: VendorMediaItem[]) {
  if (useMockVendorMedia || !accessToken) return getLocalVendorMedia(fallback);

  try {
    const response = await apiRequest<unknown>("/vendor/media", {
      token: accessToken
    });
    const media = extractMedia(response);
    media.forEach(cacheLocalMedia);
    return media.length ? media : getLocalVendorMedia(fallback);
  } catch {
    return getLocalVendorMedia(fallback);
  }
}

export async function uploadAndCreateVendorMedia(file: File, sortOrder: number, accessToken?: string | null) {
  const uploaded = await uploadImageFile(file, "VENDOR_PORTFOLIO", useMockVendorMedia ? null : accessToken);
  return createVendorMedia({
    url: uploaded.url,
    storageKey: uploaded.storageKey,
    fileName: uploaded.fileName,
    caption: uploaded.fileName,
    sortOrder,
    isCover: sortOrder === 0
  }, accessToken);
}

export async function createVendorMedia(payload: VendorMediaPayload, accessToken?: string | null) {
  if (useMockVendorMedia || !accessToken) return createLocalMedia(payload);

  try {
    const response = await apiRequest<unknown>("/vendor/media", {
      method: "POST",
      token: accessToken,
      body: JSON.stringify(toMediaRequest(payload))
    });
    const media = toMediaItem(response, payload) ?? createLocalMedia(payload);
    cacheLocalMedia(media);
    return media;
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 409].includes(exception.status)) {
      throw exception;
    }
    return createLocalMedia(payload);
  }
}

export async function setVendorMediaCover(mediaId: string, accessToken?: string | null) {
  return updateVendorMedia(mediaId, { isCover: true }, accessToken);
}

export async function updateVendorMedia(mediaId: string, patch: Partial<Pick<VendorMediaItem, "caption" | "isCover" | "sortOrder">>, accessToken?: string | null) {
  if (useMockVendorMedia || !accessToken) return updateLocalMedia(mediaId, patch);

  try {
    const response = await apiRequest<unknown>(`/vendor/media/${encodeURIComponent(mediaId)}`, {
      method: "PATCH",
      token: accessToken,
      body: JSON.stringify(toMediaRequest(patch))
    });
    const media = toMediaItem(response) ?? updateLocalMedia(mediaId, patch);
    if (media) cacheLocalMedia(media);
    return media;
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 404, 409].includes(exception.status)) {
      throw exception;
    }
    return updateLocalMedia(mediaId, patch);
  }
}

export async function deleteVendorMedia(mediaId: string, accessToken?: string | null) {
  if (useMockVendorMedia || !accessToken) return deleteLocalMedia(mediaId);

  try {
    await apiRequest<void>(`/vendor/media/${encodeURIComponent(mediaId)}`, {
      method: "DELETE",
      token: accessToken
    });
    return deleteLocalMedia(mediaId);
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 404, 409].includes(exception.status)) {
      throw exception;
    }
    return deleteLocalMedia(mediaId);
  }
}

function getLocalVendorMedia(fallback: VendorMediaItem[]) {
  if (typeof window === "undefined") return fallback;
  const media = readMediaStore();
  return media.length ? media : fallback;
}

function createLocalMedia(payload: VendorMediaPayload) {
  const media: VendorMediaItem = {
    id: `MEDIA-${Date.now().toString().slice(-6)}`,
    url: payload.url,
    caption: payload.caption ?? payload.fileName,
    isCover: Boolean(payload.isCover),
    sortOrder: payload.sortOrder ?? getLocalVendorMedia([]).length,
    storageKey: payload.storageKey
  };
  cacheLocalMedia(media);
  return media;
}

function updateLocalMedia(mediaId: string, patch: Partial<Pick<VendorMediaItem, "caption" | "isCover" | "sortOrder">>) {
  const updated = normalizeCover(getLocalVendorMedia([]).map((item) => item.id === mediaId ? { ...item, ...patch } : item));
  saveLocalMedia(updated);
  return updated.find((item) => item.id === mediaId);
}

function deleteLocalMedia(mediaId: string) {
  const updated = normalizeCover(getLocalVendorMedia([]).filter((item) => item.id !== mediaId));
  saveLocalMedia(updated);
  return mediaId;
}

function cacheLocalMedia(media: VendorMediaItem) {
  const current = getLocalVendorMedia([]).filter((item) => item.id !== media.id);
  saveLocalMedia(normalizeCover([media, ...current].sort((first, second) => first.sortOrder - second.sortOrder)));
}

function saveLocalMedia(media: VendorMediaItem[]) {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(media));
}

function readMediaStore() {
  if (typeof window === "undefined") return [];
  try {
    const parsed = JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? "[]") as unknown;
    return Array.isArray(parsed) ? normalizeCover(parsed.map((item) => toMediaItem(item)).filter(Boolean) as VendorMediaItem[]) : [];
  } catch {
    return [];
  }
}

function normalizeCover(media: VendorMediaItem[]) {
  if (media.length === 0) return media;
  const coverIndex = media.findIndex((item) => item.isCover);
  return media.map((item, index) => ({
    ...item,
    sortOrder: index,
    isCover: coverIndex >= 0 ? index === coverIndex : index === 0
  }));
}

function extractMedia(response: unknown) {
  if (Array.isArray(response)) return response.map((item) => toMediaItem(item)).filter(Boolean) as VendorMediaItem[];
  if (!isRecord(response)) return [];
  const candidates = [response.items, response.content, response.data, response.results, response.media];
  const list = candidates.find(Array.isArray);
  return Array.isArray(list) ? list.map((item) => toMediaItem(item)).filter(Boolean) as VendorMediaItem[] : [];
}

function toMediaRequest(payload: VendorMediaPayload | Partial<Pick<VendorMediaItem, "caption" | "isCover" | "sortOrder">>) {
  const url = "url" in payload ? payload.url : undefined;
  const isCover = "isCover" in payload ? payload.isCover : undefined;
  return {
    url,
    mediaUrl: url,
    storageKey: "storageKey" in payload ? payload.storageKey : undefined,
    fileName: "fileName" in payload ? payload.fileName : undefined,
    caption: payload.caption,
    isCover,
    primary: isCover,
    sortOrder: payload.sortOrder,
    mediaType: "IMAGE",
    type: "IMAGE"
  };
}

function toMediaItem(value: unknown, fallback?: VendorMediaPayload): VendorMediaItem | undefined {
  const record = unwrapRecord(value);
  if (!record) return fallback ? createMediaFromPayload(fallback) : undefined;

  const url = stringValue(record, ["url", "mediaUrl", "media_url", "imageUrl"]) ?? fallback?.url;
  if (!url) return undefined;

  return {
    id: stringValue(record, ["id", "mediaId", "media_id"]) ?? `MEDIA-${Date.now().toString().slice(-6)}`,
    url,
    caption: stringValue(record, ["caption", "fileName", "file_name"]) ?? fallback?.caption ?? fallback?.fileName,
    isCover: booleanValue(record, ["isCover", "primary", "cover"]) ?? Boolean(fallback?.isCover),
    sortOrder: numberValue(record, ["sortOrder", "displayOrder", "order"]) ?? fallback?.sortOrder ?? 0,
    storageKey: stringValue(record, ["storageKey", "storage_key"]) ?? fallback?.storageKey
  };
}

function createMediaFromPayload(payload: VendorMediaPayload): VendorMediaItem {
  return {
    id: `MEDIA-${Date.now().toString().slice(-6)}`,
    url: payload.url,
    caption: payload.caption ?? payload.fileName,
    isCover: Boolean(payload.isCover),
    sortOrder: payload.sortOrder ?? 0,
    storageKey: payload.storageKey
  };
}

function unwrapRecord(value: unknown): Record<string, unknown> | undefined {
  if (!isRecord(value)) return undefined;
  if (isRecord(value.data)) return value.data;
  if (isRecord(value.media)) return value.media;
  if (isRecord(value.upload)) return value.upload;
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
