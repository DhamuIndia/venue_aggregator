"use client";

import {
  BadgeCheck,
  CalendarDays,
  CheckCircle2,
  ChevronRight,
  Clock3,
  Heart,
  LogOut,
  MessageSquareText,
  Star,
  UserRound
} from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { HallCard } from "@/components/halls/HallCard";
import { useAuth } from "@/features/auth/AuthProvider";
import { customerEnquiries, reviewEligibleBooking, type CustomerEnquiry } from "@/features/customer/mock-data";
import { getLocalEnquiries } from "@/features/enquiries/enquiry-client";
import { halls } from "@/features/halls/mock-data";
import { ReviewDialog } from "./ReviewDialog";

type DashboardTab = "overview" | "enquiries" | "saved" | "reviews";

const tabs: Array<{ id: DashboardTab; label: string }> = [
  { id: "overview", label: "Overview" },
  { id: "enquiries", label: "Enquiries" },
  { id: "saved", label: "Saved venues" },
  { id: "reviews", label: "Reviews" }
];

const statusStyles = {
  PENDING_OWNER_RESPONSE: "bg-blue-50 text-blue-700",
  CONFIRMED: "bg-emerald-50 text-emerald-700",
  AWAITING_RESPONSE: "bg-amber-50 text-amber-700",
  DECLINED: "bg-rose-50 text-rose-700",
  COMPLETED: "bg-muted text-muted-foreground"
};

function statusLabel(status: keyof typeof statusStyles) {
  return status.toLowerCase().replace(/_/g, " ");
}

