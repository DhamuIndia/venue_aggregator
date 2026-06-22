"use client";

import { MapPin, RotateCcw, Search, SlidersHorizontal } from "lucide-react";
import Link from "next/link";
import { useMemo, useState } from "react";
import { categoryLabels, vendors } from "@/features/vendors/mock-data";
import type { VendorCategory } from "@/features/vendors/types";
import { VendorCard } from "./VendorCard";

type CategoryFilter = "ALL" | VendorCategory;
type SortOption = "recommended" | "rating" | "price-low";

const categories: CategoryFilter[] = ["ALL", "CATERING", "DECORATION", "PHOTOGRAPHY", "BRIDAL_MAKEUP", "MUSIC_AND_DJ", "EVENT_PLANNING"];

export function VendorDiscovery() {
  const [query, setQuery] = useState("");
  const [location, setLocation] = useState("");
  const [category, setCategory] = useState<CategoryFilter>("ALL");
  const [sort, setSort] = useState<SortOption>("recommended");

  const filteredVendors = useMemo(() => {
    const term = query.trim().toLowerCase();
    const place = location.trim().toLowerCase();
    const matches = vendors.filter((vendor) => {
      const matchesTerm = !term || `${vendor.businessName} ${vendor.services.join(" ")} ${categoryLabels[vendor.category]}`.toLowerCase().includes(term);
      const matchesLocation = !place || `${vendor.city} ${vendor.area}`.toLowerCase().includes(place);
      return matchesTerm && matchesLocation && (category === "ALL" || vendor.category === category);
    });
    return [...matches].sort((first, second) => {
      if (sort === "rating") return second.rating - first.rating;
      if (sort === "price-low") return first.startingPrice - second.startingPrice;
      return Number(second.verified) - Number(first.verified) || second.rating - first.rating;
    });
  }, [category, location, query, sort]);

  function reset() {
    setQuery("");
    setLocation("");
    setCategory("ALL");
    setSort("recommended");
  }

  return (
    <>
      <section className="border-b border-border bg-white">
        <div className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6 sm:py-10">
          <div className="flex flex-wrap items-end justify-between gap-5"><div className="max-w-3xl"><p className="text-sm font-semibold text-primary">Trusted event professionals</p><h1 className="mt-2 text-3xl font-semibold sm:text-4xl">Services that bring the event together</h1><p className="mt-3 text-muted-foreground">Compare verified teams, packages, starting prices, and completed-event reviews.</p></div><Link className="inline-flex h-10 items-center rounded-md border border-border px-4 text-sm font-semibold hover:border-primary hover:text-primary" href="/auth/register">Join as a vendor</Link></div>
          <div className="mt-7 grid overflow-hidden rounded-lg border border-border bg-white shadow-sm lg:grid-cols-[1.35fr_1fr_auto]">
            <label className="flex min-h-16 items-center gap-3 border-b border-border px-4 lg:border-b-0 lg:border-r"><Search className="shrink-0 text-primary" size={20} /><span className="min-w-0 flex-1"><span className="block text-xs font-medium text-muted-foreground">Service</span><input className="mt-1 w-full bg-transparent text-sm font-medium outline-none placeholder:font-normal placeholder:text-muted-foreground" onChange={(event) => setQuery(event.target.value)} placeholder="Catering, photography, decoration..." value={query} /></span></label>
            <label className="flex min-h-16 items-center gap-3 border-b border-border px-4 lg:border-b-0 lg:border-r"><MapPin className="shrink-0 text-primary" size={20} /><span className="min-w-0 flex-1"><span className="block text-xs font-medium text-muted-foreground">Location</span><input className="mt-1 w-full bg-transparent text-sm font-medium outline-none placeholder:font-normal placeholder:text-muted-foreground" onChange={(event) => setLocation(event.target.value)} placeholder="City or area" value={location} /></span></label>
            <button className="m-2 inline-flex min-h-12 items-center justify-center gap-2 rounded-md bg-primary px-6 text-sm font-semibold text-white"><Search size={18} /> Search vendors</button>
          </div>
        </div>
      </section>

      <main className="mx-auto w-full max-w-7xl px-4 py-7 sm:px-6">
        <div aria-label="Vendor category" className="flex gap-2 overflow-x-auto pb-2" role="group">{categories.map((item) => <button className={`shrink-0 rounded-md border px-4 py-2 text-sm font-medium ${category === item ? "border-foreground bg-foreground text-white" : "border-border bg-white hover:border-foreground"}`} key={item} onClick={() => setCategory(item)}>{item === "ALL" ? "All services" : categoryLabels[item]}</button>)}<button aria-label="Reset filters" className="ml-auto hidden size-10 shrink-0 place-items-center rounded-md border border-border bg-white text-muted-foreground sm:grid" onClick={reset} title="Reset filters"><RotateCcw size={17} /></button></div>
        <div className="mt-6 flex flex-wrap items-end justify-between gap-4"><div><h2 className="text-xl font-semibold">{filteredVendors.length} {filteredVendors.length === 1 ? "vendor" : "vendors"} found</h2><p className="mt-1 text-sm text-muted-foreground">Profiles with services, pricing, and response times</p></div><label className="inline-flex h-10 items-center gap-2 rounded-md border border-border bg-white px-3 text-sm"><SlidersHorizontal size={16} /><span className="hidden sm:inline">Sort:</span><select aria-label="Sort vendors" className="bg-transparent font-medium outline-none" onChange={(event) => setSort(event.target.value as SortOption)} value={sort}><option value="recommended">Recommended</option><option value="rating">Top rated</option><option value="price-low">Price: low to high</option></select></label></div>
        {filteredVendors.length > 0 ? <div className="mt-5 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">{filteredVendors.map((vendor) => <VendorCard key={vendor.id} vendor={vendor} />)}</div> : <div className="mt-8 flex min-h-72 flex-col items-center justify-center rounded-lg border border-dashed border-border bg-white px-6 text-center"><Search className="text-muted-foreground" size={30} /><h2 className="mt-4 text-lg font-semibold">No vendors match those filters</h2><p className="mt-2 text-sm text-muted-foreground">Try another service, nearby city, or browse all categories.</p><button className="mt-5 inline-flex h-10 items-center gap-2 rounded-md border border-border px-4 text-sm font-medium" onClick={reset}><RotateCcw size={16} /> Reset filters</button></div>}
      </main>
    </>
  );
}
