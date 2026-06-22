import { halls } from "@/features/halls/mock-data";
import type { EnquiryStatus } from "@/features/enquiries/types";

export type ModerationStatus = "PENDING_APPROVAL" | "APPROVED" | "REJECTED";

export type VenueApplication = {
  id: string;
  name: string;
  ownerName: string;
  ownerPhone: string;
  location: string;
  venueType: string;
  capacity: number;
  startingPrice: number;
  submittedAt: string;
  imageUrl: string;
  status: ModerationStatus;
  documents: { ownership: boolean; identity: boolean; address: boolean };
};

export type VendorApplication = {
  id: string;
  businessName: string;
  contactName: string;
  category: string;
  city: string;
  submittedAt: string;
  status: ModerationStatus;
};

export type ReportedReview = {
  id: string;
  hallName: string;
  customerName: string;
  rating: number;
  comment: string;
  reportReason: string;
  verifiedService: boolean;
  status: "REPORTED" | "PUBLISHED" | "HIDDEN";
};

export type AdminEnquiry = {
  id: string;
  hallName: string;
  customerName: string;
  eventDate: string;
  submittedAt: string;
  status: EnquiryStatus;
};

export const initialVenueApplications: VenueApplication[] = [
  {
    id: "HALL-3201",
    name: "Pearl Grand Hall",
    ownerName: "Suresh Babu",
    ownerPhone: "9840012233",
    location: "Velachery, Chennai",
    venueType: "Marriage Hall",
    capacity: 850,
    startingPrice: 145000,
    submittedAt: "2026-06-22T08:45:00Z",
    imageUrl: halls[1].imageUrl,
    status: "PENDING_APPROVAL",
    documents: { ownership: true, identity: true, address: true }
  },
  {
    id: "HALL-3194",
    name: "Aadhira Mini Hall",
    ownerName: "Lakshmi Narayanan",
    ownerPhone: "9884076112",
    location: "Anna Nagar, Chennai",
    venueType: "Mini Hall",
    capacity: 180,
    startingPrice: 48000,
    submittedAt: "2026-06-21T11:10:00Z",
    imageUrl: halls[2].imageUrl,
    status: "PENDING_APPROVAL",
    documents: { ownership: true, identity: true, address: false }
  },
  {
    id: "HALL-3188",
    name: "The Tamarind Courtyard",
    ownerName: "Vikram Anand",
    ownerPhone: "9789014502",
    location: "ECR, Chennai",
    venueType: "Banquet Hall",
    capacity: 420,
    startingPrice: 98000,
    submittedAt: "2026-06-20T15:25:00Z",
    imageUrl: halls[4].imageUrl,
    status: "PENDING_APPROVAL",
    documents: { ownership: true, identity: true, address: true }
  }
];

export const initialVendorApplications: VendorApplication[] = [
  { id: "VEN-809", businessName: "Bloom Story Decor", contactName: "Nivedha R.", category: "Decoration", city: "Chennai", submittedAt: "22 Jun 2026", status: "PENDING_APPROVAL" },
  { id: "VEN-804", businessName: "Saffron Leaf Catering", contactName: "Manoj K.", category: "Catering", city: "Chennai", submittedAt: "21 Jun 2026", status: "PENDING_APPROVAL" },
  { id: "VEN-798", businessName: "Framecraft Weddings", contactName: "Akhil S.", category: "Photography", city: "Coimbatore", submittedAt: "20 Jun 2026", status: "PENDING_APPROVAL" }
];

export const initialReportedReviews: ReportedReview[] = [
  { id: "REV-240", hallName: "Lotus Heritage Hall", customerName: "Ravi M.", rating: 2, comment: "The service was delayed. Call me directly for the full details.", reportReason: "Contains personal contact request", verifiedService: true, status: "REPORTED" },
  { id: "REV-236", hallName: "The Grand Pavilion", customerName: "Anonymous user", rating: 1, comment: "Avoid this place. I never booked it but heard bad things from others.", reportReason: "Possible non-customer review", verifiedService: false, status: "REPORTED" }
];

export const adminEnquiries: AdminEnquiry[] = [
  { id: "ENQ-022690", hallName: "Emerald Convention Centre", customerName: "Priya Raman", eventDate: "18 Jul 2026", submittedAt: "22 Jun 2026, 10:30", status: "CONFIRMED" },
  { id: "ENQ-519842", hallName: "Emerald Convention Centre", customerName: "Deepa Raj", eventDate: "2 Aug 2026", submittedAt: "21 Jun 2026, 15:00", status: "PENDING_OWNER_RESPONSE" },
  { id: "ENQ-519117", hallName: "The Grand Pavilion", customerName: "Karthik S.", eventDate: "9 Aug 2026", submittedAt: "21 Jun 2026, 09:15", status: "DECLINED" },
  { id: "ENQ-518022", hallName: "Marigold Mini Hall", customerName: "Meena V.", eventDate: "14 Jun 2026", submittedAt: "18 Jun 2026, 12:40", status: "COMPLETED" }
];

export const auditEvents = [
  { id: "AUD-921", action: "Hall approved", subject: "Silver Oak Banquets", actor: "Ananya Iyer", timestamp: "Today, 09:42" },
  { id: "AUD-918", action: "Review hidden", subject: "Review REV-228", actor: "Rahul Menon", timestamp: "Yesterday, 17:20" },
  { id: "AUD-914", action: "Vendor rejected", subject: "Party Pixel Photo Booth", actor: "Ananya Iyer", timestamp: "Yesterday, 12:05" }
];
