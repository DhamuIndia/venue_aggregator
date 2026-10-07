"use client";

import { ChevronLeft, ChevronRight, CircleAlert, Compass, ExternalLink, History, ListFilter, LoaderCircle, RefreshCw, Search, ShieldCheck } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  getDiscoveryCandidates,
  getDiscoveryPreview,
  getDiscoveryRun,
  getDiscoveryRuns,
  getDiscoverySettings,
  reviewDiscoveryCandidate,
  safeDiscoveryExternalUrl,
  searchDiscoveryVenues,
  type DiscoveryCandidate,
  type DiscoveryCandidateStatus,
  type DiscoveryPage,
  type DiscoveryPlacePreview,
  type DiscoveryRun,
  type DiscoveryRunDetail,
  type DiscoverySettings,
  type ReviewableDiscoveryStatus
} from "@/features/admin/discovery-client";
import { useAuth } from "@/features/auth/AuthProvider";
import { ApiError } from "@/lib/api-client";

type DiscoveryView = "search" | "prospects" | "history";
const reviewStatuses: ReviewableDiscoveryStatus[] = ["DISCOVERED", "SHORTLISTED", "REJECTED", "CLOSED"];
const allStatuses: DiscoveryCandidateStatus[] = [...reviewStatuses, "INVITED", "CLAIMED", "DUPLICATE", "OPTED_OUT"];
const inputStyle = "mt-1.5 h-11 w-full rounded-md border border-border bg-white px-3 text-sm outline-none focus:border-primary focus:ring-1 focus:ring-primary disabled:bg-slate-50";
const buttonStyle = "inline-flex min-h-10 items-center justify-center gap-2 rounded-md border border-border bg-white px-3 py-2 text-sm font-medium hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50";

function label(value: string) {
  return value.toLowerCase().replaceAll("_", " ").replace(/^./, (letter) => letter.toUpperCase());
}

function date(value: string | null) {
  return value ? new Date(value).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" }) : "—";
}

function message(exception: unknown) {
  return exception instanceof Error ? exception.message : "This request could not be completed. Please try again.";
}

