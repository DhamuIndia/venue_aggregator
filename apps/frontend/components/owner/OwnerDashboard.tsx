"use client";

import {
  BadgeCheck,
  Ban,
  CalendarDays,
  Check,
  CheckCircle2,
  ChevronRight,
  Eye,
  ImagePlus,
  MapPin,
  MessageSquareText,
  MoreHorizontal,
  Plus,
  Star,
  UploadCloud,
  UsersRound,
  X
} from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { getLocalEnquiries, updateLocalEnquiryStatus } from "@/features/enquiries/enquiry-client";
import type { EnquiryStatus, StoredEnquiry } from "@/features/enquiries/types";
import { fallbackOwnerEnquiries, initialBlockedDates, ownerHall, ownerReviews } from "@/features/owner/mock-data";
import { BlockDateDialog, type BlockedDate } from "./BlockDateDialog";

type OwnerTab = "overview" | "enquiries" | "availability" | "listing" | "media" | "reviews";

const tabs: Array<{ id: OwnerTab; label: string }> = [
  { id: "overview", label: "Overview" },
  { id: "enquiries", label: "Enquiries" },
  { id: "availability", label: "Availability" },
  { id: "listing", label: "Listing" },
  { id: "media", label: "Media" },
  { id: "reviews", label: "Reviews" }
];

const statusStyle: Record<EnquiryStatus, string> = {
  PENDING_OWNER_RESPONSE: "bg-blue-50 text-blue-700",
  CONFIRMED: "bg-emerald-50 text-emerald-700",
  DECLINED: "bg-rose-50 text-rose-700",
  COMPLETED: "bg-muted text-muted-foreground"
};

function formatStatus(status: EnquiryStatus) {
  return status.toLowerCase().replace(/_/g, " ");
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-IN", { dateStyle: "medium" }).format(new Date(`${value}T00:00:00`));
}

