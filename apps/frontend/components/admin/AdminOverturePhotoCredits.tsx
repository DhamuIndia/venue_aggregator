"use client";

import { useEffect, useState } from "react";
import { PublicPhotoCredit } from "@/components/halls/PublicPhotoCredit";
import { PHOTO_PROCESSING_NOTICE, SUPPORTED_PHOTO_LICENSES } from "@/features/halls/photo-credit";
import { originalPhotoLicenseCode, overtureCreditForm, overtureCreditSave, type OvertureCreditForm } from "@/features/admin/overture-credit-form";
import { overtureMediaReason } from "@/features/admin/overture-media-form";
import type { OvertureMediaItem, OverturePhotoCreditSave } from "@/features/admin/overture-media-client";

const field = "mt-1 min-h-11 w-full rounded-md border border-border bg-white px-3 py-2 text-sm font-normal disabled:bg-slate-50";
const button = "min-h-11 rounded-md border border-border px-3 py-2 text-sm font-medium disabled:opacity-50";
export function AdminOverturePhotoCredits({ photo, mediaVersion, disabled, onDirtyChange, onSave, onReview }: {
  photo: OvertureMediaItem; mediaVersion: number; disabled: boolean;
  onDirtyChange: (id: number, dirty: boolean) => void;
  onSave: (photo: OvertureMediaItem, request: OverturePhotoCreditSave) => Promise<void>;
  onReview: (photo: OvertureMediaItem, status: "APPROVED" | "REJECTED", reason: string, rightsConfirmed: boolean, attributionConfirmed: boolean) => Promise<void>;
}) {
  const [form, setForm] = useState<OvertureCreditForm>(() => overtureCreditForm(photo.credit));
  const [reason, setReason] = useState(""); const [rights, setRights] = useState(false); const [attribution, setAttribution] = useState(false); const [error, setError] = useState("");
  useEffect(() => { setForm(overtureCreditForm(photo.credit)); setReason(""); setRights(false); setAttribution(false); setError(""); }, [photo.id, photo.credit?.creditVersion]);
  const fieldsDirty = JSON.stringify(form) !== JSON.stringify(overtureCreditForm(photo.credit));
  useEffect(() => { onDirtyChange(photo.id, fieldsDirty || Boolean(reason || rights || attribution)); }, [photo.id, fieldsDirty, reason, rights, attribution, onDirtyChange]);
  const supported = originalPhotoLicenseCode(photo.licenseName); const exhausted = (photo.credit?.creditVersion ?? 0) >= 100; const saveExhausted = (photo.credit?.creditVersion ?? 0) >= 99;
  const locked = disabled || photo.status === "ARCHIVED" || exhausted;
  const reviewable = photo.credit?.status === "PENDING" && !fieldsDirty;
  let preview = null;
  try { const checked = overtureCreditSave(form, mediaVersion, photo.credit?.creditVersion ?? 0, photo.licenseName); const license = SUPPORTED_PHOTO_LICENSES[checked.licenseCode]; preview = { ...checked, licenseLabel: license.label, licenseUrl: license.url, processingNotice: PHOTO_PROCESSING_NOTICE }; } catch { /* Incomplete drafts are not presented as valid public credits. */ }
  function change(key: keyof OvertureCreditForm, value: string) { setForm((current) => ({ ...current, [key]: value })); setError(""); }
  async function save() { try { const request = overtureCreditSave(form, mediaVersion, photo.credit?.creditVersion ?? 0, photo.licenseName); setError(""); await onSave(photo, request); } catch (e) { setError(e instanceof Error ? e.message : "Could not save photo credits."); } }
  async function review(status: "APPROVED" | "REJECTED") {
    try { if (status === "APPROVED" && (!rights || !attribution)) throw new Error("Confirm both rights and public attribution checks before approving credits."); setError(""); await onReview(photo, status, overtureMediaReason(reason), rights, attribution); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not review photo credits."); }
  }
  return <article className="min-w-0 space-y-3 rounded-lg border border-border p-4" aria-label={`Public credits for private photo ${photo.id}`}>
    <h4 className="font-semibold">Public credits · Photo #{photo.id}</h4><p className="text-xs text-muted-foreground">Original license: {photo.licenseName || "Not recorded"} · Credit v{photo.credit?.creditVersion ?? 0} · {photo.credit?.status ?? "No saved credits"}</p>
    <p className="text-xs text-muted-foreground">Credits are separate from private permission evidence. Saving creates a pending revision; editing approved credits invalidates approval. This does not publish the photo.</p>
    {!supported && <p className="rounded-md bg-amber-50 p-3 text-sm text-amber-900">This original license is not supported for public credits. The original provenance cannot be changed. Reupload a permitted photo with the correct documented CC BY 4.0 or CC0 1.0 license if applicable.</p>}
    {exhausted && <p className="text-sm text-amber-900">This photo reached the 100-revision credit limit. Existing records remain retained.</p>}
    {saveExhausted && !exhausted && <p className="text-sm text-amber-900">No further credit edits can be saved. The final revision slot is reserved for reviewing the current pending credits.</p>}
    {error && <p className="rounded-md bg-rose-50 p-3 text-sm text-rose-800" role="alert">{error}</p>}
    <fieldset className="min-w-0 space-y-3" disabled={locked || !supported || saveExhausted}>
      <label className="block text-sm font-medium">Photo title<input aria-label={`Photo title for photo ${photo.id}`} className={field} maxLength={200} onChange={(event) => change("title", event.target.value)} value={form.title} /></label>
      <label className="block text-sm font-medium">Creator / credited name<input aria-label={`Creator for photo ${photo.id}`} className={field} maxLength={300} onChange={(event) => change("creator", event.target.value)} value={form.creator} /></label>
      <label className="block text-sm font-medium">Creator public URL (optional)<input aria-label={`Creator URL for photo ${photo.id}`} className={field} maxLength={2048} onChange={(event) => change("creatorUrl", event.target.value)} placeholder="https://" type="url" value={form.creatorUrl} /></label>
      <label className="block text-sm font-medium">Public photo source URL<input aria-label={`Photo source URL for photo ${photo.id}`} className={field} maxLength={2048} onChange={(event) => change("sourceUrl", event.target.value)} placeholder="https://" type="url" value={form.sourceUrl} /></label>
      <label className="block text-sm font-medium">Supported original license<select aria-label={`Credit license for photo ${photo.id}`} className={field} onChange={(event) => change("licenseCode", event.target.value)} value={form.licenseCode}><option value="">Select documented license</option>{Object.entries(SUPPORTED_PHOTO_LICENSES).map(([code, license]) => <option key={code} value={code}>{license.label}</option>)}</select></label>
      <label className="block text-sm font-medium">Prior changes notice<textarea aria-label={`Changes notice for photo ${photo.id}`} className={`${field} min-h-20`} maxLength={1000} onChange={(event) => change("changesNotice", event.target.value)} placeholder="Describe earlier modifications, or state that no prior changes are reported." value={form.changesNotice} /></label>
      <p className="rounded-md bg-slate-50 p-3 text-xs">Fixed processing notice: {PHOTO_PROCESSING_NOTICE}</p>
      <label className="block text-sm font-medium">Required copyright / additional notices (optional)<textarea aria-label={`Required notices for photo ${photo.id}`} className={`${field} min-h-20`} maxLength={2000} onChange={(event) => change("requiredNotices", event.target.value)} placeholder="Retain any required source notices. Do not include private permission notes or contact details." value={form.requiredNotices} /></label>
      {preview && <div className="rounded-md border border-dashed p-3"><p className="mb-2 text-xs font-medium">Draft public credit preview — not a rights certificate</p><PublicPhotoCredit credit={preview} /></div>}
      <button className={`${button} bg-primary text-white`} disabled={saveExhausted || (!fieldsDirty && Boolean(photo.credit))} onClick={() => void save()} type="button">Save pending credits for photo #{photo.id}</button>
    </fieldset>
    {photo.credit && <details className="text-xs text-muted-foreground"><summary className="cursor-pointer">Private credit audit record</summary><p className="mt-2">Saved by {photo.credit.changedBy.adminName} · {new Date(photo.credit.changedAt).toLocaleString("en-IN")}</p>{photo.credit.reviewedBy && <><p className="mt-1">Reviewed by {photo.credit.reviewedBy.adminName} · {photo.credit.reviewedAt ? new Date(photo.credit.reviewedAt).toLocaleString("en-IN") : "Not recorded"}</p><p className="mt-1 whitespace-pre-wrap">{photo.credit.reviewReason}</p></>}</details>}
    {fieldsDirty && photo.credit && <p className="text-xs text-amber-900">Save the changed credit fields before reviewing them.</p>}
    {photo.credit?.status === "PENDING" && <fieldset className="min-w-0 space-y-3 border-t pt-3" disabled={locked || !reviewable}>
      <label className="block text-sm font-medium">Private credit review reason<textarea aria-label={`Credit review reason for photo ${photo.id}`} className={`${field} min-h-20`} maxLength={4000} onChange={(event) => setReason(event.target.value)} value={reason} /></label>
      <label className="flex items-start gap-2 text-sm"><input aria-label={`Rights checked for photo ${photo.id}`} checked={rights} className="mt-1 shrink-0 accent-primary" onChange={(event) => setRights(event.target.checked)} type="checkbox" /><span>I checked the usage rights and original license for this venue photo, including the processing performed.</span></label>
      <label className="flex items-start gap-2 text-sm"><input aria-label={`Attribution checked for photo ${photo.id}`} checked={attribution} className="mt-1 shrink-0 accent-primary" onChange={(event) => setAttribution(event.target.checked)} type="checkbox" /><span>I checked the public credit fields and retained notices; no private permission evidence is included.</span></label>
      <div className="flex flex-wrap gap-2"><button className={`${button} bg-primary text-white`} disabled={photo.status !== "APPROVED" || !rights || !attribution || reason.trim().length < 10} onClick={() => void review("APPROVED")} type="button">Approve credits for photo #{photo.id}</button><button className={button} disabled={reason.trim().length < 10} onClick={() => void review("REJECTED")} type="button">Reject credits for photo #{photo.id}</button></div>
      {photo.status !== "APPROVED" && <p className="text-xs text-amber-900">Approve the photo privately before approving its credits.</p>}
    </fieldset>}
  </article>;
}
