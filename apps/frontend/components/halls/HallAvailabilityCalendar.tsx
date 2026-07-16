"use client";

import { CalendarDays, ChevronLeft, ChevronRight, LoaderCircle } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { getPublicHallAvailability, slotStatusForDate, type PublicHallUnavailableSlot } from "@/features/halls/availability-client";
import { HALL_SLOT_SELECTION_EVENT, emitHallSlotSelection, type HallSlotSelectionDetail } from "@/features/halls/slot-selection-events";
import { HALL_SLOT_DETAILS, formatDisplayDate, toDateInputValue, type HallBaseSlot } from "@/features/halls/slot-model";

type HallAvailabilityCalendarProps = {
  hallId: string;
};

const statusStyle = {
  AVAILABLE: "border-emerald-200 bg-emerald-50 text-emerald-800",
  BOOKED: "border-amber-200 bg-amber-50 text-amber-800",
  BLOCKED: "border-rose-200 bg-rose-50 text-rose-700"
};

export function HallAvailabilityCalendar({ hallId }: HallAvailabilityCalendarProps) {
  const [unavailableSlots, setUnavailableSlots] = useState<PublicHallUnavailableSlot[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isFullCalendarOpen, setIsFullCalendarOpen] = useState(false);
  const [visibleMonth, setVisibleMonth] = useState(() => startOfMonth(new Date()));
  const [selectedDate, setSelectedDate] = useState("");
  const [selectedSlot, setSelectedSlot] = useState<HallBaseSlot | "">("");

  useEffect(() => {
    let isCurrent = true;

    async function loadAvailability() {
      setIsLoading(true);
      const slots = await getPublicHallAvailability(hallId);
      if (!isCurrent) return;
      setUnavailableSlots(slots);
      setIsLoading(false);
    }

    loadAvailability();

    return () => {
      isCurrent = false;
    };
  }, [hallId]);

  useEffect(() => {
    function handleSlotSelection(event: Event) {
      const detail = (event as CustomEvent<HallSlotSelectionDetail>).detail;
      if (!detail || detail.hallId !== hallId) return;
      setSelectedDate(detail.date);
      setSelectedSlot(detail.slot);
    }

    window.addEventListener(HALL_SLOT_SELECTION_EVENT, handleSlotSelection);
    return () => window.removeEventListener(HALL_SLOT_SELECTION_EVENT, handleSlotSelection);
  }, [hallId]);

  const monthDates = useMemo(() => datesForMonth(visibleMonth), [visibleMonth]);
  const compactDates = useMemo(() => {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return Array.from({ length: 14 }, (_, index) => {
      const date = new Date(today);
      date.setDate(today.getDate() + index);
      return toDateInputValue(date);
    });
  }, []);
  const visibleDates = isFullCalendarOpen ? monthDates : compactDates;

  function moveMonth(direction: -1 | 1) {
    setVisibleMonth((current) => new Date(current.getFullYear(), current.getMonth() + direction, 1));
    setIsFullCalendarOpen(true);
  }

  return (
    <section className="border-b border-border py-7" id="availability-calendar">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="text-xl font-semibold">Availability</h2>
          <p className="mt-1 text-sm text-muted-foreground">Morning, afternoon, and evening slot status.</p>
        </div>
        <div className="flex items-center gap-2">
          {isFullCalendarOpen && (
            <>
              <button aria-label="Previous month" className="grid size-9 place-items-center rounded-md border border-border bg-white" onClick={() => moveMonth(-1)} type="button">
                <ChevronLeft size={17} />
              </button>
              <button aria-label="Next month" className="grid size-9 place-items-center rounded-md border border-border bg-white" onClick={() => moveMonth(1)} type="button">
                <ChevronRight size={17} />
              </button>
            </>
          )}
          <button className="inline-flex h-9 items-center gap-2 rounded-md border border-border bg-white px-3 text-sm font-medium text-primary" onClick={() => setIsFullCalendarOpen((current) => !current)} type="button">
            <CalendarDays size={16} />
            {isFullCalendarOpen ? "Show next 14 days" : "View full calendar"}
          </button>
        </div>
      </div>

      <div className="mt-5 rounded-lg border border-border bg-white p-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h3 className="font-semibold">
            {isFullCalendarOpen
              ? new Intl.DateTimeFormat("en-IN", { month: "long", year: "numeric" }).format(visibleMonth)
              : "Next 14 days"}
          </h3>
          <div className="flex flex-wrap gap-3 text-xs text-muted-foreground">
            <span className="inline-flex items-center gap-1.5"><i className="size-2.5 rounded-full bg-emerald-200" /> Available</span>
            <span className="inline-flex items-center gap-1.5"><i className="size-2.5 rounded-full bg-amber-200" /> Booked</span>
            <span className="inline-flex items-center gap-1.5"><i className="size-2.5 rounded-full bg-rose-200" /> Blocked</span>
          </div>
        </div>

        {isLoading ? (
          <div className="mt-5 grid min-h-48 place-items-center rounded-md bg-muted/40 text-sm text-muted-foreground">
            <span className="inline-flex items-center gap-2"><LoaderCircle className="animate-spin" size={17} /> Loading availability</span>
          </div>
        ) : (
          <>
            {isFullCalendarOpen && (
              <div className="mt-5 grid grid-cols-7 text-center text-xs font-medium text-muted-foreground">
                {["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"].map((day) => <span className="py-2" key={day}>{day}</span>)}
              </div>
            )}
            <div className={`mt-3 grid gap-2 ${isFullCalendarOpen ? "grid-cols-1 sm:grid-cols-2 xl:grid-cols-7" : "grid-cols-1 sm:grid-cols-2 lg:grid-cols-4"}`}>
              {isFullCalendarOpen && Array.from({ length: firstDayOffset(visibleMonth) }, (_, index) => <span className="hidden xl:block" key={`blank-${index}`} />)}
              {visibleDates.map((date) => <AvailabilityDayCard date={date} hallId={hallId} key={date} selectedDate={selectedDate} selectedSlot={selectedSlot} unavailableSlots={unavailableSlots} />)}
            </div>
          </>
        )}
      </div>
    </section>
  );
}