export function AdminVenueDiscovery() {
  const { accessToken } = useAuth();
  const [view, setView] = useState<DiscoveryView>("search");
  const [settings, setSettings] = useState<DiscoverySettings | null>(null);
  const [settingsError, setSettingsError] = useState("");
  const [revision, setRevision] = useState(0);
  const [city, setCity] = useState("");
  const [area, setArea] = useState("");
  const [venueType, setVenueType] = useState("");
  const [searching, setSearching] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [searchResult, setSearchResult] = useState<DiscoveryRunDetail | null>(null);
  const [previews, setPreviews] = useState<Record<string, DiscoveryPlacePreview>>({});
  const [candidatePage, setCandidatePage] = useState(0);
  const [candidateFilter, setCandidateFilter] = useState<DiscoveryCandidateStatus | "ALL">("ALL");
  const [candidates, setCandidates] = useState<DiscoveryPage<DiscoveryCandidate> | null>(null);
  const [candidateError, setCandidateError] = useState("");
  const [loadingCandidates, setLoadingCandidates] = useState(false);
  const [runPage, setRunPage] = useState(0);
  const [runs, setRuns] = useState<DiscoveryPage<DiscoveryRun> | null>(null);
  const [runError, setRunError] = useState("");
  const [loadingRuns, setLoadingRuns] = useState(false);
  const [selectedRun, setSelectedRun] = useState<DiscoveryRunDetail | null>(null);
  const [loadingRunId, setLoadingRunId] = useState<number | null>(null);
  const [busyCandidateId, setBusyCandidateId] = useState<number | null>(null);
  const [previewing, setPreviewing] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const requestGeneration = useRef(0);
  const runRequest = useRef(0);
  const actionInFlight = useRef(false);

  useEffect(() => {
    requestGeneration.current += 1;
    setSearchResult(null);
    setSelectedRun(null);
    setPreviews({});
    setSettings(null);
    setCandidates(null);
    setRuns(null);
    setSearching(false);
    setRefreshing(false);
    setBusyCandidateId(null);
    setLoadingRunId(null);
    actionInFlight.current = false;
    setNotice("");
    setError("");
    return () => { requestGeneration.current += 1; };
  }, [accessToken]);

  useEffect(() => {
    const controller = new AbortController();
    setSettingsError("");
    void getDiscoverySettings(accessToken, controller.signal).then((response) => {
      if (controller.signal.aborted) return;
      setSettings(response);
      setVenueType((current) => response.venueTypes.some((item) => item.value === current) ? current : (response.venueTypes[0]?.value ?? ""));
    }).catch((exception) => {
      if (!controller.signal.aborted) {
        setSettings(null);
        setSettingsError(message(exception));
      }
    });
    return () => controller.abort();
  }, [accessToken, revision]);

  useEffect(() => {
    if (view !== "prospects" || !settings?.enabled) return;
    const controller = new AbortController();
    setLoadingCandidates(true);
    setCandidateError("");
    void getDiscoveryCandidates(candidatePage, candidateFilter, accessToken, controller.signal).then((response) => {
      if (controller.signal.aborted) return;
      setCandidates(response);
    }).catch((exception) => {
      if (!controller.signal.aborted) {
        setCandidates(null);
        setCandidateError(message(exception));
      }
    }).finally(() => { if (!controller.signal.aborted) setLoadingCandidates(false); });
    return () => controller.abort();
  }, [accessToken, candidateFilter, candidatePage, revision, settings?.enabled, view]);

  useEffect(() => {
    if (view !== "history" || !settings?.enabled) return;
    const controller = new AbortController();
    setLoadingRuns(true);
    setRunError("");
    void getDiscoveryRuns(runPage, accessToken, controller.signal).then((response) => {
      if (controller.signal.aborted) return;
      setRuns(response);
    }).catch((exception) => {
      if (!controller.signal.aborted) {
        setRuns(null);
        setRunError(message(exception));
      }
    }).finally(() => { if (!controller.signal.aborted) setLoadingRuns(false); });
    return () => controller.abort();
  }, [accessToken, runPage, revision, settings?.enabled, view]);

  const ready = Boolean(settings?.enabled && settings.liveApiEnabled && settings.ready);
  const quotaReached = Boolean(settings && settings.requestsUsedToday >= settings.dailyRequestLimit);
  const billableReady = ready && !quotaReached;
  const busy = searching || refreshing || busyCandidateId !== null;
  const readinessMessage = !settings ? "Checking discovery configuration…"
    : !settings.enabled ? "Venue discovery is disabled. No Google API requests can be made."
    : !settings.liveApiEnabled ? "Live Google API access is disabled. Stored prospects and history remain available."
    : !settings.ready ? "Discovery is not ready. A server administrator must finish the Google Places configuration."
    : quotaReached ? "Today’s discovery request limit has been reached. Stored prospects and history remain available."
    : "Live discovery is ready. Searches and preview refreshes use your Google Places API quota.";

  function changeView(next: DiscoveryView) {
    setView(next);
    setError("");
    setNotice("");
  }

  async function refreshStoredRuns() {
    if (!settings?.enabled) return;
    const generation = requestGeneration.current;
    const ids = new Set([searchResult?.run.id, selectedRun?.run.id].filter((id): id is number => id !== undefined));
    const responses = await Promise.allSettled([...ids].map((id) => getDiscoveryRun(id, accessToken)));
    if (generation !== requestGeneration.current) return;
    for (const response of responses) {
      if (response.status === "fulfilled") {
        setSearchResult((current) => current?.run.id === response.value.run.id ? response.value : current);
        setSelectedRun((current) => current?.run.id === response.value.run.id ? response.value : current);
      } else {
        setError(message(response.reason));
      }
    }
  }

  async function refreshWorkspace() {
    if (actionInFlight.current) return;
    actionInFlight.current = true;
    const generation = requestGeneration.current;
    setRefreshing(true);
    setRevision((value) => value + 1);
    try {
      await refreshStoredRuns();
    } finally {
      if (generation === requestGeneration.current) {
        actionInFlight.current = false;
        setRefreshing(false);
      }
    }
  }

  async function search(event: FormEvent) {
    event.preventDefault();
    if (!billableReady || actionInFlight.current) return;
    actionInFlight.current = true;
    const generation = requestGeneration.current;
    setSearching(true);
    setError("");
    setNotice("");
    setSearchResult(null);
    setPreviews({});
    try {
      const response = await searchDiscoveryVenues({ city: city.trim(), area: area.trim(), venueType }, accessToken);
      if (generation !== requestGeneration.current) return;
      setSearchResult({ run: response.run, candidates: response.candidates });
      setPreviews(Object.fromEntries(response.previews.map((preview) => [preview.placeId, preview])));
      setNotice(`${response.candidates.length} prospect${response.candidates.length === 1 ? "" : "s"} recorded in search #${response.run.id}. Existing Place IDs are reused; nothing has been published.`);
    } catch (exception) {
      if (generation === requestGeneration.current) setError(message(exception));
    } finally {
      if (generation === requestGeneration.current) {
        actionInFlight.current = false;
        setSearching(false);
        setRevision((value) => value + 1);
      }
    }
  }

  async function loadPreview(candidate: DiscoveryCandidate) {
    if (!billableReady || actionInFlight.current) return;
    actionInFlight.current = true;
    const generation = requestGeneration.current;
    setBusyCandidateId(candidate.id);
    setPreviewing(true);
    setError("");
    setNotice("");
    try {
      const response = await getDiscoveryPreview(candidate.id, accessToken);
      if (generation === requestGeneration.current) setPreviews((current) => ({ ...current, [candidate.placeId]: response }));
    } catch (exception) {
      if (generation === requestGeneration.current) setError(message(exception));
    } finally {
      if (generation === requestGeneration.current) {
        actionInFlight.current = false;
        setBusyCandidateId(null);
        setPreviewing(false);
        setRevision((value) => value + 1);
      }
    }
  }

  async function review(candidate: DiscoveryCandidate, status: ReviewableDiscoveryStatus) {
    if (actionInFlight.current || !settings?.enabled) return;
    actionInFlight.current = true;
    const generation = requestGeneration.current;
    setBusyCandidateId(candidate.id);
    setPreviewing(false);
    setError("");
    setNotice("");
    try {
      const response = await reviewDiscoveryCandidate(candidate, status, accessToken);
      if (generation !== requestGeneration.current) return;
      const update = (items: DiscoveryCandidate[]) => items.map((item) => item.id === response.id ? response : item);
      setSearchResult((current) => current ? { ...current, candidates: update(current.candidates) } : null);
      setSelectedRun((current) => current ? { ...current, candidates: update(current.candidates) } : null);
      setNotice(`Prospect #${response.id} marked ${label(response.status).toLowerCase()}. No public listing or outreach was created.`);
    } catch (exception) {
      if (generation === requestGeneration.current) {
        setError(message(exception));
        // Fetch current internal statuses after a concurrent edit, never a Google preview.
        if (exception instanceof ApiError && exception.status === 409) await refreshStoredRuns();
      }
    } finally {
      if (generation === requestGeneration.current) {
        actionInFlight.current = false;
        setBusyCandidateId(null);
        setRevision((value) => value + 1);
      }
    }
  }

  async function openRun(id: number) {
    if (!settings?.enabled) return;
    const request = ++runRequest.current;
    const generation = requestGeneration.current;
    setLoadingRunId(id);
    setSelectedRun(null);
    setError("");
    try {
      const response = await getDiscoveryRun(id, accessToken);
      if (request === runRequest.current && generation === requestGeneration.current) setSelectedRun(response);
    } catch (exception) {
      if (request === runRequest.current && generation === requestGeneration.current) setError(message(exception));
    } finally {
      if (request === runRequest.current && generation === requestGeneration.current) setLoadingRunId(null);
    }
  }

  function candidateCards(items: DiscoveryCandidate[]) {
    return <div className="grid gap-4 lg:grid-cols-2">{items.map((candidate) => (
      <CandidateCard
        busy={busy}
        candidate={candidate}
        canPreview={billableReady}
        reviewEnabled={Boolean(settings?.enabled)}
        isPreviewing={busyCandidateId === candidate.id && previewing}
        isReviewing={busyCandidateId === candidate.id && !previewing}
        key={candidate.id}
        onPreview={() => void loadPreview(candidate)}
        onReview={(status) => void review(candidate, status)}
        preview={previews[candidate.placeId]}
      />
    ))}</div>;
  }

  return (
    <section aria-labelledby="discovery-title" className="py-7">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-primary"><Compass size={16} /> Venue growth · Phase 2</p>
          <h2 className="mt-2 text-2xl font-semibold" id="discovery-title">Venue discovery</h2>
          <p className="mt-2 max-w-2xl text-sm text-muted-foreground">Find potential venues, review prospects, and build an owner-verification pipeline.</p>
        </div>
        <button className={buttonStyle} disabled={busy} onClick={() => void refreshWorkspace()} type="button"><RefreshCw className={refreshing ? "animate-spin" : ""} size={16} /> Refresh workspace</button>
      </div>

      <div className="mt-5 flex items-start gap-3 rounded-lg border border-blue-100 bg-blue-50 p-4 text-sm text-blue-950">
        <ShieldCheck className="mt-0.5 shrink-0" size={19} />
        <p><strong>Internal prospects only.</strong> Searching records Place IDs and your team’s review status, not public VenueMart listings. Google names and addresses are previewed temporarily. Owner verification, photo collection, and outreach are not part of this phase.</p>
      </div>

      <div className={`mt-3 rounded-lg border p-4 text-sm ${billableReady ? "border-emerald-200 bg-emerald-50 text-emerald-900" : "border-amber-200 bg-amber-50 text-amber-900"}`}>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="flex items-start gap-2"><CircleAlert className="mt-0.5 shrink-0" size={17} /><span>{settingsError || readinessMessage}</span></p>
          {settings && <span className="shrink-0 rounded-md bg-white/70 px-3 py-1.5 text-xs font-medium">{settings.requestsUsedToday} / {settings.dailyRequestLimit} requests today (UTC)</span>}
        </div>
      </div>
      {notice && <p className="mt-4 rounded-md bg-emerald-50 p-4 text-sm text-emerald-900" role="status">{notice}</p>}
      {error && <p className="mt-4 rounded-md bg-rose-50 p-4 text-sm text-rose-800" role="alert">{error}</p>}

      <div aria-label="Discovery sections" className="my-5 flex flex-wrap gap-2">
        {([{ id: "search", name: "Discover venues", icon: Search }, { id: "prospects", name: "Saved prospects", icon: ListFilter }, { id: "history", name: "Search history", icon: History }] as const).map((item) => (
          <button aria-pressed={view === item.id} className={`${buttonStyle} ${view === item.id ? "border-primary bg-primary/5 text-primary" : "text-muted-foreground"}`} key={item.id} onClick={() => changeView(item.id)} type="button"><item.icon size={16} />{item.name}</button>
        ))}
      </div>

      {view === "search" && <>
        <form className="rounded-lg border border-border bg-white p-5" onSubmit={(event) => void search(event)}>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-[1fr_1fr_1fr_auto] lg:items-end">
            <label className="text-sm font-medium" htmlFor="discovery-city">City<input autoComplete="off" className={inputStyle} disabled={searching} id="discovery-city" maxLength={100} onChange={(event) => setCity(event.target.value)} placeholder="e.g. Chennai" required value={city} /></label>
            <label className="text-sm font-medium" htmlFor="discovery-area">Area <span className="font-normal text-muted-foreground">(optional)</span><input autoComplete="off" className={inputStyle} disabled={searching} id="discovery-area" maxLength={100} onChange={(event) => setArea(event.target.value)} placeholder="e.g. Adyar" value={area} /></label>
            <label className="text-sm font-medium" htmlFor="discovery-type">Venue type<select aria-label="Venue type" className={inputStyle} disabled={searching || !settings?.venueTypes.length} id="discovery-type" onChange={(event) => setVenueType(event.target.value)} required value={venueType}>{!settings?.venueTypes.length && <option value="">Not configured</option>}{settings?.venueTypes.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label>
            <button className="inline-flex h-11 items-center justify-center gap-2 rounded-md bg-primary px-5 text-sm font-semibold text-white hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50" disabled={!billableReady || busy || !city.trim() || !venueType} type="submit">{searching ? <LoaderCircle className="animate-spin" size={17} /> : <Search size={17} />}{searching ? "Searching…" : "Search Google Places"}</button>
          </div>
          <p className="mt-4 text-xs leading-relaxed text-muted-foreground">One search uses one API request and automatically records up to {settings?.maxResults ?? "the configured number of"} prospect Place IDs. Duplicates reuse the existing prospect. Google previews are not saved with the record.</p>
        </form>
        {searching && <Loading text="Searching Google Places and recording prospect references…" />}
        {searchResult && <div className="mt-6">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-2"><h3 className="font-semibold">Search #{searchResult.run.id} · {searchResult.run.city}{searchResult.run.area ? `, ${searchResult.run.area}` : ""}</h3><span className="text-sm text-muted-foreground">{searchResult.candidates.length} prospects · {label(searchResult.run.status)}</span></div>
          {searchResult.candidates.length ? candidateCards(searchResult.candidates) : <Empty text="No venues were returned. Try a different area or venue type." />}
        </div>}
        {!searchResult && !searching && <Empty text="Choose a city and venue type to discover prospects. Nothing is published or sent to venue owners." />}
      </>}

      {view === "prospects" && !settings?.enabled && <Empty text="Venue discovery must be enabled on the server to access saved prospects. No records have been requested." />}
      {view === "prospects" && settings?.enabled && <>
        <div className="mb-4 flex flex-wrap items-end justify-between gap-4">
          <div><h3 className="font-semibold">Saved prospects</h3><p className="mt-1 text-sm text-muted-foreground">Place IDs and internal review decisions are retained. Load a fresh preview when needed.</p></div>
          <label className="text-sm font-medium" htmlFor="discovery-status">Review status<select aria-label="Review status" className={`${inputStyle} min-w-44`} id="discovery-status" onChange={(event) => { setCandidateFilter(event.target.value as DiscoveryCandidateStatus | "ALL"); setCandidatePage(0); }} value={candidateFilter}><option value="ALL">All statuses</option>{allStatuses.map((status) => <option key={status} value={status}>{label(status)}</option>)}</select></label>
        </div>
        {candidateError && <p className="mb-4 rounded-md bg-rose-50 p-4 text-sm text-rose-800" role="alert">{candidateError}</p>}
        {loadingCandidates ? <Loading text="Loading stored prospects…" /> : candidates?.content.length ? candidateCards(candidates.content) : !candidateError && <Empty text="No prospects match this filter yet." />}
        {candidates && <Pagination busy={loadingCandidates} data={candidates} onPage={setCandidatePage} />}
      </>}

      {view === "history" && !settings?.enabled && <Empty text="Venue discovery must be enabled on the server to access search history. No records have been requested." />}
      {view === "history" && settings?.enabled && <>
        <h3 className="font-semibold">Search history</h3><p className="mb-4 mt-1 text-sm text-muted-foreground">Open a run to see its recorded prospect references. This does not call Google.</p>
        {runError && <p className="mb-4 rounded-md bg-rose-50 p-4 text-sm text-rose-800" role="alert">{runError}</p>}
        {loadingRuns ? <Loading text="Loading search history…" /> : runs?.content.length ? <div className="divide-y divide-border overflow-hidden rounded-lg border border-border bg-white">{runs.content.map((run) => <button aria-expanded={selectedRun?.run.id === run.id} className="flex w-full flex-wrap items-center justify-between gap-3 p-4 text-left hover:bg-slate-50" key={run.id} onClick={() => void openRun(run.id)} type="button"><span><span className="text-sm font-semibold">#{run.id} · {run.city}{run.area ? `, ${run.area}` : ""}</span><span className="mt-1 block text-xs text-muted-foreground">{settings?.venueTypes.find((item) => item.value === run.venueType)?.label ?? label(run.venueType)} · {date(run.createdAt)}</span></span><span className="flex items-center gap-3"><span className="text-xs text-muted-foreground">{run.resultCount} results</span><Status value={run.status} />{loadingRunId === run.id ? <LoaderCircle className="animate-spin" size={16} /> : <ChevronRight size={16} />}</span></button>)}</div> : !runError && <Empty text="No searches have been recorded yet." />}
        {runs && <Pagination busy={loadingRuns} data={runs} onPage={setRunPage} />}
        {selectedRun && <div className="mt-6 border-t border-border pt-6">
          <div className="mb-4 flex items-center justify-between gap-3"><h3 className="font-semibold">Prospects from search #{selectedRun.run.id}</h3><button className={buttonStyle} onClick={() => setSelectedRun(null)} type="button">Close details</button></div>
          {selectedRun.run.failureReason && <p className="mb-4 rounded-md bg-rose-50 p-4 text-sm text-rose-800">{selectedRun.run.failureReason}</p>}
          {selectedRun.candidates.length ? candidateCards(selectedRun.candidates) : <Empty text="This run has no recorded prospects." />}
        </div>}
      </>}
    </section>
  );
}

function CandidateCard({ candidate, preview, canPreview, reviewEnabled, busy, isPreviewing, isReviewing, onPreview, onReview }: {
  candidate: DiscoveryCandidate;
  preview?: DiscoveryPlacePreview;
  canPreview: boolean;
  reviewEnabled: boolean;
  busy: boolean;
  isPreviewing: boolean;
  isReviewing: boolean;
  onPreview: () => void;
  onReview: (status: ReviewableDiscoveryStatus) => void;
}) {
  const canReview = reviewEnabled && reviewStatuses.includes(candidate.status as ReviewableDiscoveryStatus) && candidate.linkedHallId === null;
  const mapsUrl = safeDiscoveryExternalUrl(preview?.googleMapsUri);
  return <article className="flex flex-col overflow-hidden rounded-lg border border-border bg-white">
    <div className="flex items-center justify-between gap-3 border-b border-border px-4 py-3"><h4 className="text-sm font-semibold">Prospect #{candidate.id}</h4><Status value={candidate.status} /></div>
    {preview ? <div className="m-4 rounded-md border border-slate-200 bg-slate-50 p-4">
      <p className="text-xs text-slate-500">Live place preview</p>
      <h5 className="mt-1 break-words text-lg font-semibold">{preview.displayName || "Unnamed place"}</h5>
      <p className="mt-2 text-sm text-slate-700">{preview.formattedAddress || "Address unavailable"}</p>
      {preview.businessStatus && <p className="mt-2 text-xs text-slate-600">Business status: {label(preview.businessStatus)}</p>}
      {mapsUrl && <a className="mt-3 inline-flex items-center gap-1.5 text-sm font-medium text-primary underline underline-offset-2" href={mapsUrl} rel="noopener noreferrer" target="_blank">Open in Google Maps <ExternalLink aria-hidden="true" size={14} /></a>}
      <div className="mt-4 border-t border-slate-200 pt-3 text-xs leading-5 text-slate-600"><span className="font-sans text-sm font-normal not-italic" translate="no">Google Maps</span>{preview.attributions?.map((attribution, index) => { const uri = safeDiscoveryExternalUrl(attribution.uri); return <span className="ml-2 inline-block" key={`${attribution.displayName}-${index}`}>{uri ? <a className="underline underline-offset-2" href={uri} rel="noopener noreferrer" target="_blank">{attribution.displayName}</a> : attribution.displayName}</span>; })}</div>
    </div> : <div className="m-4 rounded-md border border-dashed border-border bg-slate-50 p-4 text-sm text-muted-foreground"><p>Google place details are not stored.</p><p className="mt-1">Load a fresh preview to see this venue’s name and address.</p></div>}
    <div className="flex flex-1 flex-col px-4 pb-4">
      <dl className="space-y-2 text-xs text-muted-foreground"><div><dt className="font-medium text-slate-700">Google Place ID</dt><dd className="mt-1 break-all font-mono">{candidate.placeId}</dd></div><div><dt className="inline">First discovered: </dt><dd className="inline">{date(candidate.discoveredAt)}</dd></div><div><dt className="inline">Source checked: </dt><dd className="inline">{date(candidate.sourceCheckedAt)}</dd></div>{candidate.linkedHallId !== null && <div><dt className="inline">Linked VenueMart hall: </dt><dd className="inline">#{candidate.linkedHallId}</dd></div>}</dl>
      <div className="mt-4 flex flex-wrap items-center gap-2">
        <button className={buttonStyle} disabled={!canPreview || busy} onClick={onPreview} type="button">{isPreviewing ? <LoaderCircle className="animate-spin" size={15} /> : <RefreshCw size={15} />}{preview ? "Refresh preview" : "Load preview"}<span className="text-xs font-normal text-muted-foreground">· 1 API request</span></button>
        {canReview && <label className="flex items-center gap-2 text-xs text-muted-foreground">{isReviewing ? <LoaderCircle aria-label="Updating review status" className="animate-spin" size={15} /> : "Review"}<select aria-label={`Review status for prospect ${candidate.id}`} className="h-10 rounded-md border border-border bg-white px-2 text-sm text-foreground disabled:opacity-50" disabled={busy} onChange={(event) => onReview(event.target.value as ReviewableDiscoveryStatus)} value={candidate.status}>{reviewStatuses.map((status) => <option key={status} value={status}>{label(status)}</option>)}</select></label>}
      </div>
      {!canReview && <p className="mt-3 text-xs text-muted-foreground">This prospect is outside the discovery review stage and cannot be changed here.</p>}
    </div>
  </article>;
}

function Status({ value }: { value: string }) {
  const tone = ["SHORTLISTED", "COMPLETED"].includes(value) ? "bg-emerald-50 text-emerald-800"
    : ["REJECTED", "FAILED", "OPTED_OUT"].includes(value) ? "bg-rose-50 text-rose-800"
    : ["DISCOVERED", "RUNNING", "CREATED"].includes(value) ? "bg-blue-50 text-blue-800" : "bg-slate-100 text-slate-700";
  return <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ${tone}`}>{label(value)}</span>;
}

function Empty({ text }: { text: string }) {
  return <p className="mt-5 rounded-lg border border-dashed border-border bg-white px-6 py-10 text-center text-sm text-muted-foreground">{text}</p>;
}

function Loading({ text }: { text: string }) {
  return <p aria-live="polite" className="flex min-h-36 items-center justify-center gap-2 text-sm text-muted-foreground"><LoaderCircle className="animate-spin" size={19} />{text}</p>;
}

function Pagination({ data, busy, onPage }: { data: DiscoveryPage<unknown>; busy: boolean; onPage: (page: number) => void }) {
  return <div className="mt-4 flex flex-wrap items-center justify-between gap-3 text-sm text-muted-foreground"><span>{data.totalElements} records · Page {data.totalPages === 0 ? 0 : data.page + 1} of {data.totalPages}</span><div className="flex gap-2"><button aria-label="Previous page" className={buttonStyle} disabled={busy || data.page === 0} onClick={() => onPage(data.page - 1)} type="button"><ChevronLeft size={16} />Previous</button><button aria-label="Next page" className={buttonStyle} disabled={busy || data.page + 1 >= data.totalPages} onClick={() => onPage(data.page + 1)} type="button">Next<ChevronRight size={16} /></button></div></div>;
}
