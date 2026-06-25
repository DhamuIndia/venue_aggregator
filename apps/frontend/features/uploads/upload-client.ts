import { ApiError, apiRequest } from "@/lib/api-client";

const MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024;
const allowedImageTypes = new Set(["image/jpeg", "image/png", "image/webp"]);

export type UploadPurpose = "OWNER_HALL_MEDIA" | "VENDOR_PORTFOLIO";

export type UploadedMediaFile = {
  url: string;
  storageKey?: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
};

type PresignResponse = {
  uploadUrl: string;
  storageKey: string;
  publicUrl?: string;
  url?: string;
  method?: string;
  headers?: Record<string, string>;
};

export async function uploadImageFile(file: File, purpose: UploadPurpose, accessToken?: string | null): Promise<UploadedMediaFile> {
  validateImageFile(file);

  const localUrl = URL.createObjectURL(file);
  if (!accessToken) return localUpload(file, localUrl);

  try {
    const presign = await apiRequest<unknown>("/uploads/presign", {
      method: "POST",
      token: accessToken,
      body: JSON.stringify({
        fileName: file.name,
        contentType: file.type,
        sizeBytes: file.size,
        purpose
      })
    });

    const upload = toPresignResponse(presign);
    if (!upload) return localUpload(file, localUrl);

    const uploadResponse = await fetch(upload.uploadUrl, {
      method: upload.method ?? "PUT",
      headers: upload.headers ?? { "Content-Type": file.type },
      body: file
    });

    if (!uploadResponse.ok) {
      throw new Error(`Upload failed with status ${uploadResponse.status}`);
    }

    return {
      url: upload.publicUrl ?? upload.url ?? upload.storageKey,
      storageKey: upload.storageKey,
      fileName: file.name,
      contentType: file.type,
      sizeBytes: file.size
    };
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 413, 415, 422].includes(exception.status)) {
      throw exception;
    }
    return localUpload(file, localUrl);
  }
}

function validateImageFile(file: File) {
  if (!allowedImageTypes.has(file.type)) {
    throw new Error("Upload JPEG, PNG, or WebP images only.");
  }
  if (file.size > MAX_IMAGE_SIZE_BYTES) {
    throw new Error("Image size must be 10 MB or less.");
  }
}

function localUpload(file: File, url: string): UploadedMediaFile {
  return {
    url,
    fileName: file.name,
    contentType: file.type,
    sizeBytes: file.size
  };
}

function toPresignResponse(value: unknown): PresignResponse | undefined {
  const record = unwrapRecord(value);
  if (!record) return undefined;

  const uploadUrl = stringValue(record, ["uploadUrl", "upload_url", "signedUrl", "signed_url"]);
  const storageKey = stringValue(record, ["storageKey", "storage_key", "key"]);
  if (!uploadUrl || !storageKey) return undefined;

  return {
    uploadUrl,
    storageKey,
    publicUrl: stringValue(record, ["publicUrl", "public_url", "cdnUrl", "cdn_url"]),
    url: stringValue(record, ["url", "mediaUrl", "media_url", "fileUrl", "file_url"]),
    method: stringValue(record, ["method", "httpMethod", "http_method"]),
    headers: isRecord(record.headers) ? stringRecord(record.headers) : undefined
  };
}

function unwrapRecord(value: unknown): Record<string, unknown> | undefined {
  if (!isRecord(value)) return undefined;
  if (isRecord(value.data)) return unwrapRecord(value.data);
  if (isRecord(value.upload)) return value.upload;
  return value;
}

function stringRecord(record: Record<string, unknown>) {
  return Object.fromEntries(Object.entries(record).filter((entry): entry is [string, string] => typeof entry[1] === "string"));
}

function stringValue(record: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string" && value.trim()) return value;
    if (typeof value === "number") return String(value);
  }
  return undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}
