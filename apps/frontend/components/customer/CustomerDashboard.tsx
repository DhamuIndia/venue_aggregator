"use client";

import {
  BadgeCheck,
  CalendarDays,
  CheckCircle2,
  ChevronRight,
  ClipboardList,
  Clock3,
  X,
  CreditCard,
  Heart,
  LoaderCircle,
  MessageSquareText,
  Plus,
  Star,
  UserRound
} from "lucide-react";
import Link from "next/link";
import type { Route } from "next";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { HallCard } from "@/components/halls/HallCard";
import { NotificationActivity } from "@/components/notifications/NotificationCenter";
import { VenueCompare } from "@/components/customer/VenueCompare";
import { formatGuestCount } from "@/lib/display-format";
import { useAuth } from "@/features/auth/AuthProvider";
import { bookingFromEnquiry, getCustomerBookings, type BookingItem, type BookingStatus } from "@/features/bookings/booking-client";
import { createBookingAdvanceOrder, verifyBookingAdvancePayment } from "@/features/bookings/payment-client";
import { customerEnquiries, reviewEligibleBooking, type CustomerEnquiry } from "@/features/customer/mock-data";
import { getVendorReviewEligibility, submitVendorReview, getCustomerReviewEligibility, submitCustomerReview, type ReviewEligibility } from "@/features/customer/review-client";
import { getCustomerSavedHalls, subscribeToSavedHallChanges } from "@/features/customer/saved-halls-client";
import { getCustomerEnquiries } from "@/features/enquiries/enquiry-client";
import type { StoredEnquiry } from "@/features/enquiries/types";
import { halls } from "@/features/halls/mock-data";
import { formatSlot } from "@/features/halls/slot-model";
import type { HallSummary } from "@/features/halls/types";
import { getCustomerQuotes } from "@/features/quotes/quote-client";
import type { VendorQuote } from "@/features/quotes/types";
import { getCustomerRequirements } from "@/features/requirements/requirements-client";
import type { CustomerRequirement, CustomerRequirementStatus, PreferredContactChannel } from "@/features/requirements/types";
import { getCustomerVendorLeads } from "@/features/vendors/lead-client";
import type { VendorLead } from "@/features/vendors/types";
import { ReviewDialog } from "./ReviewDialog";

type DashboardTab = "overview" | "requirements" | "enquiries" | "bookings" | "saved" | "reviews" | "activity";

const tabs: Array<{ id: DashboardTab; label: string }> = [
  { id: "overview", label: "Overview" },
  { id: "requirements", label: "Requirements" },
  { id: "enquiries", label: "Enquiries" },
  { id: "bookings", label: "Bookings" },
  { id: "saved", label: "Saved venues" },
  { id: "reviews", label: "Reviews" },
  { id: "activity", label: "Activity" }
];

const statusStyles = {
  NEW: "bg-blue-50 text-blue-700",
  PENDING_OWNER_RESPONSE: "bg-blue-50 text-blue-700",
  CONFIRMED: "bg-emerald-50 text-emerald-700",
  AWAITING_RESPONSE: "bg-amber-50 text-amber-700",
  DECLINED: "bg-rose-50 text-rose-700",
  COMPLETED: "bg-muted text-muted-foreground"
};

const bookingStatusStyles: Record<BookingStatus, string> = {
  REQUESTED: "bg-blue-50 text-blue-700",
  CONFIRMED: "bg-emerald-50 text-emerald-700",
  CANCELLED: "bg-rose-50 text-rose-700",
  COMPLETED: "bg-muted text-muted-foreground"
};

const requirementStatusStyles: Record<CustomerRequirementStatus, string> = {
  OPEN: "bg-blue-50 text-blue-700",
  CLOSED: "bg-emerald-50 text-emerald-700",
  CANCELLED: "bg-rose-50 text-rose-700",
  EXPIRED: "bg-muted text-muted-foreground"
};

function statusLabel(status: keyof typeof statusStyles) {
  return status.toLowerCase().replace(/_/g, " ");
}

function bookingStatusLabel(status: BookingStatus) {
  return status.toLowerCase().replace(/_/g, " ");
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-IN", { dateStyle: "medium" }).format(new Date(`${value}T00:00:00`));
}

function formatMoney(value: number) {
  return new Intl.NumberFormat("en-IN").format(value);
}

function requirementBudget(requirement: CustomerRequirement) {
  if (requirement.budgetMin !== undefined && requirement.budgetMax !== undefined) {
    return `INR ${formatMoney(requirement.budgetMin)}–${formatMoney(requirement.budgetMax)}`;
  }
  if (requirement.budgetMin !== undefined) return `From INR ${formatMoney(requirement.budgetMin)}`;
  if (requirement.budgetMax !== undefined) return `Up to INR ${formatMoney(requirement.budgetMax)}`;
  return "Not specified";
}

function contactChannelLabel(channel: PreferredContactChannel) {
  return channel === "IN_APP" ? "VenueMart" : channel === "WHATSAPP" ? "WhatsApp" : channel.toLowerCase().replace(/^./, (letter) => letter.toUpperCase());
}

function formatSubmittedDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat("en-IN", { dateStyle: "medium" }).format(date);
}

function advanceAmount(booking: BookingItem) {
  if (booking.amount && booking.amount > 0) return Math.max(5000, Math.round(booking.amount * 0.2));
  return 25000;
}

function bookingDetailLine(booking: BookingItem) {
  if (booking.guestCount > 0) {
    return `${formatDate(booking.eventDate)} | ${formatSlot(booking.slot)} | ${formatGuestCount(booking.guestCount)} guests`;
  }

  return `${formatDate(booking.eventDate)} | Service booking`;
}

const fallbackReviewEligibility: ReviewEligibility = {
  eligible: reviewEligibleBooking.verified,
  enquiryId: reviewEligibleBooking.enquiryId,
  hallName: reviewEligibleBooking.venue,
  eventDate: reviewEligibleBooking.eventDate,
  eventType: reviewEligibleBooking.serviceType,
  reason: null
};

