import type { EnquiryStatus } from "@/features/enquiries/types";

export type CustomerEnquiry = {
  id: string;
  venue: string;
  eventDate: string;
  submittedAt: string;
  status: EnquiryStatus | "AWAITING_RESPONSE";
};

export const customerEnquiries: CustomerEnquiry[] = [
  {
    id: "ENQ-2048",
    venue: "Emerald Convention Centre",
    eventDate: "18 July 2026",
    submittedAt: "21 June 2026",
    status: "CONFIRMED"
  },
  {
    id: "ENQ-2031",
    venue: "The Grand Pavilion",
    eventDate: "2 August 2026",
    submittedAt: "18 June 2026",
    status: "AWAITING_RESPONSE"
  },
  {
    id: "ENQ-1884",
    venue: "Marigold Mini Hall",
    eventDate: "10 May 2026",
    submittedAt: "12 April 2026",
    status: "COMPLETED"
  }
];

export const reviewEligibleBooking = {
  id: "BOOK-1884",
  venue: "Marigold Mini Hall",
  eventDate: "10 May 2026",
  serviceType: "Birthday celebration",
  status: "COMPLETED" as const,
  verified: true
};
