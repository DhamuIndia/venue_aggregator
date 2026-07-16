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
import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import { createEnquiry } from "@/features/enquiries/enquiry-client";
import { getPublicHallAvailability, type PublicHallUnavailableSlot } from "@/features/halls/availability-client";
import { HALL_SLOT_SELECTION_EVENT, type HallSlotSelectionDetail } from "@/features/halls/slot-selection-events";
import { HALL_SLOT_COMBINATIONS, buildSlotRequests, formatDisplayDate, formatSlotRequests, representativeSlot, slotConflicts, toDateInputValue, type HallSlotCombinationId } from "@/features/halls/slot-model";
import type { HallSummary } from "@/features/halls/types";
import { formatGuestCount } from "@/lib/display-format";

type EnquiryPanelProps = {
  hall: HallSummary;
};

type AvailabilityState = "idle" | "available" | "unavailable";

export function EnquiryPanel({ hall }: EnquiryPanelProps) {
  const { getValidAccessToken, user } = useAuth();
  const router = useRouter();
  const [eventDate, setEventDate] = useState("");
  const [eventType, setEventType] = useState("");
  const [guestCount, setGuestCount] = useState("");
  const [slotCombination, setSlotCombination] = useState<HallSlotCombinationId>("FULL_DAY");
  const [notes, setNotes] = useState("");
  const [availability, setAvailability] = useState<AvailabilityState>("idle");
  const [unavailableSlots, setUnavailableSlots] = useState<PublicHallUnavailableSlot[]>([]);
  const [isAvailabilityLoading, setIsAvailabilityLoading] = useState(true);
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const todayValue = toDateInputValue(new Date());
  const suggestedDates = useMemo(() => [0, 1, 2].map((offset) => {
    const date = new Date(`${todayValue}T00:00:00`);
    date.setDate(date.getDate() + offset);
    return toDateInputValue(date);
  }), [todayValue]);
  const selectedSlotRequests = eventDate ? buildSlotRequests(eventDate, slotCombination) : [];

  useEffect(() => {
    let isCurrent = true;

    async function loadAvailability() {
      setIsAvailabilityLoading(true);
      const slots = await getPublicHallAvailability(hall.id);
      if (!isCurrent) return;
      setUnavailableSlots(slots);
      setIsAvailabilityLoading(false);
    }

    loadAvailability();

    return () => {
      isCurrent = false;
    };
  }, [hall.id]);

  useEffect(() => {
    function handleSlotSelection(event: Event) {
      const detail = (event as CustomEvent<HallSlotSelectionDetail>).detail;
      if (!detail || detail.hallId !== hall.id) return;

      setEventDate(detail.date);
      setSlotCombination(detail.slot);
      setAvailability("available");
      setError("");
    }

    window.addEventListener(HALL_SLOT_SELECTION_EVENT, handleSlotSelection);
    return () => window.removeEventListener(HALL_SLOT_SELECTION_EVENT, handleSlotSelection);
  }, [hall.id]);

  function validateCoreFields() {
    if (!eventDate) return "Choose an event date.";
    if (!guestCount || Number(guestCount) < 1) return "Enter the expected guest count.";
    if (Number(guestCount) > hall.capacity) return `This venue supports up to ${formatGuestCount(hall.capacity)} guests.`;
    return "";
  }

  function checkAvailability() {
    const message = validateCoreFields();
    if (message) {
      setError(message);
      setAvailability("idle");
      return;
    }
    if (isAvailabilityLoading) {
      setError("Checking slot availability. Please wait a moment.");
      setAvailability("idle");
      return;
    }

    setError("");
    setAvailability(hasUnavailableRequest(selectedSlotRequests, unavailableSlots) ? "unavailable" : "available");
  }

  function updateDate(value: string) {
    setEventDate(value);
    setAvailability("idle");
    setError("");
  }

  function updateSlotCombination(value: HallSlotCombinationId) {
    setSlotCombination(value);
    setAvailability("idle");
  }

  async function submitEnquiry(event: FormEvent<HTMLFormElement>) {
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
    if (isAvailabilityLoading) {
      setError("Checking slot availability. Please wait a moment.");
      return;
    }
    if (hasUnavailableRequest(selectedSlotRequests, unavailableSlots)) {
      setAvailability("unavailable");
      setError("One or more selected slots are blocked or booked. Try another date or combination.");
      return;
    }

    try {
      setIsSubmitting(true);
      setAvailability("available");
      const token = await getValidAccessToken();
      if (!token) {
        setError("Your session expired. Sign in again to send this enquiry.");
        router.push(`/auth/login?next=/halls/${hall.id}`);
        return;
      }
      const enquiry = await createEnquiry({
        hallId: hall.id,
        hallName: hall.name,
        customerId: user.id,
        eventDate,
        eventType,
        guestCount: Number(guestCount),
        slot: representativeSlot(selectedSlotRequests),
        slotRequests: selectedSlotRequests,
        notes: notes.trim() || undefined
      }, token);
      router.push(`/enquiries/confirmation/${enquiry.id}`);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not send enquiry. Please try again.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <aside className="h-fit rounded-lg border border-border bg-white p-5 shadow-sm lg:sticky lg:top-24" id="enquiry">
      <p className="text-sm text-muted-foreground">Starting from</p>
      <p className="mt-1 text-2xl font-semibold">INR {new Intl.NumberFormat("en-IN").format(hall.startingPrice)}</p>
      <p className="mt-1 text-xs text-muted-foreground">Final price depends on date, slot, and package.</p>

      <form className="mt-5 grid gap-4" onSubmit={submitEnquiry}>
        <div>
          <label className="text-sm font-medium">Event date<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" min={todayValue} onChange={(event) => updateDate(event.target.value)} required type="date" value={eventDate} /></label>
          <div className="mt-2 flex gap-2 overflow-x-auto" aria-label="Suggested available dates">
            {suggestedDates.map((date) => (
              <button aria-pressed={eventDate === date} className={`shrink-0 rounded-md border px-3 py-1.5 text-xs font-medium ${eventDate === date ? "border-primary bg-emerald-50 text-primary" : "border-border text-muted-foreground hover:border-primary"}`} key={date} onClick={() => updateDate(date)} type="button">{formatDisplayDate(date)}</button>
            ))}
          </div>
        </div>

        <fieldset>
          <legend className="text-sm font-medium">Preferred slot</legend>
          <div className="mt-2 grid gap-2">
            {HALL_SLOT_COMBINATIONS.map((option) => (
              <button aria-pressed={slotCombination === option.id} className={`min-h-14 rounded-md border px-3 py-2 text-left text-xs ${slotCombination === option.id ? "border-primary bg-emerald-50 text-primary" : "border-border text-muted-foreground hover:border-primary hover:text-foreground"}`} key={option.id} onClick={() => updateSlotCombination(option.id)} type="button">
                <span className="block font-semibold">{option.label}</span>
                <span className="mt-0.5 block">{option.description}</span>
              </button>
            ))}
          </div>
          {selectedSlotRequests.length > 0 && <p className="mt-2 text-xs text-muted-foreground">Selected: {formatSlotRequests(selectedSlotRequests)}</p>}
        </fieldset>

        <label className="text-sm font-medium">Event type<select className="mt-2 h-11 w-full rounded-md border border-border bg-white px-3 font-normal outline-none focus:border-primary" onChange={(event) => setEventType(event.target.value)} required value={eventType}><option value="">Select event</option><option>Wedding</option><option>Reception</option><option>Engagement</option><option>Birthday celebration</option><option>Corporate event</option><option>Other</option></select></label>
        <label className="text-sm font-medium">Guest count<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" max={hall.capacity} min="1" onChange={(event) => setGuestCount(event.target.value)} placeholder={`Up to ${formatGuestCount(hall.capacity)}`} required type="number" value={guestCount} /></label>
        <label className="text-sm font-medium">Message <span className="font-normal text-muted-foreground">(optional)</span><textarea className="mt-2 min-h-20 w-full resize-y rounded-md border border-border p-3 font-normal outline-none focus:border-primary" maxLength={300} onChange={(event) => setNotes(event.target.value)} placeholder="Package, catering, or timing requirements" value={notes} /></label>

        {error && <p className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{error}</p>}
        {availability === "available" && <p className="flex items-start gap-2 rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-700"><CalendarCheck2 className="mt-0.5 shrink-0" size={17} /><span>Selected slot combination is available for enquiry.</span></p>}
        {availability === "unavailable" && <p className="flex items-start gap-2 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-700"><CalendarX2 className="mt-0.5 shrink-0" size={17} /><span>One or more selected slots are blocked or booked. Try another date or combination.</span></p>}

        <button className="h-11 rounded-md border border-primary text-sm font-semibold text-primary hover:bg-emerald-50 disabled:opacity-60" disabled={isAvailabilityLoading} onClick={checkAvailability} type="button">Check availability</button>
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

function hasUnavailableRequest(requests: ReturnType<typeof buildSlotRequests>, unavailableSlots: PublicHallUnavailableSlot[]) {
  return requests.some((request) => unavailableSlots.some((unavailable) => unavailable.date === request.date && slotConflicts(unavailable.slot, request.slot)));
}
