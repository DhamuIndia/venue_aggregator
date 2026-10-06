"use client";

import { Building2, ChevronLeft, ChevronRight, CircleAlert, Database, ExternalLink, FileCheck2, ImagePlus, LoaderCircle, RefreshCw, Search } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import {
  getOvertureCatalog, getOvertureDrafts, getOvertureSettings, importOvertureVenues,
  previewOvertureVenues, readyOvertureIds, safeOvertureWebsite, overtureWebsiteText,
  type OvertureCatalog, type OvertureDraft, type OvertureImport, type OverturePage,
  type OverturePreview, type OvertureSettings, type OvertureSource, type OvertureVenue
} from "@/features/admin/overture-client";
import { ApiError } from "@/lib/api-client";
import { AdminOvertureDraftReview } from "@/components/admin/AdminOvertureDraftReview";
import { AdminOvertureDraftMedia } from "@/components/admin/AdminOvertureDraftMedia";

const buttonStyle = "inline-flex min-h-10 items-center justify-center gap-2 rounded-md border border-border bg-white px-3 py-2 text-sm font-medium hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50";
const primaryStyle = "inline-flex min-h-10 items-center justify-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50";
const fieldStyle = "min-h-11 w-full rounded-md border border-border bg-white px-3 text-sm outline-none focus:border-primary focus:ring-1 focus:ring-primary disabled:bg-slate-50";
const readable = (value: string) => value.replace(/([a-z])([A-Z])/g, "$1 $2").toLowerCase().replaceAll("_", " ").replace(/^./, (letter) => letter.toUpperCase());
const date = (value: string | null) => value ? new Date(value).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" }) : "Not provided";
const message = (exception: unknown) => exception instanceof Error ? exception.message : "This request could not be completed. Please try again.";

function categoryLabel(value: string) {
  if (value === "event_venue") return "Event venue";
  if (value === "exhibition_and_trade_fair_venue") return "Exhibition and trade fair venue";
  return readable(value);
}

function SourceAttribution({ sources }: { sources: OvertureSource[] }) {
  return <details className="mt-3 text-xs text-muted-foreground">
    <summary className="cursor-pointer">Source: Overture Maps · {sources.length} source record{sources.length === 1 ? "" : "s"}</summary>
    <ul className="mt-2 space-y-1.5 break-words">
      {sources.map((source, index) => <li key={`${source.dataset}-${source.recordId}-${index}`}>
        {source.dataset} · License: {source.license}{source.recordId ? <span className="block">Record: {source.recordId}</span> : null}
      </li>)}
    </ul>
    <a className="mt-2 inline-flex items-center gap-1 text-primary underline" href="https://docs.overturemaps.org/attribution/" rel="noopener noreferrer" target="_blank">Overture attribution <ExternalLink size={12} /></a>
  </details>;
}

function VenueFacts({ venue }: { venue: OvertureVenue }) {
  const website = safeOvertureWebsite(venue.website);
  const websiteText = overtureWebsiteText(venue.website);
  return <>
    <p className="mt-1 text-sm text-muted-foreground">{categoryLabel(venue.category)}</p>
    <dl className="mt-3 grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
      <div><dt className="text-xs text-muted-foreground">Address</dt><dd className="break-words">{venue.address || "Not provided"}</dd></div>
      <div><dt className="text-xs text-muted-foreground">City / area</dt><dd>{[venue.city, venue.area].filter(Boolean).join(" / ") || "Not provided"}</dd></div>
      <div><dt className="text-xs text-muted-foreground">Phone</dt><dd>{venue.phone || "Not provided"}</dd></div>
      <div><dt className="text-xs text-muted-foreground">Website</dt><dd>{website ? <a className="inline-flex max-w-full items-center gap-1 break-all text-primary underline" href={website} rel="noopener noreferrer" target="_blank">Visit venue website <ExternalLink className="shrink-0" size={13} /></a> : websiteText ? <span className="break-all">{websiteText}</span> : "Not provided"}</dd></div>
      <div><dt className="text-xs text-muted-foreground">Source operating status</dt><dd>{venue.operatingStatus ? readable(venue.operatingStatus) : "Not provided"}</dd></div>
      <div><dt className="text-xs text-muted-foreground">Source confidence</dt><dd>{venue.confidence === null ? "Not provided" : new Intl.NumberFormat("en-IN", { style: "percent", maximumFractionDigits: 0 }).format(venue.confidence)}</dd></div>
    </dl>
    <SourceAttribution sources={venue.sources} />
  </>;
}

