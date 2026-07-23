import type { VendorServiceBooking } from "@/features/bookings/vendor-service-booking-client";

export type VendorQuoteStatus = "SENT" | "ACCEPTED" | "NOT_SELECTED" | "WITHDRAWN" | "EXPIRED";

export type VendorQuote = {
  id: string;
  leadId: string;
  requirementId?: string;
  vendorId: string;
  vendorName: string;
  service: string;
  amount: number;
  packageName: string;
  serviceDescription: string;
  inclusions: string[];
  additionalCharges: number;
  additionalChargesDescription?: string;
  totalAmount: number;
  notes?: string;
  validUntil: string;
  status: VendorQuoteStatus;
  shortlisted: boolean;
  shortlistedAt?: string;
  createdAt: string;
  updatedAt: string;
};

export type UpsertVendorQuoteInput = {
  amount: number;
  packageName: string;
  serviceDescription: string;
  inclusions: string[];
  additionalCharges?: number;
  additionalChargesDescription?: string;
  notes?: string;
  validUntil: string;
};

export type QuoteAcceptanceResult = {
  acceptedQuote: VendorQuote;
  requirementQuotes: VendorQuote[];
  booking: VendorServiceBooking;
};