export function CustomerDashboard() {
  const { logout, user } = useAuth();
  const router = useRouter();
  const [activeTab, setActiveTab] = useState<DashboardTab>("overview");
  const [reviewOpen, setReviewOpen] = useState(false);
  const [reviewSubmitted, setReviewSubmitted] = useState(false);
  const [enquiries, setEnquiries] = useState<CustomerEnquiry[]>(customerEnquiries);
  const savedHalls = halls.slice(0, 2);

  useEffect(() => {
    const requestedTab = new URLSearchParams(window.location.search).get("tab");
    if (tabs.some((tab) => tab.id === requestedTab)) setActiveTab(requestedTab as DashboardTab);

    const localEnquiries: CustomerEnquiry[] = getLocalEnquiries().map((enquiry) => ({
      id: enquiry.id,
      venue: enquiry.hallName,
      eventDate: new Intl.DateTimeFormat("en-IN", { dateStyle: "long" }).format(new Date(`${enquiry.eventDate}T00:00:00`)),
      submittedAt: enquiry.submittedAt,
      status: enquiry.status
    }));
    setEnquiries([...localEnquiries, ...customerEnquiries]);
  }, []);

  function signOut() {
    logout();
    router.push("/");
  }

  return (
    <>
      <main className="mx-auto w-full max-w-7xl px-4 py-7 sm:px-6 sm:py-10">
        <div className="flex flex-wrap items-center justify-between gap-5">
          <div className="flex items-center gap-4">
            <span className="grid size-12 place-items-center rounded-full bg-emerald-50 text-lg font-semibold text-emerald-800">{user?.fullName.charAt(0)}</span>
            <div><p className="text-sm text-muted-foreground">Welcome back</p><h1 className="text-2xl font-semibold sm:text-3xl">{user?.fullName}</h1></div>
          </div>
          <button className="inline-flex h-10 items-center gap-2 rounded-md border border-border bg-white px-3 text-sm font-medium hover:border-foreground" onClick={signOut} type="button"><LogOut size={17} /> Sign out</button>
        </div>

        <div className="mt-8 flex gap-1 overflow-x-auto border-b border-border" role="tablist" aria-label="Customer account">
          {tabs.map((tab) => (
            <button aria-selected={activeTab === tab.id} className={`shrink-0 border-b-2 px-4 py-3 text-sm font-medium ${activeTab === tab.id ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:text-foreground"}`} key={tab.id} onClick={() => setActiveTab(tab.id)} role="tab" type="button">{tab.label}</button>
          ))}
        </div>

        {activeTab === "overview" && (
          <div className="py-7">
            <div className="grid gap-4 sm:grid-cols-3">
              <div className="rounded-lg border border-border bg-white p-5"><MessageSquareText className="text-primary" size={21} /><p className="mt-5 text-2xl font-semibold">{enquiries.length}</p><p className="mt-1 text-sm text-muted-foreground">Total enquiries</p></div>
              <div className="rounded-lg border border-border bg-white p-5"><CalendarDays className="text-primary" size={21} /><p className="mt-5 text-2xl font-semibold">1</p><p className="mt-1 text-sm text-muted-foreground">Upcoming event</p></div>
              <div className="rounded-lg border border-border bg-white p-5"><Heart className="text-primary" size={21} /><p className="mt-5 text-2xl font-semibold">2</p><p className="mt-1 text-sm text-muted-foreground">Saved venues</p></div>
            </div>

            <section className="mt-9">
              <div className="flex items-center justify-between gap-4"><h2 className="text-xl font-semibold">Upcoming event</h2><button className="text-sm font-semibold text-primary" onClick={() => setActiveTab("enquiries")}>View enquiries</button></div>
              <article className="mt-4 flex flex-col gap-5 rounded-lg border border-border bg-white p-5 sm:flex-row sm:items-center">
                <div className="grid size-14 shrink-0 place-items-center rounded-md bg-emerald-50 text-emerald-700"><CalendarDays size={25} /></div>
                <div className="min-w-0 flex-1"><div className="flex items-center gap-2"><h3 className="font-semibold">Emerald Convention Centre</h3><BadgeCheck className="text-emerald-700" size={17} /></div><p className="mt-1 text-sm text-muted-foreground">18 July 2026, Full day slot</p></div>
                <span className="w-fit rounded-full bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700">Confirmed</span>
              </article>
            </section>

            <section className="mt-9">
              <div className="flex items-center justify-between gap-4"><h2 className="text-xl font-semibold">Recent activity</h2><button className="text-sm font-semibold text-primary" onClick={() => setActiveTab("reviews")}>View reviews</button></div>
              <button className="mt-4 flex w-full items-center gap-4 rounded-lg border border-border bg-white p-5 text-left hover:border-primary" onClick={() => setActiveTab("reviews")}>
                <span className="grid size-11 shrink-0 place-items-center rounded-md bg-amber-50 text-amber-600"><Star size={21} /></span>
                <span className="min-w-0 flex-1"><strong className="block">Review Marigold Mini Hall</strong><span className="mt-1 block text-sm text-muted-foreground">Your completed event is eligible for a verified review.</span></span>
                <ChevronRight className="text-muted-foreground" size={19} />
              </button>
            </section>
          </div>
        )}

        {activeTab === "enquiries" && (
          <section className="py-7"><h2 className="text-xl font-semibold">Your enquiries</h2><p className="mt-1 text-sm text-muted-foreground">Track responses and confirmed event details.</p><div className="mt-5 grid gap-3">{enquiries.map((enquiry) => <article className="grid gap-4 rounded-lg border border-border bg-white p-5 sm:grid-cols-[1fr_auto] sm:items-center" key={enquiry.id}><div><div className="flex flex-wrap items-center gap-2"><h3 className="font-semibold">{enquiry.venue}</h3><span className={`rounded-full px-2.5 py-1 text-xs font-medium ${statusStyles[enquiry.status]}`}>{statusLabel(enquiry.status)}</span></div><p className="mt-2 text-sm text-muted-foreground">Event: {enquiry.eventDate} | Enquiry {enquiry.id}</p></div><button className="inline-flex h-9 w-fit items-center gap-2 rounded-md border border-border px-3 text-sm font-medium hover:border-primary">View details <ChevronRight size={16} /></button></article>)}</div></section>
        )}

        {activeTab === "saved" && (
          <section className="py-7"><h2 className="text-xl font-semibold">Saved venues</h2><p className="mt-1 text-sm text-muted-foreground">Your shortlist for easy comparison.</p><div className="mt-5 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">{savedHalls.map((hall) => <HallCard hall={hall} key={hall.id} />)}</div></section>
        )}

        {activeTab === "reviews" && (
          <section className="py-7">
            <h2 className="text-xl font-semibold">Your reviews</h2><p className="mt-1 text-sm text-muted-foreground">Only completed bookings can receive a verified review.</p>
            {reviewSubmitted ? (
              <div className="mt-5 flex items-start gap-3 rounded-lg border border-emerald-200 bg-emerald-50 p-5"><CheckCircle2 className="mt-0.5 shrink-0 text-emerald-700" size={21} /><div><h3 className="font-semibold">Review submitted</h3><p className="mt-1 text-sm text-muted-foreground">Your verified review for {reviewEligibleBooking.venue} is pending moderation.</p></div></div>
            ) : (
              <article className="mt-5 rounded-lg border border-border bg-white p-5">
                <div className="flex flex-col gap-5 sm:flex-row sm:items-center"><span className="grid size-12 shrink-0 place-items-center rounded-md bg-emerald-50 text-emerald-700"><BadgeCheck size={23} /></span><div className="min-w-0 flex-1"><div className="flex items-center gap-2"><h3 className="font-semibold">{reviewEligibleBooking.venue}</h3><span className="rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-medium text-emerald-700">Verified service</span></div><p className="mt-2 text-sm text-muted-foreground">{reviewEligibleBooking.serviceType} | {reviewEligibleBooking.eventDate}</p></div><button className="h-10 rounded-md bg-primary px-4 text-sm font-semibold text-white" onClick={() => setReviewOpen(true)}>Write review</button></div>
              </article>
            )}
            <div className="mt-8 flex items-center gap-3 border-t border-border pt-6 text-sm text-muted-foreground"><Clock3 size={18} /><span>Reviews appear publicly after moderation.</span></div>
          </section>
        )}
      </main>

      <ReviewDialog onClose={() => setReviewOpen(false)} onSubmitted={() => { setReviewSubmitted(true); setReviewOpen(false); }} open={reviewOpen} venueName={reviewEligibleBooking.venue} />
    </>
  );
}
