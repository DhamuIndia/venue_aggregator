"use client";

import { Check, ChevronLeft, ChevronRight, Expand, FileCheck2, ImagePlus, X, XCircle } from "lucide-react";
import Image from "next/image";
import { useState } from "react";
import type { VenueApplication } from "@/features/admin/mock-data";

type Props = {
  venue: VenueApplication;
  onClose: () => void;
  onApprove: () => void;
  onReject: () => void;
};

export function VenueDetailsDrawer({ venue, onClose, onApprove, onReject }: Props) {
  const galleryImages = [venue.imageUrl, ...venue.imageUrls]
    .filter((url, index, urls) => Boolean(url) && urls.indexOf(url) === index);
  const [selectedImageIndex, setSelectedImageIndex] = useState(0);
  const [isLightboxOpen, setIsLightboxOpen] = useState(false);
  const selectedImage = galleryImages[selectedImageIndex];
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
          <section aria-label="Venue photo gallery">
            <div className="mb-3 flex items-center justify-between gap-3">
              <h3 className="text-lg font-semibold">Venue photos</h3>
              <span className="text-sm text-muted-foreground">{galleryImages.length} photo{galleryImages.length === 1 ? "" : "s"}</span>
            </div>
            <div className="relative h-64 overflow-hidden rounded-lg border border-border bg-muted">
              {selectedImage ? (
                <button aria-label={`Open photo ${selectedImageIndex + 1} full screen`} className="group relative block h-full w-full" onClick={() => setIsLightboxOpen(true)} type="button">
                  <Image alt={`${venue.name} photo ${selectedImageIndex + 1}`} className="object-cover" fill sizes="576px" src={selectedImage} unoptimized={selectedImage.startsWith("blob:")} />
                  <span className="absolute bottom-3 right-3 inline-flex items-center gap-2 rounded-md bg-black/70 px-3 py-2 text-xs font-semibold text-white"><Expand size={15} /> View full size</span>
                </button>
              ) : (
                <div className="grid h-full place-items-center text-muted-foreground"><ImagePlus size={28} /></div>
              )}
            </div>
            {galleryImages.length > 1 && (
              <div className="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-4">
                {galleryImages.map((imageUrl, index) => (
                  <button
                    aria-label={`Select venue photo ${index + 1}`}
                    aria-pressed={selectedImageIndex === index}
                    className={`relative aspect-[4/3] overflow-hidden rounded-md border-2 bg-muted ${selectedImageIndex === index ? "border-primary" : "border-transparent hover:border-border"}`}
                    key={imageUrl}
                    onClick={() => setSelectedImageIndex(index)}
                    type="button"
                  >
                    <Image alt="" className="object-cover" fill sizes="140px" src={imageUrl} unoptimized={imageUrl.startsWith("blob:")} />
                    {index === 0 && <span className="absolute left-1.5 top-1.5 rounded bg-black/70 px-1.5 py-0.5 text-[10px] font-semibold text-white">Cover</span>}
                  </button>
                ))}
              </div>
            )}
          </section>

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

      {isLightboxOpen && selectedImage && (
        <div aria-label={`${venue.name} photo viewer`} aria-modal="true" className="fixed inset-0 z-[70] grid place-items-center bg-black/90 p-4" onClick={() => setIsLightboxOpen(false)} role="dialog">
          <div className="relative h-full max-h-[88vh] w-full max-w-6xl" onClick={(event) => event.stopPropagation()}>
            <Image alt={`${venue.name} photo ${selectedImageIndex + 1} full size`} className="object-contain" fill priority sizes="100vw" src={selectedImage} unoptimized={selectedImage.startsWith("blob:")} />
          </div>
          <div className="absolute left-4 top-4 rounded-md bg-black/60 px-3 py-2 text-sm font-medium text-white">{selectedImageIndex + 1} of {galleryImages.length}</div>
          <button aria-label="Close full-size photo" className="absolute right-4 top-4 grid size-11 place-items-center rounded-md bg-black/60 text-white hover:bg-black/80" onClick={() => setIsLightboxOpen(false)} type="button"><X size={22} /></button>
          {galleryImages.length > 1 && (
            <>
              <button aria-label="Previous photo" className="absolute left-4 top-1/2 grid size-11 -translate-y-1/2 place-items-center rounded-md bg-black/60 text-white hover:bg-black/80" onClick={(event) => { event.stopPropagation(); setSelectedImageIndex((current) => (current - 1 + galleryImages.length) % galleryImages.length); }} type="button"><ChevronLeft size={24} /></button>
              <button aria-label="Next photo" className="absolute right-4 top-1/2 grid size-11 -translate-y-1/2 place-items-center rounded-md bg-black/60 text-white hover:bg-black/80" onClick={(event) => { event.stopPropagation(); setSelectedImageIndex((current) => (current + 1) % galleryImages.length); }} type="button"><ChevronRight size={24} /></button>
            </>
          )}
        </div>
      )}
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