const useCustomerDemoFallbacks = process.env.NEXT_PUBLIC_AUTH_MODE !== "api";
const useCustomerEnquiryDemoFallback = useCustomerDemoFallbacks || process.env.NEXT_PUBLIC_ENQUIRIES_MODE === "mock";
const useCustomerBookingDemoFallback = useCustomerDemoFallbacks || process.env.NEXT_PUBLIC_BOOKINGS_MODE === "mock";
const useCustomerReviewDemoFallback = useCustomerDemoFallbacks || process.env.NEXT_PUBLIC_CUSTOMER_REVIEWS_MODE === "mock";

function emptyReviewEligibility(reason = "Completed eligible services will appear here."): ReviewEligibility {
  return {
    eligible: false,
    enquiryId: "",
    hallName: "",
    eventDate: "",
    reason
  };
}

export function CustomerDashboard() {
  const { accessToken, user } = useAuth();
  const router = useRouter();
  const [activeTab, setActiveTab] = useState<DashboardTab>("overview");
  const [reviewOpen, setReviewOpen] = useState(false);
  const [reviewSubmitted, setReviewSubmitted] = useState(false);
  const [reviewEligibility, setReviewEligibility] = useState<ReviewEligibility>(() => useCustomerReviewDemoFallback ? fallbackReviewEligibility : emptyReviewEligibility());
  const [isLoadingReviewEligibility, setIsLoadingReviewEligibility] = useState(true);
  const [reviewError, setReviewError] = useState("");
  const [enquiries, setEnquiries] = useState<CustomerEnquiry[]>(() => useCustomerEnquiryDemoFallback ? customerEnquiries : []);
  const [isLoadingEnquiries, setIsLoadingEnquiries] = useState(true);
  const [enquiriesError, setEnquiriesError] = useState("");
  const [expandedEnquiryId, setExpandedEnquiryId] = useState<string | null>(null);
  const [bookings, setBookings] = useState<BookingItem[]>([]);
  const [isLoadingBookings, setIsLoadingBookings] = useState(true);
  const [bookingsError, setBookingsError] = useState("");
  const [paymentMessage, setPaymentMessage] = useState("");
  const [paymentBookingId, setPaymentBookingId] = useState<string | null>(null);
  const [savedHalls, setSavedHalls] = useState<HallSummary[]>([]);
  const [isLoadingSavedHalls, setIsLoadingSavedHalls] = useState(true);
  const [savedHallsError, setSavedHallsError] = useState("");
  const [requirements, setRequirements] = useState<CustomerRequirement[]>([]);
  const [isLoadingRequirements, setIsLoadingRequirements] = useState(true);
  const [requirementsError, setRequirementsError] = useState("");
  const [quotes, setQuotes] = useState<VendorQuote[]>([]);
  const [isLoadingQuotes, setIsLoadingQuotes] = useState(true);
  const [quotesError, setQuotesError] = useState("");

  useEffect(() => {
    const requestedTab = new URLSearchParams(window.location.search).get("tab");
    if (tabs.some((tab) => tab.id === requestedTab)) setActiveTab(requestedTab as DashboardTab);
  }, []);

  useEffect(() => {
    let isCurrent = true;

    async function loadEnquiries() {
      setIsLoadingEnquiries(true);
      setEnquiriesError("");

      try {
        const [response, vendorResponse] = await Promise.all([
          getCustomerEnquiries(accessToken),
          getCustomerVendorLeads(accessToken)
        ]);
        if (!isCurrent) return;

        const apiEnquiries = sortCustomerEnquiries([
          ...response.enquiries.map(toCustomerEnquiry),
          ...vendorResponse.leads.map(toCustomerVendorEnquiry)
        ]);
        setEnquiries(response.source === "api" || !useCustomerEnquiryDemoFallback ? apiEnquiries : sortCustomerEnquiries([...apiEnquiries, ...customerEnquiries]));
      } catch {
        if (!isCurrent) return;
        setEnquiries(useCustomerEnquiryDemoFallback ? customerEnquiries : []);
        setEnquiriesError("Could not load latest enquiries.");
      } finally {
        if (isCurrent) setIsLoadingEnquiries(false);
      }
    }

    loadEnquiries();

    return () => {
      isCurrent = false;
    };
  }, [accessToken]);

  useEffect(() => {
    let isCurrent = true;

    async function loadQuotes() {
      setIsLoadingQuotes(true);
      setQuotesError("");
      try {
        const response = await getCustomerQuotes(accessToken);
        if (isCurrent) setQuotes(response);
      } catch {
        if (!isCurrent) return;
        setQuotes([]);
        setQuotesError("Could not load the latest vendor quotations.");
      } finally {
        if (isCurrent) setIsLoadingQuotes(false);
      }
    }

    loadQuotes();
    return () => {
      isCurrent = false;
    };
  }, [accessToken]);

  useEffect(() => {
    let isCurrent = true;

    async function loadRequirements() {
      setIsLoadingRequirements(true);
      setRequirementsError("");
      try {
        const response = await getCustomerRequirements(accessToken);
        if (isCurrent) setRequirements(response);
      } catch {
        if (!isCurrent) return;
        setRequirements([]);
        setRequirementsError("Could not load your marketplace requirements.");
      } finally {
        if (isCurrent) setIsLoadingRequirements(false);
      }
    }

    loadRequirements();
    return () => {
      isCurrent = false;
    };
  }, [accessToken]);

  useEffect(() => {
    let isCurrent = true;

    async function loadBookings() {
      setIsLoadingBookings(true);
      setBookingsError("");

      try {
        const fallbackBookings = (useCustomerBookingDemoFallback ? customerEnquiries : [])
          .filter((enquiry) => enquiry.status === "CONFIRMED" || enquiry.status === "COMPLETED")
          .map(toStoredCustomerEnquiry)
          .map(bookingFromEnquiry);
        const [response, vendorResponse] = await Promise.all([
          getCustomerBookings(accessToken, fallbackBookings),
          getCustomerVendorLeads(accessToken)
        ]);
        if (!isCurrent) return;
        const hallBookings = response.source === "api" || useCustomerBookingDemoFallback ? response.bookings : [];
        const vendorBookings = vendorResponse.leads
          .filter((lead) => lead.status === "BOOKED" || lead.status === "COMPLETED")
          .map(bookingFromVendorLead);
        setBookings(sortBookings([...hallBookings, ...vendorBookings]));
      } catch {
        if (!isCurrent) return;
        setBookings([]);
        setBookingsError("Could not load latest bookings.");
      } finally {
        if (isCurrent) setIsLoadingBookings(false);
      }
    }

    loadBookings();

    return () => {
      isCurrent = false;
    };
  }, [accessToken]);

  useEffect(() => {
    let isCurrent = true;

    // async function loadReviewEligibility() {
    //   if (isLoadingBookings) return;

    //   setIsLoadingReviewEligibility(true);
    //   setReviewError("");

    //   const completedReviewBooking = bookings.find((booking): booking is BookingItem & { enquiryId: string } => (
    //     booking.status === "COMPLETED" && Boolean(booking.enquiryId)
    //   ));
    //   const isVendorReview =
    //     completedReviewBooking?.enquiryId.startsWith("VLEAD-") ?? false;
    //   const fallback = completedReviewBooking ? reviewEligibilityFromBooking(completedReviewBooking) : fallbackReviewEligibility;

    //   if (!useCustomerReviewDemoFallback && !completedReviewBooking) {
    //     if (!isCurrent) return;
    //     setReviewEligibility(emptyReviewEligibility());
    //     setReviewSubmitted(false);
    //     setIsLoadingReviewEligibility(false);
    //     return;
    //   }

    //   try {
    //     let eligibility: ReviewEligibility;

    //     if (isVendorReview) {
    //       const response = await getVendorReviewEligibility(
    //         completedReviewBooking!.enquiryId,
    //         accessToken
    //       );

    //       eligibility = {
    //         eligible: response.eligible,
    //         enquiryId: response.leadId,
    //         hallName: response.vendorName,
    //         eventDate: response.eventDate,
    //         eventType: response.eventType,
    //         reason: response.reason,
    //         submittedReviewId: response.submittedReviewId
    //       };
    //     } else {
    //       eligibility = await getCustomerReviewEligibility(
    //         completedReviewBooking!.enquiryId,
    //         accessToken,
    //         fallback
    //       );
    //     }
    //     if (!isCurrent) return;
    //     setReviewEligibility(eligibility);
    //     setReviewSubmitted(Boolean(eligibility.submittedReviewId));
    //   } catch {
    //     if (!isCurrent) return;
    //     setReviewEligibility(useCustomerReviewDemoFallback ? fallback : emptyReviewEligibility());
    //     setReviewError("Could not load review eligibility.");
    //   } finally {
    //     if (isCurrent) setIsLoadingReviewEligibility(false);
    //   }
    // }
    async function loadReviewEligibility() {
      if (isLoadingBookings) return;

      setIsLoadingReviewEligibility(true);
      setReviewError("");

      const completedReviewBookings = bookings.filter(
        (booking): booking is BookingItem & { enquiryId: string } =>
          booking.status === "COMPLETED" &&
          Boolean(booking.enquiryId)
      );

      if (!useCustomerReviewDemoFallback && completedReviewBookings.length === 0) {
        if (!isCurrent) return;

        setReviewEligibility(emptyReviewEligibility());
        setReviewSubmitted(false);
        setIsLoadingReviewEligibility(false);
        return;
      }

      try {
        let foundEligibility: ReviewEligibility | null = null;

        for (const booking of completedReviewBookings) {
          const isVendorReview = booking.enquiryId.startsWith("VLEAD-");
          const fallback = reviewEligibilityFromBooking(booking);

          let eligibility: ReviewEligibility;

          if (isVendorReview) {
            const response = await getVendorReviewEligibility(
              booking.enquiryId,
              accessToken
            );

            eligibility = {
              eligible: response.eligible,
              enquiryId: response.leadId,
              hallName: response.vendorName,
              eventDate: response.eventDate,
              eventType: response.eventType,
              reason: response.reason,
              submittedReviewId: response.submittedReviewId
            };
          } else {
            eligibility = await getCustomerReviewEligibility(
              booking.enquiryId,
              accessToken,
              fallback
            );
          }

          // First booking that is eligible
          if (eligibility.eligible) {
            foundEligibility = eligibility;
            break;
          }

          // Keep the last response (usually "Review already exists")
          foundEligibility = eligibility;
        }

        if (!isCurrent) return;

        if (foundEligibility) {
          setReviewEligibility(foundEligibility);
          setReviewSubmitted(Boolean(foundEligibility.submittedReviewId));
        } else {
          setReviewEligibility(
            useCustomerReviewDemoFallback
              ? fallbackReviewEligibility
              : emptyReviewEligibility()
          );
          setReviewSubmitted(false);
        }
      } catch {
        if (!isCurrent) return;

        setReviewEligibility(
          useCustomerReviewDemoFallback
            ? fallbackReviewEligibility
            : emptyReviewEligibility()
        );
        setReviewError("Could not load review eligibility.");
      } finally {
        if (isCurrent) {
          setIsLoadingReviewEligibility(false);
        }
      }
    }

    loadReviewEligibility();

    return () => {
      isCurrent = false;
    };
  }, [accessToken, bookings, isLoadingBookings]);

  useEffect(() => {
    let isCurrent = true;

    async function loadSavedHalls() {
      setIsLoadingSavedHalls(true);
      setSavedHallsError("");

      try {
        const response = await getCustomerSavedHalls(accessToken);
        if (!isCurrent) return;
        setSavedHalls(response.halls);
      } catch {
        if (!isCurrent) return;
        setSavedHalls([]);
        setSavedHallsError("Could not load saved venues.");
      } finally {
        if (isCurrent) setIsLoadingSavedHalls(false);
      }
    }

    loadSavedHalls();
    const unsubscribe = subscribeToSavedHallChanges(loadSavedHalls);

    return () => {
      isCurrent = false;
      unsubscribe();
    };
  }, [accessToken]);

  async function submitReview(payload: { rating: number; comment: string }) {
    if (!reviewEligibility.eligible) {
      throw new Error(reviewEligibility.reason ?? "This completed service is not eligible for review.");
    }
    if (!reviewEligibility.enquiryId) {
      throw new Error("No completed eligible service is available for review.");
    }

    let review;

    if (reviewEligibility.enquiryId.startsWith("VLEAD-")) {
      review = await submitVendorReview(
        {
          leadId: reviewEligibility.enquiryId,
          rating: payload.rating,
          comment: payload.comment
        },
        accessToken
      );
    } else {
      review = await submitCustomerReview(
        {
          enquiryId: reviewEligibility.enquiryId,
          rating: payload.rating,
          comment: payload.comment
        },
        accessToken
      );
    }
    setReviewSubmitted(true);
    setReviewEligibility((current) => ({
      ...current,
      eligible: false,
      reason: "You already submitted a review for this completed service.",
      submittedReviewId: review.id
    }));
    setReviewOpen(false);
  }

  function updateSavedHall(hallId: string, isSaved: boolean) {
    if (isSaved) return;
    setSavedHalls((current) => current.filter((hall) => hall.id !== hallId));
  }

  async function payAdvance(booking: BookingItem) {
    try {
      setPaymentBookingId(booking.id);
      setBookingsError("");
      setPaymentMessage("");

      const order = await createBookingAdvanceOrder(booking, accessToken);
      if (order.checkoutUrl) {
        window.open(order.checkoutUrl, "_blank", "noopener,noreferrer");
        setPaymentMessage(`Payment order ${order.orderId} created. Complete checkout in the opened window.`);
        return;
      }

      if (order.keyId) {
        setPaymentMessage(`Payment order ${order.orderId} created for INR ${formatMoney(order.amount)}. Razorpay checkout can use the returned key and order id.`);
        return;
      }

      const updated = await verifyBookingAdvancePayment({
        bookingId: booking.id,
        orderId: order.orderId,
        razorpayPaymentId: `PAY-${Date.now().toString().slice(-6)}`,
        razorpaySignature: "local-simulated-signature"
      }, accessToken);
      setBookings((current) => current.map((item) => item.id === booking.id ? updated ?? { ...item, paymentStatus: "ADVANCE_PAID" } : item));
      setPaymentMessage("Advance payment marked as paid for local testing.");
    } catch (exception) {
      setBookingsError(exception instanceof Error ? exception.message : "Could not start payment.");
    } finally {
      setPaymentBookingId(null);
    }
  }

  const activeBookings = bookings.filter((booking) => booking.status === "REQUESTED" || booking.status === "CONFIRMED");
  const upcomingBooking = activeBookings.find((booking) => booking.status === "CONFIRMED") ?? activeBookings[0];
  const reviewVenueName = reviewEligibility.hallName || (useCustomerReviewDemoFallback ? reviewEligibleBooking.venue : "completed service");
  const recentReviewTitle = reviewSubmitted ? "Review submitted" : reviewEligibility.eligible ? `Review ${reviewVenueName}` : "No review pending";
  const recentReviewMessage = reviewSubmitted
    ? "Your verified review is pending moderation."
    : reviewEligibility.eligible
      ? "Your completed event is eligible for a verified review."
      : "Completed bookings will appear here for review.";

  return (
    <>
      <main className="mx-auto w-full max-w-7xl px-4 py-7 sm:px-6 sm:py-10">
        <div className="flex flex-wrap items-center justify-between gap-5">
          <div className="flex items-center gap-4">
            <span className="grid size-12 place-items-center rounded-full bg-emerald-50 text-lg font-semibold text-emerald-800">{user?.fullName.charAt(0)}</span>
            <div><p className="text-sm text-muted-foreground">Welcome back</p><h1 className="text-2xl font-semibold sm:text-3xl">{user?.fullName}</h1></div>
          </div>
        </div>

        <div className="mt-8 flex gap-1 overflow-x-auto border-b border-border" role="tablist" aria-label="Customer account">
          {tabs.map((tab) => (
            <button aria-selected={activeTab === tab.id} className={`shrink-0 border-b-2 px-4 py-3 text-sm font-medium ${activeTab === tab.id ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:text-foreground"}`} key={tab.id} onClick={() => setActiveTab(tab.id)} role="tab" type="button">{tab.label}</button>
          ))}
        </div>

        {activeTab === "overview" && (
          <div className="py-7">
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <button className="rounded-lg border border-border bg-white p-5 text-left hover:border-primary" onClick={() => setActiveTab("requirements")} type="button"><ClipboardList className="text-primary" size={21} /><p className="mt-5 text-2xl font-semibold">{requirements.length}</p><p className="mt-1 text-sm text-muted-foreground">Requirements</p></button>
              <div className="rounded-lg border border-border bg-white p-5"><MessageSquareText className="text-primary" size={21} /><p className="mt-5 text-2xl font-semibold">{enquiries.length}</p><p className="mt-1 text-sm text-muted-foreground">Total enquiries</p></div>
              <div className="rounded-lg border border-border bg-white p-5"><CalendarDays className="text-primary" size={21} /><p className="mt-5 text-2xl font-semibold">{activeBookings.length}</p><p className="mt-1 text-sm text-muted-foreground">Active bookings</p></div>
              <div className="rounded-lg border border-border bg-white p-5"><Heart className="text-primary" size={21} /><p className="mt-5 text-2xl font-semibold">{savedHalls.length}</p><p className="mt-1 text-sm text-muted-foreground">Saved venues</p></div>
            </div>

            <section className="mt-9">
              <div className="flex items-center justify-between gap-4"><h2 className="text-xl font-semibold">Upcoming event</h2><button className="text-sm font-semibold text-primary" onClick={() => setActiveTab("bookings")}>View bookings</button></div>
              {isLoadingBookings ? (
                <div className="mt-4 h-28 animate-pulse rounded-lg border border-border bg-white" />
              ) : upcomingBooking ? (
                <article className="mt-4 flex flex-col gap-5 rounded-lg border border-border bg-white p-5 sm:flex-row sm:items-center">
                  <div className="grid size-14 shrink-0 place-items-center rounded-md bg-emerald-50 text-emerald-700"><CalendarDays size={25} /></div>
                  <div className="min-w-0 flex-1"><div className="flex items-center gap-2"><h3 className="font-semibold">{upcomingBooking.hallName}</h3><BadgeCheck className="text-emerald-700" size={17} /></div><p className="mt-1 text-sm text-muted-foreground">{formatDate(upcomingBooking.eventDate)}, {formatSlot(upcomingBooking.slot)} slot</p></div>
                  <span className={`w-fit rounded-full px-3 py-1 text-xs font-medium ${bookingStatusStyles[upcomingBooking.status]}`}>{bookingStatusLabel(upcomingBooking.status)}</span>
                </article>
              ) : (
                <div className="mt-4 rounded-lg border border-dashed border-border bg-white p-6 text-sm text-muted-foreground">Confirmed bookings will appear here.</div>
              )}
            </section>

            <section className="mt-9">
              <div className="flex items-center justify-between gap-4"><h2 className="text-xl font-semibold">Recent activity</h2><button className="text-sm font-semibold text-primary" onClick={() => setActiveTab("reviews")}>View reviews</button></div>
              <button className="mt-4 flex w-full items-center gap-4 rounded-lg border border-border bg-white p-5 text-left hover:border-primary" onClick={() => setActiveTab("reviews")}>
                <span className="grid size-11 shrink-0 place-items-center rounded-md bg-amber-50 text-amber-600"><Star size={21} /></span>
                <span className="min-w-0 flex-1"><strong className="block">{recentReviewTitle}</strong><span className="mt-1 block text-sm text-muted-foreground">{recentReviewMessage}</span></span>
                <ChevronRight className="text-muted-foreground" size={19} />
              </button>
            </section>
          </div>
        )}

        {activeTab === "requirements" && (
          <section className="py-7">
            <div className="flex flex-wrap items-end justify-between gap-4">
              <div><h2 className="text-xl font-semibold">My requirements</h2><p className="mt-1 text-sm text-muted-foreground">Review the event service requirements saved to your account.</p></div>
              <Link className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 text-sm font-semibold text-white" href={"/requirements/new" as Route}><Plus size={17} /> Post requirement</Link>
            </div>
            {requirementsError && <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{requirementsError}</p>}
            {quotesError && <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{quotesError}</p>}
            {isLoadingRequirements || isLoadingQuotes ? (
              <div className="mt-5 grid gap-4">{[1, 2].map((item) => <div className="h-48 animate-pulse rounded-lg border border-border bg-white" key={item} />)}</div>
            ) : requirements.length > 0 ? (
              <div className="mt-5 grid gap-4">
                {requirements.map((requirement) => {
                  const requirementQuotes = quotes.filter((quote) => quote.requirementId === requirement.id);
                  return (
                    <article className="rounded-lg border border-border bg-white p-5" key={requirement.id}>
                      <div className="flex flex-wrap items-start justify-between gap-4">
                        <div><div className="flex flex-wrap items-center gap-2"><h3 className="font-semibold">{requirement.eventType}</h3><span className={`rounded-full px-2.5 py-1 text-xs font-medium ${requirementStatusStyles[requirement.status]}`}>{requirement.status.toLowerCase()}</span></div><p className="mt-2 text-sm text-muted-foreground">{formatDate(requirement.eventDate)} | {[requirement.location, requirement.city].filter(Boolean).join(", ")}</p></div>
                        <div className="text-right"><p className="text-sm font-semibold">Reference {requirement.id}</p><p className="mt-1 text-xs text-muted-foreground">Submitted {formatSubmittedDate(requirement.createdAt)}</p></div>
                      </div>
                      <div className="mt-4 flex flex-wrap gap-2">{requirement.services.map((service) => <span className="rounded-full bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-800" key={service.id}>{service.name.toLowerCase() === "makeup" ? "Bridal makeup" : service.name}</span>)}</div>
                      <div className="mt-4 grid gap-3 rounded-md bg-muted/50 p-4 text-sm sm:grid-cols-3"><p><span className="text-muted-foreground">Budget:</span> {requirementBudget(requirement)}</p><p><span className="text-muted-foreground">Guests:</span> {requirement.guestCount ? formatGuestCount(requirement.guestCount) : "Not specified"}</p><p><span className="text-muted-foreground">Contact:</span> {contactChannelLabel(requirement.preferredContactChannel)}</p></div>
                      {requirement.details && <p className="mt-4 text-sm leading-6 text-muted-foreground">{requirement.details}</p>}
                      {requirementQuotes.length > 0 && (
                        <div className="mt-5 border-t border-border pt-5">
                          <div className="flex flex-wrap items-center justify-between gap-2"><h4 className="font-semibold">Vendor quotations</h4><span className="rounded-full bg-violet-50 px-2.5 py-1 text-xs font-medium text-violet-700">{requirementQuotes.length} received</span></div>
                          <div className="mt-3 grid gap-3">{requirementQuotes.map((quote) => <CustomerQuoteCard key={quote.id} quote={quote} />)}</div>
                        </div>
                      )}
                      <p className="mt-4 border-t border-border pt-4 text-xs font-medium text-muted-foreground">{requirement.matchedVendorCount > 0 ? `Sent to ${requirement.matchedVendorCount} matching vendor${requirement.matchedVendorCount === 1 ? "" : "s"}. You can track their responses under Enquiries.` : "Your requirement is open. We will show vendor matches here as they become available."}</p>
                    </article>
                  );
                })}
              </div>
            ) : (
              <div className="mt-5 rounded-lg border border-dashed border-border bg-white p-8 text-center"><ClipboardList className="mx-auto text-muted-foreground" size={30} /><h3 className="mt-4 font-semibold">No requirements yet</h3><p className="mt-2 text-sm text-muted-foreground">Post the services you need for your event in one place.</p><Link className="mt-5 inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 text-sm font-semibold text-white" href={"/requirements/new" as Route}><Plus size={16} /> Post your first requirement</Link></div>
            )}
          </section>
        )}

        {activeTab === "enquiries" && (
          <section className="py-7">
            <h2 className="text-xl font-semibold">Your enquiries</h2>
            <p className="mt-1 text-sm text-muted-foreground">Track responses and confirmed event details.</p>
            {enquiriesError && <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{enquiriesError}</p>}
            {quotesError && <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{quotesError}</p>}
            {isLoadingEnquiries || isLoadingQuotes ? <div className="mt-5 grid gap-3">{[1, 2, 3].map((item) => <div className="h-24 animate-pulse rounded-lg border border-border bg-white" key={item} />)}</div> : enquiries.length > 0 ? (
              <div className="mt-5 grid gap-3">
                {enquiries.map((enquiry) => {
                  const isExpanded = expandedEnquiryId === enquiry.id;
                  const quote = quotes.find((item) => vendorLeadReference(item.leadId) === enquiry.id);
                  return (
                    <article className="rounded-lg border border-border bg-white p-5" key={enquiry.id}>
                      <div className="grid gap-4 sm:grid-cols-[1fr_auto] sm:items-center">
                        <div><div className="flex flex-wrap items-center gap-2"><h3 className="font-semibold">{enquiry.venue}</h3><span className={`rounded-full px-2.5 py-1 text-xs font-medium ${statusStyles[enquiry.status]}`}>{statusLabel(enquiry.status)}</span></div><p className="mt-2 text-sm text-muted-foreground">Event: {enquiry.eventDate} | Enquiry {enquiry.id}</p></div>
                        <button aria-expanded={isExpanded} className="inline-flex h-9 w-fit items-center gap-2 rounded-md border border-border px-3 text-sm font-medium hover:border-primary" onClick={() => setExpandedEnquiryId(isExpanded ? null : enquiry.id)} type="button">{isExpanded ? "Close details" : "View details"} {isExpanded ? <X size={16} /> : <ChevronRight size={16} />}</button>
                      </div>
                      {isExpanded && (
                        <div className="mt-5 border-t border-border pt-5">
                          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                            <EnquiryDetail label="Enquiry ID" value={enquiry.id} />
                            <EnquiryDetail label="Venue / provider" value={enquiry.venue} />
                            <EnquiryDetail label="Event date" value={enquiry.eventDate} />
                            <EnquiryDetail label="Status" value={statusLabel(enquiry.status)} />
                            <EnquiryDetail label="Submitted" value={formatCustomerEventDate(enquiry.submittedAt)} />
                            {enquiry.eventType && <EnquiryDetail label="Event type" value={enquiry.eventType} />}
                            {enquiry.guestCount !== undefined && <EnquiryDetail label="Guests" value={formatGuestCount(enquiry.guestCount)} />}
                            {enquiry.slot && <EnquiryDetail label="Time slot" value={formatSlot(enquiry.slot as StoredEnquiry["slot"])} />}
                            {enquiry.location && <EnquiryDetail label="Location" value={enquiry.location} />}
                            {enquiry.budget !== undefined && <EnquiryDetail label="Budget" value={`INR ${new Intl.NumberFormat("en-IN").format(enquiry.budget)}`} />}
                          </div>
                          {enquiry.notes && <div className="mt-4 rounded-md bg-muted/50 p-4"><p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Notes</p><p className="mt-1 text-sm leading-6">{enquiry.notes}</p></div>}
                          {enquiry.declineReason && <div className="mt-4 rounded-md border border-rose-200 bg-rose-50 p-4"><p className="text-xs font-medium uppercase tracking-wide text-rose-700">Vendor response</p><p className="mt-1 text-sm leading-6 text-rose-800">{enquiry.declineReason}</p></div>}
                          {quote && <div className="mt-4"><CustomerQuoteCard quote={quote} /></div>}
                        </div>
                      )}
                    </article>
                  );
                })}
              </div>
            ) : <div className="mt-5 rounded-lg border border-dashed border-border bg-white p-8 text-center"><MessageSquareText className="mx-auto text-muted-foreground" size={28} /><h3 className="mt-4 font-semibold">No enquiries yet</h3><p className="mt-2 text-sm text-muted-foreground">Browse venues and send your first enquiry.</p></div>}
          </section>
        )}

        {activeTab === "bookings" && (
          <section className="py-7">
            <h2 className="text-xl font-semibold">Your bookings</h2>
            <p className="mt-1 text-sm text-muted-foreground">Track confirmed events, cancelled bookings, and completed services.</p>
            {bookingsError && <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{bookingsError}</p>}
            {paymentMessage && <p className="mt-4 rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-800" role="status">{paymentMessage}</p>}
            {isLoadingBookings ? (
              <div className="mt-5 grid gap-4">{[1, 2].map((item) => <div className="h-36 animate-pulse rounded-lg border border-border bg-white" key={item} />)}</div>
            ) : bookings.length > 0 ? (
              <div className="mt-5 grid gap-4">
                {bookings.map((booking) => (
                  <article className="rounded-lg border border-border bg-white p-5" key={booking.id}>
                    <div className="flex flex-col gap-5 lg:flex-row lg:items-center">
                      <div className="grid size-12 shrink-0 place-items-center rounded-md bg-emerald-50 text-emerald-700"><CalendarDays size={22} /></div>
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2"><h3 className="font-semibold">{booking.hallName}</h3><span className={`rounded-full px-2.5 py-1 text-xs font-medium ${bookingStatusStyles[booking.status]}`}>{bookingStatusLabel(booking.status)}</span></div>
                        <p className="mt-2 text-sm text-muted-foreground">{bookingDetailLine(booking)}</p>
                        <p className="mt-1 text-sm text-muted-foreground">{booking.eventType} | Booking {booking.id}</p>
                      </div>
                      <div className="flex flex-wrap items-center gap-2">
                        <div className="rounded-md border border-border bg-muted/40 px-3 py-2 text-sm">
                          <span className="text-muted-foreground">Payment</span>
                          <strong className="ml-2 capitalize">{booking.paymentStatus.toLowerCase().replace(/_/g, " ")}</strong>
                        </div>
                        {booking.status === "CONFIRMED" && booking.paymentStatus === "ADVANCE_PENDING" && (
                          <button className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-3 text-sm font-semibold text-white disabled:opacity-60" disabled={paymentBookingId === booking.id} onClick={() => payAdvance(booking)} type="button">
                            {paymentBookingId === booking.id ? <LoaderCircle className="animate-spin" size={16} /> : <CreditCard size={16} />} Pay INR {formatMoney(advanceAmount(booking))}
                          </button>
                        )}
                      </div>
                    </div>
                  </article>
                ))}
              </div>
            ) : (
              <div className="mt-5 rounded-lg border border-dashed border-border bg-white p-8 text-center"><CalendarDays className="mx-auto text-muted-foreground" size={28} /><h3 className="mt-4 font-semibold">No bookings yet</h3><p className="mt-2 text-sm text-muted-foreground">When an owner confirms your enquiry, the booking will appear here.</p></div>
            )}
          </section>
        )}

        {activeTab === "saved" && (
          <section className="py-7">
            <div className="flex flex-wrap items-end justify-between gap-4">
              <div><h2 className="text-xl font-semibold">Saved venues</h2><p className="mt-1 text-sm text-muted-foreground">Your shortlist for easy comparison.</p></div>
              <button className="inline-flex h-10 items-center rounded-md border border-border bg-white px-4 text-sm font-medium hover:border-primary hover:text-primary" onClick={() => router.push("/")} type="button">Browse venues</button>
            </div>
            {savedHallsError && <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{savedHallsError}</p>}
            {isLoadingSavedHalls ? (
              <div className="mt-5 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">{[1, 2, 3].map((item) => <div className="h-80 animate-pulse rounded-lg border border-border bg-white" key={item} />)}</div>
            ) : savedHalls.length > 0 ? (
              <>
                <VenueCompare halls={savedHalls} />
                <div className="mt-6 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">{savedHalls.map((hall) => <HallCard hall={hall} initialSaved key={hall.id} onSavedChange={updateSavedHall} />)}</div>
              </>
            ) : (
              <div className="mt-5 rounded-lg border border-dashed border-border bg-white p-8 text-center"><Heart className="mx-auto text-muted-foreground" size={28} /><h3 className="mt-4 font-semibold">No saved venues</h3><p className="mt-2 text-sm text-muted-foreground">Save halls while browsing to compare them here.</p></div>
            )}
          </section>
        )}

        {activeTab === "reviews" && (
          <section className="py-7">
            <h2 className="text-xl font-semibold">Your reviews</h2><p className="mt-1 text-sm text-muted-foreground">Only completed bookings can receive a verified review.</p>
            {reviewError && <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{reviewError}</p>}
            {isLoadingReviewEligibility ? (
              <div className="mt-5 h-28 animate-pulse rounded-lg border border-border bg-white" />
            ) : reviewSubmitted ? (
              <div className="mt-5 flex items-start gap-3 rounded-lg border border-emerald-200 bg-emerald-50 p-5"><CheckCircle2 className="mt-0.5 shrink-0 text-emerald-700" size={21} /><div><h3 className="font-semibold">Review submitted</h3><p className="mt-1 text-sm text-muted-foreground">Your verified review for {reviewVenueName} is pending moderation.</p></div></div>
            ) : reviewEligibility.eligible ? (
              <article className="mt-5 rounded-lg border border-border bg-white p-5">
                <div className="flex flex-col gap-5 sm:flex-row sm:items-center"><span className="grid size-12 shrink-0 place-items-center rounded-md bg-emerald-50 text-emerald-700"><BadgeCheck size={23} /></span><div className="min-w-0 flex-1"><div className="flex items-center gap-2"><h3 className="font-semibold">{reviewVenueName}</h3><span className="rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-medium text-emerald-700">Verified service</span></div><p className="mt-2 text-sm text-muted-foreground">{reviewEligibility.eventType ?? reviewEligibleBooking.serviceType} | {reviewEligibility.eventDate}</p></div><button className="h-10 rounded-md bg-primary px-4 text-sm font-semibold text-white" onClick={() => setReviewOpen(true)}>Write review</button></div>
              </article>
            ) : (
              <div className="mt-5 rounded-lg border border-dashed border-border bg-white p-6"><h3 className="font-semibold">No review available</h3><p className="mt-2 text-sm text-muted-foreground">{reviewEligibility.reason ?? "Completed eligible services will appear here."}</p></div>
            )}
            <div className="mt-8 flex items-center gap-3 border-t border-border pt-6 text-sm text-muted-foreground"><Clock3 size={18} /><span>Reviews appear publicly after moderation.</span></div>
          </section>
        )}

        {activeTab === "activity" && <NotificationActivity />}
      </main>

      <ReviewDialog onClose={() => setReviewOpen(false)} onSubmitted={submitReview} open={reviewOpen} venueName={reviewVenueName} />
    </>
  );
}

