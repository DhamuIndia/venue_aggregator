import {
  ArrowLeft,
  BadgeCheck,
  CalendarDays,
  Car,
  Check,
  MapPin,
  Snowflake,
  Star,
  UsersRound
} from "lucide-react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ShareButton } from "@/components/common/ShareButton";
import { EnquiryPanel } from "@/components/enquiries/EnquiryPanel";
import { SaveHallButton } from "@/components/customer/SaveHallButton";
import { HallAvailabilityCalendar } from "@/components/halls/HallAvailabilityCalendar";
import { HallPhotoGallery } from "@/components/halls/HallPhotoGallery";
import { SiteHeader } from "@/components/layout/SiteHeader";
import { getPublicHall } from "@/features/halls/hall-client";
import { formatGuestCount } from "@/lib/display-format";

// Publication withdrawal must take effect on the next request, including HTML.
export const dynamic = "force-dynamic";

type HallDetailPageProps = {
  params: Promise<{ id: string }>;
};

export default async function HallDetailPage({ params }: HallDetailPageProps) {
  const { id } = await params;
  const hall = await getPublicHall(id);

  if (!hall) notFound();
  const applicationManaged = hall.listingOrigin === "APPLICATION";

  return (
    <div className="min-h-screen bg-background">
      <SiteHeader />
      <main className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6">
        <div className="flex items-center justify-between gap-4">
          <Link className="inline-flex items-center gap-2 text-sm font-medium text-muted-foreground hover:text-foreground" href="/">
            <ArrowLeft aria-hidden="true" size={17} /> Back to venues
          </Link>
          <div className="flex items-center gap-2">
            <SaveHallButton hall={hall} variant="compact" />
            <ShareButton label="Share venue" text={`View ${hall.name} on VenueMart`} title={hall.name} />
          </div>
        </div>

        {hall.imageUrl ? <HallPhotoGallery coverImage={hall.imageUrl} galleryImages={hall.galleryUrls} hallName={hall.name} unoptimized={applicationManaged} applicationPhotos={hall.applicationPhotos} /> : <p className="mt-5 rounded-lg bg-muted p-8 text-center">Venue photo not provided.</p>}

        <div className="mt-8 grid gap-10 lg:grid-cols-[minmax(0,1fr)_360px]">
          <div>
            <div className="flex flex-wrap items-start justify-between gap-5 border-b border-border pb-7">
              <div>
                <div className="flex items-center gap-2 text-sm font-medium text-primary">
                  {hall.venueType}
                  {hall.isVerified && <BadgeCheck aria-label="Verified listing" size={17} />}
                </div>
                <h1 className="mt-2 text-3xl font-semibold text-foreground sm:text-4xl">{hall.name}</h1>
                <p className="mt-3 flex items-center gap-2 text-muted-foreground">
                  <MapPin aria-hidden="true" size={17} /> {hall.area}, {hall.city}
                </p>
              </div>
              {hall.rating !== null && <div className="flex items-center gap-2 rounded-md bg-amber-50 px-3 py-2 text-sm font-semibold">
                <Star aria-hidden="true" className="fill-amber-400 text-amber-400" size={17} />
                {hall.rating} <span className="font-normal text-muted-foreground">({hall.reviewCount})</span>
              </div>}
            </div>

            <section className="border-b border-border py-7">
              <h2 className="text-xl font-semibold">Venue overview</h2>
              <p className="mt-3 max-w-3xl leading-7 text-muted-foreground">{hall.description}</p>
              <div className="mt-6 grid grid-cols-2 gap-4 sm:grid-cols-4">
                <div className="flex items-center gap-3"><UsersRound className="text-primary" size={20} /><span className="text-sm"><strong className="block">{hall.capacity === null ? "To be confirmed" : formatGuestCount(hall.capacity)}</strong>Guests</span></div>
                {!applicationManaged && <><div className="flex items-center gap-3"><Snowflake className="text-primary" size={20} /><span className="text-sm"><strong className="block">Available</strong>Air conditioning</span></div>
                <div className="flex items-center gap-3"><Car className="text-primary" size={20} /><span className="text-sm"><strong className="block">On site</strong>Parking</span></div>
                <div className="flex items-center gap-3"><CalendarDays className="text-primary" size={20} /><span className="text-sm"><strong className="block">3 slots</strong>Event timings</span></div></>}
              </div>
              {applicationManaged && <p className="mt-5 rounded-md border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">VenueMart-managed listing. Availability, pricing and arrangements are not confirmed. Request availability from our team; this is not an online booking and no payment is accepted.</p>}
            </section>

            <section className="border-b border-border py-7">
              <h2 className="text-xl font-semibold">Amenities</h2>
              <div className="mt-5 grid gap-3 sm:grid-cols-2">
                {hall.amenities.map((amenity) => (
                  <div className="flex items-center gap-2 text-sm" key={amenity}>
                    <span className="grid size-6 place-items-center rounded-full bg-emerald-50 text-emerald-700"><Check aria-hidden="true" size={14} /></span>
                    {amenity}
                  </div>
                ))}
              </div>
              {applicationManaged && !hall.amenities.length && <p className="mt-3 text-sm text-muted-foreground">Amenities have not been confirmed.</p>}
            </section>

            {!applicationManaged && <HallAvailabilityCalendar hallId={hall.id} />}

            {applicationManaged && <section className="border-b border-border py-7"><h2 className="text-xl font-semibold">Listing sources</h2><p className="mt-3 text-sm text-muted-foreground">Overture Maps Places{hall.sourceRelease ? ` · Release ${hall.sourceRelease}` : ""}. Source records support venue discovery, not a partnership or availability guarantee.</p><ul className="mt-3 space-y-2 text-sm text-muted-foreground">{hall.sourceAttribution?.map((source, index) => <li className="break-words" key={index}>{source.dataset} · {source.license}{source.recordId ? ` · Record ${source.recordId}` : ""}</li>)}</ul><a className="mt-3 inline-block text-sm text-primary underline" href="https://docs.overturemaps.org/attribution/" rel="noopener noreferrer" target="_blank">Overture attribution</a></section>}

            {!applicationManaged && <section className="py-7">
              <div className="flex items-center justify-between gap-4">
                <h2 className="text-xl font-semibold">Verified customer reviews</h2>
                <span className="inline-flex items-center gap-1 text-sm font-medium text-emerald-700"><BadgeCheck size={17} /> Verified service</span>
              </div>
              {hall.reviews?.length ? (
                <div className="mt-5 grid gap-6">
                  {hall.reviews.map((review, index) => (
                    <article className="border-l-2 border-emerald-500 pl-4" key={`${review.customerName}-${index}`}>
                      <div className="flex items-center gap-2 text-sm">
                        <strong>{review.customerName}</strong>
                        {review.verifiedService && <span className="text-muted-foreground">Completed event</span>}
                      </div>
                      <div className="mt-2 flex gap-1 text-amber-400">
                        {Array.from({ length: Math.max(0, Math.min(5, Math.round(review.rating))) }, (_, star) => (
                          <Star aria-hidden="true" className="fill-current" key={star} size={15} />
                        ))}
                      </div>
                      <p className="mt-3 max-w-2xl text-sm leading-6 text-muted-foreground">{review.comment}</p>
                    </article>
                  ))}
                </div>
              ) : (
                <p className="mt-5 rounded-md border border-dashed border-border bg-white px-4 py-5 text-sm text-muted-foreground">
                  No verified customer reviews yet.
                </p>
              )}
            </section>}
          </div>

          <EnquiryPanel hall={hall} />
        </div>
      </main>
    </div>
  );
}
