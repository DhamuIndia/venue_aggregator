"use client";

import { BadgeCheck, Heart, LoaderCircle, X } from "lucide-react";
import type { ReactNode } from "react";
import type { VendorQuote } from "@/features/quotes/types";

type Props = {
  quotes: VendorQuote[];
  updatingQuoteId: string | null;
  acceptingQuoteId: string | null;
  onClose: () => void;
  onAccept: (quote: VendorQuote) => void;
  onToggleShortlist: (quote: VendorQuote) => void;
};

export function QuoteComparisonDialog({
  quotes,
  updatingQuoteId,
  acceptingQuoteId,
  onClose,
  onAccept,
  onToggleShortlist
}: Props) {
  if (quotes.length === 0) return null;

  return (
    <div aria-label="Compare vendor quotations" aria-modal="true" className="fixed inset-0 z-50 bg-black/60 p-3 sm:p-6" role="dialog">
      <div className="mx-auto flex max-h-full w-full max-w-7xl flex-col rounded-xl bg-white shadow-2xl">
        <div className="flex items-start justify-between gap-4 border-b border-border p-5 sm:p-6">
          <div>
            <p className="text-sm font-semibold text-primary">Quote comparison</p>
            <h2 className="mt-1 text-2xl font-semibold">Compare {quotes.length} vendor quotation{quotes.length === 1 ? "" : "s"}</h2>
            <p className="mt-2 text-sm text-muted-foreground">Review the complete price and deliverables before choosing whom to contact.</p>
          </div>
          <button aria-label="Close quote comparison" className="grid size-10 shrink-0 place-items-center rounded-md hover:bg-muted" onClick={onClose} type="button"><X size={19} /></button>
        </div>

        <div className="overflow-auto p-5 sm:p-6">
          <table className="w-full min-w-[760px] border-separate border-spacing-0 text-left text-sm">
            <thead>
              <tr>
                <th className="sticky left-0 z-10 w-44 border-b border-r border-border bg-white p-3 text-xs font-medium uppercase tracking-wide text-muted-foreground">Compare</th>
                {quotes.map((quote) => (
                  <th className={`min-w-64 border-b border-border p-4 align-top ${quote.shortlisted ? "bg-rose-50/60" : "bg-white"}`} key={quote.id}>
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <div><p className="text-base font-semibold text-foreground">{quote.vendorName}</p><p className="mt-1 text-xs font-normal text-muted-foreground">{quote.service}</p></div>
                      {quote.shortlisted && <span className="rounded-full bg-rose-100 px-2.5 py-1 text-xs font-medium text-rose-700">Shortlisted</span>}
                    </div>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              <ComparisonRow label="Package" quotes={quotes} render={(quote) => <strong>{quote.packageName}</strong>} />
              <ComparisonRow label="Total price" quotes={quotes} render={(quote) => <strong className="text-base">INR {formatMoney(quote.totalAmount)}</strong>} />
              <ComparisonRow label="Base price" quotes={quotes} render={(quote) => <>INR {formatMoney(quote.amount)}</>} />
              <ComparisonRow label="Additional charges" quotes={quotes} render={(quote) => quote.additionalCharges > 0
                ? <><span>INR {formatMoney(quote.additionalCharges)}</span>{quote.additionalChargesDescription && <p className="mt-1 text-xs leading-5 text-muted-foreground">{quote.additionalChargesDescription}</p>}</>
                : <span className="text-muted-foreground">None</span>} />
              <ComparisonRow label="Valid until" quotes={quotes} render={(quote) => <><span className={isQuoteExpired(quote) ? "font-medium text-rose-700" : ""}>{formatDate(quote.validUntil)}</span>{isQuoteExpired(quote) && <p className="mt-1 text-xs text-rose-700">Expired</p>}</>} />
              <ComparisonRow label="Service details" quotes={quotes} render={(quote) => <p className="leading-6">{quote.serviceDescription}</p>} />
              <ComparisonRow label="Inclusions" quotes={quotes} render={(quote) => <ul className="grid gap-2">{quote.inclusions.map((item, index) => <li className="flex gap-2" key={`${quote.id}-${index}`}><span className="text-emerald-700">✓</span><span>{item}</span></li>)}</ul>} />
              <ComparisonRow label="Vendor notes" quotes={quotes} render={(quote) => quote.notes ? <p className="leading-6">{quote.notes}</p> : <span className="text-muted-foreground">No additional notes</span>} />
              <tr>
                <th className="sticky left-0 z-10 border-r border-border bg-white p-3 text-xs font-medium uppercase tracking-wide text-muted-foreground">Shortlist</th>
                {quotes.map((quote) => {
                  const isUpdating = updatingQuoteId === quote.id;
                  const isExpired = isQuoteExpired(quote);
                  const isUnavailable = quote.status !== "SENT" || isExpired;
                  return (
                    <td className={`p-4 ${quote.shortlisted ? "bg-rose-50/60" : "bg-white"}`} key={quote.id}>
                      <button
                        className={`inline-flex h-10 items-center gap-2 rounded-md px-4 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-60 ${quote.shortlisted ? "border border-rose-200 bg-white text-rose-700" : "bg-primary text-white"}`}
                        disabled={isUpdating || (isUnavailable && !quote.shortlisted)}
                        onClick={() => onToggleShortlist(quote)}
                        type="button"
                      >
                        {isUpdating ? <LoaderCircle className="animate-spin" size={17} /> : <Heart fill={quote.shortlisted ? "currentColor" : "none"} size={17} />}
                        {quote.shortlisted ? "Remove shortlist" : isExpired ? "Quote expired" : quote.status !== "SENT" ? "Quote unavailable" : "Shortlist"}
                      </button>
                    </td>
                  );
                })}
              </tr>
              <tr>
                <th className="sticky left-0 z-10 border-r border-border bg-white p-3 text-xs font-medium uppercase tracking-wide text-muted-foreground">Choose vendor</th>
                {quotes.map((quote) => {
                  const isAccepting = acceptingQuoteId === quote.id;
                  const isUnavailable = quote.status !== "SENT" || isQuoteExpired(quote);
                  return (
                    <td className={`p-4 ${quote.shortlisted ? "bg-rose-50/60" : "bg-white"}`} key={quote.id}>
                      {quote.status === "ACCEPTED" ? (
                        <span className="inline-flex h-10 items-center gap-2 rounded-md bg-emerald-50 px-4 text-sm font-semibold text-emerald-700"><BadgeCheck size={17} /> Accepted</span>
                      ) : quote.status === "NOT_SELECTED" ? (
                        <span className="inline-flex h-10 items-center rounded-md bg-slate-100 px-4 text-sm font-semibold text-slate-600">Not selected</span>
                      ) : (
                        <button
                          className="inline-flex h-10 items-center gap-2 rounded-md bg-emerald-700 px-4 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60"
                          disabled={isUnavailable || Boolean(acceptingQuoteId)}
                          onClick={() => onAccept(quote)}
                          type="button"
                        >
                          {isAccepting ? <LoaderCircle className="animate-spin" size={17} /> : <BadgeCheck size={17} />}
                          {isQuoteExpired(quote) ? "Quote expired" : quote.status !== "SENT" ? "Unavailable" : "Choose this vendor"}
                        </button>
                      )}
                    </td>
                  );
                })}
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function ComparisonRow({
  label,
  quotes,
  render
}: {
  label: string;
  quotes: VendorQuote[];
  render: (quote: VendorQuote) => ReactNode;
}) {
  return (
    <tr>
      <th className="sticky left-0 z-10 border-b border-r border-border bg-white p-3 align-top text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</th>
      {quotes.map((quote) => <td className={`border-b border-border p-4 align-top ${quote.shortlisted ? "bg-rose-50/60" : "bg-white"}`} key={quote.id}>{render(quote)}</td>)}
    </tr>
  );
}

export function isQuoteExpired(quote: VendorQuote) {
  const now = new Date();
  const offset = now.getTimezoneOffset() * 60_000;
  const today = new Date(now.getTime() - offset).toISOString().slice(0, 10);
  return Boolean(quote.validUntil && quote.validUntil < today);
}

function formatMoney(value: number) {
  return new Intl.NumberFormat("en-IN").format(value);
}

function formatDate(value: string) {
  const date = new Date(`${value}T00:00:00`);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat("en-IN", { dateStyle: "medium" }).format(date);
}
