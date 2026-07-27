"use client";

import {
  BadgeCheck,
  Ban,
  CalendarDays,
  Check,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  CreditCard,
  Eye,
  ImagePlus,
  LoaderCircle,
  MapPin,
  MessageSquareText,
  Plus,
  Star,
  Trash2,
  UploadCloud,
  UsersRound,
  X
} from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { NotificationActivity } from "@/components/notifications/NotificationCenter";
import { formatGuestCapacityOption, formatGuestCount, guestCapacityOptions, toTitleCase } from "@/lib/display-format";
import { fallbackOwnerAnalytics, getOwnerAnalytics, type OwnerAnalytics } from "@/features/analytics/analytics-client";
import { useAuth } from "@/features/auth/AuthProvider";
import {
  bookingFromEnquiry as lifecycleBookingFromEnquiry,
  getOwnerBookings,
  updateOwnerBookingStatus,
  upsertLocalBooking,
  type BookingItem,
  type BookingPaymentStatus,
  type BookingStatus
} from "@/features/bookings/booking-client";
import { getOwnerHallEnquiries, updateOwnerEnquiryStatus } from "@/features/enquiries/enquiry-client";
import type { EnquiryStatus, StoredEnquiry } from "@/features/enquiries/types";
import { formatSlot } from "@/features/halls/slot-model";
import {
  createOwnerBlockedDate,
  deleteOwnerBlockedDate,
  getOwnerAvailability,
  type AvailabilityBooking,
  type BlockDatePayload,
  type BlockedDate
} from "@/features/owner/availability-client";
import {
  getOwnerHallListing,
  submitOwnerHallListing,
  updateOwnerHallListing,
  getOwnerHalls,
  type OwnerHallListing,
  type OwnerHallUpdatePayload,
  type OwnerListingStatus
} from "@/features/owner/listing-client";
import {
  deleteOwnerMedia,
  getOwnerMedia,
  getLocalOwnerMedia,
  mediaFromListing,
  updateOwnerMedia,
  uploadAndCreateOwnerMedia,
  type OwnerMediaItem
} from "@/features/owner/media-client";
import { getOwnerHallReviews, type OwnerReview } from "@/features/owner/review-client";
import { fallbackOwnerEnquiries, initialBlockedDates, ownerHall, ownerReviews } from "@/features/owner/mock-data";
import type { VenueType } from "@/features/halls/types";
import { BlockDateDialog } from "./BlockDateDialog";

type OwnerTab = "overview" | "enquiries" | "bookings" | "reports" | "availability" | "listing" | "media" | "reviews" | "activity";

const tabs: Array<{ id: OwnerTab; label: string }> = [
  { id: "overview", label: "Overview" },
  { id: "enquiries", label: "Enquiries" },
  { id: "bookings", label: "Bookings" },
  { id: "reports", label: "Reports" },
  { id: "availability", label: "Availability" },
  { id: "listing", label: "Listing" },
  { id: "media", label: "Media" },
  { id: "reviews", label: "Reviews" },
  { id: "activity", label: "Activity" }
];

const statusStyle: Record<EnquiryStatus, string> = {
  NEW: "bg-blue-50 text-blue-700",
  PENDING_OWNER_RESPONSE: "bg-blue-50 text-blue-700",
  CONFIRMED: "bg-emerald-50 text-emerald-700",
  DECLINED: "bg-rose-50 text-rose-700",
  COMPLETED: "bg-muted text-muted-foreground"
};

const bookingStatusStyle: Record<BookingStatus, string> = {
  REQUESTED: "bg-blue-50 text-blue-700",
  CONFIRMED: "bg-emerald-50 text-emerald-700",
  CANCELLED: "bg-rose-50 text-rose-700",
  COMPLETED: "bg-muted text-muted-foreground"
};

const paymentStatusStyle: Record<BookingPaymentStatus, string> = {
  NOT_STARTED: "bg-slate-100 text-slate-700",
  ADVANCE_PENDING: "bg-amber-50 text-amber-700",
  ADVANCE_PAID: "bg-emerald-50 text-emerald-700",
  REFUNDED: "bg-violet-50 text-violet-700"
};

const listingStatusStyle: Record<OwnerListingStatus, string> = {
  DRAFT: "bg-slate-100 text-slate-700",
  PENDING_APPROVAL: "bg-amber-50 text-amber-700",
  APPROVED: "bg-emerald-50 text-emerald-700",
  REJECTED: "bg-rose-50 text-rose-700"
};

const amenityOptions = ["Air conditioned", "Parking", "Dining hall", "Guest rooms", "Lift", "Generator", "Bridal room", "Catering kitchen"];

const ownerListingFallback: OwnerHallListing = {
  ...ownerHall,
  status: "APPROVED"
};

const useOwnerDemoFallbacks = process.env.NEXT_PUBLIC_AUTH_MODE !== "api";
const useOwnerListingDemoFallback = useOwnerDemoFallbacks || process.env.NEXT_PUBLIC_OWNER_LISTINGS_MODE === "mock";
const useOwnerEnquiryDemoFallback = useOwnerDemoFallbacks || process.env.NEXT_PUBLIC_ENQUIRIES_MODE === "mock";
const useOwnerBookingDemoFallback = useOwnerDemoFallbacks || process.env.NEXT_PUBLIC_BOOKINGS_MODE === "mock";
const useOwnerAvailabilityDemoFallback = useOwnerDemoFallbacks || process.env.NEXT_PUBLIC_AVAILABILITY_MODE === "mock";
const useOwnerReviewDemoFallback = useOwnerDemoFallbacks || process.env.NEXT_PUBLIC_OWNER_REVIEWS_MODE === "mock";

const OWNER_ONBOARDING_DRAFT_KEY = "venue-owner-onboarding-draft";
const LEGACY_OWNER_ONBOARDING_DRAFT_KEY = "venue-owner-onboarding";
const OWNER_LISTINGS_KEY = "venue-aggregator-owner-listings";

type ListingForm = {
  name: string;
  venueType: VenueType;
  addressLine: string;
  contactNumber: string;
  city: string;
  area: string;
  pincode: string;
  latitude: string;
  longitude: string;
  capacity: string;
  startingPrice: string;
  amenities: string[];
  description: string;
};

function formatStatus(status: EnquiryStatus) {
  return status.toLowerCase().replace(/_/g, " ");
}

function formatBookingStatus(status: BookingStatus) {
  return status.toLowerCase().replace(/_/g, " ");
}

function formatPaymentStatus(status: BookingPaymentStatus) {
  return status.toLowerCase().replace(/_/g, " ");
}

function formatListingStatus(status: OwnerListingStatus) {
  return status.toLowerCase().replace(/_/g, " ");
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-IN", { dateStyle: "medium" }).format(new Date(`${value}T00:00:00`));
}

function formatCompactMoney(value: number) {
  return `INR ${new Intl.NumberFormat("en-IN", { maximumFractionDigits: 1, notation: "compact" }).format(value)}`;
}

function emptyOwnerAnalytics(): OwnerAnalytics {
  return {
    enquiries: 0,
    confirmedBookings: 0,
    completedBookings: 0,
    estimatedRevenue: 0,
    conversionRate: 0,
    averageRating: 0,
    occupancyRate: 0,
    trends: [],
    eventMix: []
  };
}

function bookingFromEnquiry(enquiry: StoredEnquiry): AvailabilityBooking {
  return {
    id: enquiry.id,
    enquiryId: enquiry.id,
    eventDate: enquiry.eventDate,
    slot: enquiry.slot,
    eventType: enquiry.eventType,
    guestCount: enquiry.guestCount
  };
}

function availabilityBookingFromLifecycle(booking: BookingItem): AvailabilityBooking {
  return {
    id: booking.id,
    enquiryId: booking.enquiryId,
    eventDate: booking.eventDate,
    slot: booking.slot,
    eventType: booking.eventType,
    guestCount: booking.guestCount,
    customerName: booking.customerName
  };
}

function formFromListing(listing: OwnerHallListing): ListingForm {
  return {
    name: listing.name,
    venueType: listing.venueType,
    addressLine: listing.addressLine ?? "",
    contactNumber: listing.contactNumber ?? "",
    city: listing.city,
    area: listing.area,
    pincode: listing.pincode ?? "",
    latitude: listing.latitude ? String(listing.latitude) : "",
    longitude: listing.longitude ? String(listing.longitude) : "",
    capacity: String(listing.capacity || ""),
    startingPrice: String(listing.startingPrice || ""),
    amenities: listing.amenities,
    description: listing.description
  };
}

function currentCoverImageUrl(listing: OwnerHallListing, media: OwnerMediaItem[]) {
  const coverMedia = media.find((item) => item.isCover) ?? media[0];
  return coverMedia?.url || listing.imageUrl || listing.galleryUrls[0] || "";
}

function payloadFromForm(form: ListingForm, coverImageUrl: string): OwnerHallUpdatePayload {
  return {
    name: form.name.trim(),
    venueType: form.venueType,
    addressLine: form.addressLine.trim(),
    contactNumber: form.contactNumber.trim(),
    coverImageUrl,
    city: form.city.trim(),
    area: form.area.trim(),
    pincode: form.pincode.trim(),
    latitude: parseCoordinate(form.latitude),
    longitude: parseCoordinate(form.longitude),
    capacity: Number(form.capacity),
    startingPrice: Number(form.startingPrice),
    amenities: form.amenities,
    description: form.description.trim()
  };
}

function validateListingForm(form: ListingForm, coverImageUrl: string) {
  if (!form.name.trim() || !form.addressLine.trim() || !form.contactNumber.trim() || !form.area.trim() || !form.city.trim() || !form.pincode.trim() || !hasCapturedListingLocation(form)) return "Enter venue name, address, contact number, city, area, pincode, and location coordinates.";
  if (!form.capacity || Number(form.capacity) < 1) return "Enter a valid guest capacity.";
  if (!form.startingPrice || Number(form.startingPrice) < 1) return "Enter a valid starting price.";
  if (!coverImageUrl) return "Add a cover image from the Media tab.";
  if (form.amenities.length === 0) return "Select at least one amenity.";
  if (form.description.trim().length < 20) return "Add a short description with at least 20 characters.";
  return "";
}

function hasCapturedListingLocation(form: ListingForm) {
  return parseCoordinate(form.latitude) !== undefined && parseCoordinate(form.longitude) !== undefined;
}

function googleMapsUrl(latitude: string, longitude: string) {
  return `https://www.google.com/maps?q=${encodeURIComponent(`${latitude},${longitude}`)}`;
}

