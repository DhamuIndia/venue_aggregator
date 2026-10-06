"use client";

import { CalendarDays, Check, CheckCircle2, Clock3, MapPin, UsersRound } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import { getEnquiry } from "@/features/enquiries/enquiry-client";
import type { StoredEnquiry } from "@/features/enquiries/types";
import { venueMartEnquiryStatus } from "@/features/enquiries/enquiry-display";
import { formatSlot, formatSlotRequests } from "@/features/halls/slot-model";

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-IN", { dateStyle: "long" }).format(new Date(`${value}T00:00:00`));
}

export function EnquiryConfirmation({ enquiryId }: { enquiryId: string }) {
  const { accessToken } = useAuth();
  const [enquiry, setEnquiry] = useState<StoredEnquiry | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isCurrent = true;

    async function loadEnquiry() {
      setIsLoading(true);
      const result = await getEnquiry(enquiryId, accessToken);
      if (!isCurrent) return;
      setEnquiry(result ?? null);
      setIsLoading(false);
    }

    loadEnquiry();

    return () => {
      isCurrent = false;
    };
  }, [accessToken, enquiryId]);

  if (isLoading) return <div className="min-h-80 animate-pulse rounded-lg bg-muted" />;

  if (!enquiry) {
    return (
      <div className="mx-auto max-w-xl py-20 text-center">
        <h1 className="text-2xl font-semibold">Enquiry not found</h1>
        <p className="mt-2 text-sm text-muted-foreground">Sign in to check your requests. The enquiry may be unavailable or could not be loaded.</p>
        <Link className="mt-6 inline-flex h-11 items-center rounded-md bg-primary px-5 text-sm font-semibold text-white" href="/">Browse venues</Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl py-8 sm:py-14">
      <div className="text-center">
        <span className="mx-auto grid size-14 place-items-center rounded-full bg-emerald-100 text-emerald-700"><Check size={28} /></span>
        <p className="mt-5 text-sm font-semibold text-primary">Enquiry {enquiry.id}</p>
        <h1 className="mt-2 text-3xl font-semibold sm:text-4xl">Your enquiry has been sent</h1>
        <p className="mx-auto mt-3 max-w-xl text-muted-foreground">{enquiry.routingTarget === "VENUEMART" ? "The VenueMart team has received your event request. Availability and pricing still need to be checked." : "The venue owner will review your event details and respond through your account."}</p>
      </div>

      <section className="mt-8 rounded-lg border border-border bg-white p-5 shadow-sm sm:p-6">
        <div className="flex items-start justify-between gap-4 border-b border-border pb-5">
          <div><p className="text-xs font-medium text-muted-foreground">VENUE</p><h2 className="mt-1 text-xl font-semibold">{enquiry.hallName}</h2></div>
          <Link className="text-sm font-semibold text-primary" href={`/halls/${enquiry.hallId}`}>View venue</Link>
        </div>
        <dl className="grid gap-5 py-5 sm:grid-cols-2">
          <div className="flex gap-3"><CalendarDays className="mt-0.5 shrink-0 text-primary" size={19} /><div><dt className="text-xs text-muted-foreground">Event date and slot</dt><dd className="mt-1 text-sm font-medium">{enquiry.slotRequests?.length ? formatSlotRequests(enquiry.slotRequests) : `${formatDate(enquiry.eventDate)}, ${formatSlot(enquiry.slot)}`}</dd></div></div>
          <div className="flex gap-3"><UsersRound className="mt-0.5 shrink-0 text-primary" size={19} /><div><dt className="text-xs text-muted-foreground">Event and guests</dt><dd className="mt-1 text-sm font-medium">{enquiry.eventType}, {enquiry.guestCount} guests</dd></div></div>
          <div className="flex gap-3"><MapPin className="mt-0.5 shrink-0 text-primary" size={19} /><div><dt className="text-xs text-muted-foreground">Request status</dt><dd className="mt-1 text-sm font-medium">{enquiry.routingTarget === "VENUEMART" ? venueMartEnquiryStatus(enquiry.status) : "Awaiting owner response"}</dd></div></div>
          <div className="flex gap-3"><Clock3 className="mt-0.5 shrink-0 text-primary" size={19} /><div><dt className="text-xs text-muted-foreground">{enquiry.routingTarget === "VENUEMART" ? "Handled by" : "Expected response"}</dt><dd className="mt-1 text-sm font-medium">{enquiry.routingTarget === "VENUEMART" ? "VenueMart team — no response time guarantee" : "Within 24 hours"}</dd></div></div>
        </dl>
        {enquiry.routingTarget === "VENUEMART" && enquiry.responseMessage && <p className="mb-5 whitespace-pre-wrap rounded-md bg-blue-50 p-4 text-sm">VenueMart team response: {enquiry.responseMessage}</p>}
        <div className="flex items-start gap-3 border-t border-border pt-5 text-sm text-muted-foreground"><CheckCircle2 className="mt-0.5 shrink-0 text-emerald-700" size={18} /><p>{enquiry.routingTarget === "VENUEMART" ? "This is an enquiry, not a confirmed booking. No payment is accepted for this listing." : "No payment has been collected. Pricing and booking confirmation will follow the owner response."}</p></div>
      </section>

      <div className="mt-6 flex flex-col justify-center gap-3 sm:flex-row">
        <Link className="inline-flex h-11 items-center justify-center rounded-md bg-primary px-5 text-sm font-semibold text-white" href="/customer?tab=enquiries">Track enquiry</Link>
        <Link className="inline-flex h-11 items-center justify-center rounded-md border border-border bg-white px-5 text-sm font-semibold hover:border-foreground" href="/">Browse more venues</Link>
      </div>
    </div>
  );
}
