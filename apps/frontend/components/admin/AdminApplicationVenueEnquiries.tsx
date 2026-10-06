"use client";

import { useEffect, useRef, useState } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import { ApiError } from "@/lib/api-client";
import { getApplicationEnquiries, updateApplicationEnquiry, type ApplicationEnquiry, type ApplicationEnquiryPage } from "@/features/admin/application-enquiry-client";
import { venueMartEnquiryStatus } from "@/features/enquiries/enquiry-display";
import { formatSlotRequests, formatSlot } from "@/features/halls/slot-model";

const button = "min-h-10 rounded-md border border-border px-3 py-2 text-sm font-medium disabled:opacity-50";
export function AdminApplicationVenueEnquiries() {
  const { accessToken, user } = useAuth(); const token = useRef(accessToken); token.current = accessToken;
  const [page, setPage] = useState(0); const [status, setStatus] = useState("ALL"); const [revision, setRevision] = useState(0);
  const [data, setData] = useState<ApplicationEnquiryPage | null>(null); const [selected, setSelected] = useState<ApplicationEnquiry | null>(null);
  const [response, setResponse] = useState(""); const [reason, setReason] = useState(""); const [confirmed, setConfirmed] = useState(false);
  const [loading, setLoading] = useState(false); const [busy, setBusy] = useState(false); const [error, setError] = useState(""); const [conflict, setConflict] = useState(false);
  const generation = useRef(0); const inFlight = useRef(false);
  useEffect(() => { generation.current++; setData(null); setSelected(null); setResponse(""); setReason(""); setConfirmed(false); setError(""); setBusy(false); setConflict(false); inFlight.current = false; return () => { generation.current++; }; }, [user?.id, user?.role]);
  useEffect(() => {
    const abort = new AbortController(); setLoading(true); setError(""); setData(null);
    void getApplicationEnquiries(page, status, accessToken, abort.signal).then((value) => { if (!abort.signal.aborted) setData(value); }).catch((e) => { if (!abort.signal.aborted) setError(e instanceof Error ? e.message : "Could not load VenueMart enquiries."); }).finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [page, status, revision, accessToken]);
  function select(item: ApplicationEnquiry | null) { if (busy) return; setSelected(item); setResponse(item?.responseMessage ?? ""); setReason(""); setConfirmed(false); setConflict(false); setError(""); }
  async function save() {
    if (!selected || inFlight.current || conflict || !confirmed || !response.trim() || reason.trim().length < 10) return;
    const current = generation.current; inFlight.current = true; setBusy(true); setError("");
    try {
      const updated = await updateApplicationEnquiry(selected, response, reason, token.current);
      if (current !== generation.current) return;
      setSelected(updated); setResponse(updated.responseMessage ?? ""); setReason(""); setConfirmed(false);
      setData((value) => value ? { ...value, items: value.items.map((item) => item.id === updated.id ? updated : item) } : value);
    } catch (e) { if (current === generation.current) { setError(e instanceof Error ? e.message : "Could not update the enquiry."); if (e instanceof ApiError && e.status === 409) setConflict(true); } }
    finally { if (current === generation.current) { inFlight.current = false; setBusy(false); } }
  }
  return <section className="space-y-4 py-7" aria-label="VenueMart-managed venue enquiries">
    <div className="flex flex-wrap items-end justify-between gap-3"><div><h2 className="text-xl font-semibold">VenueMart team enquiries</h2><p className="mt-1 max-w-3xl text-sm text-muted-foreground">Requests for application-managed venues are handled here, not sent to an invented owner. Contact and closure updates do not create bookings or payments.</p></div><button className={button} disabled={busy} onClick={() => { select(null); setRevision((v) => v + 1); }} type="button">Discard form and reload queue</button></div>
    <label className="inline-grid gap-1 text-sm font-medium">Status<select className="min-h-10 rounded-md border border-border bg-white px-3" disabled={busy} onChange={(event) => { select(null); setPage(0); setStatus(event.target.value); }} value={status}><option value="ALL">All team enquiries</option><option value="NEW">New</option><option value="CONTACTED">Contacted</option><option value="CLOSED">Closed</option></select></label>
    {error && <p className="rounded-md bg-rose-50 p-3 text-sm text-rose-800" role="alert">{error}</p>}
    {loading && <p role="status">Loading VenueMart enquiries…</p>}
    {data && <><div className="grid gap-3 sm:grid-cols-2">{data.items.map((item) => <button aria-pressed={selected?.id === item.id} className={`rounded-lg border p-4 text-left ${selected?.id === item.id ? "border-primary bg-blue-50" : "border-border bg-white"}`} disabled={busy} key={item.id} onClick={() => select(item)} type="button"><strong className="break-words">{item.hallName}</strong><span className="mt-1 block text-sm">Enquiry #{item.id} · {item.eventDate} · {item.guestCount} guests</span><span className="mt-2 block text-xs text-muted-foreground">{venueMartEnquiryStatus(item.status)}</span></button>)}</div>{!data.items.length && <p className="rounded-md border border-dashed p-6 text-sm text-muted-foreground">No VenueMart team enquiries match this filter.</p>}<div className="flex flex-wrap items-center justify-between gap-2"><p className="text-sm">{data.totalItems} enquiries</p><div className="flex gap-2"><button className={button} disabled={busy || page === 0} onClick={() => { select(null); setPage((v) => v - 1); }} type="button">Previous</button><button className={button} disabled={busy || page + 1 >= data.totalPages} onClick={() => { select(null); setPage((v) => v + 1); }} type="button">Next</button></div></div></>}
    {selected && <section className="space-y-4 rounded-lg border border-border bg-white p-4" aria-label={`VenueMart enquiry ${selected.id}`}>
      <h3 className="font-semibold">{selected.hallName} · Enquiry #{selected.id}</h3><p className="text-sm">{venueMartEnquiryStatus(selected.status)} · Version {selected.version} · Listing publication v{selected.publicationVersion}</p>
      <dl className="grid gap-3 text-sm sm:grid-cols-2"><div><dt className="text-xs text-muted-foreground">Customer</dt><dd>{selected.customerName || "Not provided"}</dd></div><div><dt className="text-xs text-muted-foreground">Contact</dt><dd className="break-all">{selected.customerPhone || "No phone"}{selected.customerEmail ? ` · ${selected.customerEmail}` : ""}</dd></div><div><dt className="text-xs text-muted-foreground">Event</dt><dd>{selected.eventType} · {selected.guestCount} guests</dd></div><div><dt className="text-xs text-muted-foreground">Requested date and timings</dt><dd>{selected.slotRequests?.length ? formatSlotRequests(selected.slotRequests) : `${selected.eventDate} · ${formatSlot(selected.slot)}`}</dd></div></dl>
      {selected.notes && <p className="whitespace-pre-wrap break-words rounded-md bg-slate-50 p-3 text-sm">Customer notes: {selected.notes}</p>}
      <p className="text-xs text-muted-foreground">Contact details are for responding to this enquiry only. Availability, pricing and arrangements must be checked separately.</p>
      {selected.status === "CLOSED" ? <p className="whitespace-pre-wrap rounded-md bg-slate-50 p-3 text-sm">Team response: {selected.responseMessage || "Not provided"}. This enquiry is closed; no booking was created.</p> : <form className="grid gap-3 border-t pt-4" onSubmit={(event) => { event.preventDefault(); void save(); }}>
        <label className="grid gap-1 text-sm font-medium">Customer-visible team response<textarea aria-label="Customer-visible team response" className="min-h-24 rounded-md border border-border p-3 font-normal" disabled={busy} maxLength={2000} minLength={1} onChange={(event) => setResponse(event.target.value)} required value={response} /></label>
        <label className="grid gap-1 text-sm font-medium">Internal audit reason<textarea aria-label="Internal audit reason" className="min-h-20 rounded-md border border-border p-3 font-normal" disabled={busy} maxLength={1000} minLength={10} onChange={(event) => setReason(event.target.value)} required value={reason} /></label>
        <label className="flex items-start gap-2 text-sm"><input checked={confirmed} className="mt-1 accent-primary" disabled={busy || conflict} onChange={(event) => setConfirmed(event.target.checked)} type="checkbox" /><span>{selected.status === "NEW" ? "I have contacted the customer and confirm the response above can be shown in their account." : "I confirm closing this enquiry with the response above. This does not confirm a venue booking or collect payment."}</span></label>
        {conflict && <p className="text-sm text-amber-900" role="alert">Another admin changed this enquiry. Your response and reason are preserved. Discard form and reload queue before reviewing and submitting again.</p>}
        <button className={`${button} w-fit bg-primary text-white`} disabled={busy || conflict || !confirmed || !response.trim() || reason.trim().length < 10} type="submit">{busy ? "Saving…" : selected.status === "NEW" ? "Mark contacted" : "Close enquiry"}</button>
      </form>}
    </section>}
  </section>;
}
