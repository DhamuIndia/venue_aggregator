import type { OvertureMediaGallery, OvertureMediaRightsBasis, OvertureMediaSourceKind, OvertureMediaUploadMetadata } from "./overture-media-client";

export type OvertureMediaUploadForm = { caption: string; sourceKind: OvertureMediaSourceKind; sourceReference: string; licenseName: string; permissionEvidence: string; rightsConfirmed: boolean };
export const emptyOvertureMediaForm = (): OvertureMediaUploadForm => ({ caption: "", sourceKind: "TEAM_PHOTO", sourceReference: "", licenseName: "", permissionEvidence: "", rightsConfirmed: false });
export const overtureMediaSourceLabels: Record<OvertureMediaSourceKind, string> = { TEAM_PHOTO: "Photo taken by our team", BUSINESS_PROVIDED: "Provided with business permission", LICENSED_IMAGE: "Image with an open license" };
export const overtureMediaRights: Record<OvertureMediaSourceKind, OvertureMediaRightsBasis> = { TEAM_PHOTO: "TEAM_OWNED", BUSINESS_PROVIDED: "BUSINESS_PERMISSION", LICENSED_IMAGE: "OPEN_LICENSE" };

export function validateOvertureMediaFile(file: Pick<File, "type" | "size" | "name">, maxInputBytes = 8 * 1024 * 1024) {
  if (!["image/jpeg", "image/png"].includes(file.type)) throw new Error("Choose a JPEG or PNG image. WebP, SVG and other formats are not accepted here.");
  if (file.size < 1 || file.size > Math.min(maxInputBytes, 8 * 1024 * 1024)) throw new Error("Choose a non-empty image of 8 MiB or less.");
}
function text(value: string, limit: number, label: string, multiline = false): string | null {
  const controls = multiline ? /[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F-\u009F\p{Cf}]/u : /[\u0000-\u001F\u007F-\u009F\p{Cf}]/u;
  if (value.length > limit || controls.test(value)) throw new Error(`${label} contains invalid characters or exceeds its limit.`);
  return value.trim() || null;
}
export function overtureMediaMetadata(form: OvertureMediaUploadForm, expectedVersion: number): OvertureMediaUploadMetadata {
  const permissionEvidence = text(form.permissionEvidence, 4000, "Permission evidence", true);
  if (!permissionEvidence || permissionEvidence.length < 10) throw new Error("Describe the source and permission evidence in at least 10 characters.");
  if (!form.rightsConfirmed) throw new Error("Confirm that the team has the rights and permission to use this photo.");
  if (!Object.prototype.hasOwnProperty.call(overtureMediaRights, form.sourceKind)) throw new Error("Choose a supported photo source.");
  const sourceReference = text(form.sourceReference, 2048, "Source reference");
  if (sourceReference) {
    let checked: string;
    try { checked = decodeURIComponent(sourceReference.replaceAll("+", " ")).toLowerCase(); } catch { throw new Error("The source reference contains invalid encoding."); }
    if (/googleusercontent|google maps|maps\.google\.|maps\.app\.goo\.gl|goo\.gl\/maps|google\.[^/\s]+\/maps/i.test(checked)) throw new Error("Google Maps photos, screenshots and source references are not accepted for this workflow.");
  }
  const licenseName = form.sourceKind === "LICENSED_IMAGE" ? text(form.licenseName, 180, "License name") : null;
  if (form.sourceKind === "LICENSED_IMAGE" && (!sourceReference || !licenseName)) throw new Error("An openly licensed image requires its source reference and license name.");
  return { expectedVersion, caption: text(form.caption, 500, "Caption"), sourceKind: form.sourceKind, sourceReference,
    rightsBasis: overtureMediaRights[form.sourceKind], licenseName, permissionEvidence, rightsConfirmed: true };
}
export function overtureMediaReason(reason: string) {
  const value = text(reason, 4000, "Review or archive reason", true);
  if (!value || value.length < 10) throw new Error("Add a reason of at least 10 characters explaining this photo decision.");
  return value;
}
export function overtureApprovedMediaIds(gallery: OvertureMediaGallery): number[] {
  return gallery.items.filter((item) => item.status === "APPROVED").sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id).map((item) => item.id);
}
export function retainedOvertureMediaCover(currentCoverId: number | null, gallery: OvertureMediaGallery): number | null {
  // Null is an intentional unsaved choice too, not a request to restore the
  // previously saved cover after an unrelated upload or photo review.
  return currentCoverId === null || overtureApprovedMediaIds(gallery).includes(currentCoverId) ? currentCoverId : gallery.coverMediaId;
}
export function moveOverturePhoto(ids: number[], id: number, offset: -1 | 1): number[] {
  const index = ids.indexOf(id); const target = index + offset;
  if (index < 0 || target < 0 || target >= ids.length) return ids;
  const next = [...ids]; [next[index], next[target]] = [next[target], next[index]]; return next;
}