function AvailabilityDayCard({ date, hallId, selectedDate, selectedSlot, unavailableSlots }: { date: string; hallId: string; selectedDate: string; selectedSlot: HallBaseSlot | ""; unavailableSlots: PublicHallUnavailableSlot[] }) {
  const statuses = slotStatusForDate(date, unavailableSlots);
  const isToday = date === toDateInputValue(new Date());
  const isSelectedDay = selectedDate === date;

  function selectSlot(slot: HallBaseSlot) {
    emitHallSlotSelection({ hallId, date, slot });
    document.getElementById("enquiry")?.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  return (
    <article className={`rounded-md border p-3 ${isSelectedDay ? "border-primary ring-2 ring-primary/15" : isToday ? "border-primary" : "border-border"}`}>
      <div className="flex items-center justify-between gap-2">
        <div>
          <p className="text-sm font-semibold">{formatDisplayDate(date)}</p>
          <p className="mt-0.5 text-xs text-muted-foreground">{new Intl.DateTimeFormat("en-IN", { weekday: "short" }).format(new Date(`${date}T00:00:00`))}</p>
        </div>
        {isToday && <span className="rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-primary">Today</span>}
      </div>
      <div className="mt-3 grid gap-1">
        {statuses.map((item) => <SlotStatusChip key={item.slot} onSelect={() => selectSlot(item.slot)} selected={isSelectedDay && selectedSlot === item.slot} slot={item.slot} status={item.status} />)}
      </div>
    </article>
  );
}

function SlotStatusChip({ onSelect, selected, slot, status }: { onSelect: () => void; selected: boolean; slot: HallBaseSlot; status: "AVAILABLE" | "BOOKED" | "BLOCKED" }) {
  const detail = HALL_SLOT_DETAILS[slot];
  const isAvailable = status === "AVAILABLE";

  return (
    <button
      aria-label={`${detail.label} ${detail.shortTime} ${isAvailable ? "available" : status.toLowerCase()}`}
      aria-pressed={selected}
      className={`grid min-h-[52px] content-center gap-1 rounded-md border px-2 py-1.5 text-left outline-none transition ${statusStyle[status]} ${selected ? "border-primary ring-2 ring-primary/25" : ""} ${isAvailable ? "cursor-pointer hover:border-primary focus-visible:ring-2 focus-visible:ring-primary/25" : "cursor-not-allowed opacity-80"}`}
      disabled={!isAvailable}
      onClick={onSelect}
      type="button"
    >
      <span className="truncate text-xs font-semibold leading-tight">{detail.label}</span>
      <span className="whitespace-nowrap text-[11px] font-medium leading-none opacity-80">{detail.shortTime}</span>
    </button>
  );
}

function startOfMonth(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

function firstDayOffset(date: Date) {
  return startOfMonth(date).getDay();
}

function datesForMonth(date: Date) {
  const year = date.getFullYear();
  const month = date.getMonth();
  const days = new Date(year, month + 1, 0).getDate();
  return Array.from({ length: days }, (_, index) => toDateInputValue(new Date(year, month, index + 1)));
}
