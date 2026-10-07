"use client";

import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { Archive, ArrowDown, ArrowUp, Camera, Check, CircleAlert, ImageOff, LoaderCircle, Save, Upload, X } from "lucide-react";
import { ApiError } from "@/lib/api-client";
import {
  archiveOvertureMedia, arrangeOvertureMedia, getOvertureMedia, getOvertureMediaContent, reviewOvertureMedia, uploadOvertureMedia, saveOverturePhotoCredit, reviewOverturePhotoCredit,
  type OvertureMediaGallery, type OvertureMediaItem, type OvertureMediaSourceKind, type OverturePhotoCreditSave
} from "@/features/admin/overture-media-client";
import {
  emptyOvertureMediaForm, moveOverturePhoto, overtureApprovedMediaIds, overtureMediaMetadata, overtureMediaReason,
  overtureMediaSourceLabels, retainedOvertureMediaCover, validateOvertureMediaFile, type OvertureMediaUploadForm
} from "@/features/admin/overture-media-form";
import { AdminOverturePhotoCredits } from "@/components/admin/AdminOverturePhotoCredits";

const buttonStyle = "inline-flex min-h-11 items-center justify-center gap-2 rounded-md border border-border bg-white px-3 py-2 text-sm font-medium hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50";
const primaryStyle = "inline-flex min-h-11 items-center justify-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50";
const fieldStyle = "min-h-11 w-full rounded-md border border-border bg-white px-3 py-2 text-sm outline-none focus:border-primary focus:ring-1 focus:ring-primary disabled:bg-slate-50";
const date = (value: string | null) => value ? new Date(value).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" }) : "Not recorded";
const size = (value: number) => `${(value / 1024 / 1024).toFixed(2)} MiB`;
const message = (error: unknown) => error instanceof Error ? error.message : "The photo action could not be completed. Your inputs remain in this form.";
type Preview = { url: string; expiresAt: number };
type Reasons = Record<number, { review: string; archive: string }>;