function parseCoordinate(value: string) {
  if (!value.trim()) return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function preferredOwnerHallId() {
  if (typeof window === "undefined") return useOwnerListingDemoFallback ? ownerHall.id : "new";

  const queryHallId = new URLSearchParams(window.location.search).get("hallId");
  if (queryHallId) return queryHallId;

  const draft = readLocalRecord(OWNER_ONBOARDING_DRAFT_KEY) ?? readLocalRecord(LEGACY_OWNER_ONBOARDING_DRAFT_KEY);
  const draftId = stringValue(draft, ["id", "hallId", "hall_id", "slug"]);
  if (draftId) return draftId;

  const listings = readLocalRecord(OWNER_LISTINGS_KEY);
  const firstListingId = listings ? Object.keys(listings).find(Boolean) : undefined;
  return firstListingId ?? (useOwnerListingDemoFallback ? ownerHall.id : "new");
}

function blankListingForHall(hallId: string): OwnerHallListing {
  return {
    id: hallId,
    name: "Your venue",
    city: "",
    area: "",
    pincode: "",
    capacity: 0,
    startingPrice: 0,
    rating: 0,
    reviewCount: 0,
    imageUrl: "",
    galleryUrls: [],
    venueType: "Marriage Hall",
    amenities: [],
    isVerified: false,
    availableThisMonth: false,
    description: "",
    status: "DRAFT"
  };
}

function fallbackListingForHall(hallId: string): OwnerHallListing {
  const baseListing = useOwnerListingDemoFallback ? { ...ownerListingFallback, id: hallId } : blankListingForHall(hallId);
  const draft = readLocalRecord(OWNER_ONBOARDING_DRAFT_KEY) ?? readLocalRecord(LEGACY_OWNER_ONBOARDING_DRAFT_KEY);
  if (!draft || stringValue(draft, ["id", "hallId", "hall_id", "slug"]) !== hallId) {
    return baseListing;
  }

  const amenities = Array.isArray(draft.amenities)
    ? draft.amenities.filter((item): item is string => typeof item === "string" && item.trim().length > 0)
    : baseListing.amenities;
  const coverImageUrl = stringValue(draft, ["coverImageUrl", "cover_image_url", "imageUrl"]) ?? baseListing.imageUrl;

  return {
    ...baseListing,
    id: hallId,
    name: stringValue(draft, ["hallName", "name", "title"]) ?? baseListing.name,
    city: stringValue(draft, ["city"]) ?? baseListing.city,
    area: stringValue(draft, ["area", "locality", "location"]) ?? baseListing.area,
    pincode: stringValue(draft, ["pincode", "pinCode", "postalCode"]) ?? baseListing.pincode,
    latitude: numberValue(draft, ["latitude", "lat"]) ?? baseListing.latitude,
    longitude: numberValue(draft, ["longitude", "lng", "lon"]) ?? baseListing.longitude,
    capacity: numberValue(draft, ["capacity", "capacityMax", "capacity_max"]) ?? baseListing.capacity,
    startingPrice: numberValue(draft, ["fullDayPrice", "full_day_price", "startingPrice", "amount"]) ?? baseListing.startingPrice,
    imageUrl: coverImageUrl,
    galleryUrls: coverImageUrl ? [coverImageUrl] : baseListing.galleryUrls,
    venueType: venueTypeFromDraft(draft) ?? baseListing.venueType,
    amenities,
    description: stringValue(draft, ["description", "summary"]) ?? baseListing.description,
    status: statusFromDraft(draft) ?? "DRAFT",
    isVerified: false
  };
}

function readLocalRecord(key: string) {
  if (typeof window === "undefined") return undefined;
  try {
    const parsed = JSON.parse(window.localStorage.getItem(key) ?? "null") as unknown;
    return isRecord(parsed) ? parsed : undefined;
  } catch {
    return undefined;
  }
}

function venueTypeFromDraft(record: Record<string, unknown>): VenueType | undefined {
  const value = stringValue(record, ["venueType", "venue_type", "hallType", "type"]);
  if (!value) return undefined;
  const normalized = value.trim().toUpperCase().replace(/[\s-]+/g, "_");
  if (normalized === "MARRIAGE_HALL") return "Marriage Hall";
  if (normalized === "BANQUET_HALL") return "Banquet Hall";
  if (normalized === "MINI_HALL") return "Mini Hall";
  if (normalized === "CONVENTION_CENTRE" || normalized === "CONVENTION_CENTER") return "Convention Centre";
  if (value === "Marriage Hall" || value === "Banquet Hall" || value === "Mini Hall" || value === "Convention Centre") return value;
  return undefined;
}

function statusFromDraft(record: Record<string, unknown>): OwnerListingStatus | undefined {
  const value = stringValue(record, ["status", "listingStatus", "listing_status", "approvalStatus"]);
  const normalized = value?.trim().toUpperCase();
  if (normalized === "DRAFT") return "DRAFT";
  if (normalized === "PENDING" || normalized === "PENDING_APPROVAL" || normalized === "SUBMITTED") return "PENDING_APPROVAL";
  if (normalized === "APPROVED") return "APPROVED";
  if (normalized === "REJECTED") return "REJECTED";
  return undefined;
}

function stringValue(record: Record<string, unknown> | undefined, keys: string[]) {
  if (!record) return undefined;
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string" && value.trim()) return value;
    if (typeof value === "number") return String(value);
  }
  return undefined;
}

function numberValue(record: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "number") return value;
    if (typeof value === "string" && value.trim() && !Number.isNaN(Number(value))) return Number(value);
  }
  return undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function normalizeMediaCover(media: OwnerMediaItem[]) {
  if (media.length === 0) return media;
  const coverIndex = media.findIndex((item) => item.isCover);
  return media.map((item, index) => ({ ...item, isCover: coverIndex >= 0 ? index === coverIndex : index === 0 }));
}

function reviewCounts(reviews: OwnerReview[]) {
  return [5, 4, 3, 2, 1].map((rating) => {
    const count = reviews.filter((review) => Math.round(review.rating) === rating).length;
    return {
      rating,
      count,
      percentage: reviews.length ? Math.round((count / reviews.length) * 100) : 0
    };
  });
}

