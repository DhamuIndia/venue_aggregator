"use client";

import {
  Activity,
  BadgeCheck,
  Building2,
  Check,
  ChevronRight,
  CircleAlert,
  ClipboardCheck,
  FileCheck2,
  MessageSquareWarning,
  ShieldCheck,
  Store,
  X,
  XCircle
} from "lucide-react";
import Image from "next/image";
import { useEffect, useMemo, useState } from "react";
import { RejectionDialog } from "@/components/admin/RejectionDialog";
import { useAuth } from "@/features/auth/AuthProvider";
import { getAdminQueues, moderateAdminReview, reviewAdminHall, reviewAdminVendor } from "@/features/admin/admin-client";
import {
  adminEnquiries,
  auditEvents as initialAuditEvents,
  initialReportedReviews,
  initialVendorApplications,
  initialVenueApplications,
  type ModerationStatus,
  type ReportedReview,
  type VendorApplication,
  type VenueApplication
} from "@/features/admin/mock-data";
import type { EnquiryStatus } from "@/features/enquiries/types";

type AdminTab = "overview" | "venues" | "vendors" | "reviews" | "enquiries";
type RejectTarget = { kind: "venue" | "vendor"; id: string; name: string };

const tabs: { id: AdminTab; label: string }[] = [
  { id: "overview", label: "Overview" },
  { id: "venues", label: "Venue approvals" },
  { id: "vendors", label: "Vendor approvals" },
  { id: "reviews", label: "Reviews" },
  { id: "enquiries", label: "Enquiries" }
];

const moderationStyle: Record<ModerationStatus, string> = {
  PENDING_APPROVAL: "bg-amber-50 text-amber-800",
  APPROVED: "bg-emerald-50 text-emerald-800",
  REJECTED: "bg-rose-50 text-rose-700"
};

const enquiryStyle: Record<EnquiryStatus, string> = {
  NEW: "bg-blue-50 text-blue-700",
  PENDING_OWNER_RESPONSE: "bg-blue-50 text-blue-700",
  CONFIRMED: "bg-emerald-50 text-emerald-800",
  DECLINED: "bg-rose-50 text-rose-700",
  COMPLETED: "bg-violet-50 text-violet-700"
};

function readableStatus(status: string) {
  return status.toLowerCase().replaceAll("_", " ");
}

function formatPrice(value: number) {
  return `INR ${new Intl.NumberFormat("en-IN").format(value)}`;
}

