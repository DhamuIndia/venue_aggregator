"use client";

import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { CircleAlert, LoaderCircle, Save, X } from "lucide-react";
import { ApiError } from "@/lib/api-client";
import {
  getOvertureDraft, updateOvertureDraft,
  type OvertureDraftDetail, type OvertureDraftUpdate, type OvertureDuplicateDecision,
  type OvertureFactField, type OvertureReviewStatus
} from "@/features/admin/overture-client";
import {
  overtureAmenityLabels, overtureDisplayFact, overtureFactsFromForm, overtureFactValue,
  overtureFieldLabels, overtureFormFromFacts, overtureIdentityFields, overtureRequiredVerifiedFields,
  type OvertureReviewForm
} from "@/features/admin/overture-review-form";

const buttonStyle = "inline-flex min-h-11 items-center justify-center gap-2 rounded-md border border-border bg-white px-3 py-2 text-sm font-medium hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50";
const fieldStyle = "min-h-11 w-full rounded-md border border-border bg-white px-3 py-2 text-sm outline-none focus:border-primary focus:ring-1 focus:ring-primary disabled:bg-slate-50";
const date = (value: string | null) => value ? new Date(value).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" }) : "Not yet reviewed";
const message = (error: unknown) => error instanceof Error ? error.message : "The draft could not be saved. Your changes remain in this form.";
const reviewLabel: Record<OvertureReviewStatus, string> = {
  UNREVIEWED: "Not reviewed", IN_REVIEW: "Review in progress", VERIFIED: "Facts reviewed — still private", DUPLICATE: "Confirmed duplicate — still private"
};
const limits: Partial<Record<OvertureFactField, number>> = { name: 180, address: 120, city: 120, area: 120, postcode: 16, phone: 20, website: 2048, description: 4000 };
const groups: { title: string; fields: OvertureFactField[] }[] = [
  { title: "Identity and location", fields: ["name", "address", "city", "area", "postcode", "latitude", "longitude"] },
  { title: "Contacts and venue details", fields: ["phone", "website", "operatingStatus", "capacity", "description"] },
  { title: "Amenities", fields: Object.keys(overtureAmenityLabels).map((key) => `amenities.${key}` as OvertureFactField) }
];

