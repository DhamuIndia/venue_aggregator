"use client";

import { BadgeCheck, LoaderCircle, ShieldCheck, X } from "lucide-react";
import type { VendorQuote } from "@/features/quotes/types";

type Props = {
  quote: VendorQuote | null;
  isAccepting: boolean;
  onClose: () => void;
  onConfirm: (quote: VendorQuote) => void;
};

export function QuoteAcceptanceDialog({
  quote,
  isAccepting,
  onClose,
  onConfirm
}: Props) {
  if (!quote) return null;

  return (
    <div aria-label="Confirm vendor quotation" aria-modal="true" className="fixed inset-0 z-[60] grid place-items-center bg-black/60 p-4" role="dialog">
      <div className="w-full max-w-lg rounded-xl bg-white p-6 shadow-2xl">
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="flex items-center gap-2 text-sm font-semibold text-emerald-700"><BadgeCheck size={17} /> Confirm your vendor</p>
            <h2 className="mt-2 text-2xl font-semibold">Accept {quote.vendorName}&apos;s quote?</h2>
          </div>
          <button aria-label="Close confirmation" className="grid size-9 place-items-center rounded-md hover:bg-muted disabled:opacity-50" disabled={isAccepting} onClick={onClose} type="button"><X size={18} /></button>
        </div>

        <div className="mt-5 rounded-lg border border-emerald-200 bg-emerald-50/70 p-4">
          <p className="text-sm text-emerald-900">{quote.service}</p>
          <div className="mt-2 flex flex-wrap items-end justify-between gap-3">
            <div><p className="font-semibold">{quote.packageName}</p><p className="mt-1 text-xs text-emerald-800">Valid until {formatDate(quote.validUntil)}</p></div>
            <div className="text-right"><p className="text-xl font-semibold">INR {formatMoney(quote.totalAmount)}</p><p className="text-xs text-emerald-800">Final quoted total</p></div>
          </div>
        </div>

        <div className="mt-5 flex gap-3 rounded-md bg-muted/60 p-4 text-sm leading-6">
          <ShieldCheck className="mt-0.5 shrink-0 text-primary" size={20} />
          <p>Confirming creates the booking, shares your contact details only with this vendor, closes the requirement, and marks the other active quotes as not selected.</p>
        </div>

        <p className="mt-4 text-sm font-medium text-rose-700">This selection is final for this requirement.</p>

        <div className="mt-6 flex flex-wrap justify-end gap-2">
          <button className="h-10 rounded-md border border-border px-4 text-sm font-semibold disabled:opacity-50" disabled={isAccepting} onClick={onClose} type="button">Review again</button>
          <button className="inline-flex h-10 items-center gap-2 rounded-md bg-emerald-700 px-5 text-sm font-semibold text-white disabled:opacity-60" disabled={isAccepting} onClick={() => onConfirm(quote)} type="button">
            {isAccepting ? <LoaderCircle className="animate-spin" size={17} /> : <BadgeCheck size={17} />}
            {isAccepting ? "Creating booking..." : "Accept and confirm booking"}
          </button>
        </div>
      </div>
    </div>
  );
}

function formatMoney(value: number) {
  return new Intl.NumberFormat("en-IN").format(value);
}

function formatDate(value: string) {
  const date = new Date(`${value}T00:00:00`);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat("en-IN", { dateStyle: "medium" }).format(date);
}
