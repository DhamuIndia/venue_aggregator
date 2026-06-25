"use client";

import { BadgeCheck, CalendarDays, IndianRupee, LoaderCircle, MapPin, Send } from "lucide-react";
import Link from "next/link";
import { useState, type FormEvent } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import { createVendorLead } from "@/features/vendors/lead-client";
import type { VendorSummary } from "@/features/vendors/types";

export function VendorQuotePanel({ vendor }: { vendor: VendorSummary }) {
  const { accessToken, user } = useAuth();
  const [eventDate, setEventDate] = useState("");
  const [eventType, setEventType] = useState("Wedding");
  const [location, setLocation] = useState("");
  const [service, setService] = useState(vendor.services[0]);
  const [budget, setBudget] = useState("");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState("");
  const [leadId, setLeadId] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    if (!eventDate || !location.trim() || Number(budget) < 1) {
      setError("Add the event date, location, and expected budget.");
      return;
    }
    if (!user || user.role !== "CUSTOMER") {
      setError("Sign in with a customer account to request a quote.");
      return;
    }
    try {
      setSubmitting(true);
      const lead = await createVendorLead({
        vendorId: vendor.id,
        vendorName: vendor.businessName,
        customerId: user.id,
        customerName: user.fullName,
        eventDate,
        eventType,
        location: location.trim(),
        service,
        budget: Number(budget),
        notes: notes.trim() || undefined
      }, accessToken);
      setLeadId(lead.id);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not send quote request.");
    } finally {
      setSubmitting(false);
    }
  }

  if (leadId) {
    return (
      <aside className="h-fit rounded-lg border border-emerald-200 bg-white p-5 shadow-sm lg:sticky lg:top-24">
        <span className="grid size-11 place-items-center rounded-md bg-emerald-50 text-emerald-700"><BadgeCheck size={23} /></span>
        <h2 className="mt-4 text-xl font-semibold">Quote request sent</h2>
        <p className="mt-2 text-sm leading-6 text-muted-foreground">{vendor.businessName} can now review your event details and respond with a quote.</p>
        <div className="mt-5 rounded-md bg-muted px-3 py-3 text-sm"><span className="text-muted-foreground">Reference</span><strong className="ml-2">{leadId}</strong></div>
        <button className="mt-5 h-10 w-full rounded-md border border-border text-sm font-semibold" onClick={() => setLeadId("")}>Send another request</button>
      </aside>
    );
  }

  return (
    <aside className="h-fit rounded-lg border border-border bg-white p-5 shadow-sm lg:sticky lg:top-24">
      <div className="flex items-start justify-between gap-4"><div><p className="text-xs text-muted-foreground">Starting from</p><p className="mt-1 text-xl font-semibold">INR {new Intl.NumberFormat("en-IN").format(vendor.startingPrice)}{vendor.category === "CATERING" ? " / plate" : ""}</p></div><span className="rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-medium text-emerald-800">Free quote</span></div>
      <form className="mt-5 grid gap-4" onSubmit={submit}>
        <label className="text-sm font-medium">Service<select className="mt-2 h-11 w-full rounded-md border border-border bg-white px-3 font-normal outline-none focus:border-primary" onChange={(event) => setService(event.target.value)} value={service}>{vendor.services.map((item) => <option key={item}>{item}</option>)}</select></label>
        <div className="grid grid-cols-2 gap-3"><label className="text-sm font-medium">Event type<select className="mt-2 h-11 w-full rounded-md border border-border bg-white px-3 font-normal outline-none focus:border-primary" onChange={(event) => setEventType(event.target.value)} value={eventType}><option>Wedding</option><option>Reception</option><option>Engagement</option><option>Birthday</option><option>Corporate event</option></select></label><label className="text-sm font-medium">Event date<span className="relative mt-2 block"><CalendarDays className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" size={16} /><input className="h-11 w-full rounded-md border border-border pl-9 pr-2 font-normal outline-none focus:border-primary" min="2026-06-22" onChange={(event) => setEventDate(event.target.value)} type="date" value={eventDate} /></span></label></div>
        <label className="text-sm font-medium">Event location<span className="relative mt-2 block"><MapPin className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" size={16} /><input className="h-11 w-full rounded-md border border-border pl-9 pr-3 font-normal outline-none focus:border-primary" onChange={(event) => setLocation(event.target.value)} placeholder="Area, city" value={location} /></span></label>
        <label className="text-sm font-medium">Expected budget<span className="relative mt-2 block"><IndianRupee className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" size={16} /><input className="h-11 w-full rounded-md border border-border pl-9 pr-3 font-normal outline-none focus:border-primary" min="1" onChange={(event) => setBudget(event.target.value)} placeholder="Your estimated budget" type="number" value={budget} /></span></label>
        <label className="text-sm font-medium">Notes <span className="font-normal text-muted-foreground">(optional)</span><textarea className="mt-2 min-h-24 w-full resize-y rounded-md border border-border p-3 font-normal leading-6 outline-none focus:border-primary" onChange={(event) => setNotes(event.target.value)} placeholder="Guest count, preferences, and timings" value={notes} /></label>
        {error && <p className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{error}{!user && <> <Link className="font-semibold underline" href={`/auth/login?next=/vendors/${vendor.id}`}>Sign in</Link></>}</p>}
        <button className="inline-flex h-12 items-center justify-center gap-2 rounded-md bg-primary text-sm font-semibold text-white disabled:opacity-60" disabled={submitting} type="submit">{submitting ? <LoaderCircle className="animate-spin" size={18} /> : <Send size={17} />} Request quote</button>
        <p className="text-center text-xs text-muted-foreground">Your contact details are shared only after submission.</p>
      </form>
    </aside>
  );
}
