import type { PublicPhotoCredit } from "./types";

export const PHOTO_PROCESSING_NOTICE = "VenueMart normalized this image to JPEG and may have resized it.";
export const SUPPORTED_PHOTO_LICENSES = {
  CC_BY_4_0: { label: "CC BY 4.0", url: "https://creativecommons.org/licenses/by/4.0/" },
  CC0_1_0: { label: "CC0 1.0", url: "https://creativecommons.org/publicdomain/zero/1.0/" }
} as const;
export type SupportedPhotoLicense = keyof typeof SUPPORTED_PHOTO_LICENSES;

// Validation is local only: credit links are not fetched or used as image sources.
export function safePhotoCreditUrl(value: unknown): string | null {
  if (typeof value !== "string" || !value.trim() || value.length > 2048 || /[\s\u0000-\u001f\u007f-\u009f\p{Cf}\\@]/u.test(value) || /%(?![a-f\d]{2})/i.test(value)) return null;
  try {
    const url = new URL(value); const host = url.hostname.toLowerCase();
    if (url.protocol !== "https:" || url.username || url.password || url.search || url.hash || url.port || !host.includes(".") || host.endsWith(".") || /^\d+(\.\d+){3}$/.test(host) || host.includes(":") || host.startsWith("[")) return null;
    if (host.length > 253 || !/^[a-z\d](?:[a-z\d-]*[a-z\d])?(?:\.[a-z\d](?:[a-z\d-]*[a-z\d])?)+$/.test(host) || !/[a-z]/.test(host) || host.split(".").some((label) => label.length > 63)) return null;
    if (["localhost", "local", "internal", "home", "lan", "corp", "test", "invalid", "example", "onion", "arpa"].some((suffix) => host === suffix || host.endsWith(`.${suffix}`))) return null;
    let decoded = value;
    for (let pass = 0; pass < 16; pass++) {
      const next = decoded.replace(/(?:%[a-f\d]{2})+/gi, (run) => decodeURIComponent(run));
      if (/[\u0000-\u001f\u007f-\u009f\p{Cf}\\@]/u.test(next)) return null;
      if (next === decoded) break;
      decoded = next;
    }
    if (/%[a-f\d]{2}/i.test(decoded)) return null;
    if (/googleusercontent|ggpht|gstatic/.test(host) || host.startsWith("photos.google.") || host.startsWith("maps.google.") || host === "maps.app.goo.gl" || host === "goo.gl" || (/^(?:[^/]+\.)?google\.[^/]+$/.test(host) && /^\/(?:maps|photos)(?:\/|$)/i.test(new URL(decoded).pathname))) return null;
    return url.href;
  } catch { return null; }
}
function text(value: unknown, max: number, multiline = false): string | null {
  const normalized = typeof value === "string" ? (multiline ? value.replace(/\r\n?/g, "\n") : value).trim() : "";
  return normalized && normalized.length <= max && !(multiline ? /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f-\u009f\p{Cf}]/u : /[\u0000-\u001f\u007f-\u009f\p{Cf}]/u).test(normalized) ? normalized : null;
}
export function parsePublicPhotoCredit(value: unknown): PublicPhotoCredit | undefined {
  if (!value || typeof value !== "object" || Array.isArray(value)) return undefined;
  const item = value as Record<string, unknown>;
  if (typeof item.licenseCode !== "string" || !Object.prototype.hasOwnProperty.call(SUPPORTED_PHOTO_LICENSES, item.licenseCode)) return undefined;
  const licenseCode = item.licenseCode as SupportedPhotoLicense; const license = SUPPORTED_PHOTO_LICENSES[licenseCode];
  const title = text(item.title, 200), creator = text(item.creator, 300), changesNotice = text(item.changesNotice, 1000, true), sourceUrl = safePhotoCreditUrl(item.sourceUrl);
  const creatorUrl = item.creatorUrl === null ? null : safePhotoCreditUrl(item.creatorUrl);
  const requiredNotices = item.requiredNotices === null ? null : text(item.requiredNotices, 2000, true);
  if (!title || !creator || !changesNotice || !sourceUrl || (item.creatorUrl !== null && !creatorUrl) || (item.requiredNotices !== null && !requiredNotices) || item.licenseLabel !== license.label || item.licenseUrl !== license.url || item.processingNotice !== PHOTO_PROCESSING_NOTICE) return undefined;
  return { title, creator, creatorUrl, sourceUrl, licenseCode, licenseLabel: license.label, licenseUrl: license.url, changesNotice, processingNotice: PHOTO_PROCESSING_NOTICE, requiredNotices };
}
