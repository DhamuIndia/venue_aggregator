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
  packages: VendorPackage[];
  reviews: VendorReview[];
};

export type VendorLeadStatus = "NEW" | "CONTACTED" | "QUOTE_SENT" | "BOOKED" | "DECLINED";

export type VendorLead = {
  id: string;
  vendorId: string;
  vendorName: string;
  customerId: string;
  customerName: string;
  eventDate: string;
  eventType: string;
  location: string;
  service: string;
  budget: number;
  notes?: string;
  status: VendorLeadStatus;
  submittedAt: string;
};
