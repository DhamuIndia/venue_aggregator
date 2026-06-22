import type { HallSummary } from "./types";

export const halls: HallSummary[] = [
  {
    id: "emerald-convention-centre",
    name: "Emerald Convention Centre",
    city: "Chennai",
    area: "ECR",
    capacity: 900,
    startingPrice: 125000,
    rating: 4.8,
    reviewCount: 86,
    imageUrl:
      "https://images.unsplash.com/photo-1519167758481-83f550bb49b3?auto=format&fit=crop&w=1200&q=85",
    galleryUrls: [
      "https://images.unsplash.com/photo-1464366400600-7168b8af9bc3?auto=format&fit=crop&w=1200&q=85",
      "https://images.unsplash.com/photo-1507504031003-b417219a0fde?auto=format&fit=crop&w=1200&q=85"
    ],
    venueType: "Marriage Hall",
    amenities: ["Air conditioned", "Parking", "Dining hall", "Generator"],
    isVerified: true,
    availableThisMonth: true,
    description:
      "A spacious celebration venue near the coast with separate dining, generous parking, and flexible event slots."
  },
  {
    id: "the-grand-pavilion",
    name: "The Grand Pavilion",
    city: "Chennai",
    area: "Anna Nagar",
    capacity: 550,
    startingPrice: 95000,
    rating: 4.7,
    reviewCount: 61,
    imageUrl:
      "https://images.unsplash.com/photo-1464366400600-7168b8af9bc3?auto=format&fit=crop&w=1200&q=85",
    galleryUrls: [
      "https://images.unsplash.com/photo-1519167758481-83f550bb49b3?auto=format&fit=crop&w=1200&q=85",
      "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?auto=format&fit=crop&w=1200&q=85"
    ],
    venueType: "Banquet Hall",
    amenities: ["Air conditioned", "Valet parking", "Lift", "Bridal room"],
    isVerified: true,
    availableThisMonth: false,
    description:
      "A polished city venue designed for receptions, engagements, and corporate gatherings."
  },
  {
    id: "marigold-mini-hall",
    name: "Marigold Mini Hall",
    city: "Chennai",
    area: "Velachery",
    capacity: 180,
    startingPrice: 42000,
    rating: 4.6,
    reviewCount: 39,
    imageUrl:
      "https://images.unsplash.com/photo-1507504031003-b417219a0fde?auto=format&fit=crop&w=1200&q=85",
    galleryUrls: [
      "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?auto=format&fit=crop&w=1200&q=85",
      "https://images.unsplash.com/photo-1464366400600-7168b8af9bc3?auto=format&fit=crop&w=1200&q=85"
    ],
    venueType: "Mini Hall",
    amenities: ["Air conditioned", "Dining area", "Power backup"],
    isVerified: true,
    availableThisMonth: true,
    description:
      "A compact, well-connected venue for intimate functions, birthdays, and family celebrations."
  },
  {
    id: "lotus-heritage-hall",
    name: "Lotus Heritage Hall",
    city: "Coimbatore",
    area: "RS Puram",
    capacity: 700,
    startingPrice: 110000,
    rating: 4.9,
    reviewCount: 112,
    imageUrl:
      "https://images.unsplash.com/photo-1519167758481-83f550bb49b3?auto=format&fit=crop&w=1200&q=85",
    galleryUrls: [
      "https://images.unsplash.com/photo-1519167758481-83f550bb49b3?auto=format&fit=crop&w=1200&q=85",
      "https://images.unsplash.com/photo-1507504031003-b417219a0fde?auto=format&fit=crop&w=1200&q=85"
    ],
    venueType: "Marriage Hall",
    amenities: ["Parking", "Guest rooms", "Dining hall", "Generator"],
    isVerified: true,
    availableThisMonth: true,
    description:
      "A traditional hall with modern guest facilities and a large, separate dining space."
  },
  {
    id: "athena-celebration-hall",
    name: "Athena Celebration Hall",
    city: "Madurai",
    area: "KK Nagar",
    capacity: 350,
    startingPrice: 68000,
    rating: 4.5,
    reviewCount: 27,
    imageUrl:
      "https://images.unsplash.com/photo-1507504031003-b417219a0fde?auto=format&fit=crop&w=1200&q=85",
    galleryUrls: [
      "https://images.unsplash.com/photo-1464366400600-7168b8af9bc3?auto=format&fit=crop&w=1200&q=85",
      "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?auto=format&fit=crop&w=1200&q=85"
    ],
    venueType: "Banquet Hall",
    amenities: ["Air conditioned", "Parking", "Stage", "Catering kitchen"],
    isVerified: false,
    availableThisMonth: true,
    description:
      "A flexible hall with a modern stage and practical facilities for medium-sized events."
  },
  {
    id: "olive-courtyard",
    name: "Olive Courtyard",
    city: "Bengaluru",
    area: "Whitefield",
    capacity: 220,
    startingPrice: 78000,
    rating: 4.7,
    reviewCount: 45,
    imageUrl:
      "https://images.unsplash.com/photo-1464366400600-7168b8af9bc3?auto=format&fit=crop&w=1200&q=85",
    galleryUrls: [
      "https://images.unsplash.com/photo-1519167758481-83f550bb49b3?auto=format&fit=crop&w=1200&q=85",
      "https://images.unsplash.com/photo-1507504031003-b417219a0fde?auto=format&fit=crop&w=1200&q=85"
    ],
    venueType: "Mini Hall",
    amenities: ["Courtyard", "Parking", "Indoor dining", "Power backup"],
    isVerified: true,
    availableThisMonth: false,
    description:
      "An indoor-outdoor venue suited to intimate ceremonies and relaxed evening celebrations."
  }
];

export function getHallById(id: string) {
  return halls.find((hall) => hall.id === id);
}
