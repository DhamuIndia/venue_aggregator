import type { StoredEnquiry } from "@/features/enquiries/types";
import { halls } from "@/features/halls/mock-data";
import type { BlockedDate } from "./availability-client";

export const ownerHall = halls[0];

export const fallbackOwnerEnquiries: StoredEnquiry[] = [
  {
    id: "ENQ-519842",
    hallId: ownerHall.id,
    hallName: ownerHall.name,
    customerId: "customer-301",
    eventDate: "2026-08-02",
    eventType: "Reception",
    guestCount: 620,
    slot: "EVENING",
    notes: "Need dinner service and stage decoration details.",
    status: "PENDING_OWNER_RESPONSE",
    submittedAt: "2026-06-21T09:30:00.000Z"
  },
  {
    id: "ENQ-513207",
    hallId: ownerHall.id,
    hallName: ownerHall.name,
    customerId: "customer-302",
    eventDate: "2026-07-18",
    eventType: "Wedding",
    guestCount: 450,
    slot: "FULL_DAY",
    status: "CONFIRMED",
    submittedAt: "2026-06-18T06:15:00.000Z"
  }
];

export const ownerReviews = [
  { id: "REV-41", name: "Priya S.", rating: 5, event: "Wedding", comment: "Clean venue, excellent dining setup, and quick communication.", date: "12 June 2026" },
  { id: "REV-38", name: "Karthik R.", rating: 4, event: "Reception", comment: "The hall was spacious and the parking team handled arrivals well.", date: "28 May 2026" },
  { id: "REV-31", name: "Meena V.", rating: 5, event: "Engagement", comment: "Everything was ready on time and the staff were helpful throughout.", date: "10 May 2026" }
];

export const initialBlockedDates: BlockedDate[] = [
  { id: "BLOCK-1", date: "2026-07-15", slot: "FULL_DAY", reason: "Maintenance" },
  { id: "BLOCK-2", date: "2026-07-22", slot: "EVENING", reason: "Private event" }
];
