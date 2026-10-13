import { safePhotoCreditUrl, type SupportedPhotoLicense } from "@/features/halls/photo-credit";
import type { OverturePhotoCredit, OverturePhotoCreditSave } from "./overture-media-client";

export type OvertureCreditForm = { title: string; creator: string; creatorUrl: string; sourceUrl: string; licenseCode: SupportedPhotoLicense | ""; changesNotice: string; requiredNotices: string };
export function overtureCreditForm(credit?: OverturePhotoCredit | null): OvertureCreditForm {
  return { title: credit?.title ?? "", creator: credit?.creator ?? "", creatorUrl: credit?.creatorUrl ?? "", sourceUrl: credit?.sourceUrl ?? "", licenseCode: credit?.licenseCode ?? "", changesNotice: credit?.changesNotice ?? "", requiredNotices: credit?.requiredNotices ?? "" };
}
export function originalPhotoLicenseCode(value: string | null): SupportedPhotoLicense | null {
  const normalized = value?.trim().replace(/\s+/g, " ").toUpperCase();
  if (["CC BY 4.0", "CC-BY-4.0", "CREATIVE COMMONS ATTRIBUTION 4.0 INTERNATIONAL"].includes(normalized ?? "")) return "CC_BY_4_0";
  if (["CC0 1.0", "CC0-1.0", "CC0 1.0 UNIVERSAL", "CREATIVE COMMONS ZERO 1.0 UNIVERSAL"].includes(normalized ?? "")) return "CC0_1_0";
  return null;
}
function text(value: string, max: number, label: string, required = true, multiline = false): string | null {
  const normalized = value.replace(/\r\n?/g, "\n").trim();
  if (!normalized) { if (required) throw new Error(`${label} is required.`); return null; }
  if (normalized.length > max || (multiline ? /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f-\u009f\p{Cf}]/u : /[\u0000-\u001f\u007f-\u009f\p{Cf}]/u).test(normalized)) throw new Error(`${label} contains unsupported text or exceeds ${max} characters.`);
  return normalized;
}
export function overtureCreditSave(form: OvertureCreditForm, expectedVersion: number, expectedCreditVersion: number, originalLicense: string | null): OverturePhotoCreditSave {
  const originalCode = originalPhotoLicenseCode(originalLicense);
  if (!originalCode) throw new Error("The original uploaded license is not supported. Preserve that record; reupload a photo with the correct documented supported license instead of changing its provenance.");
  if (form.licenseCode !== originalCode) throw new Error("Choose the supported license matching the original uploaded photo license.");
  const sourceUrl = safePhotoCreditUrl(form.sourceUrl.trim()); const creatorUrl = form.creatorUrl.trim() ? safePhotoCreditUrl(form.creatorUrl.trim()) : null;
  if (!sourceUrl || (form.creatorUrl.trim() && !creatorUrl)) throw new Error("Credit links must be public HTTPS URLs without credentials, query strings, fragments or private hosts. Google Maps photo links are not accepted.");
  return { expectedVersion, expectedCreditVersion, title: text(form.title, 200, "Photo title")!, creator: text(form.creator, 300, "Creator")!, creatorUrl, sourceUrl,
    licenseCode: originalCode, changesNotice: text(form.changesNotice, 1000, "Changes notice", true, true)!, requiredNotices: text(form.requiredNotices, 2000, "Required notices", false, true) };
}
