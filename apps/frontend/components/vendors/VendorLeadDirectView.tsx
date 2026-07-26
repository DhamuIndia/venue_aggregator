"use client";

import {
  ArrowLeft,
  CalendarDays,
  IndianRupee,
  LoaderCircle,
  MapPin,
  MessageSquareText,
  ShieldCheck,
  UserRound
} from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useAuth } from "@/features/auth/AuthProvider";
import { getVendorLeadByReference } from "@/features/vendors/lead-client";
import type { VendorLead, VendorLeadStatus } from "@/features/vendors/types";
import { ApiError } from "@/lib/api-client";

const statusStyle: Record<VendorLeadStatus, string> = {
  NEW: "bg-blue-50 text-blue-700",
  INTERESTED: "bg-emerald-50 text-emerald-700",
  CONTACTED: "bg-amber-50 text-amber-700",
  QUOTE_SENT: "bg-violet-50 text-violet-700",
  BOOKED: "bg-emerald-50 text-emerald-700",
  NOT_SELECTED: "bg-slate-100 text-slate-700",
  DECLINED: "bg-rose-50 text-rose-700",
  COMPLETED: "bg-muted text-muted-foreground"
};

export function VendorLeadDirectView({ leadReference }: { leadReference: string }) {
  const { getValidAccessToken } = useAuth();
  const [lead, setLead] = useState<VendorLead | null>(null);
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isMounted = true;

    async function loadLead() {
      try {
        setIsLoading(true);
        setError("");
        const accessToken = await getValidAccessToken();
        const result = await getVendorLeadByReference(leadReference, accessToken);
        if (isMounted) setLead(result);
      } catch (exception) {
        if (!isMounted) return;
        setLead(null);
        setError(directLeadError(exception));
      } finally {
        if (isMounted) setIsLoading(false);
      }
    }

    void loadLead();
    return () => {
      isMounted = false;
    };
  }, [getValidAccessToken, leadReference]);

  return (
    <main className="mx-auto w-full max-w-5xl px-4 py-8 sm:px-6">
      <Link
        className="inline-flex items-center gap-2 text-sm font-medium text-muted-foreground hover:text-foreground"
        href="/vendor"
      >
        <ArrowLeft aria-hidden="true" size={17} /> Back to vendor workspace
      </Link>

      {isLoading ? (
        <div className="mt-8 grid min-h-72 place-items-center rounded-xl border border-border bg-white">
          <div className="text-center">
            <LoaderCircle className="mx-auto animate-spin text-primary" size={28} />
            <p className="mt-3 text-sm text-muted-foreground">Opening your lead securely…</p>
          </div>
        </div>
      ) : error ? (
        <section className="mt-8 rounded-xl border border-rose-200 bg-rose-50 p-8 text-center">
          <ShieldCheck className="mx-auto text-rose-700" size={30} />
          <h1 className="mt-4 text-xl font-semibold">This lead cannot be opened</h1>
          <p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-rose-800">{error}</p>
        </section>
      ) : lead ? (
        <article className="mt-8 overflow-hidden rounded-xl border border-border bg-white shadow-sm">
          <header className="border-b border-border bg-muted/40 p-6 sm:p-8">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div>
                <p className="text-sm font-semibold text-primary">VenueMart lead</p>
                <h1 className="mt-2 text-2xl font-semibold sm:text-3xl">
                  {lead.eventType} · {lead.service}
                </h1>
                <p className="mt-3 font-mono text-xs text-muted-foreground">
                  {lead.leadReference ?? leadReference}
                </p>
              </div>
              <span className={`rounded-full px-3 py-1.5 text-xs font-semibold ${statusStyle[lead.status]}`}>
                {readableStatus(lead.status)}
              </span>
            </div>
          </header>

          <div className="grid gap-6 p-6 sm:grid-cols-2 sm:p-8">
            <Detail icon={<CalendarDays size={19} />} label="Event date" value={formatDate(lead.eventDate)} />
            <Detail icon={<MapPin size={19} />} label="Location" value={lead.location} />
            <Detail
              icon={<IndianRupee size={19} />}
              label="Customer budget"
              value={lead.budget === undefined ? "Not specified" : `INR ${formatMoney(lead.budget)}`}
            />
            <Detail
              icon={<MessageSquareText size={19} />}
              label="Lead source"
              value={lead.source === "MARKETPLACE_REQUIREMENT" ? "Marketplace requirement" : "Direct enquiry"}
            />
          </div>

          <section className="border-t border-border p-6 sm:p-8">
            <div className="flex items-center gap-2">
              <UserRound className="text-primary" size={20} />
              <h2 className="text-lg font-semibold">Customer and requirement</h2>
            </div>
            <div className="mt-4 rounded-lg bg-muted/60 p-5 text-sm">
              <p><span className="text-muted-foreground">Customer:</span> {lead.customerName}</p>
              {lead.contactDetailsShared && (lead.customerPhone || lead.customerEmail) ? (
                <p className="mt-3">
                  <span className="text-muted-foreground">Contact:</span>{" "}
                  {[lead.customerPhone, lead.customerEmail].filter(Boolean).join(" · ")}
                </p>
              ) : lead.source === "MARKETPLACE_REQUIREMENT" ? (
                <p className="mt-3 text-muted-foreground">
                  Customer contact details remain private. Respond through VenueMart.
                </p>
              ) : null}
              {lead.notes && (
                <p className="mt-4 border-t border-border pt-4 leading-6">
                  <span className="text-muted-foreground">Requirement:</span> {lead.notes}
                </p>
              )}
              {lead.declineReason && (
                <p className="mt-4 border-t border-border pt-4 leading-6 text-rose-700">
                  <span className="font-medium">Decline reason:</span> {lead.declineReason}
                </p>
              )}
            </div>
          </section>
        </article>
      ) : null}
    </main>
  );
}

function Detail({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="flex gap-3 rounded-lg border border-border p-4">
      <span className="mt-0.5 text-primary">{icon}</span>
      <div>
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
        <p className="mt-1 font-medium">{value}</p>
      </div>
    </div>
  );
}

function directLeadError(exception: unknown) {
  if (exception instanceof ApiError && exception.status === 404) {
    return "The lead was not found or is not assigned to this vendor account.";
  }
  if (exception instanceof ApiError && (exception.status === 401 || exception.status === 403)) {
    return "Please sign in with the vendor account that received this lead.";
  }
  return exception instanceof Error
    ? exception.message
    : "VenueMart could not load this lead. Please try again.";
}

function readableStatus(status: VendorLeadStatus) {
  return status.toLowerCase().replaceAll("_", " ").replace(/^\w/, (character) => character.toUpperCase());
}

function formatDate(value: string) {
  const date = new Date(`${value}T00:00:00`);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "long", year: "numeric" }).format(date);
}

function formatMoney(value: number) {
  return new Intl.NumberFormat("en-IN", { maximumFractionDigits: 0 }).format(value);
}
