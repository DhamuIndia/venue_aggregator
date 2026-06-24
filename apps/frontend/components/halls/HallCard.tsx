"use client";

import { BadgeCheck, MapPin, Star, UsersRound } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { SaveHallButton } from "@/components/customer/SaveHallButton";
import type { HallSummary } from "@/features/halls/types";

type HallCardProps = {
  hall: HallSummary;
  initialSaved?: boolean;
  onSavedChange?: (hallId: string, isSaved: boolean) => void;
};

function formatPrice(value: number) {
  return new Intl.NumberFormat("en-IN").format(value);
}

export function HallCard({ hall, initialSaved, onSavedChange }: HallCardProps) {
  return (
    <article className="group overflow-hidden rounded-lg border border-border bg-white shadow-sm transition hover:-translate-y-0.5 hover:shadow-md">
      <div className="relative aspect-[4/3] overflow-hidden bg-muted">
        <Image
          alt={`${hall.name} venue interior`}
          className="object-cover transition duration-500 group-hover:scale-[1.03]"
          fill
          sizes="(max-width: 768px) 100vw, (max-width: 1200px) 50vw, 33vw"
          src={hall.imageUrl}
        />
        <div className="absolute left-3 top-3 flex gap-2">
          {hall.availableThisMonth && (
            <span className="rounded-full bg-white px-2.5 py-1 text-xs font-medium text-emerald-700 shadow-sm">
              Dates available
            </span>
          )}
        </div>
        <SaveHallButton className="absolute right-3 top-3 grid size-9 place-items-center rounded-full bg-white text-foreground shadow-sm hover:bg-rose-50 hover:text-rose-600 disabled:cursor-not-allowed disabled:opacity-60" hall={hall} initialSaved={initialSaved} onSavedChange={onSavedChange} />
      </div>

      <div className="p-4">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <p className="text-xs font-medium text-primary">{hall.venueType}</p>
            <Link href={`/halls/${hall.id}`}>
              <h2 className="mt-1 truncate text-lg font-semibold text-foreground hover:text-primary">
                {hall.name}
              </h2>
            </Link>
          </div>
          <div className="flex shrink-0 items-center gap-1 text-sm font-semibold">
            <Star aria-hidden="true" className="fill-amber-400 text-amber-400" size={16} />
            {hall.rating}
          </div>
        </div>

        <div className="mt-2 flex items-center gap-1.5 text-sm text-muted-foreground">
          <MapPin aria-hidden="true" size={15} />
          {hall.area}, {hall.city}
        </div>
        <div className="mt-1.5 flex items-center gap-1.5 text-sm text-muted-foreground">
          <UsersRound aria-hidden="true" size={15} />
          Up to {hall.capacity} guests
          {hall.isVerified && (
            <span className="ml-auto inline-flex items-center gap-1 text-xs font-medium text-emerald-700">
              <BadgeCheck aria-hidden="true" size={15} /> Verified
            </span>
          )}
        </div>

        <div className="mt-4 flex items-end justify-between border-t border-border pt-4">
          <div>
            <p className="text-xs text-muted-foreground">Starting from</p>
            <p className="font-semibold text-foreground">INR {formatPrice(hall.startingPrice)}</p>
          </div>
          <Link
            className="inline-flex h-9 items-center rounded-md border border-border px-3 text-sm font-medium hover:border-primary hover:text-primary"
            href={`/halls/${hall.id}`}
          >
            View details
          </Link>
        </div>
      </div>
    </article>
  );
}
