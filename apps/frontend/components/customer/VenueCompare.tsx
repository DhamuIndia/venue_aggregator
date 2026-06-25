"use client";

import { ArrowRight, BadgeCheck, Check, MapPin, Minus, Star, UsersRound } from "lucide-react";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import type { HallSummary } from "@/features/halls/types";

type VenueCompareProps = {
  halls: HallSummary[];
};

const maxCompareCount = 3;

function formatMoney(value: number) {
  return new Intl.NumberFormat("en-IN").format(value);
}

export function VenueCompare({ halls }: VenueCompareProps) {
  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  useEffect(() => {
    setSelectedIds((current) => {
      const validIds = current.filter((id) => halls.some((hall) => hall.id === id)).slice(0, maxCompareCount);
      return validIds.length ? validIds : halls.slice(0, Math.min(2, halls.length)).map((hall) => hall.id);
    });
  }, [halls]);

  const selectedHalls = useMemo(
    () => selectedIds.map((id) => halls.find((hall) => hall.id === id)).filter(Boolean) as HallSummary[],
    [halls, selectedIds]
  );

  function toggleHall(hallId: string) {
    setSelectedIds((current) => {
      if (current.includes(hallId)) return current.filter((id) => id !== hallId);
      if (current.length >= maxCompareCount) return [current[1], current[2], hallId].filter(Boolean);
      return [...current, hallId];
    });
  }

  if (halls.length < 2) {
    return (
      <div className="mt-5 rounded-lg border border-dashed border-border bg-white p-6">
        <h3 className="font-semibold">Compare venues</h3>
        <p className="mt-2 text-sm text-muted-foreground">Save at least two venues to compare capacity, price, amenities, and availability.</p>
      </div>
    );
  }

  return (
    <section className="mt-6 rounded-lg border border-border bg-white">
      <div className="border-b border-border p-5">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h3 className="text-lg font-semibold">Compare saved venues</h3>
            <p className="mt-1 text-sm text-muted-foreground">Select up to three halls to compare the details that affect your decision.</p>
          </div>
          <span className="rounded-md bg-muted px-3 py-2 text-sm font-medium">{selectedHalls.length}/{maxCompareCount} selected</span>
        </div>
        <div className="mt-4 flex gap-2 overflow-x-auto pb-1">
          {halls.map((hall) => {
            const selected = selectedIds.includes(hall.id);

            return (
              <button
                aria-pressed={selected}
                className={`inline-flex min-h-10 shrink-0 items-center gap-2 rounded-md border px-3 text-sm font-medium ${selected ? "border-primary bg-emerald-50 text-primary" : "border-border hover:border-primary"}`}
                key={hall.id}
                onClick={() => toggleHall(hall.id)}
                type="button"
              >
                <span className={`grid size-5 place-items-center rounded border ${selected ? "border-primary bg-primary text-white" : "border-border bg-white"}`}>
                  {selected && <Check size={13} />}
                </span>
                {hall.name}
              </button>
            );
          })}
        </div>
      </div>

      {selectedHalls.length < 2 ? (
        <div className="p-6 text-sm text-muted-foreground">Choose one more venue to start comparing.</div>
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-[760px] w-full border-collapse text-left text-sm">
            <thead>
              <tr className="border-b border-border">
                <th className="w-44 bg-muted/60 px-4 py-4 font-semibold">Criteria</th>
                {selectedHalls.map((hall) => (
                  <th className="min-w-56 px-4 py-4 align-top" key={hall.id}>
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <p className="font-semibold">{hall.name}</p>
                        <p className="mt-1 flex items-center gap-1.5 text-xs font-normal text-muted-foreground"><MapPin size={13} /> {hall.area}, {hall.city}</p>
                      </div>
                      {hall.isVerified && <BadgeCheck className="shrink-0 text-emerald-700" size={17} />}
                    </div>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              <CompareRow label="Capacity" halls={selectedHalls} render={(hall) => <span className="inline-flex items-center gap-1.5"><UsersRound size={15} /> {hall.capacity} guests</span>} />
              <CompareRow label="Starting price" halls={selectedHalls} render={(hall) => `INR ${formatMoney(hall.startingPrice)}`} />
              <CompareRow label="Rating" halls={selectedHalls} render={(hall) => <span className="inline-flex items-center gap-1.5"><Star className="fill-amber-400 text-amber-400" size={15} /> {hall.rating} ({hall.reviewCount})</span>} />
              <CompareRow label="Venue type" halls={selectedHalls} render={(hall) => hall.venueType} />
              <CompareRow label="Availability" halls={selectedHalls} render={(hall) => hall.availableThisMonth ? <span className="text-emerald-700">Dates available</span> : <span className="text-muted-foreground">Limited dates</span>} />
              <tr className="border-t border-border">
                <th className="bg-muted/60 px-4 py-4 align-top font-semibold">Amenities</th>
                {selectedHalls.map((hall) => (
                  <td className="px-4 py-4 align-top" key={hall.id}>
                    <div className="flex flex-wrap gap-1.5">
                      {hall.amenities.slice(0, 5).map((amenity) => <span className="rounded-full bg-muted px-2 py-1 text-xs" key={amenity}>{amenity}</span>)}
                    </div>
                  </td>
                ))}
              </tr>
              <tr className="border-t border-border">
                <th className="bg-muted/60 px-4 py-4 align-top font-semibold">Next step</th>
                {selectedHalls.map((hall) => (
                  <td className="px-4 py-4 align-top" key={hall.id}>
                    <div className="flex flex-wrap gap-2">
                      <Link className="inline-flex h-9 items-center gap-2 rounded-md border border-border px-3 text-sm font-semibold hover:border-primary hover:text-primary" href={`/halls/${hall.id}`}>Details <ArrowRight size={15} /></Link>
                      <Link className="inline-flex h-9 items-center gap-2 rounded-md bg-primary px-3 text-sm font-semibold text-white" href={`/halls/${hall.id}#enquiry`}>Enquire</Link>
                    </div>
                  </td>
                ))}
              </tr>
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function CompareRow({
  halls,
  label,
  render
}: {
  halls: HallSummary[];
  label: string;
  render: (hall: HallSummary) => React.ReactNode;
}) {
  return (
    <tr className="border-t border-border">
      <th className="bg-muted/60 px-4 py-4 align-top font-semibold">{label}</th>
      {halls.map((hall) => (
        <td className="px-4 py-4 align-top" key={hall.id}>
          {render(hall) || <span className="inline-flex items-center gap-1 text-muted-foreground"><Minus size={14} /> Not available</span>}
        </td>
      ))}
    </tr>
  );
}
