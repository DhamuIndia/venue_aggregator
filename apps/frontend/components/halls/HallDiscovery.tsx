"use client";

import {
  CalendarDays,
  LoaderCircle,
  Map,
  MapPin,
  RotateCcw,
  Search,
  SlidersHorizontal,
  UsersRound
} from "lucide-react";
import { useEffect, useState } from "react";
import { searchPublicHalls, type HallSort } from "@/features/halls/hall-client";
import type { HallSummary, VenueType } from "@/features/halls/types";
import { HallCard } from "./HallCard";

type TypeFilter = "All venues" | VenueType;

const venueTypes: TypeFilter[] = [
  "All venues",
  "Marriage Hall",
  "Banquet Hall",
  "Mini Hall"
];

export function HallDiscovery() {
  const [location, setLocation] = useState("");
  const [date, setDate] = useState("");
  const [guests, setGuests] = useState(0);
  const [venueType, setVenueType] = useState<TypeFilter>("All venues");
  const [sort, setSort] = useState<HallSort>("recommended");
  const [refreshKey, setRefreshKey] = useState(0);
  const [results, setResults] = useState<HallSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isCurrent = true;

    async function loadHalls() {
      setIsLoading(true);
      setError("");

      try {
        const response = await searchPublicHalls({
          q: location.trim() || undefined,
          eventDate: date || undefined,
          minCapacity: guests || undefined,
          venueType: venueType === "All venues" ? undefined : venueType,
          sort
        });

        if (!isCurrent) return;
        setResults(response.halls);
        setTotal(response.total);
      } catch {
        if (!isCurrent) return;
        setResults([]);
        setTotal(0);
        setError("Could not load venues. Please try again.");
      } finally {
        if (isCurrent) setIsLoading(false);
      }
    }

    loadHalls();

    return () => {
      isCurrent = false;
    };
  }, [date, guests, location, refreshKey, sort, venueType]);

  function resetFilters() {
    setLocation("");
    setDate("");
    setGuests(0);
    setVenueType("All venues");
    setSort("recommended");
    setRefreshKey((value) => value + 1);
  }

  return (
    <>
      <section className="border-b border-border bg-white">
        <div className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6 sm:py-10">
          <div className="max-w-3xl">
            <p className="text-sm font-semibold text-primary">Find the right venue</p>
            <h1 className="mt-2 text-3xl font-semibold text-foreground sm:text-4xl">
              Halls for every kind of celebration
            </h1>
            <p className="mt-3 text-base text-muted-foreground">
              Compare verified halls, pricing, capacity, and available dates in one place.
            </p>
          </div>

          <div className="mt-7 grid overflow-hidden rounded-lg border border-border bg-white shadow-sm lg:grid-cols-[1.4fr_1fr_0.8fr_auto]">
            <label className="flex min-h-16 items-center gap-3 border-b border-border px-4 lg:border-b-0 lg:border-r">
              <MapPin aria-hidden="true" className="shrink-0 text-primary" size={20} />
              <span className="min-w-0 flex-1">
                <span className="block text-xs font-medium text-muted-foreground">Location</span>
                <input
                  className="mt-1 w-full bg-transparent text-sm font-medium outline-none placeholder:font-normal placeholder:text-muted-foreground"
                  onChange={(event) => setLocation(event.target.value)}
                  placeholder="City, area, or venue"
                  value={location}
                />
              </span>
            </label>
            <label className="flex min-h-16 items-center gap-3 border-b border-border px-4 lg:border-b-0 lg:border-r">
              <CalendarDays aria-hidden="true" className="shrink-0 text-primary" size={20} />
              <span className="min-w-0 flex-1">
                <span className="block text-xs font-medium text-muted-foreground">Event date</span>
                <input
                  className="mt-1 w-full bg-transparent text-sm font-medium outline-none"
                  min="2026-06-22"
                  onChange={(event) => setDate(event.target.value)}
                  type="date"
                  value={date}
                />
              </span>
            </label>
            <label className="flex min-h-16 items-center gap-3 border-b border-border px-4 lg:border-b-0 lg:border-r">
              <UsersRound aria-hidden="true" className="shrink-0 text-primary" size={20} />
              <span className="min-w-0 flex-1">
                <span className="block text-xs font-medium text-muted-foreground">Guests</span>
                <input
                  className="mt-1 w-full bg-transparent text-sm font-medium outline-none placeholder:font-normal placeholder:text-muted-foreground"
                  min="1"
                  onChange={(event) => setGuests(Number(event.target.value))}
                  placeholder="Any capacity"
                  type="number"
                  value={guests || ""}
                />
              </span>
            </label>
            <button
              className="m-2 inline-flex min-h-12 items-center justify-center gap-2 rounded-md bg-primary px-6 text-sm font-semibold text-white hover:bg-primary/90 disabled:opacity-60"
              disabled={isLoading}
              onClick={() => setRefreshKey((value) => value + 1)}
              type="button"
            >
              {isLoading ? <LoaderCircle aria-hidden="true" className="animate-spin" size={18} /> : <Search aria-hidden="true" size={18} />}
              Search
            </button>
          </div>
        </div>
      </section>

      <main className="mx-auto w-full max-w-7xl px-4 py-7 sm:px-6">
        <div className="flex gap-2 overflow-x-auto pb-2" role="group" aria-label="Venue type">
          {venueTypes.map((type) => (
            <button
              className={`shrink-0 rounded-md border px-4 py-2 text-sm font-medium transition ${
                venueType === type
                  ? "border-foreground bg-foreground text-white"
                  : "border-border bg-white text-foreground hover:border-foreground"
              }`}
              key={type}
              onClick={() => setVenueType(type)}
              type="button"
            >
              {type}
            </button>
          ))}
          <button
            className="ml-auto hidden size-10 shrink-0 place-items-center rounded-md border border-border bg-white text-muted-foreground hover:text-foreground sm:grid"
            onClick={resetFilters}
            title="Reset filters"
            type="button"
          >
            <RotateCcw aria-label="Reset filters" size={17} />
          </button>
        </div>

        <div className="mt-6 flex flex-wrap items-end justify-between gap-4">
          <div>
            <h2 className="text-xl font-semibold text-foreground">
              {total} {total === 1 ? "venue" : "venues"} found
            </h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Verified listings with transparent starting prices
            </p>
          </div>
          <div className="flex items-center gap-2">
            <label className="inline-flex h-10 items-center gap-2 rounded-md border border-border bg-white px-3 text-sm">
              <SlidersHorizontal aria-hidden="true" size={16} />
              <span className="hidden sm:inline">Sort:</span>
              <select
                aria-label="Sort venues"
                className="bg-transparent font-medium outline-none"
                onChange={(event) => setSort(event.target.value as HallSort)}
                value={sort}
              >
                <option value="recommended">Recommended</option>
                <option value="rating">Top rated</option>
                <option value="price-low">Price: low to high</option>
                <option value="capacity">Capacity: high to low</option>
              </select>
            </label>
            <button
              aria-label="Show venues on map"
              className="inline-flex h-10 items-center gap-2 rounded-md border border-border bg-white px-3 text-sm font-medium hover:border-foreground"
            >
              <Map aria-hidden="true" size={17} />
              <span className="hidden sm:inline">Map</span>
            </button>
          </div>
        </div>

        {error && (
          <p className="mt-5 rounded-md bg-rose-50 px-4 py-3 text-sm text-rose-700" role="alert">
            {error}
          </p>
        )}

        {isLoading ? (
          <div className="mt-5 grid gap-5 sm:grid-cols-2 lg:grid-cols-3" aria-label="Loading venues">
            {[1, 2, 3, 4, 5, 6].map((item) => (
              <div className="overflow-hidden rounded-lg border border-border bg-white shadow-sm" key={item}>
                <div className="aspect-[4/3] animate-pulse bg-muted" />
                <div className="space-y-3 p-4">
                  <div className="h-3 w-24 animate-pulse rounded bg-muted" />
                  <div className="h-5 w-3/4 animate-pulse rounded bg-muted" />
                  <div className="h-4 w-1/2 animate-pulse rounded bg-muted" />
                  <div className="h-10 animate-pulse rounded bg-muted" />
                </div>
              </div>
            ))}
          </div>
        ) : results.length > 0 ? (
          <div className="mt-5 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {results.map((hall) => (
              <HallCard hall={hall} key={hall.id} />
            ))}
          </div>
        ) : (
          <div className="mt-8 flex min-h-72 flex-col items-center justify-center rounded-lg border border-dashed border-border bg-white px-6 text-center">
            <Search aria-hidden="true" className="text-muted-foreground" size={30} />
            <h2 className="mt-4 text-lg font-semibold">No venues match those filters</h2>
            <p className="mt-2 max-w-md text-sm text-muted-foreground">
              Try a nearby location, reduce the guest count, or browse all venue types.
            </p>
            <button
              className="mt-5 inline-flex h-10 items-center gap-2 rounded-md border border-border px-4 text-sm font-medium hover:border-foreground"
              onClick={resetFilters}
              type="button"
            >
              <RotateCcw aria-hidden="true" size={16} /> Reset filters
            </button>
          </div>
        )}
      </main>
    </>
  );
}