export function OwnerDashboard() {
  const { accessToken } = useAuth();
  const router = useRouter();
  const [isCheckingOnboarding, setIsCheckingOnboarding] = useState(true);
  const [activeTab, setActiveTab] = useState<OwnerTab>("overview");
  const [activeHallId, setActiveHallId] = useState(() => preferredOwnerHallId());
  const [listing, setListing] = useState<OwnerHallListing>(() => fallbackListingForHall(preferredOwnerHallId()));
  const [listingForm, setListingForm] = useState<ListingForm>(() => formFromListing(fallbackListingForHall(preferredOwnerHallId())));
  const [isLoadingListing, setIsLoadingListing] = useState(true);
  const [listingError, setListingError] = useState("");
  const [isSavingListing, setIsSavingListing] = useState(false);
  const [isSubmittingListing, setIsSubmittingListing] = useState(false);
  const [isCapturingListingLocation, setIsCapturingListingLocation] = useState(false);
  const [enquiries, setEnquiries] = useState<StoredEnquiry[]>(() => useOwnerEnquiryDemoFallback ? fallbackOwnerEnquiries : []);
  const [isLoadingEnquiries, setIsLoadingEnquiries] = useState(true);
  const [enquiriesError, setEnquiriesError] = useState("");
  const [updatingEnquiryId, setUpdatingEnquiryId] = useState<string | null>(null);
  const [bookings, setBookings] = useState<BookingItem[]>(() => useOwnerBookingDemoFallback ? fallbackOwnerEnquiries.filter((enquiry) => enquiry.status === "CONFIRMED" || enquiry.status === "COMPLETED").map(lifecycleBookingFromEnquiry) : []);
  const [isLoadingBookings, setIsLoadingBookings] = useState(true);
  const [bookingsError, setBookingsError] = useState("");
  const [updatingBookingId, setUpdatingBookingId] = useState<string | null>(null);
  const [blockedDates, setBlockedDates] = useState<BlockedDate[]>(() => useOwnerAvailabilityDemoFallback ? initialBlockedDates : []);
  const [availabilityBookings, setAvailabilityBookings] = useState<AvailabilityBooking[]>([]);
  const [isLoadingAvailability, setIsLoadingAvailability] = useState(true);
  const [availabilityError, setAvailabilityError] = useState("");
  const [deletingBlockId, setDeletingBlockId] = useState<string | null>(null);
  const [blockDialogOpen, setBlockDialogOpen] = useState(false);
  const [selectedBlockDate, setSelectedBlockDate] = useState("");
  const [notice, setNotice] = useState("");
  const [media, setMedia] = useState<OwnerMediaItem[]>(() => mediaFromListing(fallbackListingForHall(preferredOwnerHallId())));
  const [mediaError, setMediaError] = useState("");
  const [isUploadingMedia, setIsUploadingMedia] = useState(false);
  const [updatingMediaId, setUpdatingMediaId] = useState<string | null>(null);
  const [reviews, setReviews] = useState<OwnerReview[]>(() => useOwnerReviewDemoFallback ? ownerReviews : []);
  const [averageRating, setAverageRating] = useState(useOwnerReviewDemoFallback ? ownerHall.rating : 0);
  const [totalReviews, setTotalReviews] = useState(useOwnerReviewDemoFallback ? ownerHall.reviewCount : 0);
  const [isLoadingReviews, setIsLoadingReviews] = useState(true);
  const [reviewsError, setReviewsError] = useState("");
  const [analytics, setAnalytics] = useState<OwnerAnalytics>(() => useOwnerDemoFallbacks ? fallbackOwnerAnalytics : emptyOwnerAnalytics());
  const [isLoadingAnalytics, setIsLoadingAnalytics] = useState(true);
  const [analyticsError, setAnalyticsError] = useState("");
  const [expandedEnquiryId, setExpandedEnquiryId] = useState<string | null>(null);
  const [currentMonth, setCurrentMonth] = useState(() => {
    const today = new Date();
    return new Date(today.getFullYear(), today.getMonth(), 1);
  }); const monthKey = `${currentMonth.getFullYear()}-${String(
    currentMonth.getMonth() + 1
  ).padStart(2, "0")}`;

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    if (params.get("submitted") === "true") setNotice("Your venue was submitted for admin approval.");
    setActiveHallId(preferredOwnerHallId());
  }, []);

  useEffect(() => {
    let isCurrent = true;

    async function loadAnalytics() {
      setIsLoadingAnalytics(true);
      setAnalyticsError("");

      try {
        const response = await getOwnerAnalytics(activeHallId, accessToken);
        if (!isCurrent) return;
        setAnalytics(response);
      } catch {
        if (!isCurrent) return;
        setAnalytics(useOwnerDemoFallbacks ? fallbackOwnerAnalytics : emptyOwnerAnalytics());
        setAnalyticsError("Could not load latest reports.");
      } finally {
        if (isCurrent) setIsLoadingAnalytics(false);
      }
    }

    loadAnalytics();

    return () => {
      isCurrent = false;
    };
  }, [accessToken, activeHallId]);

  useEffect(() => {
    let isCurrent = true;

    async function loadListing() {
      setIsLoadingListing(true);
      setListingError("");

      try {
        const fallbackListing = fallbackListingForHall(activeHallId);
        const response = await getOwnerHallListing(activeHallId, accessToken, fallbackListing);
        const loadedMedia = await getOwnerMedia(response.id, accessToken, mediaFromListing(response));
        if (!isCurrent) return;
        setListing(response);
        setActiveHallId(response.id);
        setListingForm(formFromListing(response));
        setMedia(loadedMedia);
      } catch {
        if (!isCurrent) return;
        const fallbackListing = fallbackListingForHall(activeHallId);
        setListing(fallbackListing);
        setListingForm(formFromListing(fallbackListing));
        setMedia(getLocalOwnerMedia(fallbackListing.id, mediaFromListing(fallbackListing)));
        setListingError("Could not load latest listing details.");
      } finally {
        if (isCurrent) setIsLoadingListing(false);
      }
    }

    loadListing();

    return () => {
      isCurrent = false;
    };
  }, [accessToken, activeHallId]);

  useEffect(() => {
    let isCurrent = true;

    async function loadBookings() {
      setIsLoadingBookings(true);
      setBookingsError("");

      try {
        const fallbackBookings = (useOwnerBookingDemoFallback ? fallbackOwnerEnquiries : [])
          .filter((enquiry) => enquiry.status === "CONFIRMED" || enquiry.status === "COMPLETED")
          .map(lifecycleBookingFromEnquiry);
        const response = await getOwnerBookings(activeHallId, accessToken, fallbackBookings);
        if (!isCurrent) return;
        setBookings(response.bookings);
      } catch {
        if (!isCurrent) return;
        setBookings((useOwnerBookingDemoFallback ? fallbackOwnerEnquiries : []).filter((enquiry) => enquiry.status === "CONFIRMED" || enquiry.status === "COMPLETED").map(lifecycleBookingFromEnquiry));
        setBookingsError("Could not load latest bookings.");
      } finally {
        if (isCurrent) setIsLoadingBookings(false);
      }
    }

    loadBookings();

    return () => {
      isCurrent = false;
    };
  }, [accessToken, activeHallId]);

  useEffect(() => {
    let isCurrent = true;

    async function loadEnquiries() {
      setIsLoadingEnquiries(true);
      setEnquiriesError("");

      try {
        const response = await getOwnerHallEnquiries(activeHallId, listing.name, accessToken);
        if (!isCurrent) return;

        const loadedIds = new Set(response.enquiries.map((enquiry) => enquiry.id));
        setEnquiries(response.source === "api" || !useOwnerEnquiryDemoFallback ? response.enquiries : [
          ...response.enquiries,
          ...fallbackOwnerEnquiries.filter((enquiry) => !loadedIds.has(enquiry.id))
        ]);
      } catch {
        if (!isCurrent) return;
        setEnquiries(useOwnerEnquiryDemoFallback ? fallbackOwnerEnquiries : []);
        setEnquiriesError("Could not load latest owner enquiries.");
      } finally {
        if (isCurrent) setIsLoadingEnquiries(false);
      }
    }

    loadEnquiries();

    return () => {
      isCurrent = false;
    };
  }, [accessToken, activeHallId, listing.name]);

  useEffect(() => {
    let isCurrent = true;

    async function loadAvailability() {
      setIsLoadingAvailability(true);
      setAvailabilityError("");

      try {
        const fallbackBookings = (useOwnerAvailabilityDemoFallback ? fallbackOwnerEnquiries : [])
          .filter((enquiry) => enquiry.status === "CONFIRMED")
          .map(bookingFromEnquiry);
        const response = await getOwnerAvailability(activeHallId, accessToken, useOwnerAvailabilityDemoFallback ? initialBlockedDates : [], fallbackBookings);
        if (!isCurrent) return;

        setBlockedDates(response.blockedDates);
        setAvailabilityBookings(response.bookings);
      } catch {
        if (!isCurrent) return;
        setBlockedDates(useOwnerAvailabilityDemoFallback ? initialBlockedDates : []);
        setAvailabilityBookings((useOwnerAvailabilityDemoFallback ? fallbackOwnerEnquiries : []).filter((enquiry) => enquiry.status === "CONFIRMED").map(bookingFromEnquiry));
        setAvailabilityError("Could not load latest availability.");
      } finally {
        if (isCurrent) setIsLoadingAvailability(false);
      }
    }

    loadAvailability();

    return () => {
      isCurrent = false;
    };
  }, [accessToken, activeHallId]);

  useEffect(() => {
    let isCurrent = true;

    async function loadReviews() {
      setIsLoadingReviews(true);
      setReviewsError("");

      try {
        const response = await getOwnerHallReviews(activeHallId, accessToken, useOwnerReviewDemoFallback ? ownerReviews : []);
        if (!isCurrent) return;
        setReviews(response.reviews);
        setAverageRating(response.averageRating || (useOwnerReviewDemoFallback ? ownerHall.rating : 0));
        setTotalReviews(response.totalReviews || response.reviews.length);
      } catch {
        if (!isCurrent) return;
        setReviews(useOwnerReviewDemoFallback ? ownerReviews : []);
        setAverageRating(useOwnerReviewDemoFallback ? ownerHall.rating : 0);
        setTotalReviews(useOwnerReviewDemoFallback ? ownerHall.reviewCount : 0);
        setReviewsError("Could not load latest reviews.");
      } finally {
        if (isCurrent) setIsLoadingReviews(false);
      }
    }

    loadReviews();

    return () => {
      isCurrent = false;
    };
  }, [accessToken, activeHallId]);

  useEffect(() => {
    async function loadOwnerHall() {
      if (!accessToken) return;
      try {
        const halls = await getOwnerHalls(accessToken);
        if (halls.length === 0) {
          router.replace("/owner/onboarding");
          return;
        }
        setActiveHallId(String(halls[0].id));
      } catch {
        // Do not redirect a valid owner because of a temporary API failure.
      } finally {
        setIsCheckingOnboarding(false);
      }
    }

    loadOwnerHall();
  }, [accessToken, router]);

  const pendingCount = enquiries.filter((enquiry) => enquiry.status === "NEW" || enquiry.status === "PENDING_OWNER_RESPONSE").length;
  const activeBookingCount = bookings.filter((booking) => booking.status === "REQUESTED" || booking.status === "CONFIRMED").length;
  const confirmedCount = bookings.filter((booking) => booking.status === "CONFIRMED").length;
  const listingViews = useOwnerDemoFallbacks ? "1,284" : "0";
  const listingHealthChecks = [
    Boolean(listing.name.trim() && listing.city.trim() && listing.area.trim() && listing.description.trim()),
    Boolean(listing.capacity > 0 && listing.startingPrice > 0 && listing.amenities.length > 0),
    Boolean(media.length >= 3 || listing.galleryUrls.length >= 3)
  ];
  const listingHealthScore = Math.round((listingHealthChecks.filter(Boolean).length / listingHealthChecks.length) * 100);
  const listingHealthItems = [
    { label: "Profile information complete", complete: listingHealthChecks[0] },
    { label: "Pricing and amenities added", complete: listingHealthChecks[1] },
    { label: "Add 3 or more gallery photos", complete: listingHealthChecks[2] }
  ];
  const listingCapacityOptions = useMemo(() => {
    const currentCapacity = Number(listingForm.capacity);
    if (currentCapacity > 0 && !guestCapacityOptions.includes(currentCapacity)) {
      return [currentCapacity, ...guestCapacityOptions].sort((first, second) => first - second);
    }
    return guestCapacityOptions;
  }, [listingForm.capacity]);
  const confirmedBookings = useMemo(() => {
    const bookingMap = new Map<string, AvailabilityBooking>();
    availabilityBookings.forEach((booking) => bookingMap.set(booking.enquiryId ?? booking.id, booking));
    bookings
      .filter((booking) => booking.status === "CONFIRMED")
      .map(availabilityBookingFromLifecycle)
      .forEach((booking) => bookingMap.set(booking.enquiryId ?? booking.id, booking));
    enquiries
      .filter((enquiry) => enquiry.status === "CONFIRMED")
      .map(bookingFromEnquiry)
      .forEach((booking) => {
        if (!bookingMap.has(booking.enquiryId ?? booking.id)) bookingMap.set(booking.enquiryId ?? booking.id, booking);
      });
    return Array.from(bookingMap.values()).sort((first, second) => first.eventDate.localeCompare(second.eventDate));
  }, [availabilityBookings, bookings, enquiries]);
  const confirmedDays = useMemo(() => new Set(confirmedBookings.filter((booking) => booking.eventDate.startsWith(monthKey)).map((booking) => Number(booking.eventDate.slice(-2)))), [confirmedBookings, monthKey]);
  const blockedDays = new Set(blockedDates.filter((date) => date.date.startsWith(monthKey)).map((date) => Number(date.date.slice(-2))));
  const daysInMonth = new Date(
    currentMonth.getFullYear(),
    currentMonth.getMonth() + 1,
    0
  ).getDate();

  async function respondToEnquiry(id: string, status: EnquiryStatus) {
    const currentEnquiry = enquiries.find((enquiry) => enquiry.id === id);
    const previousStatus = currentEnquiry?.status;

    try {
      setUpdatingEnquiryId(id);
      setEnquiries((current) => current.map((enquiry) => enquiry.id === id ? { ...enquiry, status } : enquiry));
      const updated = await updateOwnerEnquiryStatus(id, status, accessToken);
      if (updated) {
        setEnquiries((current) => current.map((enquiry) => enquiry.id === id ? updated : enquiry));
      }
      const confirmedEnquiry = updated ?? currentEnquiry;
      if (status === "CONFIRMED" && confirmedEnquiry) {
        const booking = upsertLocalBooking(lifecycleBookingFromEnquiry({ ...confirmedEnquiry, status: "CONFIRMED" }));
        setBookings((current) => [booking, ...current.filter((item) => item.id !== booking.id && item.enquiryId !== booking.enquiryId)]);
        setAvailabilityBookings((current) => [availabilityBookingFromLifecycle(booking), ...current.filter((item) => item.id !== booking.id && item.enquiryId !== booking.enquiryId)]);
      }
      setNotice(status === "CONFIRMED" ? "Enquiry confirmed and booking created." : "Enquiry declined and the customer status was updated.");
    } catch (exception) {
      if (previousStatus) {
        setEnquiries((current) => current.map((enquiry) => enquiry.id === id ? { ...enquiry, status: previousStatus } : enquiry));
      }
      setNotice(exception instanceof Error ? exception.message : "Could not update enquiry status. Please try again.");
    } finally {
      setUpdatingEnquiryId(null);
    }
  }

  async function changeBookingStatus(bookingId: string, status: BookingStatus) {
    const previousBookings = bookings;

    try {
      setUpdatingBookingId(bookingId);
      setBookings((current) => current.map((booking) => booking.id === bookingId ? { ...booking, status, updatedAt: new Date().toISOString() } : booking));
      const updated = await updateOwnerBookingStatus(bookingId, status, accessToken);
      if (updated) {
        setBookings((current) => current.map((booking) => booking.id === bookingId ? updated : booking));
        if (status === "COMPLETED" && updated.enquiryId) {
          setEnquiries((current) => current.map((enquiry) => enquiry.id === updated.enquiryId ? { ...enquiry, status: "COMPLETED" } : enquiry));
        }
      }
      setNotice(status === "COMPLETED" ? "Booking marked completed. Customer can now be eligible for review." : status === "CANCELLED" ? "Booking cancelled." : "Booking status updated.");
    } catch (exception) {
      setBookings(previousBookings);
      setNotice(exception instanceof Error ? exception.message : "Could not update booking.");
    } finally {
      setUpdatingBookingId(null);
    }
  }

  async function addBlockedDate(date: BlockDatePayload) {
    try {
      const blockedDate = await createOwnerBlockedDate(activeHallId, date, accessToken);
      setBlockedDates((current) => [blockedDate, ...current.filter((item) => item.id !== blockedDate.id)]);
      setNotice("The selected date and slot are now blocked.");
    } catch (exception) {
      const message = exception instanceof Error ? exception.message : "Could not block this date.";
      setNotice(message);
      throw new Error(message);
    }
  }

  async function removeBlockedDate(blockId: string) {
    const previousBlockedDates = blockedDates;

    try {
      setDeletingBlockId(blockId);
      setBlockedDates((current) => current.filter((item) => item.id !== blockId));
      await deleteOwnerBlockedDate(activeHallId, blockId, accessToken);
      setNotice("Blocked date removed.");
    } catch (exception) {
      setBlockedDates(previousBlockedDates);
      setNotice(exception instanceof Error ? exception.message : "Could not remove blocked date.");
    } finally {
      setDeletingBlockId(null);
    }
  }

  function updateListingField(field: keyof Omit<ListingForm, "amenities">, value: string) {
    setListingForm((current) => ({ ...current, [field]: value }));
    setListingError("");
  }

  function captureListingLocation() {
    if (!navigator.geolocation) {
      setListingError("Location capture is not supported in this browser.");
      return;
    }

    setListingError("");
    setNotice("");
    setIsCapturingListingLocation(true);

    navigator.geolocation.getCurrentPosition(
      (position) => {
        setListingForm((current) => ({
          ...current,
          latitude: position.coords.latitude.toFixed(6),
          longitude: position.coords.longitude.toFixed(6)
        }));
        setNotice("Location captured. Please confirm the pin belongs to the venue.");
        setIsCapturingListingLocation(false);
      },
      () => {
        setListingError("Could not capture location. Allow browser location access or enter latitude and longitude manually.");
        setIsCapturingListingLocation(false);
      },
      { enableHighAccuracy: true, maximumAge: 0, timeout: 15000 }
    );
  }

  function toggleListingAmenity(amenity: string) {
    setListingForm((current) => ({
      ...current,
      amenities: current.amenities.includes(amenity)
        ? current.amenities.filter((item) => item !== amenity)
        : [...current.amenities, amenity]
    }));
    setListingError("");
  }

  async function saveListingDraft() {
    if (listing.status === "PENDING_APPROVAL" || listing.pendingUpdate) {
      return;
    }

    const coverImageUrl = currentCoverImageUrl(listing, media);
    const validationMessage = validateListingForm(listingForm, coverImageUrl);
    if (validationMessage) {
      setListingError(validationMessage);
      return;
    }

    try {
      setIsSavingListing(true);
      setListingError("");
      const saved = await updateOwnerHallListing(listing.id, payloadFromForm(listingForm, coverImageUrl), accessToken, listing);
      setListing(saved);
      setListingForm(formFromListing(saved));
      setNotice("Listing draft saved.");
    } catch (exception) {
      setListingError(exception instanceof Error ? exception.message : "Could not save listing.");
    } finally {
      setIsSavingListing(false);
    }
  }

  async function submitListingForApproval() {
    if (listing.status === "PENDING_APPROVAL" || listing.pendingUpdate) {
      return;
    }

    const coverImageUrl = currentCoverImageUrl(listing, media);
    const validationMessage = validateListingForm(listingForm, coverImageUrl);
    if (validationMessage) {
      setListingError(validationMessage);
      return;
    }

    try {
      setIsSubmittingListing(true);
      setListingError("");
      const saved = await updateOwnerHallListing(listing.id, payloadFromForm(listingForm, coverImageUrl), accessToken, listing);
      const submitted = await submitOwnerHallListing(listing.id, accessToken, saved);
      setListing(submitted);
      setListingForm(formFromListing(submitted));
      setNotice("Listing submitted for admin approval.");
    } catch (exception) {
      setListingError(exception instanceof Error ? exception.message : "Could not submit listing.");
    } finally {
      setIsSubmittingListing(false);
    }
  }

  async function uploadMedia(files: FileList | null) {
    const selectedFiles = Array.from(files ?? []);
    if (selectedFiles.length === 0) return;

    try {
      setIsUploadingMedia(true);
      setMediaError("");

      const uploaded = await Promise.all(selectedFiles.map((file, index) => uploadAndCreateOwnerMedia(
        listing.id,
        file,
        media.length + index,
        media.length === 0 && index === 0,
        accessToken
      )));

      setMedia((current) => normalizeMediaCover([...current, ...uploaded].sort((first, second) => first.sortOrder - second.sortOrder)));
      const cover = uploaded.find((item) => item.isCover);
      if (cover) setListing((current) => ({ ...current, imageUrl: cover.url }));
      setNotice(`${uploaded.length} photo${uploaded.length === 1 ? "" : "s"} added.`);
    } catch (exception) {
      setMediaError(exception instanceof Error ? exception.message : "Could not upload media.");
    } finally {
      setIsUploadingMedia(false);
    }
  }

  async function setCoverMedia(mediaId: string) {
    try {
      setUpdatingMediaId(mediaId);
      setMediaError("");
      setMedia((current) => current.map((item) => ({ ...item, isCover: item.id === mediaId })));
      const updated = await updateOwnerMedia(listing.id, mediaId, { isCover: true }, accessToken);
      if (updated) {
        setMedia((current) => current.map((item) => item.id === mediaId ? { ...item, ...updated, isCover: true } : { ...item, isCover: false }));
      }
      const cover = media.find((item) => item.id === mediaId);
      if (cover) setListing((current) => ({ ...current, imageUrl: cover.url }));
      setNotice("Cover photo updated.");
    } catch (exception) {
      setMedia(getLocalOwnerMedia(listing.id, mediaFromListing(listing)));
      setMediaError(exception instanceof Error ? exception.message : "Could not update cover photo.");
    } finally {
      setUpdatingMediaId(null);
    }
  }

  async function removeMedia(mediaId: string) {
    const previousMedia = media;

    try {
      setUpdatingMediaId(mediaId);
      setMediaError("");
      const nextMedia = normalizeMediaCover(media.filter((item) => item.id !== mediaId));
      setMedia(nextMedia);
      await deleteOwnerMedia(listing.id, mediaId, accessToken);
      const nextCover = nextMedia.find((item) => item.isCover);
      if (nextCover) setListing((current) => ({ ...current, imageUrl: nextCover.url }));
      setNotice("Photo removed.");
    } catch (exception) {
      setMedia(previousMedia);
      setMediaError(exception instanceof Error ? exception.message : "Could not remove photo.");
    } finally {
      setUpdatingMediaId(null);
    }
  }

  const isListingPublic = listing.status === "APPROVED";
  const areListingActionsLocked = listing.status === "PENDING_APPROVAL" || listing.pendingUpdate;

  if (isCheckingOnboarding) {
    return <div className="grid min-h-[60vh] place-items-center"><LoaderCircle className="animate-spin text-primary" size={28} /></div>;
  }

  return (
    <>
      <main className="mx-auto w-full max-w-7xl px-4 py-7 sm:px-6 sm:py-10">
        <div className="flex flex-wrap items-start justify-between gap-5">
          <div><div className="flex items-center gap-2 text-sm font-semibold text-primary"><BadgeCheck size={17} /> Owner workspace</div><h1 className="mt-2 text-3xl font-semibold">{listing.name}</h1><p className="mt-2 flex items-center gap-2 text-sm text-muted-foreground"><MapPin size={16} /> {listing.area}, {listing.city}</p></div>
          <div className="flex flex-wrap items-center gap-2">{isListingPublic ? <Link className="inline-flex h-10 items-center gap-2 rounded-md border border-border bg-white px-3 text-sm font-medium hover:border-primary" href={`/halls/${listing.id}`}><Eye size={17} /> Public listing</Link> : <button className="inline-flex h-10 cursor-not-allowed items-center gap-2 rounded-md border border-border bg-white px-3 text-sm font-medium text-muted-foreground" disabled type="button"><Eye size={17} /> Awaiting approval</button>}<Link className="inline-flex h-10 items-center gap-2 rounded-md bg-foreground px-3 text-sm font-medium text-white" href="/owner/onboarding"><Plus size={17} /> Add venue</Link></div>
        </div>

        {notice && <div className="mt-6 flex items-start justify-between gap-4 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800"><span className="flex items-start gap-2"><CheckCircle2 className="mt-0.5 shrink-0" size={18} />{notice}</span><button aria-label="Dismiss notification" onClick={() => setNotice("")}><X size={17} /></button></div>}

        <div className="mt-8 flex gap-1 overflow-x-auto border-b border-border" role="tablist" aria-label="Owner dashboard">
          {tabs.map((tab) => <button aria-selected={activeTab === tab.id} className={`shrink-0 border-b-2 px-4 py-3 text-sm font-medium ${activeTab === tab.id ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:text-foreground"}`} key={tab.id} onClick={() => setActiveTab(tab.id)} role="tab" type="button">{tab.label}{tab.id === "enquiries" && pendingCount > 0 && <span className="ml-2 rounded-full bg-blue-100 px-2 py-0.5 text-xs text-blue-700">{pendingCount}</span>}{tab.id === "bookings" && activeBookingCount > 0 && <span className="ml-2 rounded-full bg-emerald-100 px-2 py-0.5 text-xs text-emerald-700">{activeBookingCount}</span>}</button>)}
        </div>

        {activeTab === "overview" && (
          <section className="py-7">
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <div className="rounded-lg border border-border bg-white p-5">
                <MessageSquareText className="text-blue-600" size={21} />
                <p className="mt-5 text-2xl font-semibold">{pendingCount}</p>
                <p className="mt-1 text-sm text-muted-foreground">New enquiries</p>
              </div>
              <div className="rounded-lg border border-border bg-white p-5">
                <CalendarDays className="text-primary" size={21} />
                <p className="mt-5 text-2xl font-semibold">{confirmedCount}</p>
                <p className="mt-1 text-sm text-muted-foreground">Confirmed events</p>
              </div>
              <div className="rounded-lg border border-border bg-white p-5">
                <Eye className="text-violet-600" size={21} />
                <p className="mt-5 text-2xl font-semibold">{listingViews}</p>
                <p className="mt-1 text-sm text-muted-foreground">Listing views</p>
              </div>
              <div className="rounded-lg border border-border bg-white p-5">
                <Star className="text-amber-500" size={21} />
                <p className="mt-5 text-2xl font-semibold">{averageRating.toFixed(1)}</p>
                <p className="mt-1 text-sm text-muted-foreground">Average rating</p>
              </div>
            </div>

            <div className="mt-9 grid gap-7 lg:grid-cols-[1.4fr_1fr]">
              <section>
                <div className="flex items-center justify-between">
                  <h2 className="text-xl font-semibold">Recent enquiries</h2>
                  <button className="text-sm font-semibold text-primary" onClick={() => setActiveTab("enquiries")}>View all</button>
                </div>
                <div className="mt-4 grid gap-3">
                  {enquiries.slice(0, 3).length > 0 ? enquiries.slice(0, 3).map((enquiry) => (
                    <button className="flex w-full items-center gap-4 rounded-lg border border-border bg-white p-4 text-left hover:border-primary" key={enquiry.id} onClick={() => setActiveTab("enquiries")}>
                      <span className="grid size-11 shrink-0 place-items-center rounded-md bg-blue-50 text-blue-700"><CalendarDays size={20} /></span>
                      <span className="min-w-0 flex-1">
                        <strong className="block">{enquiry.eventType}</strong>
                        <span className="mt-1 block text-sm text-muted-foreground">{formatDate(enquiry.eventDate)} | {enquiry.guestCount} guests</span>
                      </span>
                      <span className={`hidden rounded-full px-2.5 py-1 text-xs font-medium sm:block ${statusStyle[enquiry.status]}`}>{formatStatus(enquiry.status)}</span>
                      <ChevronRight size={18} />
                    </button>
                  )) : (
                    <div className="rounded-lg border border-dashed border-border bg-white p-6 text-sm text-muted-foreground">No enquiries yet.</div>
                  )}
                </div>
              </section>

              <section>
                <h2 className="text-xl font-semibold">Listing health</h2>
                <div className="mt-4 rounded-lg border border-border bg-white p-5">
                  <div className="flex items-center justify-between">
                    <span className={`inline-flex items-center gap-2 rounded-full px-2.5 py-1 text-sm font-semibold ${listingStatusStyle[listing.status]}`}>
                      <BadgeCheck size={17} /> {formatListingStatus(listing.status)}
                    </span>
                    <span className="text-sm font-semibold">{listingHealthScore}%</span>
                  </div>
                  <div className="mt-4 h-2 overflow-hidden rounded-full bg-muted">
                    <div className="h-full bg-primary" style={{ width: `${listingHealthScore}%` }} />
                  </div>
                  <div className="mt-5 grid gap-3 text-sm">
                    {listingHealthItems.map((item) => (
                      <p className={`flex items-center gap-2 ${item.complete ? "text-emerald-700" : "text-amber-700"}`} key={item.label}>
                        {item.complete ? <Check size={16} /> : <ImagePlus size={16} />} {item.label}
                      </p>
                    ))}
                  </div>
                  <button className="mt-5 text-sm font-semibold text-primary" onClick={() => setActiveTab("listing")}>Improve listing</button>
                </div>
              </section>
            </div>
          </section>
        )}

        {activeTab === "activity" && <NotificationActivity />}

        {activeTab === "reports" && (
          <section className="py-7">
            <div>
              <h2 className="text-xl font-semibold">Hall reports</h2>
              <p className="mt-1 text-sm text-muted-foreground">Enquiries, confirmed events, revenue, and review performance.</p>
            </div>

            {analyticsError && <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{analyticsError}</p>}

            {isLoadingAnalytics ? (
              <div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                {[1, 2, 3, 4].map((item) => <div className="h-28 animate-pulse rounded-lg border border-border bg-white" key={item} />)}
              </div>
            ) : (
              <>
                <div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                  <ReportMetric label="Total enquiries" value={analytics.enquiries.toLocaleString("en-IN")} />
                  <ReportMetric label="Confirmed bookings" value={analytics.confirmedBookings.toLocaleString("en-IN")} />
                  <ReportMetric label="Estimated revenue" value={formatCompactMoney(analytics.estimatedRevenue)} />
                  <ReportMetric label="Conversion rate" value={`${analytics.conversionRate}%`} />
                  <ReportMetric label="Completed events" value={analytics.completedBookings.toLocaleString("en-IN")} />
                  <ReportMetric label="Average rating" value={analytics.averageRating.toFixed(1)} />
                  <ReportMetric label="Occupancy rate" value={`${analytics.occupancyRate}%`} />
                </div>

                <div className="mt-6 grid gap-6 lg:grid-cols-[1.4fr_1fr]">
                  <section className="rounded-lg border border-border bg-white">
                    <div className="border-b border-border px-5 py-4">
                      <h3 className="font-semibold">Monthly trend</h3>
                    </div>
                    <div className="divide-y divide-border">
                      {analytics.trends.map((point) => (
                        <div className="grid gap-2 px-5 py-4 text-sm sm:grid-cols-[80px_1fr_1fr_1fr]" key={point.label}>
                          <strong>{point.label}</strong>
                          <span>{point.enquiries} enquiries</span>
                          <span>{point.bookings} bookings</span>
                          <span className="font-medium">{formatCompactMoney(point.revenue)}</span>
                        </div>
                      ))}
                    </div>
                  </section>

                  <section className="rounded-lg border border-border bg-white">
                    <div className="border-b border-border px-5 py-4">
                      <h3 className="font-semibold">Event mix</h3>
                    </div>
                    <div className="divide-y divide-border">
                      {analytics.eventMix.map((item) => (
                        <div className="flex items-center justify-between gap-4 px-5 py-4" key={item.eventType}>
                          <p className="font-medium">{item.eventType}</p>
                          <span className="rounded-full bg-blue-50 px-2.5 py-1 text-sm font-semibold text-blue-700">{item.count}</span>
                        </div>
                      ))}
                    </div>
                  </section>
                </div>
              </>
            )}
          </section>
        )}

        {activeTab === "enquiries" && (
          <section className="py-7">
            <div className="flex flex-wrap items-end justify-between gap-4">
              <div>
                <h2 className="text-xl font-semibold">Enquiry inbox</h2>
                <p className="mt-1 text-sm text-muted-foreground">Respond to event requests and update customer status.</p>
              </div>
              <select className="h-10 rounded-md border border-border bg-white px-3 text-sm">
                <option>All enquiries</option>
                <option>Pending response</option>
                <option>Confirmed</option>
                <option>Declined</option>
              </select>
            </div>

            {enquiriesError && <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{enquiriesError}</p>}

            {isLoadingEnquiries ? (
              <div className="mt-5 grid gap-4">
                {[1, 2, 3].map((item) => <div className="h-28 animate-pulse rounded-lg border border-border bg-white" key={item} />)}
              </div>
            ) : enquiries.length > 0 ? (
              <div className="mt-5 grid gap-4">
                {enquiries.map((enquiry) => {
                  const isPending = enquiry.status === "NEW" || enquiry.status === "PENDING_OWNER_RESPONSE";
                  const isUpdating = updatingEnquiryId === enquiry.id;

                  return (
                    <article className="rounded-lg border border-border bg-white p-5" key={enquiry.id}>
                      <div className="flex flex-col gap-5 lg:flex-row lg:items-center">
                        <div className="grid size-12 shrink-0 place-items-center rounded-md bg-blue-50 text-blue-700">
                          <UsersRound size={22} />
                        </div>
                        <div className="min-w-0 flex-1">
                          <div className="flex flex-wrap items-center gap-2">
                            <h3 className="font-semibold">{enquiry.eventType}</h3>
                            <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${statusStyle[enquiry.status]}`}>{formatStatus(enquiry.status)}</span>
                          </div>
                          <p className="mt-2 text-sm text-muted-foreground">{formatDate(enquiry.eventDate)} | {formatSlot(enquiry.slot)} | {enquiry.guestCount} guests</p>
                        </div>
                        {isPending ? (
                          <div className="flex flex-wrap gap-2">
                            <button className="inline-flex h-10 items-center gap-2 rounded-md border border-rose-200 px-3 text-sm font-semibold text-rose-700 hover:bg-rose-50 disabled:opacity-60" disabled={isUpdating} onClick={() => respondToEnquiry(enquiry.id, "DECLINED")}>
                              {isUpdating ? <LoaderCircle className="animate-spin" size={16} /> : <Ban size={16} />} Decline
                            </button>
                            <button className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-3 text-sm font-semibold text-white disabled:opacity-60" disabled={isUpdating} onClick={() => respondToEnquiry(enquiry.id, "CONFIRMED")}>
                              {isUpdating ? <LoaderCircle className="animate-spin" size={16} /> : <Check size={16} />} Confirm request
                            </button>
                          </div>
                        ) : (
                          <button
                            className="inline-flex h-10 items-center gap-2 rounded-md border border-border px-3 text-sm font-medium"
                            onClick={() =>
                              setExpandedEnquiryId(
                                expandedEnquiryId === enquiry.id
                                  ? null
                                  : enquiry.id
                              )
                            }
                          >
                            <>
                              {expandedEnquiryId === enquiry.id ? (
                                <>
                                  <ChevronDown size={16} />
                                  Hide Details
                                </>
                              ) : (
                                <>
                                  <ChevronRight size={16} />
                                  View Details
                                </>
                              )}
                            </>
                          </button>
                        )}
                      </div>
                      {expandedEnquiryId === enquiry.id && (
                        <div className="mt-6 border-t pt-6">

                          <div className="grid gap-6 md:grid-cols-2">

                            {/* Customer Information */}
                            <div className="rounded-lg border p-4">
                              <h4 className="mb-3 text-sm font-semibold text-gray-700">
                                Customer Information
                              </h4>

                              <div className="space-y-2 text-sm">
                                <p>
                                  <span className="font-medium">Name:</span>{" "}
                                  {enquiry.customerName ?? "Not Available"}
                                </p>

                                <p>
                                  <span className="font-medium">Phone:</span>{" "}
                                  {enquiry.customerPhone ?? "Not Available"}
                                </p>

                                <p>
                                  <span className="font-medium">Email:</span>{" "}
                                  {enquiry.customerEmail ?? "Not Available"}
                                </p>
                              </div>
                            </div>

                            {/* Event Information */}
                            <div className="rounded-lg border p-4">
                              <h4 className="mb-3 text-sm font-semibold text-gray-700">
                                Event Information
                              </h4>

                              <div className="space-y-2 text-sm">
                                <p>
                                  <span className="font-medium">Event:</span>{" "}
                                  {enquiry.eventType}
                                </p>

                                <p>
                                  <span className="font-medium">Guests:</span>{" "}
                                  {enquiry.guestCount}
                                </p>

                                <p>
                                  <span className="font-medium">Date:</span>{" "}
                                  {formatDate(enquiry.eventDate)}
                                </p>

                                <p>
                                  <span className="font-medium">Slot:</span>{" "}
                                  {formatSlot(enquiry.slot)}
                                </p>
                              </div>
                            </div>

                          </div>

                          {/* Notes */}
                          <div className="mt-6 rounded-lg border p-4">
                            <h4 className="mb-3 text-sm font-semibold text-gray-700">
                              Customer Notes
                            </h4>

                            <p className="text-sm text-muted-foreground">
                              {enquiry.notes || "No notes provided."}
                            </p>
                          </div>

                          {/* Status */}
                          <div className="mt-6 flex flex-wrap gap-6 text-sm">

                            <div>
                              <span className="font-medium">Status:</span>{" "}
                              <span
                                className={`rounded-full px-2 py-1 text-xs ${statusStyle[enquiry.status]}`}
                              >
                                {formatStatus(enquiry.status)}
                              </span>
                            </div>

                            <div>
                              <span className="font-medium">Enquiry ID:</span>{" "}
                              {enquiry.id}
                            </div>

                            <div>
                              <span className="font-medium">Created:</span>{" "}
                              {new Date(enquiry.submittedAt).toLocaleString()}
                            </div>

                            <div>
                              <span className="font-medium">Updated:</span>{" "}
                              {enquiry.updatedAt
                                ? new Date(enquiry.updatedAt).toLocaleString()
                                : "Not Available"}
                            </div>

                          </div>

                        </div>
                      )}
                    </article>
                  );
                })}
              </div>
            ) : (
              <div className="mt-5 rounded-lg border border-dashed border-border bg-white p-8 text-center">
                <MessageSquareText className="mx-auto text-muted-foreground" size={28} />
                <h3 className="mt-4 font-semibold">No enquiries yet</h3>
                <p className="mt-2 text-sm text-muted-foreground">New customer requests will appear here.</p>
              </div>
            )}
          </section>
        )}

        {activeTab === "bookings" && (
          <section className="py-7">
            <div className="flex flex-wrap items-end justify-between gap-4">
              <div>
                <h2 className="text-xl font-semibold">Booking lifecycle</h2>
                <p className="mt-1 text-sm text-muted-foreground">Manage confirmed events through cancellation and completion.</p>
              </div>
              <span className="rounded-md border border-border bg-white px-3 py-2 text-sm text-muted-foreground">{bookings.length} total</span>
            </div>

            {bookingsError && <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{bookingsError}</p>}

            {isLoadingBookings ? (
              <div className="mt-5 grid gap-4">{[1, 2].map((item) => <div className="h-36 animate-pulse rounded-lg border border-border bg-white" key={item} />)}</div>
            ) : bookings.length > 0 ? (
              <div className="mt-5 grid gap-4">
                {bookings.map((booking) => {
                  const isUpdating = updatingBookingId === booking.id;
                  const canComplete = booking.status === "CONFIRMED";
                  const canCancel = booking.status === "REQUESTED" || booking.status === "CONFIRMED";

                  return (
                    <article className="rounded-lg border border-border bg-white p-5" key={booking.id}>
                      <div className="flex flex-col gap-5 lg:flex-row lg:items-center">
                        <div className="grid size-12 shrink-0 place-items-center rounded-md bg-emerald-50 text-emerald-700"><CalendarDays size={22} /></div>
                        <div className="min-w-0 flex-1">
                          <div className="flex flex-wrap items-center gap-2">
                            <h3 className="font-semibold">{booking.eventType}</h3>
                            <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${bookingStatusStyle[booking.status]}`}>{formatBookingStatus(booking.status)}</span>
                          </div>
                          <p className="mt-2 text-sm text-muted-foreground">{formatDate(booking.eventDate)} | {formatSlot(booking.slot)} | {booking.guestCount} guests</p>
                          <p className="mt-1 text-sm text-muted-foreground">{booking.customerName ?? booking.customerId ?? "Customer"} | Booking {booking.id}</p>
                          <p className={`mt-2 inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium ${paymentStatusStyle[booking.paymentStatus]}`}><CreditCard size={13} /> {formatPaymentStatus(booking.paymentStatus)}</p>
                        </div>
                        <div className="flex flex-wrap gap-2">
                          {canComplete && (
                            <button className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-3 text-sm font-semibold text-white disabled:opacity-60" disabled={isUpdating} onClick={() => changeBookingStatus(booking.id, "COMPLETED")} type="button">
                              {isUpdating ? <LoaderCircle className="animate-spin" size={16} /> : <CheckCircle2 size={16} />} Complete
                            </button>
                          )}
                          {canCancel && (
                            <button className="inline-flex h-10 items-center gap-2 rounded-md border border-rose-200 px-3 text-sm font-semibold text-rose-700 hover:bg-rose-50 disabled:opacity-60" disabled={isUpdating} onClick={() => changeBookingStatus(booking.id, "CANCELLED")} type="button">
                              {isUpdating ? <LoaderCircle className="animate-spin" size={16} /> : <Ban size={16} />} Cancel
                            </button>
                          )}
                          {!canComplete && !canCancel && (
                            <span className="inline-flex h-10 items-center rounded-md border border-border px-3 text-sm text-muted-foreground">No action needed</span>
                          )}
                        </div>
                      </div>
                    </article>
                  );
                })}
              </div>
            ) : (
              <div className="mt-5 rounded-lg border border-dashed border-border bg-white p-8 text-center">
                <CalendarDays className="mx-auto text-muted-foreground" size={28} />
                <h3 className="mt-4 font-semibold">No bookings yet</h3>
                <p className="mt-2 text-sm text-muted-foreground">Confirm an enquiry to create the first booking.</p>
              </div>
            )}
          </section>
        )}

        {activeTab === "availability" && (
          <section className="py-7">
            <div className="flex flex-wrap items-end justify-between gap-4">
              <div>
                <h2 className="text-xl font-semibold">Availability calendar</h2>
                <p className="mt-1 text-sm text-muted-foreground">Confirmed bookings and owner-blocked slots.</p>
              </div>
              <button className="inline-flex h-10 items-center gap-2 rounded-md bg-foreground px-4 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50" disabled={listing.status !== "APPROVED"} onClick={() => setBlockDialogOpen(true)} title={listing.status !== "APPROVED" ? "Availability can be managed after admin approval" : undefined}>
                <Plus size={17} /> Block date
              </button>
            </div>

            {listing.status !== "APPROVED" && <p className="mt-4 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800">Date blocking is available after your hall has been approved by an admin.</p>}

            {availabilityError && <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{availabilityError}</p>}

            {isLoadingAvailability ? (
              <div className="mt-5 grid gap-4">
                <div className="h-96 animate-pulse rounded-lg border border-border bg-white" />
                <div className="grid gap-3 lg:grid-cols-2">
                  <div className="h-32 animate-pulse rounded-lg border border-border bg-white" />
                  <div className="h-32 animate-pulse rounded-lg border border-border bg-white" />
                </div>
              </div>
            ) : (
              <>
                <div className="mt-5 rounded-lg border border-border bg-white p-4 sm:p-6">
                  <div className="flex items-center justify-between">
                    <button
                      aria-label="Previous month"
                      className="grid size-9 place-items-center rounded-md border border-border"
                      onClick={() =>
                        setCurrentMonth(
                          new Date(
                            currentMonth.getFullYear(),
                            currentMonth.getMonth() - 1,
                            1
                          )
                        )
                      }
                    >
                      ‹
                    </button>                    <h3 className="font-semibold">
                      {currentMonth.toLocaleDateString("en-IN", {
                        month: "long",
                        year: "numeric",
                      })}
                    </h3>
                    <button
                      aria-label="Next month"
                      className="grid size-9 place-items-center rounded-md border border-border"
                      onClick={() =>
                        setCurrentMonth(
                          new Date(
                            currentMonth.getFullYear(),
                            currentMonth.getMonth() + 1,
                            1
                          )
                        )
                      }
                    >
                      ›
                    </button>
                  </div>
                  <div className="mt-5 grid grid-cols-7 text-center text-xs font-medium text-muted-foreground">
                    {["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"].map((day) => <span className="py-2" key={day}>{day}</span>)}
                  </div>
                  <div className="grid grid-cols-7 gap-1">
                    {[0, 0, 0].map((_, index) => <span key={`blank-${index}`} />)}
                    {Array.from({ length: daysInMonth }, (_, index) => index + 1).map((day) => {
                      const confirmed = confirmedDays.has(day);
                      const blocked = blockedDays.has(day);

                      return (
                        <button
                          key={day}
                          aria-label={`${monthKey}-${String(day).padStart(2, "0")}${confirmed ? ", confirmed" : blocked ? ", blocked" : ", available"
                            }`}
                          className={`aspect-square min-h-10 rounded-md border text-sm ${confirmed
                            ? "border-emerald-200 bg-emerald-50 font-semibold text-emerald-800"
                            : blocked
                              ? "border-rose-200 bg-rose-50 font-semibold text-rose-700"
                              : "border-transparent hover:border-primary"
                            }`}
                          onClick={() => {
                            if (listing.status !== "APPROVED" || confirmedDays.has(day) || blockedDays.has(day)) {
                              return;
                            }

                            setSelectedBlockDate(
                              `${monthKey}-${String(day).padStart(2, "0")}`
                            );

                            setBlockDialogOpen(true);
                          }}
                        >
                          {day}
                        </button>
                      );
                    })}
                  </div>
                  <div className="mt-5 flex flex-wrap gap-4 border-t border-border pt-4 text-xs text-muted-foreground">
                    <span className="flex items-center gap-2"><i className="size-3 rounded-sm bg-emerald-100" /> Confirmed</span>
                    <span className="flex items-center gap-2"><i className="size-3 rounded-sm bg-rose-100" /> Blocked</span>
                    <span className="flex items-center gap-2"><i className="size-3 rounded-sm border border-border bg-white" /> Available</span>
                  </div>
                </div>

                <div className="mt-5 grid gap-5 lg:grid-cols-2">
                  <section>
                    <div className="flex items-center justify-between gap-4">
                      <h3 className="font-semibold">Blocked slots</h3>
                      <span className="text-sm text-muted-foreground">{blockedDates.length}</span>
                    </div>
                    <div className="mt-3 grid gap-3">
                      {blockedDates.length > 0 ? blockedDates.map((date) => {
                        const isDeleting = deletingBlockId === date.id;

                        return (
                          <div className="flex items-center gap-4 rounded-lg border border-border bg-white p-4" key={date.id}>
                            <span className="grid size-10 place-items-center rounded-md bg-rose-50 text-rose-700"><CalendarDays size={19} /></span>
                            <div className="min-w-0 flex-1">
                              <p className="font-medium">{formatDate(date.date)}</p>
                              <p className="mt-1 text-sm text-muted-foreground">{formatSlot(date.slot)} | {date.reason}</p>
                            </div>
                            <button aria-label={`Remove block for ${date.date}`} className="grid size-9 place-items-center rounded-md text-muted-foreground hover:bg-muted disabled:opacity-60" disabled={listing.status !== "APPROVED" || isDeleting} onClick={() => removeBlockedDate(date.id)}>
                              {isDeleting ? <LoaderCircle className="animate-spin" size={17} /> : <X size={17} />}
                            </button>
                          </div>
                        );
                      }) : (
                        <div className="rounded-lg border border-dashed border-border bg-white p-6 text-sm text-muted-foreground">No blocked slots for this hall.</div>
                      )}
                    </div>
                  </section>

                  <section>
                    <div className="flex items-center justify-between gap-4">
                      <h3 className="font-semibold">Confirmed bookings</h3>
                      <span className="text-sm text-muted-foreground">{confirmedBookings.length}</span>
                    </div>
                    <div className="mt-3 grid gap-3">
                      {confirmedBookings.length > 0 ? confirmedBookings.map((booking) => (
                        <div className="flex items-center gap-4 rounded-lg border border-border bg-white p-4" key={booking.id}>
                          <span className="grid size-10 place-items-center rounded-md bg-emerald-50 text-emerald-700"><Check size={19} /></span>
                          <div className="min-w-0 flex-1">
                            <p className="font-medium">{booking.eventType}</p>
                            <p className="mt-1 text-sm text-muted-foreground">{formatDate(booking.eventDate)} | {formatSlot(booking.slot)} | {booking.guestCount} guests</p>
                          </div>
                        </div>
                      )) : (
                        <div className="rounded-lg border border-dashed border-border bg-white p-6 text-sm text-muted-foreground">No confirmed bookings yet.</div>
                      )}
                    </div>
                  </section>
                </div>
              </>
            )}
          </section>
        )}

        {activeTab === "listing" && (
          <section className="py-7">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div>
                <h2 className="text-xl font-semibold">Hall listing</h2>
                <p className="mt-1 text-sm text-muted-foreground">Edit public information and submit updates for admin approval.</p>
              </div>
              <span className={`inline-flex items-center gap-2 rounded-full px-3 py-1.5 text-sm font-semibold ${listingStatusStyle[listing.status]}`}>
                <BadgeCheck size={17} /> {formatListingStatus(listing.status)}
              </span>
            </div>

            {listingError && <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{listingError}</p>}
            {listing.pendingUpdate && <p className="mt-4 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800" role="status">An updated listing is already pending admin approval. You can edit again after it has been approved or rejected.</p>}
            {listing.status === "REJECTED" && listing.rejectionReason && <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700">{listing.rejectionReason}</p>}

            {isLoadingListing ? (
              <div className="mt-5 grid gap-5 lg:grid-cols-[1fr_420px]">
                <div className="h-[520px] animate-pulse rounded-lg border border-border bg-white" />
                <div className="h-[520px] animate-pulse rounded-lg border border-border bg-white" />
              </div>
            ) : (
              <div className="mt-5 grid gap-5 lg:grid-cols-[1fr_420px]">
                <section className="rounded-lg border border-border bg-white p-5 sm:p-6">
                  <div className="grid gap-5 sm:grid-cols-2">
                    <label className="text-sm font-medium sm:col-span-2">Venue name<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" onChange={(event) => updateListingField("name", event.target.value)} value={listingForm.name} /></label>
                    <label className="text-sm font-medium">Venue type<select className="mt-2 h-11 w-full rounded-md border border-border bg-white px-3 font-normal outline-none focus:border-primary" onChange={(event) => updateListingField("venueType", event.target.value as VenueType)} value={listingForm.venueType}><option>Marriage Hall</option><option>Banquet Hall</option><option>Mini Hall</option><option>Convention Centre</option></select></label>
                    <label className="text-sm font-medium">Maximum guests<span className="relative mt-2 block"><select className="h-11 w-full appearance-none rounded-md border border-border bg-white px-3 pr-10 font-normal outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/15" onChange={(event) => updateListingField("capacity", event.target.value)} value={listingForm.capacity}><option value="">Choose guest capacity</option>{listingCapacityOptions.map((option) => <option key={option} value={option}>{formatGuestCapacityOption(option)}</option>)}</select><ChevronDown aria-hidden="true" className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground" size={17} /></span></label>
                    <label className="text-sm font-medium">City<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" onChange={(event) => updateListingField("city", event.target.value)} value={listingForm.city} /></label>
                    <label className="text-sm font-medium">Area<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" onChange={(event) => updateListingField("area", event.target.value)} value={listingForm.area} /></label>
                    <label className="text-sm font-medium sm:col-span-2">Pincode<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" inputMode="numeric" onChange={(event) => updateListingField("pincode", event.target.value)} placeholder="6-digit pincode" value={listingForm.pincode} /></label>
                    <label className="text-sm font-medium sm:col-span-2">Address line<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" onChange={(event) => updateListingField("addressLine", event.target.value)} placeholder="Door number, street, landmark" value={listingForm.addressLine} /></label>
                    <div className="rounded-lg border border-border bg-background p-4 sm:col-span-2">
                      <div className="flex flex-wrap items-center justify-between gap-3">
                        <div>
                          <h3 className="text-sm font-semibold">Venue map location</h3>
                          <p className="mt-1 text-xs text-muted-foreground">Capture this while standing at the hall entrance.</p>
                        </div>
                        <button className="inline-flex h-10 items-center gap-2 rounded-md bg-foreground px-4 text-sm font-semibold text-white disabled:opacity-60" disabled={isCapturingListingLocation} onClick={captureListingLocation} type="button">
                          {isCapturingListingLocation ? <LoaderCircle className="animate-spin" size={16} /> : <MapPin size={16} />} Use current location
                        </button>
                      </div>
                      <div className="mt-4 grid gap-4 sm:grid-cols-2">
                        <label className="text-sm font-medium">Latitude<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" inputMode="decimal" onChange={(event) => updateListingField("latitude", event.target.value)} placeholder="13.082680" value={listingForm.latitude} /></label>
                        <label className="text-sm font-medium">Longitude<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" inputMode="decimal" onChange={(event) => updateListingField("longitude", event.target.value)} placeholder="80.270721" value={listingForm.longitude} /></label>
                      </div>
                      {hasCapturedListingLocation(listingForm) && <a className="mt-3 inline-flex text-sm font-semibold text-primary" href={googleMapsUrl(listingForm.latitude, listingForm.longitude)} rel="noreferrer" target="_blank">Check pin in Google Maps</a>}
                    </div>
                    <label className="text-sm font-medium sm:col-span-2">Contact number<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" inputMode="tel" onChange={(event) => updateListingField("contactNumber", event.target.value)} placeholder="Owner or venue phone" value={listingForm.contactNumber} /></label>
                    <label className="text-sm font-medium sm:col-span-2">Starting price<input className="mt-2 h-11 w-full rounded-md border border-border px-3 font-normal outline-none focus:border-primary" min="1" onChange={(event) => updateListingField("startingPrice", event.target.value)} type="number" value={listingForm.startingPrice} /></label>
                    <label className="text-sm font-medium sm:col-span-2">Description<textarea className="mt-2 min-h-28 w-full resize-y rounded-md border border-border p-3 font-normal leading-6 outline-none focus:border-primary" maxLength={800} onChange={(event) => updateListingField("description", event.target.value)} value={listingForm.description} /></label>
                  </div>

                  <fieldset className="mt-6">
                    <legend className="text-sm font-medium">Amenities</legend>
                    <div className="mt-3 grid gap-2 sm:grid-cols-2">
                      {amenityOptions.map((amenity) => (
                        <label className={`flex min-h-11 cursor-pointer items-center gap-3 rounded-md border px-3 text-sm ${listingForm.amenities.includes(amenity) ? "border-primary bg-emerald-50" : "border-border"}`} key={amenity}>
                          <input checked={listingForm.amenities.includes(amenity)} className="size-4 accent-[hsl(var(--primary))]" onChange={() => toggleListingAmenity(amenity)} type="checkbox" />
                          {amenity}
                        </label>
                      ))}
                    </div>
                  </fieldset>

                  <div className="mt-6 flex flex-wrap gap-2 border-t border-border pt-5">
                    <button className="inline-flex h-10 items-center gap-2 rounded-md border border-border px-4 text-sm font-semibold hover:border-primary disabled:cursor-not-allowed disabled:opacity-60" disabled={areListingActionsLocked || isSavingListing || isSubmittingListing} onClick={saveListingDraft} type="button">
                      {isSavingListing ? <LoaderCircle className="animate-spin" size={17} /> : <Check size={17} />} Save draft
                    </button>
                    <button className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60" disabled={areListingActionsLocked || isSavingListing || isSubmittingListing} onClick={submitListingForApproval} type="button">
                      {isSubmittingListing ? <LoaderCircle className="animate-spin" size={17} /> : <BadgeCheck size={17} />} Submit for approval
                    </button>
                  </div>
                </section>

                <aside className="h-fit overflow-hidden rounded-lg border border-border bg-white">
                  <div className="relative aspect-[4/3] bg-muted">
                    {listing.imageUrl ? (
                      <Image alt={listing.name} className="object-cover" fill sizes="420px" src={listing.imageUrl} unoptimized={listing.imageUrl.startsWith("blob:")} />
                    ) : (
                      <div className="grid h-full place-items-center text-muted-foreground">
                        <ImagePlus size={32} />
                      </div>
                    )}
                  </div>
                  <div className="p-5">
                    <p className="text-sm font-semibold text-primary">{listingForm.venueType}</p>
                    <h3 className="mt-2 text-2xl font-semibold">{listingForm.name ? toTitleCase(listingForm.name) : "Untitled venue"}</h3>
                    <p className="mt-2 flex items-center gap-2 text-sm text-muted-foreground"><MapPin size={16} /> {listingForm.area ? toTitleCase(listingForm.area) : "Area"}, {listingForm.city ? toTitleCase(listingForm.city) : "City"}</p>
                    {listingForm.addressLine && <p className="mt-2 text-sm leading-6 text-muted-foreground">{toTitleCase(listingForm.addressLine)}</p>}
                    {listingForm.contactNumber && <p className="mt-2 text-sm font-medium text-muted-foreground">Contact: {listingForm.contactNumber}</p>}
                    <p className="mt-5 leading-7 text-muted-foreground">{listingForm.description || "Add a description for customers."}</p>
                    <div className="mt-6 grid grid-cols-2 gap-4 border-t border-border pt-5">
                      <div><p className="text-xs text-muted-foreground">Capacity</p><p className="mt-1 font-semibold">{listingForm.capacity ? formatGuestCount(listingForm.capacity) : "0"}</p></div>
                      <div><p className="text-xs text-muted-foreground">Starting price</p><p className="mt-1 font-semibold">INR {new Intl.NumberFormat("en-IN").format(Number(listingForm.startingPrice || 0))}</p></div>
                      <div><p className="text-xs text-muted-foreground">Amenities</p><p className="mt-1 font-semibold">{listingForm.amenities.length}</p></div>
                      <div><p className="text-xs text-muted-foreground">Rating</p><p className="mt-1 font-semibold">{listing.rating}</p></div>
                    </div>
                    {isListingPublic ? <Link className="mt-6 inline-flex h-10 items-center rounded-md border border-border px-4 text-sm font-semibold hover:border-primary" href={`/halls/${listing.id}`}>Preview public page</Link> : <button className="mt-6 inline-flex h-10 cursor-not-allowed items-center rounded-md border border-border px-4 text-sm font-semibold text-muted-foreground" disabled type="button">Preview after approval</button>}
                  </div>
                </aside>
              </div>
            )}
          </section>
        )}

        {activeTab === "media" && (
          <section className="py-7">
            <div className="flex flex-wrap items-end justify-between gap-4">
              <div>
                <h2 className="text-xl font-semibold">Photo gallery</h2>
                <p className="mt-1 text-sm text-muted-foreground">Keep the cover and venue spaces up to date.</p>
              </div>
              <label className="inline-flex h-10 cursor-pointer items-center gap-2 rounded-md bg-foreground px-4 text-sm font-semibold text-white">
                {isUploadingMedia ? <LoaderCircle className="animate-spin" size={17} /> : <UploadCloud size={17} />} Upload photos
                <input accept="image/*" className="sr-only" disabled={isUploadingMedia} multiple onChange={(event) => { void uploadMedia(event.target.files); event.target.value = ""; }} type="file" />
              </label>
            </div>

            {mediaError && <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{mediaError}</p>}

            {media.length > 0 ? (
              <div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {media.map((item, index) => {
                  const isUpdating = updatingMediaId === item.id;

                  return (
                    <article className="overflow-hidden rounded-lg border border-border bg-white" key={item.id}>
                      <div className="relative aspect-[4/3] bg-muted">
                        <Image alt={item.caption ?? `${listing.name} gallery ${index + 1}`} className="object-cover" fill sizes="(max-width: 768px) 100vw, 33vw" src={item.url} unoptimized={item.url.startsWith("blob:")} />
                        {item.isCover && <span className="absolute left-3 top-3 rounded-full bg-white px-2.5 py-1 text-xs font-semibold text-primary">Cover photo</span>}
                      </div>
                      <div className="grid gap-3 p-3">
                        <div className="flex items-center justify-between gap-3">
                          <span className="truncate text-sm text-muted-foreground">{item.caption ?? `Photo ${index + 1}`}</span>
                          <span className="text-xs text-muted-foreground">{index + 1}</span>
                        </div>
                        <div className="flex flex-wrap gap-2">
                          <button className="inline-flex h-9 items-center gap-2 rounded-md border border-border px-3 text-sm font-medium hover:border-primary disabled:opacity-60" disabled={item.isCover || isUpdating} onClick={() => setCoverMedia(item.id)} type="button">
                            {isUpdating ? <LoaderCircle className="animate-spin" size={15} /> : <BadgeCheck size={15} />} Set cover
                          </button>
                          <button className="inline-flex h-9 items-center gap-2 rounded-md border border-rose-200 px-3 text-sm font-medium text-rose-700 hover:bg-rose-50 disabled:opacity-60" disabled={isUpdating || media.length === 1} onClick={() => removeMedia(item.id)} type="button">
                            {isUpdating ? <LoaderCircle className="animate-spin" size={15} /> : <Trash2 size={15} />} Delete
                          </button>
                        </div>
                      </div>
                    </article>
                  );
                })}
              </div>
            ) : (
              <div className="mt-5 rounded-lg border border-dashed border-border bg-white p-8 text-center">
                <ImagePlus className="mx-auto text-muted-foreground" size={30} />
                <h3 className="mt-4 font-semibold">No photos yet</h3>
                <p className="mt-2 text-sm text-muted-foreground">Upload venue photos to complete the listing gallery.</p>
              </div>
            )}
          </section>
        )}

        {activeTab === "reviews" && (
          <section className="py-7">
            <div>
              <h2 className="text-xl font-semibold">Customer reviews</h2>
              <p className="mt-1 text-sm text-muted-foreground">Verified feedback from completed events.</p>
            </div>

            {reviewsError && <p className="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700" role="alert">{reviewsError}</p>}

            {isLoadingReviews ? (
              <div className="mt-5 grid gap-6 lg:grid-cols-[280px_1fr]">
                <div className="h-72 animate-pulse rounded-lg border border-border bg-white" />
                <div className="grid gap-3">
                  {[1, 2, 3].map((item) => <div className="h-36 animate-pulse rounded-lg border border-border bg-white" key={item} />)}
                </div>
              </div>
            ) : reviews.length > 0 ? (
              <div className="mt-5 grid gap-6 lg:grid-cols-[280px_1fr]">
                <div className="h-fit rounded-lg border border-border bg-white p-6 text-center">
                  <p className="text-5xl font-semibold">{averageRating.toFixed(1)}</p>
                  <div className="mt-3 flex justify-center gap-1 text-amber-400">{[1, 2, 3, 4, 5].map((star) => <Star className="fill-current" key={star} size={18} />)}</div>
                  <p className="mt-2 text-sm text-muted-foreground">Based on {totalReviews} verified reviews</p>
                  <div className="mt-6 grid gap-2">
                    {reviewCounts(reviews).map((item) => (
                      <div className="grid grid-cols-[14px_1fr_36px] items-center gap-2 text-xs" key={item.rating}>
                        <span>{item.rating}</span>
                        <div className="h-1.5 overflow-hidden rounded-full bg-muted"><div className="h-full bg-amber-400" style={{ width: `${item.percentage}%` }} /></div>
                        <span className="text-right text-muted-foreground">{item.percentage}%</span>
                      </div>
                    ))}
                  </div>
                </div>
                <div className="grid gap-3">
                  {reviews.map((review) => (
                    <article className="rounded-lg border border-border bg-white p-5" key={review.id}>
                      <div className="flex items-start justify-between gap-4">
                        <div>
                          <div className="flex items-center gap-2"><h3 className="font-semibold">{review.customerName}</h3>{review.verifiedService && <BadgeCheck className="text-emerald-700" size={16} />}</div>
                          <p className="mt-1 text-xs text-muted-foreground">{review.eventType} | {review.eventDate}</p>
                        </div>
                        <div className="flex gap-1 text-amber-400">{Array.from({ length: Math.round(review.rating) }, (_, index) => <Star className="fill-current" key={index} size={14} />)}</div>
                      </div>
                      <p className="mt-4 text-sm leading-6 text-muted-foreground">{review.comment}</p>
                      {review.reply && <p className="mt-4 rounded-md bg-muted px-3 py-2 text-sm text-muted-foreground">Owner reply: {review.reply}</p>}
                    </article>
                  ))}
                </div>
              </div>
            ) : (
              <div className="mt-5 rounded-lg border border-dashed border-border bg-white p-8 text-center">
                <Star className="mx-auto text-muted-foreground" size={30} />
                <h3 className="mt-4 font-semibold">No reviews yet</h3>
                <p className="mt-2 text-sm text-muted-foreground">Verified customer feedback will appear here after completed events.</p>
              </div>
            )}
          </section>
        )}


      </main>

      <BlockDateDialog onAdd={addBlockedDate} initialDate={selectedBlockDate} onClose={() => setBlockDialogOpen(false)} open={blockDialogOpen} />
    </>
  );
}

function Row({
  label,
  value,
}: {
  label: string;
  value: React.ReactNode;
}) {
  return (
    <div className="flex justify-between border-b pb-2">
      <span className="font-medium text-gray-500">
        {label}
      </span>

      <span className="font-semibold text-right">
        {value}
      </span>
    </div>
  );
}

function ReportMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-border bg-white p-5">
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className="mt-2 text-2xl font-semibold">{value}</p>
    </div>
  );
}
