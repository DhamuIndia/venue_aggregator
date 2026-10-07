"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import { ApiError } from "@/lib/api-client";
import { getPublication, getPublicationList, publishApplicationVenue, unpublishApplicationVenue, type PublicationDetail, type PublicationSummary } from "@/features/admin/overture-publication-client";
import type { OverturePage } from "@/features/admin/overture-client";

const button = "min-h-10 rounded-md border border-border px-3 py-2 text-sm font-medium disabled:opacity-50";
const errorText = (error: unknown) => error instanceof Error ? error.message : "The publication request failed.";
export function AdminOverturePublications() {
  const { accessToken, user } = useAuth();
  const token = useRef(accessToken); token.current = accessToken;
  const [page, setPage] = useState(0); const [revision, setRevision] = useState(0);
  const [list, setList] = useState<OverturePage<PublicationSummary> | null>(null);
  const [selected, setSelected] = useState<number | null>(null); const [detail, setDetail] = useState<PublicationDetail | null>(null);
  const [reason, setReason] = useState(""); const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(false); const [loading, setLoading] = useState(false); const [error, setError] = useState(""); const [conflict, setConflict] = useState(false);
  const generation = useRef(0); const inFlight = useRef(false);
  useEffect(() => { generation.current++; setList(null); setDetail(null); setSelected(null); setReason(""); setConfirmed(false); setError(""); setConflict(false); setBusy(false); inFlight.current = false; return () => { generation.current++; }; }, [user?.id, user?.role]);
  useEffect(() => {
    const abort = new AbortController(); setLoading(true); setError(""); setList(null);
    void getPublicationList(page, accessToken, abort.signal).then((response) => { if (!abort.signal.aborted) setList(response); }).catch((e) => { if (!abort.signal.aborted) setError(errorText(e)); }).finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [page, revision, accessToken]);
  useEffect(() => {
    if (selected === null) return;
    const abort = new AbortController(); setDetail(null); setError(""); setConflict(false); setReason(""); setConfirmed(false);
    void getPublication(selected, token.current, abort.signal).then((response) => { if (!abort.signal.aborted) setDetail(response); }).catch((e) => { if (!abort.signal.aborted) setError(errorText(e)); });
    return () => abort.abort();
  }, [selected, revision]);
  async function mutate() {
    if (!detail || inFlight.current || conflict || !confirmed || reason.trim().length < 10) return;
    inFlight.current = true; setBusy(true); setError(""); const current = generation.current;
    try {
      const updated = detail.publicationState === "PUBLISHED" ? await unpublishApplicationVenue(detail, reason, token.current) : await publishApplicationVenue(detail, reason, token.current);
      if (current !== generation.current) return;
      setDetail(updated); setReason(""); setConfirmed(false);
      setList((value) => value ? { ...value, content: value.content.map((item) => item.hallId === updated.hallId ? updated : item) } : value);
    } catch (e) { if (current === generation.current) { setError(errorText(e)); if (e instanceof ApiError && e.status === 409) setConflict(true); } }
    finally { if (current === generation.current) { inFlight.current = false; setBusy(false); } }
  }
  return <div className="space-y-4" aria-label="Controlled venue publication">
    <div className="flex flex-wrap items-center justify-between gap-3"><div><h3 className="text-lg font-semibold">Controlled publication</h3><p className="mt-1 text-sm text-muted-foreground">Private drafts and live listings are separate. Publish only after factual, duplicate and photo checks. Unpublish before editing a live listing.</p></div><button className={button} disabled={busy} onClick={() => setRevision((v) => v + 1)} type="button">Reload publication state</button></div>
    {error && <p className="rounded-md bg-rose-50 p-3 text-sm text-rose-800" role="alert">{error}</p>}
    {loading && <p role="status">Loading publication state…</p>}
    {list && <><div className="grid gap-3 sm:grid-cols-2">{list.content.map((item) => <button aria-pressed={selected === item.hallId} className={`rounded-lg border p-4 text-left ${selected === item.hallId ? "border-primary bg-blue-50" : "border-border bg-white"}`} disabled={busy} key={item.hallId} onClick={() => setSelected(item.hallId)} type="button"><strong className="break-words">{item.name}</strong><span className="mt-1 block text-sm text-muted-foreground">Venue #{item.hallId} · {[item.area, item.city].filter(Boolean).join(", ")}</span><span className="mt-2 block text-xs font-medium">{item.publicationState === "PUBLISHED" ? "Live — enquiry only" : "Private"} · Publication v{item.publicationVersion}</span></button>)}</div>{!list.content.length && <p className="rounded-md border border-dashed p-5 text-sm">No application-managed venues.</p>}<div className="flex flex-wrap items-center justify-between gap-2"><p className="text-sm">{list.totalElements} venues</p><div className="flex gap-2"><button className={button} disabled={busy || page === 0} onClick={() => { setSelected(null); setPage((v) => v - 1); }} type="button">Previous</button><button className={button} disabled={busy || page + 1 >= list.totalPages} onClick={() => { setSelected(null); setPage((v) => v + 1); }} type="button">Next</button></div></div></>}
    {detail && <section className="space-y-4 rounded-lg border border-border bg-white p-4" aria-label={`Publication controls for ${detail.name}`}>
      <div className="flex flex-wrap justify-between gap-2"><h4 className="font-semibold">{detail.name} · {detail.publicationState === "PUBLISHED" ? "Live" : "Private"}</h4>{detail.publicationState === "PUBLISHED" && <Link className="text-sm text-primary underline" href={`/halls/${detail.hallId}`} target="_blank">View live listing</Link>}</div>
      <p className="text-sm text-muted-foreground">Publication v{detail.publicationVersion} · Facts v{detail.reviewVersion} · Photos v{detail.mediaVersion}</p>
      <p className="text-sm">{detail.approvedPhotoIds.length} approved photos · {detail.coverMediaId ? `Cover #${detail.coverMediaId}` : "No cover selected"}</p>
      {detail.blockers.length > 0 && <div className="rounded-md bg-amber-50 p-3 text-sm text-amber-900"><p className="font-medium">Publication checks</p><ul className="mt-2 list-disc space-y-1 pl-5">{detail.blockers.map((blocker, index) => <li key={index}>{blocker}</li>)}</ul></div>}
      <details className="text-sm"><summary className="cursor-pointer">Source attribution · {detail.sourceRelease}</summary><ul className="mt-2 space-y-1 break-words">{detail.sourceAttribution.map((source, i) => <li key={i}>{source.dataset} · {source.license}{source.recordId ? ` · ${source.recordId}` : ""}</li>)}</ul></details>
      <form className="grid gap-3 border-t pt-4" onSubmit={(event) => { event.preventDefault(); void mutate(); }}>
        <label className="grid gap-1 text-sm font-medium">Audit reason<textarea aria-label="Audit reason" className="min-h-24 rounded-md border border-border p-3 font-normal" disabled={busy} maxLength={4000} minLength={10} onChange={(event) => setReason(event.target.value)} required value={reason} /></label>
        <label className="flex items-start gap-2 text-sm"><input checked={confirmed} className="mt-1 accent-primary" disabled={busy || conflict} onChange={(event) => setConfirmed(event.target.checked)} type="checkbox" /><span>{detail.publicationState === "PUBLISHED" ? "I confirm removing this listing and its photos from public access. Existing enquiries will remain for the VenueMart team." : "I confirm publication as a VenueMart-managed, enquiry-only listing. Availability is unconfirmed; no owner onboarding, booking or payment is enabled."}</span></label>
        {conflict && <p className="text-sm text-amber-900" role="alert">The facts, photos or publication state changed. Your reason is preserved. Reload publication state to discard this form and review current checks before submitting again.</p>}
        <button className={`${button} w-fit ${detail.publicationState === "PUBLISHED" ? "text-rose-700" : "bg-primary text-white"}`} disabled={busy || conflict || !confirmed || reason.trim().length < 10 || (detail.publicationState !== "PUBLISHED" && !detail.ready)} type="submit">{busy ? "Saving…" : detail.publicationState === "PUBLISHED" ? "Unpublish venue" : "Publish enquiry-only venue"}</button>
      </form>
      <section className="border-t pt-4"><h5 className="font-semibold">Publication history</h5>{!detail.history.length && <p className="mt-2 text-sm text-muted-foreground">No publication changes yet.</p>}<ol className="mt-3 space-y-3">{detail.history.map((item) => <li className="rounded-md bg-slate-50 p-3 text-sm" key={item.publicationVersion}><p className="font-medium">{item.state} · Publication v{item.publicationVersion}</p><p className="mt-1 text-xs text-muted-foreground">{item.adminName} · {new Date(item.at).toLocaleString("en-IN")} · Facts v{item.reviewVersion} · Photos v{item.mediaVersion}</p><p className="mt-2 whitespace-pre-wrap break-words">{item.reason}</p></li>)}</ol></section>
    </section>}
  </div>;
}