function reviewEligibilityFromBooking(booking: BookingItem & { enquiryId: string }): ReviewEligibility {
  return {
    eligible: true,
    enquiryId: booking.enquiryId,
    hallName: booking.hallName,
    eventDate: booking.eventDate,
    eventType: booking.eventType,
    reason: null
  };
}

function toCustomerEnquiry(enquiry: StoredEnquiry): CustomerEnquiry {
  return {
    id: enquiry.id,
    venue: enquiry.hallName,
    eventDate: formatCustomerEventDate(enquiry.eventDate),
    submittedAt: enquiry.submittedAt,
    status: enquiry.status,
    eventType: enquiry.eventType,
    guestCount: enquiry.guestCount,
    slot: enquiry.slot,
    notes: enquiry.notes
  };
}

function toCustomerVendorEnquiry(lead: VendorLead): CustomerEnquiry {
  return {
    id: vendorLeadReference(lead.id),
    venue: lead.vendorName || lead.service,
    eventDate: formatCustomerEventDate(lead.eventDate),
    submittedAt: lead.submittedAt,
    status: vendorLeadCustomerStatus(lead),
    eventType: lead.eventType,
    location: lead.location,
    budget: lead.budget,
    notes: lead.notes,
    declineReason: lead.declineReason
  };
}

