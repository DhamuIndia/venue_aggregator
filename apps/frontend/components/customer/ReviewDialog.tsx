"use client";

import { BadgeCheck, Star, X } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";

type ReviewDialogProps = {
  open: boolean;
  venueName: string;
  onClose: () => void;
  onSubmitted: () => void;
};

export function ReviewDialog({ open, venueName, onClose, onSubmitted }: ReviewDialogProps) {
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState("");

  useEffect(() => {
    if (!open) return;
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === "Escape") onClose();
    }
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [onClose, open]);

  if (!open) return null;

  function submitReview(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!rating || comment.trim().length < 10) return;
    onSubmitted();
    setRating(0);
    setComment("");
  }

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4" onMouseDown={onClose}>
      <div
        aria-labelledby="review-title"
        aria-modal="true"
        className="w-full max-w-lg rounded-lg bg-white p-5 shadow-xl sm:p-6"
        onMouseDown={(event) => event.stopPropagation()}
        role="dialog"
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="flex items-center gap-1.5 text-sm font-medium text-emerald-700"><BadgeCheck size={17} /> Verified service</div>
            <h2 className="mt-2 text-xl font-semibold" id="review-title">Review {venueName}</h2>
            <p className="mt-1 text-sm text-muted-foreground">Your review will be linked to the completed booking.</p>
          </div>
          <button aria-label="Close review form" className="grid size-9 place-items-center rounded-md text-muted-foreground hover:bg-muted" onClick={onClose} type="button"><X size={19} /></button>
        </div>

        <form className="mt-6 grid gap-5" onSubmit={submitReview}>
          <fieldset>
            <legend className="text-sm font-medium">Overall rating</legend>
            <div className="mt-3 flex gap-2">
              {[1, 2, 3, 4, 5].map((value) => (
                <button
                  aria-label={`${value} star${value === 1 ? "" : "s"}`}
                  className={`grid size-11 place-items-center rounded-md border ${value <= rating ? "border-amber-400 bg-amber-50 text-amber-500" : "border-border text-muted-foreground hover:border-amber-400"}`}
                  key={value}
                  onClick={() => setRating(value)}
                  type="button"
                >
                  <Star fill={value <= rating ? "currentColor" : "none"} size={21} />
                </button>
              ))}
            </div>
          </fieldset>
          <label className="text-sm font-medium">Your experience<textarea className="mt-2 min-h-28 w-full resize-y rounded-md border border-border p-3 font-normal leading-6 outline-none focus:border-primary" maxLength={500} onChange={(event) => setComment(event.target.value)} placeholder="Tell others about the venue, service, and communication." value={comment} /></label>
          <div className="flex items-center justify-between gap-4 text-xs text-muted-foreground"><span>Minimum 10 characters</span><span>{comment.length}/500</span></div>
          <button className="h-11 rounded-md bg-primary text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50" disabled={!rating || comment.trim().length < 10} type="submit">Submit verified review</button>
        </form>
      </div>
    </div>
  );
}
