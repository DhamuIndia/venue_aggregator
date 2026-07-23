"use client";

import { Check, FileText, LoaderCircle, MessageSquareText, Send, X } from "lucide-react";
import { useMemo, useState, type Dispatch, type FormEvent, type SetStateAction } from "react";
import type { UpsertVendorQuoteInput, VendorQuote } from "@/features/quotes/types";
import type { VendorLead, VendorLeadStatus } from "@/features/vendors/types";

const statusStyle: Record<VendorLeadStatus, string> = {
  NEW: "bg-blue-50 text-blue-700",
  INTERESTED: "bg-emerald-50 text-emerald-700",
  CONTACTED: "bg-amber-50 text-amber-700",
  QUOTE_SENT: "bg-violet-50 text-violet-700",
  BOOKED: "bg-emerald-50 text-emerald-700",
  NOT_SELECTED: "bg-slate-100 text-slate-700",
  DECLINED: "bg-rose-50 text-rose-700",
  COMPLETED: "bg-muted text-muted-foreground"
};

type Props = {
  leads: VendorLead[];
  quotes: VendorQuote[];
  isLoading: boolean;
  error: string;
  filter: "ALL" | VendorLeadStatus;
  onFilterChange: (value: "ALL" | VendorLeadStatus) => void;
  onStatusChange: (leadId: string, status: VendorLeadStatus, reason?: string) => Promise<void>;
  onSaveQuote: (lead: VendorLead, payload: UpsertVendorQuoteInput) => Promise<void>;
};

type QuoteForm = {
  amount: string;
  packageName: string;
  serviceDescription: string;
  inclusions: string;
  additionalCharges: string;
  additionalChargesDescription: string;
  notes: string;
  validUntil: string;
};

const emptyQuoteForm: QuoteForm = {
  amount: "",
  packageName: "",
  serviceDescription: "",
  inclusions: "",
  additionalCharges: "",
  additionalChargesDescription: "",
  notes: "",
  validUntil: ""
};