function bookingFromVendorLead(lead: VendorLead): BookingItem {
  return {
    id: `VBOOK-${lead.id.replace(/^V?LEAD-/, "")}`,
    enquiryId: vendorLeadReference(lead.id),
    hallId: lead.vendorId || `vendor-${lead.id}`,
    hallName: lead.vendorName || lead.service,
    customerId: lead.customerId,
    customerName: lead.customerName,
    eventDate: lead.eventDate,
    eventType: lead.service || lead.eventType,
    guestCount: 0,
    slot: "FULL_DAY",
    status:
      lead.status === "COMPLETED"
        ? "COMPLETED"
        : "CONFIRMED", amount: lead.budget ?? 0,
    paymentStatus: "NOT_STARTED",
    notes: lead.notes,
    confirmedAt: lead.submittedAt,
    updatedAt: lead.submittedAt
  };
}

function vendorLeadCustomerStatus(lead: VendorLead): CustomerEnquiry["status"] {
  // if (lead.status === "BOOKED") return "CONFIRMED";
  // if (lead.status === "DECLINED") return "DECLINED";
  // return "AWAITING_RESPONSE";
  switch (lead.status) {
    case "BOOKED":
      return "CONFIRMED";

    case "COMPLETED":
      return "COMPLETED";

    case "DECLINED":
      return "DECLINED";

    default:
      return "AWAITING_RESPONSE";
  }
}