export function AdminOvertureDraftMedia({ hallId, token, onClose }: { hallId: number; token: string | null; onClose: () => void }) {
  const [gallery, setGallery] = useState<OvertureMediaGallery | null>(null);
  const [form, setForm] = useState<OvertureMediaUploadForm>(emptyOvertureMediaForm);
  const [file, setFile] = useState<File | null>(null);
  const [fileInputKey, setFileInputKey] = useState(0);
  const [reasons, setReasons] = useState<Reasons>({});
  const [orderedIds, setOrderedIds] = useState<number[]>([]);
  const [coverId, setCoverId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [conflict, setConflict] = useState(false);
  const [confirmDiscard, setConfirmDiscard] = useState<"close" | "reload" | null>(null);
  const [archiveTarget, setArchiveTarget] = useState<number | null>(null);
  const [includeArchived, setIncludeArchived] = useState(false);
  const [reload, setReload] = useState(0);
  const [previews, setPreviews] = useState<Record<number, Preview>>({});
  const [previewLoading, setPreviewLoading] = useState<number[]>([]);
  const [creditDirty, setCreditDirty] = useState<Record<number, boolean>>({});
  const [creditReset, setCreditReset] = useState(0);
  const dialog = useRef<HTMLDivElement>(null);
  const closeButton = useRef<HTMLButtonElement>(null);
  const messageRegion = useRef<HTMLDivElement>(null);
  const pendingClose = useRef<() => void>(() => undefined);
  const tokenRef = useRef(token); tokenRef.current = token;
  const galleryRef = useRef(gallery); galleryRef.current = gallery;
  const generation = useRef(0);
  const previewGeneration = useRef(0);
  const busyRef = useRef(false);
  const mounted = useRef(true);
  const previewUrls = useRef<Record<number, Preview>>({});
  const previewControllers = useRef<Record<number, AbortController>>({});
  const previewTimers = useRef<Record<number, number>>({});

  const arrangementDirty = Boolean(gallery && (coverId !== gallery.coverMediaId || JSON.stringify(orderedIds) !== JSON.stringify(overtureApprovedMediaIds(gallery))));
  const dirty = Boolean(file || JSON.stringify(form) !== JSON.stringify(emptyOvertureMediaForm()) || Object.values(reasons).some((value) => value.review || value.archive) || Object.values(creditDirty).some(Boolean) || arrangementDirty);
  const creditDirtyChanged = useCallback((id: number, value: boolean) => {
    setCreditDirty((current) => { if (Boolean(current[id]) === value) return current; const next = { ...current }; if (value) next[id] = true; else delete next[id]; return next; });
  }, []);

  const clearPreviews = useCallback(() => {
    previewGeneration.current += 1;
    Object.values(previewControllers.current).forEach((controller) => controller.abort());
    Object.values(previewUrls.current).forEach((preview) => URL.revokeObjectURL(preview.url));
    Object.values(previewTimers.current).forEach((timer) => window.clearTimeout(timer));
    previewControllers.current = {}; previewUrls.current = {}; previewTimers.current = {};
    if (mounted.current) { setPreviews({}); setPreviewLoading([]); }
  }, []);

  const applyGallery = useCallback((response: OvertureMediaGallery, reset = false) => {
    setGallery(response); galleryRef.current = response;
    const approved = overtureApprovedMediaIds(response);
    if (reset) {
      setOrderedIds(approved); setCoverId(response.coverMediaId);
      setForm(emptyOvertureMediaForm()); setFile(null); setFileInputKey((value) => value + 1); setReasons({});
      setCreditDirty({}); setCreditReset((value) => value + 1);
    } else {
      setOrderedIds((current) => [...current.filter((id) => approved.includes(id)), ...approved.filter((id) => !current.includes(id))]);
      setCoverId((current) => retainedOvertureMediaCover(current, response));
    }
    setConflict(false); setConfirmDiscard(null);
  }, []);

  useEffect(() => {
    const controller = new AbortController(); const current = ++generation.current;
    clearPreviews(); setLoading(true); setError(""); setNotice("");
    void getOvertureMedia(hallId, tokenRef.current, controller.signal).then((response) => {
      if (!controller.signal.aborted && generation.current === current) applyGallery(response, true);
    }).catch((exception) => {
      if (!controller.signal.aborted && generation.current === current) setError(message(exception));
    }).finally(() => {
      if (!controller.signal.aborted && generation.current === current) setLoading(false);
    });
    return () => { controller.abort(); generation.current += 1; };
  }, [hallId, reload, clearPreviews, applyGallery]);

  useEffect(() => {
    if (!error && !notice && !confirmDiscard && archiveTarget === null) return;
    messageRegion.current?.scrollIntoView({ block: "start" }); messageRegion.current?.focus({ preventScroll: true });
  }, [error, notice, confirmDiscard, archiveTarget]);

  function requestClose() { if (busyRef.current) return; if (dirty) setConfirmDiscard("close"); else onClose(); }
  function requestReload() { if (busyRef.current || loading) return; if (dirty) setConfirmDiscard("reload"); else setReload((value) => value + 1); }
  pendingClose.current = requestClose;
  useEffect(() => {
    mounted.current = true;
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const previousOverflow = document.body.style.overflow; document.body.style.overflow = "hidden"; closeButton.current?.focus();
    function keydown(event: KeyboardEvent) {
      if (event.key === "Escape") { event.preventDefault(); pendingClose.current(); return; }
      if (event.key !== "Tab") return;
      const focusable = Array.from(dialog.current?.querySelectorAll<HTMLElement>('button, input, select, textarea, a[href], summary, [tabindex="0"]') ?? []).filter((element) => !element.matches(":disabled") && element.getClientRects().length > 0);
      if (!focusable.length) { event.preventDefault(); return; }
      const first = focusable[0]; const last = focusable[focusable.length - 1];
      if (event.shiftKey && (document.activeElement === first || !dialog.current?.contains(document.activeElement))) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && (document.activeElement === last || !dialog.current?.contains(document.activeElement))) { event.preventDefault(); first.focus(); }
    }
    document.addEventListener("keydown", keydown);
    return () => {
      mounted.current = false; clearPreviews(); generation.current += 1;
      document.body.style.overflow = previousOverflow; document.removeEventListener("keydown", keydown);
      const target = previousFocus?.isConnected ? previousFocus : document.querySelector<HTMLElement>(`[data-overture-media-hall-id="${hallId}"]`);
      target?.focus();
    };
  }, [clearPreviews, hallId]);

  function setReason(id: number, kind: "review" | "archive", value: string) {
    setReasons((current) => ({ ...current, [id]: { review: current[id]?.review ?? "", archive: current[id]?.archive ?? "", [kind]: value } }));
  }
  async function mutate(label: string, operation: () => Promise<OvertureMediaGallery>, success: () => void) {
    if (busyRef.current || conflict || !gallery) return;
    busyRef.current = true; setBusy(label); setError(""); setNotice(""); clearPreviews();
    const current = generation.current;
    try {
      const response = await operation();
      if (current !== generation.current) return;
      applyGallery(response); success(); setNotice("Photo changes saved privately. This venue is still a draft; nothing was published.");
    } catch (exception) {
      if (current !== generation.current) return;
      if (exception instanceof ApiError && exception.status === 409) { setConflict(true); setError("The private photo gallery changed. Your file, evidence, reasons, credit fields, credit confirmations and arrangement choices are preserved. Reload the latest gallery before trying again."); }
      else setError(message(exception));
    } finally { if (current === generation.current) { busyRef.current = false; setBusy(null); } }
  }
  async function upload(event: FormEvent) {
    event.preventDefault(); if (!gallery || busyRef.current || conflict) return;
    try {
      if (!file) throw new Error("Choose a photo to upload.");
      validateOvertureMediaFile(file, gallery.limits.maxInputBytes);
      if (gallery.activeCount >= gallery.limits.maxActivePhotos) throw new Error("The active photo limit is reached. Archive an existing photo before adding another.");
      if (gallery.retainedBytes >= gallery.limits.maxRetainedBytes || gallery.items.length >= gallery.limits.maxLifetimePhotos) throw new Error("The retained photo limit is reached. Archiving retains bytes and does not free this quota.");
      const metadata = overtureMediaMetadata(form, gallery.mediaVersion);
      await mutate("upload", () => uploadOvertureMedia(hallId, file, metadata, tokenRef.current), () => { setFile(null); setFileInputKey((value) => value + 1); setForm(emptyOvertureMediaForm()); });
    } catch (exception) { setError(message(exception)); }
  }
  async function review(item: OvertureMediaItem, status: "APPROVED" | "REJECTED") {
    if (!gallery) return;
    try { const reason = overtureMediaReason(reasons[item.id]?.review ?? ""); await mutate(`review-${item.id}`, () => reviewOvertureMedia(hallId, item.id, gallery.mediaVersion, status, reason, tokenRef.current), () => setReason(item.id, "review", "")); }
    catch (exception) { setError(message(exception)); }
  }
  async function archive() {
    if (!gallery || archiveTarget === null) return;
    const id = archiveTarget;
    try { const reason = overtureMediaReason(reasons[id]?.archive ?? ""); await mutate(`archive-${id}`, () => archiveOvertureMedia(hallId, id, gallery.mediaVersion, reason, tokenRef.current), () => { setReason(id, "archive", ""); setReason(id, "review", ""); setArchiveTarget(null); }); }
    catch (exception) { setError(message(exception)); }
  }
  async function arrange() {
    if (!gallery || !arrangementDirty) return;
    const approved = overtureApprovedMediaIds(gallery);
    if (orderedIds.length !== approved.length || new Set(orderedIds).size !== approved.length || approved.some((id) => !orderedIds.includes(id)) || (coverId !== null && !approved.includes(coverId))) { setError("The arrangement must contain every approved photo once, with an approved cover or no cover."); return; }
    await mutate("arrangement", () => arrangeOvertureMedia(hallId, gallery.mediaVersion, coverId, orderedIds, tokenRef.current), () => undefined);
  }
  async function saveCredit(photo: OvertureMediaItem, request: OverturePhotoCreditSave) {
    await mutate(`credit-save-${photo.id}`, () => saveOverturePhotoCredit(hallId, photo.id, request, tokenRef.current), () => undefined);
  }
  async function reviewCredit(photo: OvertureMediaItem, status: "APPROVED" | "REJECTED", reason: string, rightsConfirmed: boolean, attributionConfirmed: boolean) {
    if (!gallery) return;
    await mutate(`credit-review-${photo.id}`, () => reviewOverturePhotoCredit(hallId, photo.id, gallery.mediaVersion, photo.credit?.creditVersion ?? 0, status, reason, rightsConfirmed, attributionConfirmed, tokenRef.current), () => undefined);
  }
  function hidePreview(id: number) {
    previewControllers.current[id]?.abort(); delete previewControllers.current[id];
    const preview = previewUrls.current[id]; if (preview) URL.revokeObjectURL(preview.url);
    if (previewTimers.current[id]) window.clearTimeout(previewTimers.current[id]);
    delete previewUrls.current[id]; delete previewTimers.current[id];
    setPreviews((current) => { const next = { ...current }; delete next[id]; return next; });
    setPreviewLoading((current) => current.filter((value) => value !== id));
  }
  async function previewPhoto(item: OvertureMediaItem) {
    if (item.status === "ARCHIVED" || busyRef.current || conflict || previewControllers.current[item.id] || previewUrls.current[item.id]) return;
    const controller = new AbortController(); const current = previewGeneration.current;
    previewControllers.current[item.id] = controller; setPreviewLoading((values) => [...values, item.id]);
    try {
      const blob = await getOvertureMediaContent(hallId, item.id, tokenRef.current, controller.signal);
      if (controller.signal.aborted || current !== previewGeneration.current || !mounted.current || galleryRef.current?.items.find((photo) => photo.id === item.id)?.status === "ARCHIVED") return;
      const value = { url: URL.createObjectURL(blob), expiresAt: Date.now() + 60_000 };
      previewUrls.current[item.id] = value; setPreviews((values) => ({ ...values, [item.id]: value }));
      previewTimers.current[item.id] = window.setTimeout(() => hidePreview(item.id), 60_000);
    } catch (exception) { if (!controller.signal.aborted && current === previewGeneration.current && mounted.current) setError(message(exception)); }
    finally { if (!controller.signal.aborted && current === previewGeneration.current && mounted.current) { delete previewControllers.current[item.id]; setPreviewLoading((values) => values.filter((id) => id !== item.id)); } }
  }

  const disabled = Boolean(busy || conflict);
  const visibleItems = gallery?.items.filter((item) => includeArchived || item.status !== "ARCHIVED") ?? [];
  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-2 sm:p-6">
    <div aria-labelledby={`private-photos-title-${hallId}`} aria-modal="true" className="flex max-h-[96dvh] w-full max-w-5xl flex-col overflow-hidden rounded-xl bg-white shadow-xl" ref={dialog} role="dialog">
      <header className="flex shrink-0 items-start justify-between gap-3 border-b border-border p-4 sm:p-5"><div className="min-w-0"><h2 className="flex items-center gap-2 text-lg font-semibold" id={`private-photos-title-${hallId}`}><Camera className="shrink-0" size={20} />Private photos · Venue #{hallId}</h2><p className="mt-1 text-sm text-muted-foreground">Admin-reviewed photo records only. Approval here does not publish photos or the venue.</p></div><button aria-label="Close private photos" className={buttonStyle} disabled={Boolean(busy)} onClick={requestClose} ref={closeButton} type="button"><X size={18} /></button></header>
      <div className="min-h-0 overflow-y-auto overscroll-contain p-4 sm:p-5">
        <div aria-label="Private photo messages" className="outline-none" ref={messageRegion} role="region" tabIndex={-1}>
          {loading ? <p className="flex items-center gap-2 p-4 text-sm" role="status"><LoaderCircle className="animate-spin" size={17} />Loading private photo records…</p> : null}
          {error ? <div className="mb-4 rounded-md border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800" role="alert"><p className="flex items-start gap-2"><CircleAlert className="mt-0.5 shrink-0" size={16} />{error}</p><button className={`${buttonStyle} mt-3`} disabled={Boolean(busy) || loading} onClick={gallery ? requestReload : () => setReload((value) => value + 1)} type="button">{gallery ? "Reload latest gallery" : "Retry loading photos"}</button></div> : null}
          {notice ? <p className="mb-4 rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800" role="status">{notice}</p> : null}
          {confirmDiscard ? <div className="mb-4 rounded-lg border border-amber-300 bg-amber-50 p-4" role="alert"><h3 className="font-semibold">Discard unsaved photo inputs?</h3><p className="mt-1 text-sm">{confirmDiscard === "reload" ? "Reloading clears the selected file, evidence, reasons and unsaved arrangement choices." : "Closing clears the selected file, evidence, reasons and unsaved arrangement choices."} Temporary previews are cleared too.</p><div className="mt-3 flex flex-wrap gap-2"><button className={buttonStyle} onClick={() => setConfirmDiscard(null)} type="button">Keep editing photos</button><button className={buttonStyle} onClick={() => { if (confirmDiscard === "close") onClose(); else { setConfirmDiscard(null); setArchiveTarget(null); setReload((value) => value + 1); } }} type="button">{confirmDiscard === "reload" ? "Discard photo inputs and reload" : "Discard photo inputs and close"}</button></div></div> : null}
          {archiveTarget !== null ? <div className="mb-4 rounded-lg border border-amber-300 bg-amber-50 p-4"><h3 className="font-semibold">Archive photo #{archiveTarget}?</h3><p className="mt-1 text-sm">This hides its preview and removes it from the private cover/order. The photo bytes and audit history remain retained; storage quota is not freed.</p><label className="mt-3 block text-sm font-medium" htmlFor={`archive-reason-${archiveTarget}`}>Archive reason for photo #{archiveTarget}<textarea className={`${fieldStyle} mt-1.5 min-h-20`} disabled={disabled} id={`archive-reason-${archiveTarget}`} maxLength={4000} onChange={(event) => setReason(archiveTarget, "archive", event.target.value)} value={reasons[archiveTarget]?.archive ?? ""} /></label><div className="mt-3 flex flex-wrap gap-2"><button className={buttonStyle} disabled={Boolean(busy)} onClick={() => setArchiveTarget(null)} type="button">Keep photo</button><button className={buttonStyle} disabled={disabled} onClick={() => void archive()} type="button"><Archive size={15} />Confirm archive</button></div></div> : null}
        </div>
        {gallery && !loading ? <div className="space-y-6">
          <div className="rounded-lg bg-slate-50 p-4 text-sm"><p className="font-medium">Gallery version {gallery.mediaVersion} · {gallery.activeCount}/{gallery.limits.maxActivePhotos} active photos</p><p className="mt-1">Retained: {size(gallery.retainedBytes)} / {size(gallery.limits.maxRetainedBytes)} · {gallery.items.length}/{gallery.limits.maxLifetimePhotos} lifetime photo records</p><p className="mt-2 text-xs text-muted-foreground">JPEG/PNG input up to {size(gallery.limits.maxInputBytes)}; the server validates and normalizes to JPEG up to {size(gallery.limits.maxOutputBytes)}. Archives remain in retained totals.</p></div>
          <p className="rounded-md bg-blue-50 p-3 text-xs text-blue-900">For licensed-image public credits, the original upload license must document CC BY 4.0 or CC0 1.0 (including the supported full license names). Other licenses can remain private but are blocked from licensed-photo publication. Source references and permission evidence are never copied into public credits.</p>
          {visibleItems.some((item) => item.sourceKind === "LICENSED_IMAGE") && <section className="space-y-3" aria-label="Private licensed-photo credits"><h3 className="font-semibold">Licensed photo public credits</h3><div className="grid gap-4 sm:grid-cols-2">{visibleItems.filter((item) => item.sourceKind === "LICENSED_IMAGE").map((item) => <AdminOverturePhotoCredits disabled={disabled} key={`credits-${creditReset}-${item.id}`} mediaVersion={gallery.mediaVersion} onDirtyChange={creditDirtyChanged} onReview={reviewCredit} onSave={saveCredit} photo={item} />)}</div></section>}
          <form className="space-y-3 rounded-lg border border-border p-4" onSubmit={(event) => void upload(event)}><h3 className="font-semibold">Upload a photo with permission evidence</h3><fieldset className="min-w-0 space-y-3" disabled={disabled}><label className="block text-sm font-medium" htmlFor={`private-photo-file-${hallId}`}>Photo file<input accept="image/jpeg,image/png" className={`${fieldStyle} mt-1.5 min-w-0 max-w-full py-2`} id={`private-photo-file-${hallId}`} key={fileInputKey} onChange={(event) => { setFile(event.target.files?.[0] ?? null); setNotice(""); }} type="file" /></label>{file ? <p className="break-all text-xs text-muted-foreground">Selected: {file.name} · {size(file.size)}</p> : null}<label className="block text-sm font-medium" htmlFor={`photo-caption-${hallId}`}>Photo caption (optional)<input className={`${fieldStyle} mt-1.5`} id={`photo-caption-${hallId}`} maxLength={500} onChange={(event) => setForm((value) => ({ ...value, caption: event.target.value }))} value={form.caption} /></label><label className="block text-sm font-medium" htmlFor={`photo-source-${hallId}`}>Photo source<select className={`${fieldStyle} mt-1.5`} id={`photo-source-${hallId}`} onChange={(event) => setForm((value) => ({ ...value, sourceKind: event.target.value as OvertureMediaSourceKind, rightsConfirmed: false }))} value={form.sourceKind}>{Object.entries(overtureMediaSourceLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><label className="block text-sm font-medium" htmlFor={`source-reference-${hallId}`}>Source reference {form.sourceKind === "LICENSED_IMAGE" ? "(required)" : "(optional)"}<input className={`${fieldStyle} mt-1.5`} id={`source-reference-${hallId}`} maxLength={2048} onChange={(event) => setForm((value) => ({ ...value, sourceReference: event.target.value }))} placeholder="Photo collection, permission document or licensed source reference" value={form.sourceReference} /></label>{form.sourceKind === "LICENSED_IMAGE" ? <label className="block text-sm font-medium" htmlFor={`license-name-${hallId}`}>License name<input className={`${fieldStyle} mt-1.5`} id={`license-name-${hallId}`} maxLength={180} onChange={(event) => setForm((value) => ({ ...value, licenseName: event.target.value }))} value={form.licenseName} /></label> : null}<label className="block text-sm font-medium" htmlFor={`permission-evidence-${hallId}`}>Permission evidence<textarea className={`${fieldStyle} mt-1.5 min-h-24`} id={`permission-evidence-${hallId}`} maxLength={4000} onChange={(event) => setForm((value) => ({ ...value, permissionEvidence: event.target.value }))} placeholder="Explain who supplied or took the photo and the permission or license checked (10–4000 characters)." value={form.permissionEvidence} /></label><label className="flex items-start gap-2 text-sm"><input checked={form.rightsConfirmed} className="mt-0.5 h-4 w-4 shrink-0 accent-primary" onChange={(event) => setForm((value) => ({ ...value, rightsConfirmed: event.target.checked }))} type="checkbox" /><span>I confirm the team has the rights and permission to use this photo.</span></label><p className="text-xs text-muted-foreground">Do not upload Google Maps photos or screenshots. Source references are recorded as evidence, never fetched automatically.</p><button className={primaryStyle} disabled={!file || disabled} type="submit">{busy === "upload" ? <LoaderCircle className="animate-spin" size={16} /> : <Upload size={16} />}Upload privately for review</button></fieldset></form>
          {orderedIds.length ? <section className="space-y-3 rounded-lg border border-border p-4"><h3 className="font-semibold">Approved photos: private cover and order</h3><p className="text-sm text-muted-foreground">Only approved photos can be selected. This arrangement is private and is not a publication action.</p><fieldset className="min-w-0 space-y-3" disabled={disabled}><label className="block text-sm font-medium" htmlFor={`private-cover-${hallId}`}>Private cover photo<select className={`${fieldStyle} mt-1.5`} id={`private-cover-${hallId}`} onChange={(event) => setCoverId(event.target.value ? Number(event.target.value) : null)} value={coverId ?? ""}><option value="">No cover selected</option>{orderedIds.map((id) => <option key={id} value={id}>Photo #{id}{gallery.items.find((item) => item.id === id)?.caption ? ` · ${gallery.items.find((item) => item.id === id)?.caption}` : ""}</option>)}</select></label><ol className="space-y-2">{orderedIds.map((id, index) => <li className="flex flex-wrap items-center justify-between gap-2 rounded-md bg-slate-50 p-2 text-sm" key={id}><span className="break-words">{index + 1}. Photo #{id}</span><div className="flex gap-2"><button aria-label={`Move photo #${id} earlier`} className={buttonStyle} disabled={index === 0} onClick={() => setOrderedIds((current) => moveOverturePhoto(current, id, -1))} type="button"><ArrowUp size={16} /></button><button aria-label={`Move photo #${id} later`} className={buttonStyle} disabled={index + 1 === orderedIds.length} onClick={() => setOrderedIds((current) => moveOverturePhoto(current, id, 1))} type="button"><ArrowDown size={16} /></button></div></li>)}</ol><button className={primaryStyle} disabled={!arrangementDirty} onClick={() => void arrange()} type="button"><Save size={16} />Save private cover and order</button></fieldset></section> : null}
          <section className="space-y-3"><div className="flex flex-wrap items-center justify-between gap-3"><h3 className="font-semibold">Photo review records</h3><label className="flex items-center gap-2 text-sm"><input checked={includeArchived} className="h-4 w-4 accent-primary" onChange={(event) => setIncludeArchived(event.target.checked)} type="checkbox" />Show archived records</label></div>{!visibleItems.length ? <p className="rounded-lg border border-dashed border-border p-6 text-sm text-muted-foreground">No {includeArchived ? "photo records" : "active photos"} yet. Upload a permitted photo to start its private review.</p> : <div className="grid gap-4 sm:grid-cols-2">{visibleItems.map((item) => <article className="min-w-0 space-y-3 rounded-lg border border-border p-4" key={item.id}><div className="flex flex-wrap items-center justify-between gap-2"><h4 className="font-semibold">Photo #{item.id}</h4><span className="rounded-full bg-slate-100 px-2 py-1 text-xs font-medium">{item.status} · Private{item.isCover ? " · Cover" : ""}</span></div>{item.caption ? <p className="break-words text-sm">{item.caption}</p> : null}{previews[item.id] ? <div><img alt={item.caption || `Private photo #${item.id} for venue #${hallId}`} className="max-h-60 w-full rounded-md bg-slate-100 object-contain" src={previews[item.id].url} /><p className="mt-1 text-xs text-muted-foreground">Temporary in-memory preview; clears automatically within 60 seconds.</p><button className={`${buttonStyle} mt-2`} onClick={() => hidePreview(item.id)} type="button">Hide preview</button></div> : <div className="grid h-32 place-items-center rounded-md bg-slate-100 text-muted-foreground"><ImageOff aria-hidden size={24} /></div>}{item.status !== "ARCHIVED" && !previews[item.id] ? <button className={buttonStyle} disabled={Boolean(busy) || previewLoading.includes(item.id)} onClick={() => void previewPhoto(item)} type="button">{previewLoading.includes(item.id) ? <LoaderCircle className="animate-spin" size={16} /> : <Camera size={16} />}Preview photo #{item.id} privately</button> : null}<p className="text-xs text-muted-foreground">Normalized JPEG · {item.width} × {item.height} · {size(item.sizeBytes)}</p><details className="break-words text-xs text-muted-foreground"><summary className="cursor-pointer font-medium">Source, permission and audit history</summary><dl className="mt-2 space-y-2"><div><dt className="font-medium">Source / rights</dt><dd>{overtureMediaSourceLabels[item.sourceKind]} · {item.rightsBasis}{item.licenseName ? ` · ${item.licenseName}` : ""}</dd></div>{item.sourceReference ? <div><dt className="font-medium">Source reference</dt><dd>{item.sourceReference}</dd></div> : null}<div><dt className="font-medium">Permission evidence</dt><dd className="whitespace-pre-wrap">{item.permissionEvidence}</dd></div><div><dt className="font-medium">Uploaded</dt><dd>{item.uploadedBy.adminName} · {date(item.uploadedAt)}</dd></div>{item.reviewedBy ? <div><dt className="font-medium">Reviewed</dt><dd>{item.reviewedBy.adminName} · {date(item.reviewedAt)}<span className="block whitespace-pre-wrap">{item.reviewReason}</span></dd></div> : null}{item.archivedBy ? <div><dt className="font-medium">Archived</dt><dd>{item.archivedBy.adminName} · {date(item.archivedAt)}<span className="block whitespace-pre-wrap">{item.archiveReason}</span></dd></div> : null}<div><dt className="font-medium">Normalized content SHA-256</dt><dd className="break-all">{item.sha256}</dd></div></dl></details>{item.status === "PENDING" ? <fieldset className="min-w-0 space-y-2 border-t border-border pt-3" disabled={disabled}><label className="block text-sm font-medium" htmlFor={`photo-review-${item.id}`}>Review reason for photo #{item.id}<textarea className={`${fieldStyle} mt-1.5 min-h-20`} id={`photo-review-${item.id}`} maxLength={4000} onChange={(event) => setReason(item.id, "review", event.target.value)} placeholder="Record the image, venue association and permission checks (10–4000 characters)." value={reasons[item.id]?.review ?? ""} /></label><div className="flex flex-wrap gap-2"><button className={primaryStyle} onClick={() => void review(item, "APPROVED")} type="button"><Check size={15} />Approve privately</button><button className={buttonStyle} onClick={() => void review(item, "REJECTED")} type="button">Reject photo</button></div></fieldset> : null}{item.status !== "ARCHIVED" ? <button className={buttonStyle} disabled={disabled} onClick={() => { setArchiveTarget(item.id); setNotice(""); }} type="button"><Archive size={15} />Archive photo #{item.id}</button> : <p className="text-xs text-muted-foreground">Archived bytes and history retained; no preview or arrangement actions.</p>}</article>)}</div>}</section>
        </div> : null}
      </div>
      <footer className="flex shrink-0 flex-wrap items-center justify-between gap-3 border-t border-border p-4 sm:p-5"><p className="text-xs text-muted-foreground">{busy ? "Saving private photo action…" : dirty ? "Unsaved photo inputs" : "Private records only · facts review remains separate"}</p><div className="flex gap-2"><button className={buttonStyle} disabled={Boolean(busy) || loading} onClick={requestReload} type="button">Reload gallery</button><button className={buttonStyle} disabled={Boolean(busy)} onClick={requestClose} type="button">Close photos</button></div></footer>
    </div>
  </div>;
}
