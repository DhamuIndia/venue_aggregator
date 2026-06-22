"use client";

import {
  BadgeCheck,
  CalendarCheck2,
  CalendarX2,
  LoaderCircle,
  LogIn,
  Send
} from "lucide-react";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import { createLocalEnquiry } from "@/features/enquiries/enquiry-client";
import type { EnquirySlot } from "@/features/enquiries/types";
import type { HallSummary } from "@/features/halls/types";

type EnquiryPanelProps = {
  hall: HallSummary;
};

type AvailabilityState = "idle" | "available" | "unavailable";

const slots: Array<{ value: EnquirySlot; label: string }> = [
  { value: "MORNING", label: "Morning" },
  { value: "EVENING", label: "Evening" },
  { value: "FULL_DAY", label: "Full day" }
];

export function EnquiryPanel({ hall }: EnquiryPanelProps) {
  const { user } = useAuth();
  const router = useRouter();
  const [eventDate, setEventDate] = useState("");
  const [eventType, setEventType] = useState("");
  const [guestCount, setGuestCount] = useState("");
  const [slot, setSlot] = useState<EnquirySlot>("FULL_DAY");
  const [notes, setNotes] = useState("");
  const [availability, setAvailability] = useState<AvailabilityState>("idle");
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  function validateCoreFields() {
    if (!eventDate) return "Choose an event date.";
    if (!guestCount || Number(guestCount) < 1) return "Enter the expected guest count.";
    if (Number(guestCount) > hall.capacity) return `This venue supports up to ${hall.capacity} guests.`;
    return "";
  }

  function checkAvailability() {
    const message = validateCoreFields();
    if (message) {
      setError(message);
      setAvailability("idle");
      return;
    }

    setError("");
    setAvailability(eventDate.endsWith("-15") && slot === "FULL_DAY" ? "unavailable" : "available");
  }

  function updateDate(value: string) {
    setEventDate(value);
    setAvailability("idle");
    setError("");
  }

  function updateSlot(value: EnquirySlot) {
    setSlot(value);
    setAvailability("idle");
  }

  function submitEnquiry(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!user) {
      router.push(`/auth/login?next=/halls/${hall.id}`);
      return;
    }

    const message = validateCoreFields();
    if (message || !eventType) {
      setError(message || "Select the type of event.");
      return;
    }
    if (availability !== "available") {
      setError("Check availability before sending your enquiry.");
      return;
    }

    setIsSubmitting(true);
    const enquiry = createLocalEnquiry({
      hallId: hall.id,
      hallName: hall.name,
      customerId: user.id,
      eventDate,
      eventType,
      guestCount: Number(guestCount),
      slot,
      notes: notes.trim() || undefined
    });
    router.push(`/enquiries/confirmation/${enquiry.id}`);
  }

  return (
    <aside className="h-fit rounded-lg border border-border bg-white p-5 shadow-sm lg:sticky lg:top-24">
      <p className="text-sm text-muted-foreground">Starting from</p>
      <p className="mt-1 text-2xl font-semibold">INR {new Intl.NumberFormat("en-IN").format(hall.startingPrice)}</p>
      <p className="mt-1 text-xs text-muted-foreground">Final price depends on date, slot, and package.</p>

      <form className="mt-5 grid gap-4" onSubmit={submitEnquiry}>
        <div>
          <label className="text-sm font-medium">Event date<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" min="2026-06-22" onChange={(event) => updateDate(event.target.value)} required type="date" value={eventDate} /></label>
          <div className="mt-2 flex gap-2 overflow-x-auto" aria-label="Suggested available dates">
            {[{ value: "2026-07-18", label: "Jul 18" }, { value: "2026-07-19", label: "Jul 19" }, { value: "2026-07-20", label: "Jul 20" }].map((date) => (
              <button aria-pressed={eventDate === date.value} className={`shrink-0 rounded-md border px-3 py-1.5 text-xs font-medium ${eventDate === date.value ? "border-primary bg-emerald-50 text-primary" : "border-border text-muted-foreground hover:border-primary"}`} key={date.value} onClick={() => updateDate(date.value)} type="button">{date.label}</button>
            ))}
          </div>
        </div>

        <fieldset>
          <legend className="text-sm font-medium">Preferred slot</legend>
          <div className="mt-2 grid grid-cols-3 gap-1 rounded-md bg-muted p-1">
            {slots.map((option) => (
              <button aria-pressed={slot === option.value} className={`min-h-10 rounded-md px-2 text-xs font-medium ${slot === option.value ? "bg-white text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"}`} key={option.value} onClick={() => updateSlot(option.value)} type="button">{option.label}</button>
            ))}
          </div>
        </fieldset>

        <label className="text-sm font-medium">Event type<select className="mt-2 h-11 w-full rounded-md border border-border bg-white px-3 font-normal outline-none focus:border-primary" onChange={(event) => setEventType(event.target.value)} required value={eventType}><option value="">Select event</option><option>Wedding</option><option>Reception</option><option>Engagement</option><option>Birthday celebration</option><option>Corporate event</option><option>Other</option></select></label>
        <label className="text-sm font-medium">Guest count<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" max={hall.capacity} min="1" onChange={(event) => { setGuestCount(event.target.value); setAvailability("idle"); }} placeholder={`Up to ${hall.capacity}`} required type="number" value={guestCount} /></label>
        <label className="text-sm font-medium">Message <span className="font-normal text-muted-foreground">(optional)</span><textarea className="mt-2 min-h-20 w-full resize-y rounded-md border border-border p-3 font-normal outline-none focus:border-primary" maxLength={300} onChange={(event) => setNotes(event.target.value)} placeholder="Package, catering, or timing requirements" value={notes} /></label>

        {error && <p className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{error}</p>}
        {availability === "available" && <p className="flex items-start gap-2 rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-700"><CalendarCheck2 className="mt-0.5 shrink-0" size={17} /><span>This slot is available for enquiry.</span></p>}
        {availability === "unavailable" && <p className="flex items-start gap-2 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-700"><CalendarX2 className="mt-0.5 shrink-0" size={17} /><span>This slot is blocked. Try another date or slot.</span></p>}

        <button className="h-11 rounded-md border border-primary text-sm font-semibold text-primary hover:bg-emerald-50" onClick={checkAvailability} type="button">Check availability</button>
        <button className="inline-flex h-11 items-center justify-center gap-2 rounded-md bg-primary text-sm font-semibold text-white disabled:opacity-60" disabled={isSubmitting} formNoValidate={!user} type="submit">
          {isSubmitting ? <LoaderCircle className="animate-spin" size={18} /> : user ? <Send size={17} /> : <LogIn size={17} />}
          {user ? "Send enquiry" : "Log in to enquire"}
        </button>
      </form>

      {user && <p className="mt-4 flex items-center justify-center gap-1.5 text-center text-xs text-muted-foreground"><BadgeCheck className="text-emerald-700" size={15} /> Enquiring as {user.fullName}</p>}
      <p className="mt-2 text-center text-xs text-muted-foreground">No payment required to send an enquiry.</p>
    </aside>
  );
}
