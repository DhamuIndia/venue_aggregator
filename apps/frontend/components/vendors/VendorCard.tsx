"use client";

import { BadgeCheck, Clock3, MapPin, Star } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { categoryLabels } from "@/features/vendors/mock-data";
import type { VendorSummary } from "@/features/vendors/types";

function formatPrice(value: number) {
  return new Intl.NumberFormat("en-IN").format(value);
}

export function VendorCard({ vendor }: { vendor: VendorSummary }) {
  return (
    <article className="group overflow-hidden rounded-lg border border-border bg-white shadow-sm transition hover:-translate-y-0.5 hover:shadow-md">
      <Link className="block" href={`/vendors/${vendor.id}`}>
        <div className="relative aspect-[4/3] overflow-hidden bg-muted">
          <Image alt={`${vendor.businessName} ${categoryLabels[vendor.category].toLowerCase()} service`} className="object-cover transition duration-500 group-hover:scale-[1.03]" fill sizes="(max-width: 768px) 100vw, (max-width: 1200px) 50vw, 33vw" src={vendor.imageUrl} />
          <span className="absolute left-3 top-3 rounded-full bg-white px-2.5 py-1 text-xs font-medium text-foreground shadow-sm">{categoryLabels[vendor.category]}</span>
        </div>
      </Link>
      <div className="p-4">
        <div className="flex items-start justify-between gap-4"><div className="min-w-0"><Link href={`/vendors/${vendor.id}`}><h2 className="truncate text-lg font-semibold hover:text-primary">{vendor.businessName}</h2></Link><p className="mt-1 flex items-center gap-1.5 text-sm text-muted-foreground"><MapPin size={15} /> {vendor.area}, {vendor.city}</p></div><span className="flex shrink-0 items-center gap-1 text-sm font-semibold"><Star className="fill-amber-400 text-amber-400" size={16} /> {vendor.rating}</span></div>
        <div className="mt-3 flex flex-wrap gap-1.5">{vendor.services.slice(0, 3).map((service) => <span className="rounded-md bg-muted px-2 py-1 text-xs text-muted-foreground" key={service}>{service}</span>)}</div>
        <div className="mt-4 flex items-center justify-between border-t border-border pt-4"><div><p className="text-xs text-muted-foreground">Starting from</p><p className="font-semibold">INR {formatPrice(vendor.startingPrice)}{vendor.category === "CATERING" ? " / plate" : ""}</p></div><div className="text-right">{vendor.verified && <p className="inline-flex items-center gap-1 text-xs font-medium text-emerald-700"><BadgeCheck size={15} /> Verified</p>}<p className="mt-1 flex items-center justify-end gap-1 text-xs text-muted-foreground"><Clock3 size={13} /> {vendor.responseTime}</p></div></div>
      </div>
    </article>
  );
}