function Pagination({ page, totalPages, totalElements, busy, onChange }: {
  page: number; totalPages: number; totalElements: number; busy: boolean; onChange: (page: number) => void;
}) {
  return <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-4">
    <p className="text-sm text-muted-foreground">{totalElements} venue{totalElements === 1 ? "" : "s"}{totalPages > 0 ? ` · Page ${page + 1} of ${totalPages}` : ""}</p>
    <div className="flex gap-2">
      <button className={buttonStyle} disabled={busy || page <= 0} onClick={() => onChange(page - 1)} type="button"><ChevronLeft size={16} /> Previous</button>
      <button className={buttonStyle} disabled={busy || page + 1 >= totalPages} onClick={() => onChange(page + 1)} type="button">Next <ChevronRight size={16} /></button>
    </div>
  </div>;
}

export function AdminOvertureOnboarding() {
  const { accessToken, user } = useAuth();
  const [view, setView] = useState<"catalog" | "drafts">("catalog");
  const [settings, setSettings] = useState<OvertureSettings | null>(null);
  const [settingsError, setSettingsError] = useState("");
  const [revision, setRevision] = useState(0);
  const [queryInput, setQueryInput] = useState("");
  const [query, setQuery] = useState("");
  const [catalogPage, setCatalogPage] = useState(0);
  const [catalog, setCatalog] = useState<OvertureCatalog | null>(null);
  const [catalogLoading, setCatalogLoading] = useState(false);
  const [catalogError, setCatalogError] = useState("");
  const [selected, setSelected] = useState<string[]>([]);
  const [preview, setPreview] = useState<OverturePreview | null>(null);
  const [result, setResult] = useState<OvertureImport | null>(null);
  const [draftPage, setDraftPage] = useState(0);
  const [drafts, setDrafts] = useState<OverturePage<OvertureDraft> | null>(null);
  const [draftLoading, setDraftLoading] = useState(false);
  const [draftError, setDraftError] = useState("");
  const [action, setAction] = useState<"preview" | "import" | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [editingHallId, setEditingHallId] = useState<number | null>(null);
  const [editingMediaHallId, setEditingMediaHallId] = useState<number | null>(null);
  const generation = useRef(0);
  const actionInFlight = useRef(false);

  useEffect(() => {
    generation.current += 1;
    setSettings(null);
    setCatalog(null);
    setDrafts(null);
    setSelected([]);
    setPreview(null);
    setResult(null);
    setAction(null);
    setError("");
    setNotice("");
    setEditingHallId(null);
    setEditingMediaHallId(null);
    actionInFlight.current = false;
    return () => { generation.current += 1; };
  }, [user?.id, user?.role]);

  useEffect(() => {
    const controller = new AbortController();
    setSettingsError("");
    void getOvertureSettings(accessToken, controller.signal).then((response) => {
      if (!controller.signal.aborted) setSettings(response);
    }).catch((exception) => {
      if (!controller.signal.aborted) {
        setSettings(null);
        setSettingsError(message(exception));
      }
    });
    return () => controller.abort();
  }, [accessToken, revision]);

  const ready = Boolean(settings?.enabled && settings.ready);
  useEffect(() => {
    if (view !== "catalog" || !ready) return;
    const controller = new AbortController();
    setCatalogLoading(true);
    setCatalogError("");
    setCatalog(null);
    void getOvertureCatalog(catalogPage, query, accessToken, controller.signal).then((response) => {
      if (!controller.signal.aborted) setCatalog(response);
    }).catch((exception) => {
      if (!controller.signal.aborted) setCatalogError(message(exception));
    }).finally(() => { if (!controller.signal.aborted) setCatalogLoading(false); });
    return () => controller.abort();
  }, [accessToken, catalogPage, query, ready, revision, view]);

  useEffect(() => {
    if (view !== "drafts" || !settings?.enabled) return;
    const controller = new AbortController();
    setDraftLoading(true);
    setDraftError("");
    setDrafts(null);
    void getOvertureDrafts(draftPage, accessToken, controller.signal).then((response) => {
      if (!controller.signal.aborted) setDrafts(response);
    }).catch((exception) => {
      if (!controller.signal.aborted) setDraftError(message(exception));
    }).finally(() => { if (!controller.signal.aborted) setDraftLoading(false); });
    return () => controller.abort();
  }, [accessToken, draftPage, revision, settings?.enabled, view]);

  const busy = action !== null;
  const maxBatchSize = Math.max(1, Math.min(20, settings?.maxBatchSize ?? 20));
  const readyIds = readyOvertureIds(preview, selected);
  const readiness = settingsError ? "The onboarding configuration could not be checked."
    : !settings ? "Checking onboarding configuration…"
    : !settings.enabled ? "Application onboarding is disabled."
    : !settings.ready ? "No prepared venue catalog is available. Existing created drafts remain available for review; a server administrator must load a catalog for new imports."
    : `The ${settings.city || "prepared"} catalog is ready with ${settings.recordCount} venue${settings.recordCount === 1 ? "" : "s"}.`;

  function clearSelection() {
    setSelected([]);
    setPreview(null);
    setError("");
  }

  function refreshWorkspace(catalogChanged = false) {
    if (actionInFlight.current && !catalogChanged) return;
    clearSelection();
    setCatalog(null);
    setSettings(null);
    setRevision((value) => value + 1);
    if (catalogChanged) setError("The venue catalog changed. Review the refreshed catalog and preview your selection again.");
    else { setResult(null); setNotice(""); }
  }

  function changeView(next: "catalog" | "drafts") {
    if (busy) return;
    clearSelection();
    setView(next);
  }

  function search(event: FormEvent) {
    event.preventDefault();
    if (busy || !ready) return;
    clearSelection();
    setResult(null);
    setNotice("");
    setCatalogPage(0);
    const trimmed = queryInput.trim();
    if (query === trimmed && catalogPage === 0) setRevision((value) => value + 1);
    setQuery(trimmed);
  }

  function toggleSelection(id: string) {
    if (busy) return;
    setPreview(null);
    setResult(null);
    setNotice("");
    setError("");
    setSelected((current) => current.includes(id) ? current.filter((value) => value !== id) : current.length < maxBatchSize ? [...current, id] : current);
  }

  async function previewSelection() {
    if (actionInFlight.current || !ready || !catalog || !selected.length) return;
    actionInFlight.current = true;
    const current = generation.current;
    setAction("preview");
    setError("");
    setNotice("");
    setResult(null);
    setPreview(null);
    try {
      const response = await previewOvertureVenues(selected, accessToken);
      if (current !== generation.current) return;
      if (response.catalogVersion !== catalog.catalogVersion) { refreshWorkspace(true); return; }
      setPreview(response);
    } catch (exception) {
      if (current === generation.current) {
        if (exception instanceof ApiError && exception.status === 409) refreshWorkspace(true);
        else setError(message(exception));
      }
    } finally {
      if (current === generation.current) { actionInFlight.current = false; setAction(null); }
    }
  }

  async function createDrafts() {
    if (actionInFlight.current || !ready || !preview || !readyIds.length) return;
    actionInFlight.current = true;
    const current = generation.current;
    setAction("import");
    setError("");
    setNotice("");
    try {
      const response = await importOvertureVenues(preview.catalogVersion, readyIds, accessToken);
      if (current !== generation.current) return;
      setResult(response);
      setSelected([]);
      setPreview(null);
      setNotice(`${response.createdCount} draft${response.createdCount === 1 ? "" : "s"} created${response.skippedCount ? `; ${response.skippedCount} skipped` : ""}. Open Created drafts to review the venue information.`);
      setRevision((value) => value + 1);
    } catch (exception) {
      if (current === generation.current) {
        if (exception instanceof ApiError && exception.status === 409) refreshWorkspace(true);
        else setError(message(exception));
      }
    } finally {
      if (current === generation.current) { actionInFlight.current = false; setAction(null); }
    }
  }

  return <section className="space-y-5 py-7" aria-label="Application onboarding">
    <div className="flex flex-wrap items-start justify-between gap-4">
      <div><h2 className="flex items-center gap-2 text-xl font-semibold"><Building2 className="text-primary" size={22} /> Application onboarding</h2><p className="mt-1 max-w-3xl text-sm text-muted-foreground">Create venue drafts from a prepared Overture Places catalog. Your team can review each draft before completing the listing.</p></div>
      <button className={buttonStyle} disabled={busy} onClick={() => refreshWorkspace()} type="button"><RefreshCw size={15} /> Refresh</button>
    </div>
    <div className={`rounded-lg border p-4 ${ready ? "border-emerald-200 bg-emerald-50" : "border-amber-200 bg-amber-50"}`}>
      <p className="flex items-start gap-2 text-sm"><Database className="mt-0.5 shrink-0" size={16} />{readiness}</p>
      {settings?.release ? <p className="mt-2 text-xs text-muted-foreground">Overture release: {settings.release} · Catalog prepared: {date(settings.generatedAt)}</p> : null}
      <p className="mt-2 text-xs text-muted-foreground">Source: <a className="text-primary underline" href="https://docs.overturemaps.org/guides/places/" rel="noopener noreferrer" target="_blank">Overture Maps Places</a>. Source details and licenses are shown with each venue.</p>
    </div>
    {settingsError ? <ErrorNotice text={settingsError} /> : null}
    {error ? <ErrorNotice text={error} /> : null}
    {notice ? <p className="rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800" role="status">{notice}</p> : null}
    <div aria-label="Onboarding views" className="flex flex-wrap gap-2" role="tablist">
      <button aria-selected={view === "catalog"} className={`${buttonStyle} ${view === "catalog" ? "border-primary text-primary" : ""}`} disabled={busy} onClick={() => changeView("catalog")} role="tab" type="button"><Database size={16} /> Venue catalog</button>
      <button aria-selected={view === "drafts"} className={`${buttonStyle} ${view === "drafts" ? "border-primary text-primary" : ""}`} disabled={busy} onClick={() => changeView("drafts")} role="tab" type="button"><FileCheck2 size={16} /> Created drafts</button>
    </div>

    {view === "catalog" && ready ? <div className="space-y-4">
      <form className="flex flex-col gap-2 sm:flex-row sm:items-end" onSubmit={search}>
        <label className="flex-1 text-sm font-medium" htmlFor="overture-query">Find in this catalog<input className={`${fieldStyle} mt-1.5`} disabled={busy} id="overture-query" maxLength={120} onChange={(event) => setQueryInput(event.target.value)} placeholder="Venue name, city or area" type="search" value={queryInput} /></label>
        <button className={buttonStyle} disabled={busy} type="submit"><Search size={16} /> Find venues</button>
      </form>
      <p className="text-xs text-muted-foreground">Select up to {maxBatchSize} venues on this page. Changing pages or searching clears the selection.</p>
      {catalogLoading ? <LoadingNotice text="Loading venue catalog…" /> : null}
      {catalogError ? <ErrorNotice text={catalogError} /> : null}
      {catalog ? <>
        {!catalog.content.length ? <EmptyNotice text="No venues match this catalog search. Try another venue name or area." /> : <>
          <div className="flex flex-wrap items-center justify-between gap-2 rounded-md bg-slate-50 p-3">
            <p className="text-sm font-medium">{selected.length} selected</p>
            <div className="flex flex-wrap gap-2">
              <button className={buttonStyle} disabled={busy} onClick={() => { setSelected(catalog.content.slice(0, maxBatchSize).map((venue) => venue.id)); setPreview(null); setResult(null); setError(""); setNotice(""); }} type="button">Select {Math.min(catalog.content.length, maxBatchSize)} on this page</button>
              <button className={buttonStyle} disabled={busy || !selected.length} onClick={clearSelection} type="button">Clear selection</button>
            </div>
          </div>
          <div className="grid gap-3 lg:grid-cols-2">
            {catalog.content.map((venue) => <article className={`min-w-0 rounded-lg border p-4 ${selected.includes(venue.id) ? "border-primary bg-blue-50/30" : "border-border bg-white"}`} key={venue.id}>
              <label className="flex cursor-pointer items-start gap-3">
                <input aria-label={`Select ${venue.name}`} checked={selected.includes(venue.id)} className="mt-1 h-4 w-4 shrink-0 accent-primary" disabled={busy || (!selected.includes(venue.id) && selected.length >= maxBatchSize)} onChange={() => toggleSelection(venue.id)} type="checkbox" />
                <span className="min-w-0 break-words font-semibold">{venue.name}</span>
              </label>
              <VenueFacts venue={venue} />
            </article>)}
          </div>
        </>}
        <Pagination busy={busy || catalogLoading} onChange={(page) => { clearSelection(); setResult(null); setNotice(""); setCatalogPage(page); }} page={catalog.page} totalElements={catalog.totalElements} totalPages={catalog.totalPages} />
      </> : null}
      <div className="flex flex-wrap items-center gap-3 rounded-lg border border-border p-4">
        <button className={primaryStyle} disabled={busy || !catalog || !selected.length} onClick={() => void previewSelection()} type="button">{action === "preview" ? <LoaderCircle className="animate-spin" size={16} /> : <FileCheck2 size={16} />} Preview selection</button>
        <p className="text-sm text-muted-foreground">Preview checks for existing imports, possible duplicates and source eligibility.</p>
      </div>
      {preview ? <div className="space-y-3 rounded-lg border border-border bg-white p-4">
        <h3 className="font-semibold">Review selected venues</h3>
        <p className="text-sm text-muted-foreground">{readyIds.length} ready to create. Every created venue starts as a draft. Photos, capacity and prices need to be supplied during review.</p>
        {preview.items.map((item) => <article className="rounded-md border border-border p-3" key={item.venue.id}>
          <div className="flex flex-wrap items-center justify-between gap-2"><h4 className="break-words text-sm font-semibold">{item.venue.name}</h4><span className={`rounded-full px-2 py-1 text-xs ${item.outcome === "READY" ? "bg-emerald-50 text-emerald-800" : "bg-amber-50 text-amber-800"}`}>{item.outcome === "READY" ? "Ready for draft" : readable(item.outcome)}</span></div>
          {item.hallId ? <p className="mt-2 text-sm text-muted-foreground">Existing venue #{item.hallId}</p> : null}
          {item.duplicateHallIds.length ? <p className="mt-2 text-sm text-muted-foreground">Possible matching venues: {item.duplicateHallIds.map((id) => `#${id}`).join(", ")}. Review these before adding another venue.</p> : null}
          {item.issues.length ? <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-muted-foreground">{item.issues.map((issue, index) => <li key={index}>{issue}</li>)}</ul> : null}
          <SourceAttribution sources={item.venue.sources} />
        </article>)}
        <button className={primaryStyle} disabled={busy || !readyIds.length} onClick={() => void createDrafts()} type="button">{action === "import" ? <LoaderCircle className="animate-spin" size={16} /> : <Building2 size={16} />} Create {readyIds.length} draft{readyIds.length === 1 ? "" : "s"}</button>
      </div> : null}
    </div> : null}

    {result ? <div className="rounded-lg border border-border bg-white p-4">
      <h3 className="font-semibold">Import result</h3>
      <ul className="mt-3 space-y-3 text-sm">{result.items.map((item) => <li key={item.sourceId}><p className="break-words"><span className="font-medium">{item.name}</span> · {readable(item.outcome)}{item.hallId ? ` · Venue #${item.hallId}` : ""}</p>{item.issues.length ? <p className="mt-1 text-muted-foreground">{item.issues.join(" · ")}</p> : null}</li>)}</ul>
    </div> : null}

    {view === "drafts" && settings?.enabled ? <div className="space-y-4">
      {draftLoading ? <LoadingNotice text="Loading created drafts…" /> : null}
      {draftError ? <ErrorNotice text={draftError} /> : null}
      {drafts ? <>
        {!drafts.content.length ? <EmptyNotice text="No Overture venue drafts have been created yet. Preview venues in the catalog to create your first drafts." /> : <div className="grid gap-3 lg:grid-cols-2">{drafts.content.map((draft) => <article className="min-w-0 rounded-lg border border-border bg-white p-4" key={draft.hallId}>
          <div className="flex items-start gap-3"><div aria-label="Private draft photo placeholder" className="grid h-14 w-14 shrink-0 place-items-center rounded-md bg-slate-100 text-muted-foreground"><ImagePlus size={21} /></div><div className="min-w-0"><h3 className="break-words font-semibold">{draft.name}</h3><p className="mt-1 text-sm text-muted-foreground">Venue #{draft.hallId} · {categoryLabel(draft.category)}</p><span className="mt-2 inline-block rounded-full bg-slate-100 px-2 py-1 text-xs font-medium">Draft</span></div></div>
          <p className="mt-3 break-words text-sm">{draft.address || "Address not provided"}</p><p className="mt-1 text-sm text-muted-foreground">{[draft.city, draft.area].filter(Boolean).join(" / ") || "City and area not provided"}</p>
          <p className="mt-3 text-xs text-muted-foreground">Created: {date(draft.importedAt)} · Overture release: {draft.release}</p>
          <div className="mt-3 rounded-md bg-amber-50 p-3"><p className="text-xs font-medium text-amber-900">Needs completion</p><p className="mt-1 text-sm text-amber-900">{draft.missingFields.length ? draft.missingFields.map(readable).join(", ") : "Review source information before completing the listing."}</p></div>
          <p className="mt-3 text-xs font-medium text-muted-foreground">Last saved review: {draft.reviewStatus === "VERIFIED" ? "Facts reviewed — still private" : draft.reviewStatus === "DUPLICATE" ? "Confirmed duplicate — still private" : draft.reviewStatus === "IN_REVIEW" ? "In progress" : "Not reviewed"}</p>
          <button className={`${buttonStyle} mt-3`} data-overture-review-hall-id={draft.hallId} onClick={() => setEditingHallId(draft.hallId)} type="button"><FileCheck2 size={16} /> Edit and review draft</button>
          <button className={`${buttonStyle} ml-0 mt-3 sm:ml-2`} data-overture-media-hall-id={draft.hallId} onClick={() => setEditingMediaHallId(draft.hallId)} type="button"><ImagePlus size={16} /> Review private photos</button>
          <SourceAttribution sources={draft.sources} />
        </article>)}</div>}
        <Pagination busy={busy || draftLoading} onChange={setDraftPage} page={drafts.page} totalElements={drafts.totalElements} totalPages={drafts.totalPages} />
      </> : null}
    </div> : null}
    {editingHallId !== null && settings?.enabled ? <AdminOvertureDraftReview hallId={editingHallId} key={`${user?.id}-${editingHallId}`} onClose={() => setEditingHallId(null)} onSaved={() => setRevision((value) => value + 1)} token={accessToken} /> : null}
    {editingMediaHallId !== null && settings?.enabled ? <AdminOvertureDraftMedia hallId={editingMediaHallId} key={`media-${user?.id}-${editingMediaHallId}`} onClose={() => setEditingMediaHallId(null)} token={accessToken} /> : null}
  </section>;
}

function ErrorNotice({ text }: { text: string }) {
  return <p className="flex items-start gap-2 rounded-md border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800" role="alert"><CircleAlert className="mt-0.5 shrink-0" size={16} />{text}</p>;
}
function LoadingNotice({ text }: { text: string }) {
  return <p className="flex items-center gap-2 p-4 text-sm text-muted-foreground" role="status"><LoaderCircle className="animate-spin" size={17} />{text}</p>;
}
function EmptyNotice({ text }: { text: string }) {
  return <p className="rounded-lg border border-dashed border-border p-6 text-sm text-muted-foreground">{text}</p>;
}
