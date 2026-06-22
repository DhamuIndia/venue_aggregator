import type { VendorLead } from "./types";
import { vendors } from "./mock-data";

export const workspaceVendor = vendors[0];

export const fallbackVendorLeads: VendorLead[] = [
  {
    id: "LEAD-804231",
    vendorId: workspaceVendor.id,
    vendorName: workspaceVendor.businessName,
    customerId: "customer-411",
    customerName: "Deepa Raj",
    eventDate: "2026-08-02",
    eventType: "Reception",
    location: "Velachery, Chennai",
    service: "Wedding catering",
    budget: 420000,
    notes: "Dinner for around 550 guests with two live counters.",
    status: "NEW",
    submittedAt: "2026-06-22T08:30:00Z"
  },
  {
    id: "LEAD-798114",
    vendorId: workspaceVendor.id,
    vendorName: workspaceVendor.businessName,
    customerId: "customer-412",
    customerName: "Aravind M.",
    eventDate: "2026-07-26",
    eventType: "Engagement",
    location: "Mylapore, Chennai",
    service: "Traditional meals",
    budget: 150000,
    status: "QUOTE_SENT",
    submittedAt: "2026-06-20T12:10:00Z"
  },
  {
    id: "LEAD-781005",
    vendorId: workspaceVendor.id,
    vendorName: workspaceVendor.businessName,
    customerId: "customer-413",
    customerName: "Nandhini P.",
    eventDate: "2026-07-12",
    eventType: "Wedding",
    location: "ECR, Chennai",
    service: "Wedding catering",
    budget: 520000,
    status: "BOOKED",
    submittedAt: "2026-06-14T07:45:00Z"
  }
];
