export type VenueType = "Marriage Hall" | "Banquet Hall" | "Mini Hall" | "Convention Centre";

export type PublicHallReview = {
  customerName: string;
  rating: number;
  comment: string;
  verifiedService: boolean;
};

export type HallSummary = {
  id: string;
  name: string;
  city: string;
  area: string;
  capacity: number;
  startingPrice: number;
  rating: number;
  reviewCount: number;
  imageUrl: string;
  galleryUrls: string[];
  venueType: VenueType;
  amenities: string[];
  isVerified: boolean;
  availableThisMonth: boolean;
  description: string;
  reviews?: PublicHallReview[];
};
