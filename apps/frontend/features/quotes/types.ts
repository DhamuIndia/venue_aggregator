export type VendorQuoteStatus = "SENT" | "WITHDRAWN" | "EXPIRED";

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
