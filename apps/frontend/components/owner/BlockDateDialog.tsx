"use client";

import { CalendarX2, LoaderCircle, X } from "lucide-react";
import { useState, type FormEvent } from "react";
import type { EnquirySlot } from "@/features/enquiries/types";
import type { BlockDatePayload } from "@/features/owner/availability-client";

export function BlockDateDialog({ open, onClose, onAdd }: { open: boolean; onClose: () => void; onAdd: (date: BlockDatePayload) => Promise<void> | void }) {
  const [date, setDate] = useState("");
  const [slot, setSlot] = useState<EnquirySlot>("FULL_DAY");
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!open) return null;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!date || !reason.trim()) return;

    try {
      setError("");
      setIsSubmitting(true);
      await onAdd({ date, slot, reason: reason.trim() });
      setDate("");
      setSlot("FULL_DAY");
      setReason("");
      onClose();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not block this date.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4" onMouseDown={isSubmitting ? undefined : onClose}>
      <div aria-labelledby="block-date-title" aria-modal="true" className="w-full max-w-md rounded-lg bg-white p-5 shadow-xl" onMouseDown={(event) => event.stopPropagation()} role="dialog">
        <div className="flex items-start justify-between gap-4"><div><span className="grid size-10 place-items-center rounded-md bg-rose-50 text-rose-700"><CalendarX2 size={20} /></span><h2 className="mt-3 text-xl font-semibold" id="block-date-title">Block a date</h2><p className="mt-1 text-sm text-muted-foreground">The selected slot will not appear as available.</p></div><button aria-label="Close blocked date form" className="grid size-9 place-items-center rounded-md text-muted-foreground hover:bg-muted" onClick={onClose}><X size={18} /></button></div>
        <form className="mt-6 grid gap-4" onSubmit={submit}><label className="text-sm font-medium">Date<input className="mt-2 h-11 w-full rounded-md border border-border px-3 outline-none focus:border-primary" min="2026-06-22" onChange={(event) => setDate(event.target.value)} required type="date" value={date} /></label><label className="text-sm font-medium">Slot<select className="mt-2 h-11 w-full rounded-md border border-border bg-white px-3 outline-none focus:border-primary" onChange={(event) => setSlot(event.target.value as EnquirySlot)} value={slot}><option value="MORNING">Morning</option><option value="EVENING">Evening</option><option value="FULL_DAY">Full day</option></select></label><label className="text-sm font-medium">Reason<input className="mt-2 h-11 w-full rounded-md border border-border px-3 outline-none focus:border-primary" onChange={(event) => setReason(event.target.value)} placeholder="Maintenance, private event, or holiday" required value={reason} /></label>{error && <p className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{error}</p>}<button className="mt-1 inline-flex h-11 items-center justify-center gap-2 rounded-md bg-rose-600 text-sm font-semibold text-white disabled:opacity-60" disabled={isSubmitting} type="submit">{isSubmitting && <LoaderCircle className="animate-spin" size={17} />} Block date</button></form>
      </div>
    </div>
  );
}
