"use client";

import { Check, FileCheck2, ImagePlus, X, XCircle } from "lucide-react";
import Image from "next/image";
import type { VenueApplication } from "@/features/admin/mock-data";

type Props = {
  venue: VenueApplication;
  onClose: () => void;
  onApprove: () => void;
  onReject: () => void;
};

export function VenueDetailsDrawer({ venue, onClose, onApprove, onReject }: Props) {
  const documentChecks = [
    { label: "Ownership", ready: venue.documents.ownership },
    { label: "Identity", ready: venue.documents.identity },
    { label: "Address", ready: venue.documents.address }
  ];
  const documentReviewRequired = venue.documentReviewRequired ?? true;
  const canApprove = !documentReviewRequired || documentChecks.every((check) => check.ready);

  return (
    <>
      <button aria-label="Close venue details" className="fixed inset-0 z-40 cursor-default bg-black/40" onClick={onClose} type="button" />
      <aside aria-label="Venue approval details" className="fixed right-0 top-0 z-50 h-screen w-full max-w-xl overflow-y-auto bg-white shadow-2xl">
        <div className="sticky top-0 flex items-center justify-between border-b border-border bg-white px-6 py-4">
          <div>
            <h2 className="text-xl font-semibold">Venue Details</h2>
            <p className="mt-1 text-sm text-muted-foreground">{venue.id}</p>
          </div>
          <button aria-label="Close venue details" className="grid size-9 place-items-center rounded-md text-muted-foreground hover:bg-muted" onClick={onClose} type="button"><X size={20} /></button>
        </div>

        <div className="space-y-6 p-6">
          <div className="relative h-52 overflow-hidden rounded-lg border border-border bg-muted">
            {venue.imageUrl ? <Image alt={venue.name} className="object-cover" fill sizes="576px" src={venue.imageUrl} unoptimized={venue.imageUrl.startsWith("blob:")} /> : <div className="grid h-full place-items-center text-muted-foreground"><ImagePlus size={28} /></div>}
          </div>

          <Section title="Venue">
            <Field label="Venue name" value={venue.name} />
            <Field label="Venue type" value={venue.venueType} />
            <Field label="Location" value={venue.location} />
            <Field label="Capacity" value={`${venue.capacity} guests`} />
            <Field label="Starting price" value={`INR ${new Intl.NumberFormat("en-IN").format(venue.startingPrice)}`} />
          </Section>

          <Section title="Hall owner">
            <Field label="Owner name" value={venue.ownerName} />
            <Field label="Owner phone" value={venue.ownerPhone} />
          </Section>

          <Section title="Approval">
            <Field label="Status" value={venue.status.toLowerCase().replaceAll("_", " ")} />
            <Field label="Submitted at" value={new Date(venue.submittedAt).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" })} />
            <div className="rounded-md border border-border p-3">
              <div className="text-xs text-muted-foreground">Document checks</div>
              <div className="mt-3 grid gap-2 text-sm">
                {documentReviewRequired ? documentChecks.map((check) => <p className={check.ready ? "flex items-center gap-2 text-emerald-700" : "flex items-center gap-2 text-amber-700"} key={check.label}><FileCheck2 size={16} /> {check.label}: {check.ready ? "Ready" : "Pending"}</p>) : <p className="text-muted-foreground">MVP manual review. Document upload is not collected yet.</p>}
              </div>
            </div>
          </Section>

          {venue.status === "PENDING_APPROVAL" && (
            <div className="grid grid-cols-2 gap-3 border-t border-border pt-5">
              <button className="inline-flex h-11 items-center justify-center gap-2 rounded-md border border-rose-200 text-sm font-semibold text-rose-700 hover:bg-rose-50" onClick={onReject} type="button"><XCircle size={18} /> Reject</button>
              <button className="inline-flex h-11 items-center justify-center gap-2 rounded-md bg-primary text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-45" disabled={!canApprove} onClick={onApprove} title={canApprove ? "Approve venue" : "Complete all document checks first"} type="button"><Check size={18} /> Approve</button>
            </div>
          )}
        </div>
      </aside>
    </>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section>
      <h3 className="mb-3 text-lg font-semibold">{title}</h3>
      <div className="space-y-3">{children}</div>
    </section>
  );
}

function Field({ label, value }: { label: string; value?: string | number | null }) {
  return (
    <div className="rounded-md border border-border p-3">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="mt-1 font-medium">{value || "-"}</div>
    </div>
  );
}