function vendorLeadReference(id: string) {
  return id.startsWith("VLEAD-") ? id : `VLEAD-${id.replace(/^LEAD-/, "")}`;
}

function formatCustomerEventDate(value: string) {
  const date = new Date(value.includes("T") ? value : `${value}T00:00:00`);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat("en-IN", { dateStyle: "long" }).format(date);
}

function sortCustomerEnquiries(items: CustomerEnquiry[]) {
  return [...items].sort((first, second) => Date.parse(second.submittedAt) - Date.parse(first.submittedAt));
}

function EnquiryDetail({ label, value }: { label: string; value: string }) {
  return <div className="rounded-md bg-muted/50 p-3"><p className="text-xs text-muted-foreground">{label}</p><p className="mt-1 text-sm font-medium">{value}</p></div>;
}

function CustomerQuoteCard({ quote }: { quote: VendorQuote }) {
  return (
    <div className="rounded-md border border-violet-200 bg-violet-50/60 p-4">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <p className="font-semibold">{quote.vendorName}</p>
            <span className="rounded-full bg-white px-2 py-1 text-xs font-medium text-violet-700">{quote.status.toLowerCase()}</span>
          </div>
          <h5 className="mt-2 text-sm font-semibold">{quote.packageName}</h5>
          <p className="mt-1 text-xs text-muted-foreground">{quote.service} | Valid until {formatDate(quote.validUntil)}</p>
        </div>
        <div className="text-right">
          <p className="font-semibold">INR {formatMoney(quote.totalAmount)}</p>
          <p className="mt-1 text-xs text-muted-foreground">Total quotation</p>
        </div>
      </div>
      <p className="mt-3 text-sm leading-6">{quote.serviceDescription}</p>
      {quote.inclusions.length > 0 && <div className="mt-3 flex flex-wrap gap-2">{quote.inclusions.map((item) => <span className="rounded-full bg-white px-2.5 py-1 text-xs text-violet-800" key={item}>{item}</span>)}</div>}
      {quote.additionalCharges > 0 && <p className="mt-3 text-xs text-muted-foreground">Includes INR {formatMoney(quote.additionalCharges)} additional charges{quote.additionalChargesDescription ? ` for ${quote.additionalChargesDescription}` : ""}.</p>}
      {quote.notes && <p className="mt-3 border-t border-violet-200 pt-3 text-sm leading-6 text-muted-foreground">{quote.notes}</p>}
    </div>
  );
}

function sortBookings(items: BookingItem[]) {
  return [...items].sort((first, second) => first.eventDate.localeCompare(second.eventDate));
}

function toStoredCustomerEnquiry(enquiry: CustomerEnquiry): StoredEnquiry {
  const hall = halls.find((item) => item.name === enquiry.venue) ?? halls[0];

  return {
    id: enquiry.id,
    hallId: hall.id,
    hallName: enquiry.venue,
    customerId: "customer-101",
    eventDate: toIsoDate(enquiry.eventDate),
    eventType: enquiry.id === reviewEligibleBooking.enquiryId ? reviewEligibleBooking.serviceType : "Wedding",
    guestCount: enquiry.id === reviewEligibleBooking.enquiryId ? 120 : 450,
    slot: "FULL_DAY",
    status: enquiry.status === "AWAITING_RESPONSE" ? "PENDING_OWNER_RESPONSE" : enquiry.status,
    submittedAt: toIsoDate(enquiry.submittedAt)
  };
}

function toIsoDate(value: string) {
  const parsed = new Date(value);
  if (!Number.isNaN(parsed.getTime())) return parsed.toISOString().slice(0, 10);
  return new Date().toISOString().slice(0, 10);
}