export function VendorLeadInbox({
  leads,
  quotes,
  isLoading,
  error,
  filter,
  onFilterChange,
  onStatusChange,
  onSaveQuote
}: Props) {
  const [quoteLead, setQuoteLead] = useState<VendorLead | null>(null);
  const [quoteForm, setQuoteForm] = useState<QuoteForm>(emptyQuoteForm);
  const [quoteError, setQuoteError] = useState("");
  const [isSavingQuote, setIsSavingQuote] = useState(false);
  const [declineLead, setDeclineLead] = useState<VendorLead | null>(null);
  const [declineReason, setDeclineReason] = useState("");
  const [declineError, setDeclineError] = useState("");
  const [isDeclining, setIsDeclining] = useState(false);

  const filteredLeads = useMemo(
    () => filter === "ALL" ? leads : leads.filter((lead) => lead.status === filter),
    [filter, leads]
  );
  const quotesByLeadId = useMemo(
    () => new Map(quotes.map((quote) => [quote.leadId, quote])),
    [quotes]
  );

  function openQuote(lead: VendorLead) {
    const quote = quotesByLeadId.get(lead.id);
    setQuoteLead(lead);
    setQuoteError("");
    setQuoteForm(quote ? {
      amount: String(quote.amount),
      packageName: quote.packageName,
      serviceDescription: quote.serviceDescription,
      inclusions: quote.inclusions.join(", "),
      additionalCharges: quote.additionalCharges ? String(quote.additionalCharges) : "",
      additionalChargesDescription: quote.additionalChargesDescription ?? "",
      notes: quote.notes ?? "",
      validUntil: quote.validUntil
    } : {
      ...emptyQuoteForm,
      amount: lead.budget ? String(lead.budget) : "",
      packageName: `${lead.eventType} ${lead.service}`.trim()
    });
  }

  async function submitQuote(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!quoteLead) return;
    const payload = quotePayload(quoteForm);
    if (typeof payload === "string") {
      setQuoteError(payload);
      return;
    }
    try {
      setIsSavingQuote(true);
      setQuoteError("");
      await onSaveQuote(quoteLead, payload);
      setQuoteLead(null);
      setQuoteForm(emptyQuoteForm);
    } catch (exception) {
      setQuoteError(exception instanceof Error ? exception.message : "Could not send the quotation.");
    } finally {
      setIsSavingQuote(false);
    }
  }

  async function submitDecline(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!declineLead) return;
    if (!declineReason.trim()) {
      setDeclineError("Enter a reason so the customer understands the decision.");
      return;
    }
    try {
      setIsDeclining(true);
      setDeclineError("");
      await onStatusChange(declineLead.id, "DECLINED", declineReason.trim());
      setDeclineLead(null);
      setDeclineReason("");
    } catch (exception) {
      setDeclineError(exception instanceof Error ? exception.message : "Could not decline the lead.");
    } finally {
      setIsDeclining(false);
    }
  }

  function startStatusChange(leadId: string, status: VendorLeadStatus) {
    void onStatusChange(leadId, status).catch(() => undefined);
  }

  return (
    <section className="py-7">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="text-xl font-semibold">Lead inbox</h2>
          <p className="mt-1 text-sm text-muted-foreground">Review requirements, show interest, and send a structured quotation.</p>
        </div>
        <label className="text-xs font-medium text-muted-foreground">
          Status
          <select className="mt-1 block h-10 rounded-md border border-border bg-white px-3 text-sm text-foreground" onChange={(event) => onFilterChange(event.target.value as "ALL" | VendorLeadStatus)} value={filter}>
            <option value="ALL">All leads</option>
            <option value="NEW">New</option>
            <option value="INTERESTED">Interested</option>
            <option value="CONTACTED">Contacted</option>
            <option value="QUOTE_SENT">Quote sent</option>
            <option value="BOOKED">Booked</option>
            <option value="NOT_SELECTED">Not selected</option>
            <option value="DECLINED">Declined</option>
          </select>
        </label>
      </div>

      {error && <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{error}</p>}

      {isLoading ? (
        <div className="mt-5 grid gap-4">{[1, 2, 3].map((item) => <div className="h-44 animate-pulse rounded-lg border border-border bg-white" key={item} />)}</div>
      ) : filteredLeads.length > 0 ? (
        <div className="mt-5 grid gap-4">
          {filteredLeads.map((lead) => {
            const quote = quotesByLeadId.get(lead.id);
            return (
              <article className="rounded-lg border border-border bg-white p-5" key={lead.id}>
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="font-semibold">{lead.eventType} | {lead.service}</h3>
                      <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${statusStyle[lead.status]}`}>{readableStatus(lead.status)}</span>
                      {lead.source === "MARKETPLACE_REQUIREMENT" && <span className="rounded-full bg-violet-50 px-2.5 py-1 text-xs font-medium text-violet-700">Marketplace match</span>}
                    </div>
                    <p className="mt-2 text-sm text-muted-foreground">{lead.eventDate} | {lead.location}</p>
                  </div>
                  <div className="text-right">
                    <p className="font-semibold">{lead.budget !== undefined ? `INR ${formatMoney(lead.budget)}` : "Not specified"}</p>
                    <p className="mt-1 text-xs text-muted-foreground">Customer budget</p>
                  </div>
                </div>

                <div className="mt-4 grid gap-3 rounded-md bg-muted/60 p-4 text-sm sm:grid-cols-2">
                  <p><span className="text-muted-foreground">Customer:</span> {lead.customerName}</p>
                  <p><span className="text-muted-foreground">Reference:</span> {lead.requirementId ? `Requirement ${lead.requirementId}` : lead.id}</p>
                  {lead.contactDetailsShared && (lead.customerPhone || lead.customerEmail)
                    ? <p className="sm:col-span-2"><span className="text-muted-foreground">Contact:</span> {[lead.customerPhone, lead.customerEmail].filter(Boolean).join(" | ")}</p>
                    : lead.source === "MARKETPLACE_REQUIREMENT"
                      ? <p className="sm:col-span-2 text-muted-foreground">Customer contact details are private. Respond through VenueMart.</p>
                      : null}
                  {lead.notes && <p className="leading-6 sm:col-span-2"><span className="text-muted-foreground">Requirement:</span> {lead.notes}</p>}
                  {lead.declineReason && <p className="leading-6 text-rose-700 sm:col-span-2"><span className="font-medium">Decline reason:</span> {lead.declineReason}</p>}
                </div>

                {quote && (
                  <div className="mt-4 rounded-md border border-violet-200 bg-violet-50/60 p-4">
                    <div className="flex flex-wrap items-start justify-between gap-4">
                      <div><p className="text-xs font-semibold uppercase tracking-wide text-violet-700">{quote.status === "ACCEPTED" ? "Quotation accepted" : quote.status === "NOT_SELECTED" ? "Quotation not selected" : "Quotation sent"}</p><h4 className="mt-1 font-semibold">{quote.packageName}</h4><p className="mt-1 text-sm text-muted-foreground">Valid until {formatDate(quote.validUntil)}</p></div>
                      <div className="text-right"><p className="font-semibold">INR {formatMoney(quote.totalAmount)}</p><p className="text-xs text-muted-foreground">Total quoted</p></div>
                    </div>
                    <p className="mt-3 text-sm leading-6">{quote.serviceDescription}</p>
                    <div className="mt-3 flex flex-wrap gap-2">{quote.inclusions.map((item) => <span className="rounded-full bg-white px-2.5 py-1 text-xs text-violet-800" key={item}>{item}</span>)}</div>
                  </div>
                )}

                {lead.status !== "COMPLETED" && lead.status !== "DECLINED" && lead.status !== "NOT_SELECTED" && (
                  <div className="mt-5 flex flex-wrap gap-2 border-t border-border pt-4">
                    {lead.status === "NEW" && <button className="inline-flex h-10 items-center gap-2 rounded-md border border-emerald-200 px-4 text-sm font-semibold text-emerald-700" onClick={() => startStatusChange(lead.id, "INTERESTED")} type="button"><Check size={17} /> Interested</button>}
                    {(lead.status === "INTERESTED" || lead.status === "NEW") && <button className="inline-flex h-10 items-center gap-2 rounded-md border border-border px-4 text-sm font-semibold" onClick={() => startStatusChange(lead.id, "CONTACTED")} type="button"><MessageSquareText size={17} /> Mark contacted</button>}
                    {["NEW", "INTERESTED", "CONTACTED", "QUOTE_SENT"].includes(lead.status) && <button className="inline-flex h-10 items-center gap-2 rounded-md bg-foreground px-4 text-sm font-semibold text-white" onClick={() => openQuote(lead)} type="button"><Send size={17} /> {quote ? "Edit quotation" : "Create quotation"}</button>}
                    {(lead.status === "CONTACTED" || lead.status === "QUOTE_SENT") && lead.source === "DIRECT_ENQUIRY" && <button className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 text-sm font-semibold text-white" onClick={() => startStatusChange(lead.id, "BOOKED")} type="button"><Check size={17} /> Mark booked</button>}
                    {lead.status === "QUOTE_SENT" && lead.source === "MARKETPLACE_REQUIREMENT" && <p className="self-center text-sm font-medium text-muted-foreground">Waiting for the customer to choose a quote.</p>}
                    {lead.status === "BOOKED" && <button className="inline-flex h-10 items-center gap-2 rounded-md bg-emerald-700 px-4 text-sm font-semibold text-white" onClick={() => startStatusChange(lead.id, "COMPLETED")} type="button"><Check size={17} /> Mark completed</button>}
                    <button className="h-10 px-3 text-sm font-medium text-rose-700" onClick={() => { setDeclineLead(lead); setDeclineReason(""); setDeclineError(""); }} type="button">Decline</button>
                  </div>
                )}
              </article>
            );
          })}
        </div>
      ) : (
        <div className="mt-5 rounded-lg border border-dashed border-border bg-white p-8 text-center">
          <MessageSquareText className="mx-auto text-muted-foreground" size={28} />
          <h3 className="mt-4 font-semibold">No leads yet</h3>
          <p className="mt-2 text-sm text-muted-foreground">New quote requests will appear here.</p>
        </div>
      )}

      {quoteLead && (
        <div aria-label="Create vendor quotation" aria-modal="true" className="fixed inset-0 z-50 grid place-items-center bg-black/55 p-4" role="dialog">
          <form className="max-h-[92vh] w-full max-w-2xl overflow-y-auto rounded-xl bg-white p-6 shadow-2xl" onSubmit={submitQuote}>
            <div className="flex items-start justify-between gap-4"><div><p className="text-sm font-semibold text-primary">Quotation for {quoteLead.service}</p><h2 className="mt-1 text-2xl font-semibold">{quotesByLeadId.has(quoteLead.id) ? "Update quotation" : "Create quotation"}</h2><p className="mt-2 text-sm text-muted-foreground">{quoteLead.eventType} on {formatDate(quoteLead.eventDate)}</p></div><button aria-label="Close quotation form" className="grid size-9 place-items-center rounded-md hover:bg-muted" onClick={() => setQuoteLead(null)} type="button"><X size={18} /></button></div>
            <div className="mt-6 grid gap-4 sm:grid-cols-2">
              <label className="text-sm font-medium">Package name<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" maxLength={160} onChange={(event) => updateQuoteField(setQuoteForm, "packageName", event.target.value)} required value={quoteForm.packageName} /></label>
              <label className="text-sm font-medium">Base quote amount<span className="relative mt-2 block"><span className="absolute left-3 top-1/2 -translate-y-1/2 text-sm text-muted-foreground">INR</span><input className="h-11 w-full rounded-md border border-border pl-12 pr-3 font-normal outline-none focus:border-primary" min="1" onChange={(event) => updateQuoteField(setQuoteForm, "amount", event.target.value)} required type="number" value={quoteForm.amount} /></span></label>
              <label className="text-sm font-medium sm:col-span-2">Service description<textarea className="mt-2 min-h-24 w-full rounded-md border border-border p-3 font-normal leading-6 outline-none focus:border-primary" maxLength={2000} onChange={(event) => updateQuoteField(setQuoteForm, "serviceDescription", event.target.value)} required value={quoteForm.serviceDescription} /></label>
              <label className="text-sm font-medium sm:col-span-2">Included deliverables<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" onChange={(event) => updateQuoteField(setQuoteForm, "inclusions", event.target.value)} placeholder="Album, edited photos, travel" required value={quoteForm.inclusions} /><span className="mt-1 block text-xs font-normal text-muted-foreground">Separate items with commas.</span></label>
              <label className="text-sm font-medium">Additional charges <span className="font-normal text-muted-foreground">(optional)</span><span className="relative mt-2 block"><span className="absolute left-3 top-1/2 -translate-y-1/2 text-sm text-muted-foreground">INR</span><input className="h-11 w-full rounded-md border border-border pl-12 pr-3 font-normal outline-none focus:border-primary" min="0" onChange={(event) => updateQuoteField(setQuoteForm, "additionalCharges", event.target.value)} type="number" value={quoteForm.additionalCharges} /></span></label>
              <label className="text-sm font-medium">Valid until<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" max={quoteLead.eventDate} min={localToday()} onChange={(event) => updateQuoteField(setQuoteForm, "validUntil", event.target.value)} required type="date" value={quoteForm.validUntil} /></label>
              <label className="text-sm font-medium sm:col-span-2">Additional charge details <span className="font-normal text-muted-foreground">(optional)</span><input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" maxLength={1000} onChange={(event) => updateQuoteField(setQuoteForm, "additionalChargesDescription", event.target.value)} placeholder="e.g. Travel outside Chennai" value={quoteForm.additionalChargesDescription} /></label>
              <label className="text-sm font-medium sm:col-span-2">Vendor notes <span className="font-normal text-muted-foreground">(optional)</span><textarea className="mt-2 min-h-20 w-full rounded-md border border-border p-3 font-normal leading-6 outline-none focus:border-primary" maxLength={2000} onChange={(event) => updateQuoteField(setQuoteForm, "notes", event.target.value)} value={quoteForm.notes} /></label>
            </div>
            {quoteError && <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{quoteError}</p>}
            <div className="mt-6 flex flex-wrap justify-end gap-2"><button className="h-10 rounded-md border border-border px-4 text-sm font-semibold" onClick={() => setQuoteLead(null)} type="button">Cancel</button><button className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-5 text-sm font-semibold text-white disabled:opacity-60" disabled={isSavingQuote} type="submit">{isSavingQuote ? <LoaderCircle className="animate-spin" size={17} /> : <FileText size={17} />} Send quotation</button></div>
          </form>
        </div>
      )}

      {declineLead && (
        <div aria-label="Decline vendor lead" aria-modal="true" className="fixed inset-0 z-50 grid place-items-center bg-black/55 p-4" role="dialog">
          <form className="w-full max-w-lg rounded-xl bg-white p-6 shadow-2xl" onSubmit={submitDecline}>
            <div className="flex items-start justify-between gap-4"><div><h2 className="text-xl font-semibold">Decline this lead?</h2><p className="mt-2 text-sm text-muted-foreground">The customer will see your reason.</p></div><button aria-label="Close decline form" className="grid size-9 place-items-center rounded-md hover:bg-muted" onClick={() => setDeclineLead(null)} type="button"><X size={18} /></button></div>
            <label className="mt-5 block text-sm font-medium">Reason<textarea className="mt-2 min-h-28 w-full rounded-md border border-border p-3 font-normal leading-6 outline-none focus:border-primary" maxLength={1000} onChange={(event) => setDeclineReason(event.target.value)} placeholder="Unavailable on the event date, service outside coverage area..." required value={declineReason} /></label>
            {declineError && <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{declineError}</p>}
            <div className="mt-6 flex justify-end gap-2"><button className="h-10 rounded-md border border-border px-4 text-sm font-semibold" onClick={() => setDeclineLead(null)} type="button">Cancel</button><button className="h-10 rounded-md bg-rose-700 px-5 text-sm font-semibold text-white disabled:opacity-60" disabled={isDeclining} type="submit">{isDeclining ? "Declining..." : "Decline lead"}</button></div>
          </form>
        </div>
      )}
    </section>
  );
}

function quotePayload(form: QuoteForm): UpsertVendorQuoteInput | string {
  const amount = Number(form.amount);
  const additionalCharges = form.additionalCharges ? Number(form.additionalCharges) : 0;
  const inclusions = [...new Set(form.inclusions.split(",").map((item) => item.trim()).filter(Boolean))];
  if (!form.packageName.trim()) return "Enter a package name.";
  if (!Number.isFinite(amount) || amount <= 0) return "Enter a quote amount greater than zero.";
  if (!form.serviceDescription.trim()) return "Describe the services included in the quote.";
  if (inclusions.length === 0) return "Add at least one included deliverable.";
  if (!Number.isFinite(additionalCharges) || additionalCharges < 0) return "Additional charges cannot be negative.";
  if (!form.validUntil) return "Select how long the quotation is valid.";
  return {
    amount,
    packageName: form.packageName.trim(),
    serviceDescription: form.serviceDescription.trim(),
    inclusions,
    additionalCharges,
    additionalChargesDescription: form.additionalChargesDescription.trim() || undefined,
    notes: form.notes.trim() || undefined,
    validUntil: form.validUntil
  };
}

function updateQuoteField(
  setter: Dispatch<SetStateAction<QuoteForm>>,
  field: keyof QuoteForm,
  value: string
) {
  setter((current) => ({ ...current, [field]: value }));
}

function readableStatus(status: VendorLeadStatus) {
  return status.toLowerCase().replaceAll("_", " ");
}

function formatMoney(value: number) {
  return new Intl.NumberFormat("en-IN").format(value);
}

function formatDate(value: string) {
  const date = new Date(`${value}T00:00:00`);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat("en-IN", { dateStyle: "medium" }).format(date);
}

function localToday() {
  const now = new Date();
  const offset = now.getTimezoneOffset() * 60_000;
  return new Date(now.getTime() - offset).toISOString().slice(0, 10);
}