export function AdminOvertureDraftReview({ hallId, token, onClose, onSaved }: {
  hallId: number; token: string | null; onClose: () => void; onSaved: () => void;
}) {
  const [detail, setDetail] = useState<OvertureDraftDetail | null>(null);
  const [form, setForm] = useState<OvertureReviewForm | null>(null);
  const [verified, setVerified] = useState<OvertureFactField[]>([]);
  const [reviewStatus, setReviewStatus] = useState<OvertureReviewStatus>("IN_REVIEW");
  const [reviewNotes, setReviewNotes] = useState("");
  const [duplicateDecision, setDuplicateDecision] = useState<OvertureDuplicateDecision>("NOT_REVIEWED");
  const [duplicateNotes, setDuplicateNotes] = useState("");
  const [reviewedIds, setReviewedIds] = useState<number[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [conflict, setConflict] = useState(false);
  const [confirmDiscard, setConfirmDiscard] = useState<"close" | "reload" | null>(null);
  const [reload, setReload] = useState(0);
  const dialog = useRef<HTMLDivElement>(null);
  const closeButton = useRef<HTMLButtonElement>(null);
  const messageRegion = useRef<HTMLDivElement>(null);
  const generation = useRef(0);
  const savingRef = useRef(false);
  const baseline = useRef("");
  const pendingClose = useRef<() => void>(() => undefined);
  const latestToken = useRef(token);
  latestToken.current = token;

  const snapshot = JSON.stringify({ form, verified, reviewStatus, reviewNotes, duplicateDecision, duplicateNotes, reviewedIds });
  const dirty = detail !== null && baseline.current !== snapshot;
  const initialForm = detail ? overtureFormFromFacts(detail.facts) : null;
  const identityChanged = Boolean(form && initialForm && overtureIdentityFields.some((field) => form[field] !== initialForm[field]));

  useEffect(() => {
    if (!error && !notice && !confirmDiscard) return;
    // Actions often happen at the bottom of this long form. Bring their result
    // and discard controls into view rather than leaving them off-screen.
    messageRegion.current?.scrollIntoView({ block: "start" });
    messageRegion.current?.focus({ preventScroll: true });
  }, [error, notice, confirmDiscard]);

  const populate = useCallback((response: OvertureDraftDetail) => {
    const nextForm = overtureFormFromFacts(response.facts);
    const nextVerified = Object.keys(response.verifications) as OvertureFactField[];
    const nextNotes = response.reviewNotes ?? "";
    const nextDuplicateNotes = response.duplicateNotes ?? "";
    setDetail(response); setForm(nextForm); setVerified(nextVerified);
    setReviewStatus(response.reviewStatus); setReviewNotes(nextNotes);
    setDuplicateDecision(response.duplicateDecision); setDuplicateNotes(nextDuplicateNotes);
    setReviewedIds(response.reviewedDuplicateHallIds);
    baseline.current = JSON.stringify({ form: nextForm, verified: nextVerified, reviewStatus: response.reviewStatus,
      reviewNotes: nextNotes, duplicateDecision: response.duplicateDecision,
      duplicateNotes: nextDuplicateNotes, reviewedIds: response.reviewedDuplicateHallIds });
    setConflict(false); setConfirmDiscard(null);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    const current = ++generation.current;
    setLoading(true); setError(""); setNotice("");
    // A routine same-account token renewal must not replace an unsaved form.
    // Account changes unmount this dialog; explicit reload uses the latest token.
    void getOvertureDraft(hallId, latestToken.current, controller.signal).then((response) => {
      if (!controller.signal.aborted && generation.current === current) populate(response);
    }).catch((exception) => {
      if (!controller.signal.aborted && generation.current === current) setError(message(exception));
    }).finally(() => {
      if (!controller.signal.aborted && generation.current === current) setLoading(false);
    });
    return () => { controller.abort(); generation.current += 1; };
  }, [hallId, reload, populate]);

  function requestClose() {
    if (savingRef.current) return;
    if (dirty) setConfirmDiscard("close");
    else onClose();
  }
  function requestReload() {
    if (savingRef.current || loading) return;
    if (dirty) setConfirmDiscard("reload");
    else setReload((value) => value + 1);
  }
  pendingClose.current = requestClose;
  useEffect(() => {
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    closeButton.current?.focus();
    function keydown(event: KeyboardEvent) {
      if (event.key === "Escape") { event.preventDefault(); pendingClose.current(); return; }
      if (event.key !== "Tab") return;
      const focusable = Array.from(dialog.current?.querySelectorAll<HTMLElement>('button, input, select, textarea, a[href], [tabindex="0"]') ?? []).filter((element) => !element.matches(":disabled") && element.getClientRects().length > 0);
      if (!focusable.length) { event.preventDefault(); return; }
      const first = focusable[0]; const last = focusable[focusable.length - 1];
      if (event.shiftKey && (document.activeElement === first || !dialog.current?.contains(document.activeElement))) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && (document.activeElement === last || !dialog.current?.contains(document.activeElement))) { event.preventDefault(); first.focus(); }
    }
    document.addEventListener("keydown", keydown);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener("keydown", keydown);
      // Saving may have refreshed the list and replaced its original edit button.
      const returnTarget = previousFocus?.isConnected ? previousFocus
        : document.querySelector<HTMLElement>(`[data-overture-review-hall-id="${hallId}"]`)
          ?? document.querySelector<HTMLElement>('[aria-label="Onboarding views"] [aria-selected="true"]');
      returnTarget?.focus();
    };
  }, []);

  function editField(field: OvertureFactField, value: string) {
    setForm((current) => current ? { ...current, [field]: value } : current);
    setVerified((current) => current.filter((key) => key !== field));
    if (reviewStatus === "VERIFIED") setReviewStatus("IN_REVIEW");
    if (overtureIdentityFields.includes(field)) {
      setDuplicateDecision("NOT_REVIEWED"); setReviewedIds([]); setDuplicateNotes("");
      if (reviewStatus === "DUPLICATE") setReviewStatus("IN_REVIEW");
    }
    setNotice("");
  }

  function chooseDecision(value: OvertureDuplicateDecision) {
    setDuplicateDecision(value);
    if (value === "NOT_REVIEWED") setReviewedIds([]);
    if (value === "CONFIRMED_DUPLICATE") setReviewStatus("DUPLICATE");
    else if (reviewStatus === "DUPLICATE") setReviewStatus("IN_REVIEW");
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    if (!detail || !form || savingRef.current || conflict) return;
    setError(""); setNotice("");
    let update: OvertureDraftUpdate;
    try {
      const facts = overtureFactsFromForm(form);
      const freshVerifications = verified.filter((field) => !detail.verifications[field] || (initialForm && form[field] !== initialForm[field]));
      if (freshVerifications.length && !reviewNotes.trim()) throw new Error("Add review evidence before verifying a new or edited fact.");
      if (verified.some((field) => overtureFactValue(facts, field) === null || (field === "operatingStatus" && ["unknown", ""].includes(String(overtureFactValue(facts, field)))))) throw new Error("Unknown or missing facts cannot be verified. Complete those values first.");
      if (reviewStatus === "VERIFIED") {
        const missing = overtureRequiredVerifiedFields.filter((field) => !verified.includes(field) || overtureFactValue(facts, field) === null);
        if (missing.length || facts.operatingStatus !== "open") throw new Error(`To mark facts reviewed, verify the identity, location, phone and capacity, and confirm that the venue is open.${missing.length ? ` Still needed: ${missing.map((field) => overtureFieldLabels[field]).join(", ")}.` : ""}`);
      }
      if (identityChanged && duplicateDecision !== "NOT_REVIEWED") throw new Error("Save identity/location edits first to refresh possible duplicates before recording a decision.");
      if (duplicateDecision !== "NOT_REVIEWED" && !duplicateNotes.trim()) throw new Error("Add duplicate review notes to explain your decision.");
      const matches = detail.duplicates.map((match) => match.hallId);
      if (duplicateDecision !== "NOT_REVIEWED" && (reviewedIds.length !== matches.length || matches.some((id) => !reviewedIds.includes(id)))) throw new Error("Review and acknowledge every possible matching venue before recording a duplicate decision.");
      if (duplicateDecision === "CONFIRMED_DUPLICATE" && !matches.length) throw new Error("A current matching venue is required to confirm a duplicate.");
      if (reviewStatus === "VERIFIED" && matches.length && duplicateDecision !== "DISTINCT") throw new Error("Resolve possible duplicate matches as distinct before completing factual review.");
      if ((duplicateDecision === "CONFIRMED_DUPLICATE") !== (reviewStatus === "DUPLICATE")) throw new Error("A confirmed duplicate must have the duplicate review status.");
      update = { expectedVersion: detail.reviewVersion, facts, verifiedFields: verified, reviewStatus,
        reviewNotes: reviewNotes.trim() || null, duplicateDecision, duplicateNotes: duplicateNotes.trim() || null, reviewedDuplicateHallIds: reviewedIds };
    } catch (exception) { setError(message(exception)); return; }
    savingRef.current = true; setSaving(true);
    const current = generation.current;
    try {
      const response = await updateOvertureDraft(hallId, update, token);
      if (current !== generation.current) return;
      populate(response); setNotice("Draft saved. This venue remains private and cannot accept bookings."); onSaved();
    } catch (exception) {
      if (current !== generation.current) return;
      if (exception instanceof ApiError && exception.status === 409) {
        setConflict(true); setError("The draft or possible duplicate matches changed. Your unsaved values are preserved. Reload the latest draft before saving again.");
      } else setError(message(exception));
    } finally {
      if (current === generation.current) { savingRef.current = false; setSaving(false); }
    }
  }

  function renderField(field: OvertureFactField) {
    if (!form || !detail || !initialForm) return null;
    const source = overtureFactValue(detail.sourceFacts, field);
    const changed = form[field] !== initialForm[field];
    const origin = changed ? "ADMIN" : detail.fieldOrigins[field] ?? "MISSING";
    const savedVerification = detail.verifications[field];
    const isUnknown = form[field] === "" || (field === "operatingStatus" && form[field] === "unknown");
    const id = `draft-${hallId}-${field.replaceAll(".", "-")}`;
    const helpId = `${id}-source`;
    const control = field.startsWith("amenities.") ? <select aria-describedby={helpId} className={fieldStyle} id={id} onChange={(event) => editField(field, event.target.value)} value={form[field]}><option value="">Unknown</option><option value="yes">Yes</option><option value="no">No</option></select>
      : field === "operatingStatus" ? <select aria-describedby={helpId} className={fieldStyle} id={id} onChange={(event) => editField(field, event.target.value)} value={form[field]}><option value="">Not supplied</option><option value="unknown">Unknown — needs review</option><option value="open">Open</option><option value="temporarily_closed">Temporarily closed</option><option value="permanently_closed">Permanently closed</option></select>
      : field === "description" ? <textarea aria-describedby={helpId} className={`${fieldStyle} min-h-28`} id={id} maxLength={limits[field]} onChange={(event) => editField(field, event.target.value)} value={form[field]} />
      : <input aria-describedby={helpId} className={fieldStyle} id={id} inputMode={["latitude", "longitude"].includes(field) ? "decimal" : field === "capacity" ? "numeric" : field === "phone" ? "tel" : undefined} maxLength={limits[field]} onChange={(event) => editField(field, event.target.value)} required={field === "name"} type="text" value={form[field]} />;
    return <div className={`min-w-0 rounded-lg border p-3 ${field === "description" ? "sm:col-span-2" : ""} ${verified.includes(field) ? "border-emerald-200 bg-emerald-50/20" : "border-border"}`} key={field}>
      <label className="mb-1.5 block text-sm font-medium" htmlFor={id}>{overtureFieldLabels[field]}{overtureRequiredVerifiedFields.includes(field) ? <span className="ml-1 text-xs text-muted-foreground">(core fact)</span> : null}</label>
      {control}
      {field === "phone" ? <p className="mt-1 text-xs text-muted-foreground">At least six digits; optional leading +, spaces, brackets and hyphens. Leave blank if unknown.</p> : null}
      <p className="mt-2 break-words text-xs text-muted-foreground" id={helpId}><span className="font-medium">Imported source:</span> {overtureDisplayFact(source)}</p>
      <p className="mt-1 text-xs text-muted-foreground">{isUnknown ? "Missing / unknown" : origin === "ADMIN" ? "Admin entered / edited" : origin === "SOURCE" ? `Source imported${verified.includes(field) ? "" : " · not independently verified"}` : "Not supplied by source"}</p>
      <label className="mt-3 flex items-start gap-2 text-sm"><input checked={verified.includes(field)} className="mt-0.5 h-4 w-4 shrink-0 accent-primary" disabled={isUnknown} onChange={(event) => setVerified((current) => event.target.checked ? [...current, field] : current.filter((key) => key !== field))} type="checkbox" /><span>Verify {overtureFieldLabels[field].toLowerCase()}</span></label>
      {savedVerification && !changed && verified.includes(field) ? <p className="mt-1.5 break-words text-xs text-emerald-800">Verified by {savedVerification.adminName} · {date(savedVerification.verifiedAt)}<span className="mt-1 block">Evidence: {savedVerification.evidence}</span></p> : verified.includes(field) ? <p className="mt-1.5 text-xs text-muted-foreground">Verification will be recorded with your name, save time and review evidence.</p> : null}
    </div>;
  }

  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-2 sm:p-6">
    <div aria-labelledby={`draft-review-title-${hallId}`} aria-modal="true" className="flex max-h-[96dvh] w-full max-w-5xl flex-col overflow-hidden rounded-xl bg-white shadow-xl" ref={dialog} role="dialog">
      <div className="flex shrink-0 items-start justify-between gap-3 border-b border-border p-4 sm:p-5">
        <div className="min-w-0"><h2 className="break-words text-lg font-semibold" id={`draft-review-title-${hallId}`}>Review venue draft #{hallId}</h2><p className="mt-1 text-sm text-muted-foreground">Private draft · admin-managed · no publication or booking actions</p></div>
        <button aria-label="Close draft review" className={buttonStyle} disabled={saving} onClick={requestClose} ref={closeButton} type="button"><X size={18} /></button>
      </div>
      <div className="min-h-0 overflow-y-auto overscroll-contain p-4 sm:p-5">
        <div aria-label="Draft review messages" className="outline-none" ref={messageRegion} role="region" tabIndex={-1}>
        {loading ? <p className="flex items-center gap-2 p-5 text-sm" role="status"><LoaderCircle className="animate-spin" size={18} />Loading draft and review history…</p> : null}
        {error ? <div className="mb-4 rounded-md border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800" role="alert"><p className="flex items-start gap-2"><CircleAlert className="mt-0.5 shrink-0" size={16} />{error}</p>{detail ? <button className={`${buttonStyle} mt-3`} disabled={saving || loading} onClick={requestReload} type="button">Reload latest draft</button> : <button className={`${buttonStyle} mt-3`} disabled={loading} onClick={() => setReload((value) => value + 1)} type="button">Retry loading draft</button>}</div> : null}
        {notice ? <p className="mb-4 rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800" role="status">{notice}</p> : null}
        {confirmDiscard ? <div className="mb-4 rounded-lg border border-amber-300 bg-amber-50 p-4" role="alert"><h3 className="font-semibold">Discard unsaved changes?</h3><p className="mt-1 text-sm">{confirmDiscard === "reload" ? "Reloading replaces your form with the latest saved draft. Your unsaved edits will be lost." : "Closing this review discards your unsaved edits."}</p><div className="mt-3 flex flex-wrap gap-2"><button className={buttonStyle} onClick={() => setConfirmDiscard(null)} type="button">Keep editing</button><button className={buttonStyle} onClick={() => { if (confirmDiscard === "close") onClose(); else { setConfirmDiscard(null); setReload((value) => value + 1); } }} type="button">{confirmDiscard === "reload" ? "Discard and reload" : "Discard and close"}</button></div></div> : null}
        </div>
        {detail && form && !loading ? <form className="space-y-6" id={`draft-review-form-${hallId}`} onSubmit={(event) => void save(event)}>
          <fieldset className="min-w-0 space-y-6" disabled={saving || conflict}>
            <div className="rounded-lg bg-slate-50 p-4 text-sm"><p className="font-medium">{reviewLabel[detail.reviewStatus]} · Version {detail.reviewVersion}</p><p className="mt-1">Created {date(detail.importedAt)} · Overture release {detail.release}</p><p className="mt-1 break-all text-xs text-muted-foreground">Source record: {detail.sourceId}</p><p className="mt-2">{detail.lastReviewedBy ? `Last reviewed by ${detail.lastReviewedBy.adminName} · ${date(detail.lastReviewedAt)}` : "No admin review has been recorded yet."}</p><p className="mt-2 text-xs text-muted-foreground">Saved missing facts: {detail.missingFields.length ? detail.missingFields.map((key) => overtureFieldLabels[key as OvertureFactField] ?? key).join(", ") : "None"}</p><p className="mt-1 text-xs text-muted-foreground">Saved unverified facts: {detail.unverifiedFields.length ? detail.unverifiedFields.map((key) => overtureFieldLabels[key as OvertureFactField] ?? key).join(", ") : "None"}</p></div>
            {groups.map((group) => <section className="space-y-3" key={group.title}><h3 className="font-semibold">{group.title}</h3><div className="grid gap-3 sm:grid-cols-2">{group.fields.map(renderField)}</div></section>)}
            <section className="space-y-3"><h3 className="font-semibold">Possible duplicates</h3><p className="text-sm text-muted-foreground">Names, cities and nearby coordinates are checked against existing venues. A decision here never merges, deletes or changes another venue.</p>{identityChanged ? <p className="rounded-md bg-amber-50 p-3 text-sm text-amber-900">Identity or location edits have changed the matching inputs. Save your edits first to refresh these possible matches, then record your decision.</p> : null}
              {!detail.duplicates.length ? <p className="rounded-md bg-slate-50 p-3 text-sm">No possible matches found for the saved facts.</p> : <div className="space-y-2">{detail.duplicates.map((match) => <article className="rounded-md border border-border p-3" key={match.hallId}><h4 className="break-words text-sm font-semibold">{match.name} · Venue #{match.hallId}</h4><p className="mt-1 text-xs text-muted-foreground">{[match.city, match.area].filter(Boolean).join(" / ") || "Location not supplied"} · {match.listingOrigin} · {match.status}{match.distanceMeters !== null ? ` · ${Math.round(match.distanceMeters)} m away` : ""}</p><label className="mt-2 flex items-start gap-2 text-sm"><input checked={reviewedIds.includes(match.hallId)} className="mt-0.5 h-4 w-4 shrink-0 accent-primary" disabled={identityChanged || duplicateDecision === "NOT_REVIEWED"} onChange={(event) => setReviewedIds((current) => event.target.checked ? [...current, match.hallId] : current.filter((id) => id !== match.hallId))} type="checkbox" /><span>I reviewed possible match #{match.hallId}</span></label></article>)}</div>}
              <label className="block text-sm font-medium" htmlFor={`duplicate-decision-${hallId}`}>Duplicate decision<select className={`${fieldStyle} mt-1.5`} disabled={identityChanged} id={`duplicate-decision-${hallId}`} onChange={(event) => chooseDecision(event.target.value as OvertureDuplicateDecision)} value={duplicateDecision}><option value="NOT_REVIEWED">Not reviewed</option><option value="DISTINCT">Distinct venue — possible matches reviewed</option><option disabled={!detail.duplicates.length} value="CONFIRMED_DUPLICATE">Confirmed duplicate — keep private</option></select></label>
              <label className="block text-sm font-medium" htmlFor={`duplicate-notes-${hallId}`}>Duplicate review notes<textarea className={`${fieldStyle} mt-1.5 min-h-20`} disabled={identityChanged} id={`duplicate-notes-${hallId}`} maxLength={4000} onChange={(event) => setDuplicateNotes(event.target.value)} placeholder="Explain how you compared the matching venues and reached this decision." value={duplicateNotes} /></label>
            </section>
            <section className="space-y-3"><h3 className="font-semibold">Review evidence and status</h3><p className="text-sm text-muted-foreground">Only tick facts your team has checked. New or edited verifications need evidence below; unchanged verifications retain their original reviewer and time.</p><label className="block text-sm font-medium" htmlFor={`review-notes-${hallId}`}>Review notes / evidence<textarea className={`${fieldStyle} mt-1.5 min-h-24`} id={`review-notes-${hallId}`} maxLength={4000} onChange={(event) => setReviewNotes(event.target.value)} placeholder="For example: site visit date, an authorized business document, or a contact confirmation. Do not include passwords or sensitive personal data." value={reviewNotes} /></label>
              <label className="block text-sm font-medium" htmlFor={`review-status-${hallId}`}>Internal review status<select className={`${fieldStyle} mt-1.5`} id={`review-status-${hallId}`} onChange={(event) => setReviewStatus(event.target.value as OvertureReviewStatus)} value={reviewStatus}><option value="UNREVIEWED">Not reviewed</option><option value="IN_REVIEW">Review in progress</option><option value="VERIFIED">Facts reviewed — still private</option><option disabled={duplicateDecision !== "CONFIRMED_DUPLICATE"} value="DUPLICATE">Confirmed duplicate — still private</option></select></label><p className="text-xs text-muted-foreground">Facts reviewed requires verified name, address, city, area, phone, coordinates, capacity and open operating status. It does not publish the listing or make it bookable.</p>
            </section>
            <details className="rounded-lg border border-border p-3 text-xs text-muted-foreground"><summary className="cursor-pointer font-medium">Immutable source attribution and licenses</summary><ul className="mt-3 space-y-2 break-words">{detail.sources.map((source, index) => <li key={`${source.dataset}-${index}`}>{source.dataset} · {source.license}{source.recordId ? <span className="block">Record: {source.recordId}</span> : null}</li>)}</ul><p className="mt-3">Imported source values shown above are preserved separately from admin edits.</p></details>
          </fieldset>
        </form> : null}
      </div>
      <div className="flex shrink-0 flex-wrap items-center justify-between gap-3 border-t border-border bg-white p-4 sm:p-5"><p className="text-xs text-muted-foreground">{saving ? "Saving review…" : dirty ? "Unsaved changes" : "Private draft — existing live venues are unchanged"}</p><div className="flex flex-wrap gap-2"><button className={buttonStyle} disabled={saving} onClick={requestClose} type="button">Close</button><button className="inline-flex min-h-11 items-center justify-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50" disabled={!detail || loading || saving || conflict || !dirty} form={`draft-review-form-${hallId}`} type="submit">{saving ? <LoaderCircle className="animate-spin" size={16} /> : <Save size={16} />}Save private draft</button></div></div>
    </div>
  </div>;
}
