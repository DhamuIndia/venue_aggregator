"use client";

import { AlertTriangle, X } from "lucide-react";
import { useState, type FormEvent } from "react";

type RejectionDialogProps = {
  subject: string;
  onClose: () => void;
  onReject: (reason: string) => void;
};

export function RejectionDialog({ subject, onClose, onReject }: RejectionDialogProps) {
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (reason.trim().length < 10) {
      setError("Add a clear reason with at least 10 characters.");
      return;
    }
    onReject(reason.trim());
  }

  return (
    <div aria-labelledby="rejection-title" aria-modal="true" className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4" role="dialog">
      <form className="w-full max-w-md rounded-lg bg-white p-5 shadow-xl sm:p-6" onSubmit={submit}>
        <div className="flex items-start gap-3">
          <span className="grid size-10 shrink-0 place-items-center rounded-md bg-rose-50 text-rose-700"><AlertTriangle size={20} /></span>
          <div className="min-w-0 flex-1"><h2 className="text-lg font-semibold" id="rejection-title">Reject application</h2><p className="mt-1 text-sm text-muted-foreground">The reason will be shared with {subject}.</p></div>
          <button aria-label="Close rejection form" className="grid size-9 shrink-0 place-items-center rounded-md text-muted-foreground hover:bg-muted" onClick={onClose} type="button"><X size={18} /></button>
        </div>
        <label className="mt-6 block text-sm font-medium">Reason<textarea autoFocus className="mt-2 min-h-28 w-full resize-y rounded-md border border-border p-3 font-normal leading-6 outline-none focus:border-primary" onChange={(event) => { setReason(event.target.value); setError(""); }} placeholder="Explain what must be corrected before resubmission." value={reason} /></label>
        {error && <p className="mt-3 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{error}</p>}
        <div className="mt-6 flex justify-end gap-2"><button className="h-10 rounded-md border border-border px-4 text-sm font-medium" onClick={onClose} type="button">Cancel</button><button className="h-10 rounded-md bg-rose-600 px-4 text-sm font-semibold text-white" type="submit">Reject application</button></div>
      </form>
    </div>
  );
}
