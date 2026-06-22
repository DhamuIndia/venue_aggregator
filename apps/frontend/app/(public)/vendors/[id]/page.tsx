import { ArrowLeft, BadgeCheck, Check, Clock3, MapPin, Share2, Star, Trophy } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { notFound } from "next/navigation";
import { SiteHeader } from "@/components/layout/SiteHeader";
import { VendorQuotePanel } from "@/components/vendors/VendorQuotePanel";
import { categoryLabels, getVendorById, vendors } from "@/features/vendors/mock-data";

type VendorDetailPageProps = { params: Promise<{ id: string }> };

export function generateStaticParams() {
  return vendors.map((vendor) => ({ id: vendor.id }));
}

export default async function VendorDetailPage({ params }: VendorDetailPageProps) {
  const { id } = await params;
  const vendor = getVendorById(id);
  if (!vendor) notFound();

  return (
    <div className="min-h-screen bg-background">
      <SiteHeader />
      <main className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6">
        <div className="flex items-center justify-between gap-4"><Link className="inline-flex items-center gap-2 text-sm font-medium text-muted-foreground hover:text-foreground" href="/vendors"><ArrowLeft size={17} /> Back to vendors</Link><button className="grid size-10 place-items-center rounded-md border border-border bg-white" title="Share vendor"><Share2 aria-label="Share vendor" size={17} /></button></div>
        <div className="mt-5 grid gap-3 overflow-hidden rounded-lg md:grid-cols-[1.6fr_1fr]"><div className="relative aspect-[16/10] overflow-hidden bg-muted md:aspect-auto md:min-h-[440px]"><Image alt={`${vendor.businessName} service portfolio`} className="object-cover" fill priority sizes="(max-width: 768px) 100vw, 65vw" src={vendor.imageUrl} /></div><div className="hidden gap-3 md:grid">{vendor.galleryUrls.map((url, index) => <div className="relative overflow-hidden bg-muted" key={url}><Image alt={`${vendor.businessName} portfolio ${index + 1}`} className="object-cover" fill sizes="35vw" src={url} /></div>)}</div></div>
        <div className="mt-8 grid gap-10 lg:grid-cols-[minmax(0,1fr)_380px]">
          <div>
            <div className="flex flex-wrap items-start justify-between gap-5 border-b border-border pb-7"><div><div className="flex items-center gap-2 text-sm font-medium text-primary">{categoryLabels[vendor.category]}{vendor.verified && <BadgeCheck aria-label="Verified vendor" size={17} />}</div><h1 className="mt-2 text-3xl font-semibold sm:text-4xl">{vendor.businessName}</h1><p className="mt-3 flex items-center gap-2 text-muted-foreground"><MapPin size={17} /> {vendor.area}, {vendor.city}</p></div><div className="flex items-center gap-2 rounded-md bg-amber-50 px-3 py-2 text-sm font-semibold"><Star className="fill-amber-400 text-amber-400" size={17} /> {vendor.rating} <span className="font-normal text-muted-foreground">({vendor.reviewCount})</span></div></div>
            <section className="border-b border-border py-7"><h2 className="text-xl font-semibold">About this vendor</h2><p className="mt-3 max-w-3xl leading-7 text-muted-foreground">{vendor.description}</p><div className="mt-6 grid gap-4 sm:grid-cols-3"><div className="flex items-center gap-3"><Trophy className="text-primary" size={21} /><span className="text-sm"><strong className="block">{vendor.completedEvents}+</strong>Completed events</span></div><div className="flex items-center gap-3"><Clock3 className="text-primary" size={21} /><span className="text-sm"><strong className="block">{vendor.responseTime}</strong>Typical response</span></div><div className="flex items-center gap-3"><BadgeCheck className={vendor.verified ? "text-primary" : "text-muted-foreground"} size={21} /><span className="text-sm"><strong className="block">{vendor.verified ? "Identity checked" : "Not yet verified"}</strong>Marketplace verification</span></div></div></section>
            <section className="border-b border-border py-7"><h2 className="text-xl font-semibold">Services</h2><div className="mt-5 grid gap-3 sm:grid-cols-2">{vendor.services.map((service) => <p className="flex items-center gap-2 text-sm" key={service}><span className="grid size-6 place-items-center rounded-full bg-emerald-50 text-emerald-700"><Check size={14} /></span>{service}</p>)}</div></section>
            <section className="border-b border-border py-7"><div><h2 className="text-xl font-semibold">Packages</h2><p className="mt-1 text-sm text-muted-foreground">Starting packages can be customized after discussion.</p></div><div className="mt-5 grid gap-4">{vendor.packages.map((item) => <article className="rounded-lg border border-border bg-white p-5" key={item.id}><div className="flex flex-wrap items-start justify-between gap-4"><div><h3 className="font-semibold">{item.name}</h3><p className="mt-1 text-sm text-muted-foreground">{item.description}</p></div><p className="font-semibold">INR {new Intl.NumberFormat("en-IN").format(item.price)}{vendor.category === "CATERING" ? " / plate" : ""}</p></div><div className="mt-4 flex flex-wrap gap-2">{item.includes.map((included) => <span className="rounded-md bg-muted px-2.5 py-1 text-xs text-muted-foreground" key={included}>{included}</span>)}</div></article>)}</div></section>
            <section className="py-7"><div className="flex flex-wrap items-center justify-between gap-4"><h2 className="text-xl font-semibold">Verified customer reviews</h2><span className="inline-flex items-center gap-1 text-sm font-medium text-emerald-700"><BadgeCheck size={17} /> Completed service only</span></div><div className="mt-5 grid gap-6">{vendor.reviews.map((review) => <article className="border-l-2 border-emerald-500 pl-4" key={review.id}><div className="flex flex-wrap items-center gap-2 text-sm"><strong>{review.customerName}</strong><span className="text-muted-foreground">{review.eventType} | {review.eventDate}</span></div><div className="mt-2 flex gap-1 text-amber-400">{Array.from({ length: review.rating }, (_, index) => <Star className="fill-current" key={index} size={15} />)}</div><p className="mt-3 max-w-2xl text-sm leading-6 text-muted-foreground">{review.comment}</p></article>)}</div></section>
          </div>
          <VendorQuotePanel vendor={vendor} />
        </div>
      </main>
    </div>
  );
}
