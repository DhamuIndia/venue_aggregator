export type VenueType = "Marriage Hall" | "Banquet Hall" | "Mini Hall";

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
};
