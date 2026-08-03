export type VendorCategory =
  | "CATERING"
  | "DECORATION"
  | "PHOTOGRAPHY"
  | "BRIDAL_MAKEUP"
  | "MUSIC_AND_DJ"
  | "EVENT_PLANNING";

export type VendorPackage = {
  id: string;
  name: string;
  description: string;
  price: number;
  includes: string[];
};

export type VendorReview = {
  id: string;
  customerName: string;
  rating: number;
  eventType: string;
  comment: string;
  eventDate: string;
  verifiedService: boolean;
};

export type VendorSummary = {
  id: string;
  businessName: string;
  ownerName: string;
  category: VendorCategory;
  city: string;
  area: string;
  rating: number;
  reviewCount: number;
  startingPrice: number;
  imageUrl: string;
  galleryUrls: string[];
  verified: boolean;
  responseTime: string;
  completedEvents: number;
  services: string[];
  description: string;
  instagramUrl?: string;
  facebookUrl?: string;
  whatsAppUrl?: string;
  packages: VendorPackage[];
  reviews: VendorReview[];
};

export type VendorLeadStatus = "NEW" | "INTERESTED" | "CONTACTED" | "QUOTE_SENT" | "BOOKED" | "NOT_SELECTED" | "DECLINED" | "COMPLETED";

export type VendorLead = {
  id: string;
  leadReference?: string;
  vendorId: string;
  vendorName: string;
  customerId: string;
  customerName: string;
  customerPhone?: string;
  customerEmail?: string;
  requirementId?: string;
  source: "DIRECT_ENQUIRY" | "MARKETPLACE_REQUIREMENT";
  contactDetailsShared: boolean;
  preferredContactChannel?: "IN_APP" | "PHONE" | "WHATSAPP" | "EMAIL";
  eventDate: string;
  eventType: string;
  location: string;
  service: string;
  budget?: number;
  notes?: string;
  declineReason?: string;
  status: VendorLeadStatus;
  submittedAt: string;
};
