export type VenueType = "Marriage Hall" | "Banquet Hall" | "Mini Hall" | "Convention Centre" | "Event Venue" | "Exhibition and Trade Fair Venue";

export type PublicHallReview = {
  customerName: string;
  rating: number;
  comment: string;
  verifiedService: boolean;
};

export type PublicPhotoCredit = {
  title: string; creator: string; creatorUrl: string | null; sourceUrl: string;
  licenseCode: "CC_BY_4_0" | "CC0_1_0"; licenseLabel: string; licenseUrl: string;
  changesNotice: string; processingNotice: string; requiredNotices: string | null;
};
export type ApplicationHallPhoto = { photoId: number; url: string; requiresCredit: boolean; credit: PublicPhotoCredit | null };

export type HallSummary = {
  id: string;
  name: string;
  city: string;
  area: string;
  capacity: number | null;
  startingPrice: number | null;
  rating: number | null;
  reviewCount: number;
  imageUrl: string;
  galleryUrls: string[];
  venueType: VenueType;
  amenities: string[];
  isVerified: boolean;
  availableThisMonth: boolean;
  description: string;
  reviews?: PublicHallReview[];
  listingOrigin?: "OWNER" | "APPLICATION";
  enquiryOnly?: boolean;
  enquiryRoutingTarget?: "OWNER" | "VENUEMART";
  publicationVersion?: number;
  sourceRelease?: string;
  sourceAttribution?: { dataset: string; license: string; recordId?: string | null }[];
  applicationPhotos?: ApplicationHallPhoto[];
};
