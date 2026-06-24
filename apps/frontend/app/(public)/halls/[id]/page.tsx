import {
  ArrowLeft,
  BadgeCheck,
  CalendarDays,
  Car,
  Check,
  MapPin,
  Share2,
  Snowflake,
  Star,
  UsersRound
} from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { notFound } from "next/navigation";
import { EnquiryPanel } from "@/components/enquiries/EnquiryPanel";
import { SaveHallButton } from "@/components/customer/SaveHallButton";
import { SiteHeader } from "@/components/layout/SiteHeader";
import { getPublicHall } from "@/features/halls/hall-client";
import { halls } from "@/features/halls/mock-data";

type HallDetailPageProps = {
  params: Promise<{ id: string }>;
};

export function generateStaticParams() {
  return halls.map((hall) => ({ id: hall.id }));
}

export default async function HallDetailPage({ params }: HallDetailPageProps) {
  const { id } = await params;
  const hall = await getPublicHall(id);

  if (!hall) notFound();

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
            <button className="inline-flex size-10 items-center justify-center rounded-md border border-border bg-white" title="Share venue">
              <Share2 aria-label="Share venue" size={17} />
            </button>
          </div>
        </div>

        <div className="mt-5 grid gap-3 overflow-hidden rounded-lg md:grid-cols-[1.6fr_1fr]">
          <div className="relative aspect-[16/10] overflow-hidden bg-muted md:aspect-auto md:min-h-[440px]">
            <Image alt={`${hall.name} main hall`} className="object-cover" fill priority sizes="(max-width: 768px) 100vw, 65vw" src={hall.imageUrl} />
          </div>
          <div className="hidden gap-3 md:grid">
            {hall.galleryUrls.map((image, index) => (
              <div className="relative overflow-hidden bg-muted" key={image}>
                <Image alt={`${hall.name} gallery view ${index + 1}`} className="object-cover" fill sizes="35vw" src={image} />
              </div>
            ))}
          </div>
        </div>

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
              <div className="flex items-center gap-2 rounded-md bg-amber-50 px-3 py-2 text-sm font-semibold">
                <Star aria-hidden="true" className="fill-amber-400 text-amber-400" size={17} />
                {hall.rating} <span className="font-normal text-muted-foreground">({hall.reviewCount})</span>
              </div>
            </div>

            <section className="border-b border-border py-7">
              <h2 className="text-xl font-semibold">Venue overview</h2>
              <p className="mt-3 max-w-3xl leading-7 text-muted-foreground">{hall.description}</p>
              <div className="mt-6 grid grid-cols-2 gap-4 sm:grid-cols-4">
                <div className="flex items-center gap-3"><UsersRound className="text-primary" size={20} /><span className="text-sm"><strong className="block">{hall.capacity}</strong>Guests</span></div>
                <div className="flex items-center gap-3"><Snowflake className="text-primary" size={20} /><span className="text-sm"><strong className="block">Available</strong>Air conditioning</span></div>
                <div className="flex items-center gap-3"><Car className="text-primary" size={20} /><span className="text-sm"><strong className="block">On site</strong>Parking</span></div>
                <div className="flex items-center gap-3"><CalendarDays className="text-primary" size={20} /><span className="text-sm"><strong className="block">3 slots</strong>Event timings</span></div>
              </div>
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
            </section>

            <section className="border-b border-border py-7">
              <div className="flex items-end justify-between gap-4">
                <div>
                  <h2 className="text-xl font-semibold">Availability</h2>
                  <p className="mt-1 text-sm text-muted-foreground">July 2026</p>
                </div>
                <button className="text-sm font-medium text-primary">View full calendar</button>
              </div>
              <div className="mt-5 grid grid-cols-4 gap-2 sm:grid-cols-7">
                {[12, 13, 14, 15, 16, 17, 18].map((day) => (
                  <button className={`min-h-16 rounded-md border text-sm ${day === 15 ? "border-primary bg-primary text-white" : "border-border bg-white hover:border-primary"}`} key={day}>
                    <span className="block text-xs opacity-70">Jul</span>{day}
                  </button>
                ))}
              </div>
            </section>

            <section className="py-7">
              <div className="flex items-center justify-between gap-4">
                <h2 className="text-xl font-semibold">Verified customer reviews</h2>
                <span className="inline-flex items-center gap-1 text-sm font-medium text-emerald-700"><BadgeCheck size={17} /> Verified service</span>
              </div>
              <div className="mt-5 border-l-2 border-emerald-500 pl-4">
                <div className="flex items-center gap-2 text-sm"><strong>Priya S.</strong><span className="text-muted-foreground">Completed event</span></div>
                <div className="mt-2 flex gap-1 text-amber-400">{[1, 2, 3, 4, 5].map((star) => <Star aria-hidden="true" className="fill-current" key={star} size={15} />)}</div>
                <p className="mt-3 max-w-2xl text-sm leading-6 text-muted-foreground">The venue was clean, the dining space was well managed, and the owner responded quickly throughout the booking.</p>
              </div>
            </section>
          </div>

          <EnquiryPanel hall={hall} />
        </div>
      </main>
    </div>
  );
}