export function OwnerDashboard() {
  const [activeTab, setActiveTab] = useState<OwnerTab>("overview");
  const [enquiries, setEnquiries] = useState<StoredEnquiry[]>(fallbackOwnerEnquiries);
  const [blockedDates, setBlockedDates] = useState<BlockedDate[]>(initialBlockedDates);
  const [blockDialogOpen, setBlockDialogOpen] = useState(false);
  const [notice, setNotice] = useState("");
  const [media, setMedia] = useState([ownerHall.imageUrl, ...ownerHall.galleryUrls]);

  useEffect(() => {
    const local = getLocalEnquiries().filter((enquiry) => enquiry.hallId === ownerHall.id);
    const localIds = new Set(local.map((enquiry) => enquiry.id));
    setEnquiries([...local, ...fallbackOwnerEnquiries.filter((enquiry) => !localIds.has(enquiry.id))]);
    if (new URLSearchParams(window.location.search).get("submitted") === "true") setNotice("Your venue was submitted for admin approval.");
  }, []);

  const pendingCount = enquiries.filter((enquiry) => enquiry.status === "PENDING_OWNER_RESPONSE").length;
  const confirmedCount = enquiries.filter((enquiry) => enquiry.status === "CONFIRMED").length;
  const confirmedDays = useMemo(() => new Set(enquiries.filter((enquiry) => enquiry.status === "CONFIRMED" && enquiry.eventDate.startsWith("2026-07")).map((enquiry) => Number(enquiry.eventDate.slice(-2)))), [enquiries]);
  const blockedDays = new Set(blockedDates.filter((date) => date.date.startsWith("2026-07")).map((date) => Number(date.date.slice(-2))));

  function respondToEnquiry(id: string, status: EnquiryStatus) {
    setEnquiries((current) => current.map((enquiry) => enquiry.id === id ? { ...enquiry, status } : enquiry));
    updateLocalEnquiryStatus(id, status);
    setNotice(status === "CONFIRMED" ? "Enquiry confirmed. The customer can now see the updated status." : "Enquiry declined and the customer status was updated.");
  }

  function addBlockedDate(date: BlockedDate) {
    setBlockedDates((current) => [...current, date]);
    setNotice("The selected date and slot are now blocked.");
  }

  return (
    <>
      <main className="mx-auto w-full max-w-7xl px-4 py-7 sm:px-6 sm:py-10">
        <div className="flex flex-wrap items-start justify-between gap-5">
          <div><div className="flex items-center gap-2 text-sm font-semibold text-primary"><BadgeCheck size={17} /> Owner workspace</div><h1 className="mt-2 text-3xl font-semibold">{ownerHall.name}</h1><p className="mt-2 flex items-center gap-2 text-sm text-muted-foreground"><MapPin size={16} /> {ownerHall.area}, {ownerHall.city}</p></div>
          <div className="flex gap-2"><Link className="inline-flex h-10 items-center gap-2 rounded-md border border-border bg-white px-3 text-sm font-medium hover:border-primary" href={`/halls/${ownerHall.id}`}><Eye size={17} /> Public listing</Link><Link className="inline-flex h-10 items-center gap-2 rounded-md bg-foreground px-3 text-sm font-medium text-white" href="/owner/onboarding"><Plus size={17} /> Add venue</Link></div>
        </div>

        {notice && <div className="mt-6 flex items-start justify-between gap-4 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800"><span className="flex items-start gap-2"><CheckCircle2 className="mt-0.5 shrink-0" size={18} />{notice}</span><button aria-label="Dismiss notification" onClick={() => setNotice("")}><X size={17} /></button></div>}

        <div className="mt-8 flex gap-1 overflow-x-auto border-b border-border" role="tablist" aria-label="Owner dashboard">
          {tabs.map((tab) => <button aria-selected={activeTab === tab.id} className={`shrink-0 border-b-2 px-4 py-3 text-sm font-medium ${activeTab === tab.id ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:text-foreground"}`} key={tab.id} onClick={() => setActiveTab(tab.id)} role="tab" type="button">{tab.label}{tab.id === "enquiries" && pendingCount > 0 && <span className="ml-2 rounded-full bg-blue-100 px-2 py-0.5 text-xs text-blue-700">{pendingCount}</span>}</button>)}
        </div>

        {activeTab === "overview" && <section className="py-7"><div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4"><div className="rounded-lg border border-border bg-white p-5"><MessageSquareText className="text-blue-600" size={21} /><p className="mt-5 text-2xl font-semibold">{pendingCount}</p><p className="mt-1 text-sm text-muted-foreground">New enquiries</p></div><div className="rounded-lg border border-border bg-white p-5"><CalendarDays className="text-primary" size={21} /><p className="mt-5 text-2xl font-semibold">{confirmedCount}</p><p className="mt-1 text-sm text-muted-foreground">Confirmed events</p></div><div className="rounded-lg border border-border bg-white p-5"><Eye className="text-violet-600" size={21} /><p className="mt-5 text-2xl font-semibold">1,284</p><p className="mt-1 text-sm text-muted-foreground">Listing views</p></div><div className="rounded-lg border border-border bg-white p-5"><Star className="text-amber-500" size={21} /><p className="mt-5 text-2xl font-semibold">4.8</p><p className="mt-1 text-sm text-muted-foreground">Average rating</p></div></div><div className="mt-9 grid gap-7 lg:grid-cols-[1.4fr_1fr]"><section><div className="flex items-center justify-between"><h2 className="text-xl font-semibold">Recent enquiries</h2><button className="text-sm font-semibold text-primary" onClick={() => setActiveTab("enquiries")}>View all</button></div><div className="mt-4 grid gap-3">{enquiries.slice(0, 3).map((enquiry) => <button className="flex w-full items-center gap-4 rounded-lg border border-border bg-white p-4 text-left hover:border-primary" key={enquiry.id} onClick={() => setActiveTab("enquiries")}><span className="grid size-11 shrink-0 place-items-center rounded-md bg-blue-50 text-blue-700"><CalendarDays size={20} /></span><span className="min-w-0 flex-1"><strong className="block">{enquiry.eventType}</strong><span className="mt-1 block text-sm text-muted-foreground">{formatDate(enquiry.eventDate)} | {enquiry.guestCount} guests</span></span><span className={`hidden rounded-full px-2.5 py-1 text-xs font-medium sm:block ${statusStyle[enquiry.status]}`}>{formatStatus(enquiry.status)}</span><ChevronRight size={18} /></button>)}</div></section><section><h2 className="text-xl font-semibold">Listing health</h2><div className="mt-4 rounded-lg border border-border bg-white p-5"><div className="flex items-center justify-between"><span className="inline-flex items-center gap-2 font-semibold text-emerald-700"><BadgeCheck size={19} /> Approved</span><span className="text-sm font-semibold">92%</span></div><div className="mt-4 h-2 overflow-hidden rounded-full bg-muted"><div className="h-full w-[92%] bg-primary" /></div><div className="mt-5 grid gap-3 text-sm"><p className="flex items-center gap-2"><Check className="text-emerald-700" size={16} /> Profile information complete</p><p className="flex items-center gap-2"><Check className="text-emerald-700" size={16} /> Pricing and amenities added</p><p className="flex items-center gap-2 text-amber-700"><ImagePlus size={16} /> Add 3 more gallery photos</p></div><button className="mt-5 text-sm font-semibold text-primary" onClick={() => setActiveTab("listing")}>Improve listing</button></div></section></div></section>}

        {activeTab === "enquiries" && <section className="py-7"><div className="flex flex-wrap items-end justify-between gap-4"><div><h2 className="text-xl font-semibold">Enquiry inbox</h2><p className="mt-1 text-sm text-muted-foreground">Respond to event requests and update customer status.</p></div><select className="h-10 rounded-md border border-border bg-white px-3 text-sm"><option>All enquiries</option><option>Pending response</option><option>Confirmed</option><option>Declined</option></select></div><div className="mt-5 grid gap-4">{enquiries.map((enquiry) => <article className="rounded-lg border border-border bg-white p-5" key={enquiry.id}><div className="flex flex-col gap-5 lg:flex-row lg:items-center"><div className="grid size-12 shrink-0 place-items-center rounded-md bg-blue-50 text-blue-700"><UsersRound size={22} /></div><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><h3 className="font-semibold">{enquiry.eventType}</h3><span className={`rounded-full px-2.5 py-1 text-xs font-medium ${statusStyle[enquiry.status]}`}>{formatStatus(enquiry.status)}</span></div><p className="mt-2 text-sm text-muted-foreground">{formatDate(enquiry.eventDate)} | {enquiry.slot.toLowerCase().replace("_", " ")} | {enquiry.guestCount} guests</p>{enquiry.notes && <p className="mt-2 text-sm leading-6 text-muted-foreground">“{enquiry.notes}”</p>}<p className="mt-2 text-xs text-muted-foreground">{enquiry.id}</p></div>{enquiry.status === "PENDING_OWNER_RESPONSE" ? <div className="flex gap-2"><button className="inline-flex h-10 items-center gap-2 rounded-md border border-rose-200 px-3 text-sm font-semibold text-rose-700 hover:bg-rose-50" onClick={() => respondToEnquiry(enquiry.id, "DECLINED")}><Ban size={16} /> Decline</button><button className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-3 text-sm font-semibold text-white" onClick={() => respondToEnquiry(enquiry.id, "CONFIRMED")}><Check size={16} /> Confirm request</button></div> : <button className="inline-flex h-10 items-center gap-2 rounded-md border border-border px-3 text-sm font-medium"><MessageSquareText size={16} /> View details</button>}</div></article>)}</div></section>}

        {activeTab === "availability" && <section className="py-7"><div className="flex flex-wrap items-end justify-between gap-4"><div><h2 className="text-xl font-semibold">Availability calendar</h2><p className="mt-1 text-sm text-muted-foreground">Confirmed bookings and owner-blocked slots.</p></div><button className="inline-flex h-10 items-center gap-2 rounded-md bg-foreground px-4 text-sm font-semibold text-white" onClick={() => setBlockDialogOpen(true)}><Plus size={17} /> Block date</button></div><div className="mt-5 rounded-lg border border-border bg-white p-4 sm:p-6"><div className="flex items-center justify-between"><button aria-label="Previous month" className="grid size-9 place-items-center rounded-md border border-border">‹</button><h3 className="font-semibold">July 2026</h3><button aria-label="Next month" className="grid size-9 place-items-center rounded-md border border-border">›</button></div><div className="mt-5 grid grid-cols-7 text-center text-xs font-medium text-muted-foreground">{["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"].map((day) => <span className="py-2" key={day}>{day}</span>)}</div><div className="grid grid-cols-7 gap-1">{[0, 0, 0].map((_, index) => <span key={`blank-${index}`} />)}{Array.from({ length: 31 }, (_, index) => index + 1).map((day) => { const confirmed = confirmedDays.has(day); const blocked = blockedDays.has(day); return <button aria-label={`July ${day}${confirmed ? ", confirmed" : blocked ? ", blocked" : ", available"}`} className={`aspect-square min-h-10 rounded-md border text-sm ${confirmed ? "border-emerald-200 bg-emerald-50 font-semibold text-emerald-800" : blocked ? "border-rose-200 bg-rose-50 font-semibold text-rose-700" : "border-transparent hover:border-primary"}`} key={day}>{day}</button>; })}</div><div className="mt-5 flex flex-wrap gap-4 border-t border-border pt-4 text-xs text-muted-foreground"><span className="flex items-center gap-2"><i className="size-3 rounded-sm bg-emerald-100" /> Confirmed</span><span className="flex items-center gap-2"><i className="size-3 rounded-sm bg-rose-100" /> Blocked</span><span className="flex items-center gap-2"><i className="size-3 rounded-sm border border-border bg-white" /> Available</span></div></div><div className="mt-5 grid gap-3">{blockedDates.map((date) => <div className="flex items-center gap-4 rounded-lg border border-border bg-white p-4" key={date.id}><span className="grid size-10 place-items-center rounded-md bg-rose-50 text-rose-700"><CalendarDays size={19} /></span><div className="flex-1"><p className="font-medium">{formatDate(date.date)}</p><p className="mt-1 text-sm text-muted-foreground">{date.slot.toLowerCase().replace("_", " ")} | {date.reason}</p></div><button aria-label={`Remove block for ${date.date}`} className="grid size-9 place-items-center rounded-md text-muted-foreground hover:bg-muted" onClick={() => setBlockedDates((current) => current.filter((item) => item.id !== date.id))}><X size={17} /></button></div>)}</div></section>}

        {activeTab === "listing" && <section className="py-7"><div className="flex items-center justify-between"><div><h2 className="text-xl font-semibold">Hall listing</h2><p className="mt-1 text-sm text-muted-foreground">Public information and approval status.</p></div><span className="inline-flex items-center gap-2 rounded-full bg-emerald-50 px-3 py-1.5 text-sm font-semibold text-emerald-700"><BadgeCheck size={17} /> Approved</span></div><div className="mt-5 grid overflow-hidden rounded-lg border border-border bg-white lg:grid-cols-[360px_1fr]"><div className="relative min-h-64 bg-muted"><Image alt={ownerHall.name} className="object-cover" fill sizes="360px" src={ownerHall.imageUrl} /></div><div className="p-5 sm:p-7"><p className="text-sm font-semibold text-primary">{ownerHall.venueType}</p><h3 className="mt-2 text-2xl font-semibold">{ownerHall.name}</h3><p className="mt-2 flex items-center gap-2 text-sm text-muted-foreground"><MapPin size={16} /> {ownerHall.area}, {ownerHall.city}</p><p className="mt-5 max-w-2xl leading-7 text-muted-foreground">{ownerHall.description}</p><div className="mt-6 grid grid-cols-2 gap-4 border-t border-border pt-5 sm:grid-cols-4"><div><p className="text-xs text-muted-foreground">Capacity</p><p className="mt-1 font-semibold">{ownerHall.capacity}</p></div><div><p className="text-xs text-muted-foreground">Starting price</p><p className="mt-1 font-semibold">INR {new Intl.NumberFormat("en-IN").format(ownerHall.startingPrice)}</p></div><div><p className="text-xs text-muted-foreground">Amenities</p><p className="mt-1 font-semibold">{ownerHall.amenities.length}</p></div><div><p className="text-xs text-muted-foreground">Rating</p><p className="mt-1 font-semibold">{ownerHall.rating}</p></div></div><div className="mt-6 flex gap-2"><button className="h-10 rounded-md bg-primary px-4 text-sm font-semibold text-white">Edit listing</button><Link className="inline-flex h-10 items-center rounded-md border border-border px-4 text-sm font-semibold" href={`/halls/${ownerHall.id}`}>Preview</Link></div></div></div></section>}

        {activeTab === "media" && <section className="py-7"><div className="flex flex-wrap items-end justify-between gap-4"><div><h2 className="text-xl font-semibold">Photo gallery</h2><p className="mt-1 text-sm text-muted-foreground">Keep the cover and venue spaces up to date.</p></div><label className="inline-flex h-10 cursor-pointer items-center gap-2 rounded-md bg-foreground px-4 text-sm font-semibold text-white"><UploadCloud size={17} /> Upload photos<input accept="image/*" className="sr-only" multiple onChange={(event) => { const files = Array.from(event.target.files ?? []); setMedia((current) => [...current, ...files.map((file) => URL.createObjectURL(file))]); }} type="file" /></label></div><div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{media.map((image, index) => <article className="overflow-hidden rounded-lg border border-border bg-white" key={`${image}-${index}`}><div className="relative aspect-[4/3] bg-muted"><Image alt={`${ownerHall.name} gallery ${index + 1}`} className="object-cover" fill sizes="(max-width: 768px) 100vw, 33vw" src={image} unoptimized={image.startsWith("blob:")} />{index === 0 && <span className="absolute left-3 top-3 rounded-full bg-white px-2.5 py-1 text-xs font-semibold text-primary">Cover photo</span>}</div><div className="flex items-center justify-between p-3"><span className="text-sm text-muted-foreground">Photo {index + 1}</span><button aria-label={`Options for photo ${index + 1}`} className="grid size-8 place-items-center rounded-md hover:bg-muted"><MoreHorizontal size={18} /></button></div></article>)}</div></section>}

        {activeTab === "reviews" && <section className="py-7"><div><h2 className="text-xl font-semibold">Customer reviews</h2><p className="mt-1 text-sm text-muted-foreground">Verified feedback from completed events.</p></div><div className="mt-5 grid gap-6 lg:grid-cols-[280px_1fr]"><div className="h-fit rounded-lg border border-border bg-white p-6 text-center"><p className="text-5xl font-semibold">4.8</p><div className="mt-3 flex justify-center gap-1 text-amber-400">{[1, 2, 3, 4, 5].map((star) => <Star className="fill-current" key={star} size={18} />)}</div><p className="mt-2 text-sm text-muted-foreground">Based on {ownerHall.reviewCount} verified reviews</p><div className="mt-6 grid gap-2">{[5, 4, 3, 2, 1].map((rating) => <div className="grid grid-cols-[14px_1fr_28px] items-center gap-2 text-xs" key={rating}><span>{rating}</span><div className="h-1.5 overflow-hidden rounded-full bg-muted"><div className="h-full bg-amber-400" style={{ width: `${rating === 5 ? 82 : rating === 4 ? 14 : 2}%` }} /></div><span className="text-muted-foreground">{rating === 5 ? "82%" : rating === 4 ? "14%" : "2%"}</span></div>)}</div></div><div className="grid gap-3">{ownerReviews.map((review) => <article className="rounded-lg border border-border bg-white p-5" key={review.id}><div className="flex items-start justify-between gap-4"><div><div className="flex items-center gap-2"><h3 className="font-semibold">{review.name}</h3><BadgeCheck className="text-emerald-700" size={16} /></div><p className="mt-1 text-xs text-muted-foreground">{review.event} | {review.date}</p></div><div className="flex gap-1 text-amber-400">{Array.from({ length: review.rating }, (_, index) => <Star className="fill-current" key={index} size={14} />)}</div></div><p className="mt-4 text-sm leading-6 text-muted-foreground">{review.comment}</p><button className="mt-4 text-sm font-semibold text-primary">Reply</button></article>)}</div></div></section>}
      </main>

      <BlockDateDialog onAdd={addBlockedDate} onClose={() => setBlockDialogOpen(false)} open={blockDialogOpen} />
    </>
  );
}