export function AdminDashboard() {
  const { accessToken } = useAuth();
  const [activeTab, setActiveTab] = useState<AdminTab>("overview");
  const [venues, setVenues] = useState<VenueApplication[]>(initialVenueApplications);
  const [vendors, setVendors] = useState<VendorApplication[]>(initialVendorApplications);
  const [reviews, setReviews] = useState<ReportedReview[]>(initialReportedReviews);
  const [enquiries, setEnquiries] = useState(adminEnquiries);
  const [auditEvents, setAuditEvents] = useState(initialAuditEvents);
  const [isLoadingQueues, setIsLoadingQueues] = useState(true);
  const [adminError, setAdminError] = useState("");
  const [venueFilter, setVenueFilter] = useState<"ALL" | ModerationStatus>("PENDING_APPROVAL");
  const [enquiryFilter, setEnquiryFilter] = useState<"ALL" | EnquiryStatus>("ALL");
  const [rejectTarget, setRejectTarget] = useState<RejectTarget | null>(null);
  const [notice, setNotice] = useState("");

  const pendingVenueCount = venues.filter((venue) => venue.status === "PENDING_APPROVAL").length;
  const pendingVendorCount = vendors.filter((vendor) => vendor.status === "PENDING_APPROVAL").length;
  const reportedReviewCount = reviews.filter((review) => review.status === "REPORTED").length;
  const pendingEnquiryCount = enquiries.filter((enquiry) => enquiry.status === "PENDING_OWNER_RESPONSE").length;

  const filteredVenues = useMemo(
    () => venueFilter === "ALL" ? venues : venues.filter((venue) => venue.status === venueFilter),
    [venueFilter, venues]
  );
  const filteredEnquiries = useMemo(
    () => enquiryFilter === "ALL" ? enquiries : enquiries.filter((enquiry) => enquiry.status === enquiryFilter),
    [enquiries, enquiryFilter]
  );

  useEffect(() => {
    let isCurrent = true;

    async function loadAdminQueues() {
      setIsLoadingQueues(true);
      setAdminError("");

      try {
        const response = await getAdminQueues(accessToken);
        if (!isCurrent) return;
        setVenues(response.venues);
        setVendors(response.vendors);
        setReviews(response.reviews);
        setEnquiries(response.enquiries);
        setAuditEvents(response.auditEvents);
      } catch {
        if (!isCurrent) return;
        setAdminError("Could not load latest admin queues.");
      } finally {
        if (isCurrent) setIsLoadingQueues(false);
      }
    }

    loadAdminQueues();

    return () => {
      isCurrent = false;
    };
  }, [accessToken]);

  async function updateVenue(id: string, status: Exclude<ModerationStatus, "PENDING_APPROVAL">, reason = "All venue and ownership documents verified.") {
    try {
      const updatedVenue = await reviewAdminHall(id, status, reason, accessToken);
      setVenues((current) => current.map((venue) => venue.id === id ? { ...venue, ...updatedVenue, status } : venue));
      setNotice(status === "APPROVED" ? "Venue approved and queued for publication." : "Venue rejected with feedback for the owner.");
    } catch (exception) {
      setNotice(exception instanceof Error ? exception.message : "Could not update venue approval.");
    }
  }

  async function updateVendor(id: string, status: Exclude<ModerationStatus, "PENDING_APPROVAL">, reason = "Business identity and service details verified.") {
    try {
      const updatedVendor = await reviewAdminVendor(id, status, reason, accessToken);
      setVendors((current) => current.map((vendor) => vendor.id === id ? { ...vendor, ...updatedVendor, status } : vendor));
      setNotice(status === "APPROVED" ? "Vendor approved and notified." : "Vendor rejected with correction guidance.");
    } catch (exception) {
      setNotice(exception instanceof Error ? exception.message : "Could not update vendor approval.");
    }
  }

  function rejectWithReason(_reason: string) {
    if (!rejectTarget) return;
    if (rejectTarget.kind === "venue") void updateVenue(rejectTarget.id, "REJECTED", _reason);
    else void updateVendor(rejectTarget.id, "REJECTED", _reason);
    setRejectTarget(null);
  }

  async function moderateReview(id: string, status: "PUBLISHED" | "HIDDEN") {
    const reason = status === "HIDDEN" ? "Contains content that violates marketplace review rules." : "Report dismissed after moderation review.";
    try {
      const updatedReview = await moderateAdminReview(id, status, reason, accessToken);
      setReviews((current) => current.map((review) => review.id === id ? { ...review, ...updatedReview, status } : review));
      setNotice(status === "HIDDEN" ? "Review hidden and the moderation action was logged." : "Report dismissed and review kept published.");
    } catch (exception) {
      setNotice(exception instanceof Error ? exception.message : "Could not update review moderation.");
    }
  }

  const tabBadge: Partial<Record<AdminTab, number>> = {
    venues: pendingVenueCount,
    vendors: pendingVendorCount,
    reviews: reportedReviewCount,
    enquiries: pendingEnquiryCount
  };

  return (
    <main className="min-h-[calc(100vh-4rem)] bg-[#f7f8fa]">
      <div className="mx-auto w-full max-w-7xl px-4 py-7 sm:px-6 sm:py-9">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="inline-flex items-center gap-2 text-sm font-semibold text-primary"><ShieldCheck size={17} /> Admin workspace</p>
            <h1 className="mt-2 text-3xl font-semibold">Platform operations</h1>
            <p className="mt-2 text-sm text-muted-foreground">Approvals, moderation, and marketplace activity.</p>
          </div>
          <div className="flex items-center gap-3 rounded-md border border-border bg-white px-4 py-3 text-sm">
            <span className="relative flex size-2"><span className="absolute inline-flex size-full animate-ping rounded-full bg-emerald-400 opacity-60" /><span className="relative inline-flex size-2 rounded-full bg-emerald-600" /></span>
            <span><strong className="block font-medium">Operations healthy</strong><span className="text-xs text-muted-foreground">Last synced just now</span></span>
          </div>
        </div>

        {notice && <div className="mt-6 flex items-center gap-3 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800" role="status"><BadgeCheck size={18} /><span className="flex-1">{notice}</span><button aria-label="Dismiss notification" className="grid size-8 place-items-center rounded-md hover:bg-emerald-100" onClick={() => setNotice("")}><X size={16} /></button></div>}
        {adminError && <div className="mt-6 rounded-md bg-rose-50 px-4 py-3 text-sm text-rose-700" role="alert">{adminError}</div>}

        <div className="mt-7 overflow-x-auto border-b border-border">
          <div aria-label="Admin dashboard" className="flex min-w-max gap-7" role="tablist">
            {tabs.map((tab) => <button aria-selected={activeTab === tab.id} className={`flex h-12 items-center gap-2 border-b-2 px-1 text-sm font-medium ${activeTab === tab.id ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:text-foreground"}`} key={tab.id} onClick={() => setActiveTab(tab.id)} role="tab">{tab.label}{tabBadge[tab.id] ? <span className="grid min-w-5 place-items-center rounded-full bg-blue-50 px-1.5 py-0.5 text-xs text-blue-700">{tabBadge[tab.id]}</span> : null}</button>)}
          </div>
        </div>

        {activeTab === "overview" && (
          <section className="py-7">
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {[
                { label: "Pending venues", value: pendingVenueCount, icon: Building2, color: "text-blue-700", tab: "venues" as const },
                { label: "Pending vendors", value: pendingVendorCount, icon: Store, color: "text-violet-700", tab: "vendors" as const },
                { label: "Reported reviews", value: reportedReviewCount, icon: MessageSquareWarning, color: "text-rose-700", tab: "reviews" as const },
                { label: "Enquiries this month", value: 284, icon: Activity, color: "text-emerald-700", tab: "enquiries" as const }
              ].map((stat) => <button className="rounded-lg border border-border bg-white p-5 text-left hover:border-primary" key={stat.label} onClick={() => setActiveTab(stat.tab)}><stat.icon className={stat.color} size={21} /><p className="mt-5 text-2xl font-semibold">{stat.value}</p><p className="mt-1 text-sm text-muted-foreground">{stat.label}</p></button>)}
            </div>

            <div className="mt-9 grid gap-8 lg:grid-cols-[1.45fr_1fr]">
              <section>
                <div className="flex items-center justify-between gap-4"><div><h2 className="text-xl font-semibold">Venue approval queue</h2><p className="mt-1 text-sm text-muted-foreground">Oldest complete applications first.</p></div><button className="text-sm font-semibold text-primary" onClick={() => setActiveTab("venues")}>Review all</button></div>
                <div className="mt-4 grid gap-3">
                  {isLoadingQueues ? [1, 2, 3].map((item) => <div className="h-[88px] animate-pulse rounded-lg border border-border bg-white" key={item} />) : venues.filter((venue) => venue.status === "PENDING_APPROVAL").map((venue) => <button className="flex w-full items-center gap-4 rounded-lg border border-border bg-white p-4 text-left hover:border-primary" key={venue.id} onClick={() => setActiveTab("venues")}><span className="relative size-14 shrink-0 overflow-hidden rounded-md bg-muted"><Image alt="" className="object-cover" fill sizes="56px" src={venue.imageUrl} /></span><span className="min-w-0 flex-1"><strong className="block truncate">{venue.name}</strong><span className="mt-1 block truncate text-sm text-muted-foreground">{venue.location} | {venue.capacity} guests</span></span><span className="hidden text-xs text-muted-foreground sm:block">{venue.id}</span><ChevronRight className="shrink-0" size={18} /></button>)}
                </div>
              </section>

              <section>
                <div className="flex items-center justify-between"><h2 className="text-xl font-semibold">Recent audit activity</h2><ClipboardCheck className="text-muted-foreground" size={20} /></div>
                <div className="mt-4 divide-y divide-border rounded-lg border border-border bg-white px-5">
                  {auditEvents.map((event) => <div className="py-4" key={event.id}><div className="flex items-center justify-between gap-3"><p className="font-medium">{event.action}</p><span className="text-xs text-muted-foreground">{event.timestamp}</span></div><p className="mt-1 text-sm text-muted-foreground">{event.subject} | {event.actor}</p></div>)}
                </div>
              </section>
            </div>
          </section>
        )}

        {activeTab === "venues" && (
          <section className="py-7">
            <div className="flex flex-wrap items-end justify-between gap-4"><div><h2 className="text-xl font-semibold">Venue applications</h2><p className="mt-1 text-sm text-muted-foreground">Verify listing details and ownership documents.</p></div><label className="text-xs font-medium text-muted-foreground">Status<select className="mt-1 block h-10 rounded-md border border-border bg-white px-3 text-sm text-foreground" onChange={(event) => setVenueFilter(event.target.value as "ALL" | ModerationStatus)} value={venueFilter}><option value="ALL">All applications</option><option value="PENDING_APPROVAL">Pending approval</option><option value="APPROVED">Approved</option><option value="REJECTED">Rejected</option></select></label></div>
            <div className="mt-5 grid gap-4">
              {filteredVenues.map((venue) => {
                const complete = Object.values(venue.documents).every(Boolean);
                return <article className="rounded-lg border border-border bg-white p-4 sm:p-5" key={venue.id}><div className="grid gap-5 lg:grid-cols-[160px_minmax(0,1fr)_220px]"><div className="relative aspect-[4/3] overflow-hidden rounded-md bg-muted lg:aspect-auto lg:min-h-32"><Image alt={`${venue.name} application`} className="object-cover" fill sizes="(min-width: 1024px) 160px, 100vw" src={venue.imageUrl} /></div><div><div className="flex flex-wrap items-center gap-2"><h3 className="text-lg font-semibold">{venue.name}</h3><span className={`rounded-full px-2.5 py-1 text-xs font-medium ${moderationStyle[venue.status]}`}>{readableStatus(venue.status)}</span></div><p className="mt-2 text-sm text-muted-foreground">{venue.location} | {venue.venueType} | {venue.capacity} guests</p><p className="mt-3 text-sm"><strong className="font-medium">Owner:</strong> {venue.ownerName} | {venue.ownerPhone}</p><p className="mt-1 text-sm"><strong className="font-medium">Starting price:</strong> {formatPrice(venue.startingPrice)}</p><p className="mt-3 text-xs text-muted-foreground">Submitted {new Date(venue.submittedAt).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" })} | {venue.id}</p></div><div><p className="text-xs font-semibold uppercase text-muted-foreground">Document checks</p><div className="mt-3 grid gap-2 text-sm">{[{ label: "Ownership", ready: venue.documents.ownership }, { label: "Identity", ready: venue.documents.identity }, { label: "Address", ready: venue.documents.address }].map((document) => <p className={`flex items-center gap-2 ${document.ready ? "text-emerald-700" : "text-amber-700"}`} key={document.label}>{document.ready ? <FileCheck2 size={16} /> : <CircleAlert size={16} />}{document.label}</p>)}</div>{venue.status === "PENDING_APPROVAL" && <div className="mt-5 grid grid-cols-2 gap-2"><button className="inline-flex h-10 items-center justify-center gap-2 rounded-md border border-rose-200 text-sm font-semibold text-rose-700" onClick={() => setRejectTarget({ kind: "venue", id: venue.id, name: venue.ownerName })}><XCircle size={17} /> Reject</button><button className="inline-flex h-10 items-center justify-center gap-2 rounded-md bg-primary text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-45" disabled={!complete} onClick={() => updateVenue(venue.id, "APPROVED")} title={complete ? "Approve venue" : "Complete all document checks first"}><Check size={17} /> Approve</button></div>}</div></div></article>;
              })}
              {filteredVenues.length === 0 && <p className="rounded-lg border border-dashed border-border bg-white px-5 py-12 text-center text-sm text-muted-foreground">No venue applications match this filter.</p>}
            </div>
          </section>
        )}

        {activeTab === "vendors" && (
          <section className="py-7">
            <div><h2 className="text-xl font-semibold">Vendor applications</h2><p className="mt-1 text-sm text-muted-foreground">Review service category and business identity.</p></div>
            <div className="mt-5 overflow-hidden rounded-lg border border-border bg-white">
              <div className="hidden grid-cols-[1.4fr_1fr_1fr_120px_220px] gap-4 border-b border-border bg-muted/60 px-5 py-3 text-xs font-semibold uppercase text-muted-foreground md:grid"><span>Business</span><span>Category</span><span>Submitted</span><span>Status</span><span className="text-right">Actions</span></div>
              {isLoadingQueues ? [1, 2, 3].map((item) => <div className="h-[76px] animate-pulse border-b border-border bg-white last:border-0" key={item} />) : vendors.map((vendor) => <article className="grid gap-3 border-b border-border px-5 py-4 last:border-0 md:grid-cols-[1.4fr_1fr_1fr_120px_220px] md:items-center" key={vendor.id}><div><h3 className="font-semibold">{vendor.businessName}</h3><p className="mt-1 text-sm text-muted-foreground">{vendor.contactName} | {vendor.city} | {vendor.id}</p></div><p className="text-sm"><span className="text-muted-foreground md:hidden">Category: </span>{vendor.category}</p><p className="text-sm text-muted-foreground">{vendor.submittedAt}</p><span className={`w-fit rounded-full px-2.5 py-1 text-xs font-medium ${moderationStyle[vendor.status]}`}>{readableStatus(vendor.status)}</span>{vendor.status === "PENDING_APPROVAL" ? <div className="flex gap-2 md:justify-end"><button aria-label={`Reject ${vendor.businessName}`} className="grid size-9 place-items-center rounded-md border border-rose-200 text-rose-700" onClick={() => setRejectTarget({ kind: "vendor", id: vendor.id, name: vendor.contactName })} title="Reject"><X size={17} /></button><button className="inline-flex h-9 items-center gap-2 rounded-md bg-primary px-3 text-sm font-semibold text-white" onClick={() => updateVendor(vendor.id, "APPROVED")}><Check size={16} /> Approve</button></div> : <span />}</article>)}
            </div>
          </section>
        )}

        {activeTab === "reviews" && (
          <section className="py-7">
            <div><h2 className="text-xl font-semibold">Reported reviews</h2><p className="mt-1 text-sm text-muted-foreground">Moderate reports while preserving verified customer feedback.</p></div>
            <div className="mt-5 grid gap-4">
              {reviews.map((review) => <article className="rounded-lg border border-border bg-white p-5" key={review.id}><div className="flex flex-wrap items-start justify-between gap-4"><div><div className="flex items-center gap-2"><h3 className="font-semibold">{review.hallName}</h3>{review.verifiedService && <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-1 text-xs font-medium text-emerald-800"><BadgeCheck size={13} /> Verified service</span>}</div><p className="mt-1 text-sm text-muted-foreground">{review.customerName} | {review.rating}/5 | {review.id}</p></div><span className={`rounded-full px-2.5 py-1 text-xs font-medium ${review.status === "REPORTED" ? "bg-rose-50 text-rose-700" : review.status === "HIDDEN" ? "bg-slate-100 text-slate-700" : "bg-emerald-50 text-emerald-800"}`}>{review.status.toLowerCase()}</span></div><blockquote className="mt-4 border-l-2 border-border pl-4 text-sm leading-6">&ldquo;{review.comment}&rdquo;</blockquote><p className="mt-4 inline-flex items-center gap-2 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800"><CircleAlert size={16} /> Report: {review.reportReason}</p>{review.status === "REPORTED" && <div className="mt-5 flex flex-wrap gap-2 border-t border-border pt-4"><button className="inline-flex h-10 items-center gap-2 rounded-md border border-border px-4 text-sm font-semibold" onClick={() => moderateReview(review.id, "PUBLISHED")}><Check size={17} /> Keep published</button><button className="inline-flex h-10 items-center gap-2 rounded-md bg-foreground px-4 text-sm font-semibold text-white" onClick={() => moderateReview(review.id, "HIDDEN")}><MessageSquareWarning size={17} /> Hide review</button></div>}</article>)}
            </div>
          </section>
        )}

        {activeTab === "enquiries" && (
          <section className="py-7">
            <div className="flex flex-wrap items-end justify-between gap-4"><div><h2 className="text-xl font-semibold">Enquiry tracking</h2><p className="mt-1 text-sm text-muted-foreground">Marketplace-wide status visibility for support and reconciliation.</p></div><label className="text-xs font-medium text-muted-foreground">Status<select className="mt-1 block h-10 rounded-md border border-border bg-white px-3 text-sm text-foreground" onChange={(event) => setEnquiryFilter(event.target.value as "ALL" | EnquiryStatus)} value={enquiryFilter}><option value="ALL">All enquiries</option><option value="PENDING_OWNER_RESPONSE">Pending owner response</option><option value="CONFIRMED">Confirmed</option><option value="DECLINED">Declined</option><option value="COMPLETED">Completed</option></select></label></div>
            <div className="mt-5 overflow-hidden rounded-lg border border-border bg-white">
              <div className="hidden grid-cols-[130px_1.4fr_1fr_1fr_150px] gap-4 border-b border-border bg-muted/60 px-5 py-3 text-xs font-semibold uppercase text-muted-foreground md:grid"><span>ID</span><span>Venue</span><span>Customer</span><span>Event date</span><span>Status</span></div>
              {filteredEnquiries.map((enquiry) => <article className="grid gap-2 border-b border-border px-5 py-4 last:border-0 md:grid-cols-[130px_1.4fr_1fr_1fr_150px] md:items-center" key={enquiry.id}><p className="text-sm font-medium">{enquiry.id}</p><div><p className="font-medium">{enquiry.hallName}</p><p className="mt-1 text-xs text-muted-foreground md:hidden">Submitted {enquiry.submittedAt}</p></div><p className="text-sm">{enquiry.customerName}</p><p className="text-sm text-muted-foreground">{enquiry.eventDate}</p><span className={`w-fit rounded-full px-2.5 py-1 text-xs font-medium ${enquiryStyle[enquiry.status]}`}>{readableStatus(enquiry.status)}</span></article>)}
            </div>
          </section>
        )}
      </div>

      {rejectTarget && <RejectionDialog onClose={() => setRejectTarget(null)} onReject={rejectWithReason} subject={rejectTarget.name} />}
    </main>
  );
}
