import type { VendorCategory, VendorSummary } from "./types";

const image = (id: string, width = 1200) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${width}&q=82`;

export const categoryLabels: Record<VendorCategory, string> = {
  CATERING: "Catering",
  DECORATION: "Decoration",
  PHOTOGRAPHY: "Photography",
  BRIDAL_MAKEUP: "Bridal makeup",
  MUSIC_AND_DJ: "Music and DJ",
  EVENT_PLANNING: "Event planning"
};

export const vendors: VendorSummary[] = [
  {
    id: "saffron-leaf-catering",
    businessName: "Saffron Leaf Catering",
    ownerName: "Manoj Krishnan",
    category: "CATERING",
    city: "Chennai",
    area: "Adyar",
    rating: 4.9,
    reviewCount: 84,
    startingPrice: 650,
    imageUrl: image("photo-1555244162-803834f70033"),
    galleryUrls: [image("photo-1547592180-85f173990554", 900), image("photo-1414235077428-338989a2e8c0", 900)],
    verified: true,
    responseTime: "Within 2 hours",
    completedEvents: 212,
    services: ["Wedding catering", "Live counters", "Traditional meals", "Dessert station", "Service staff"],
    description: "South Indian celebration menus with transparent per-plate pricing, tasting sessions, and a trained service team for events across Chennai.",
    packages: [
      { id: "PKG-C1", name: "Classic celebration", description: "A balanced vegetarian menu for intimate events.", price: 650, includes: ["Welcome drink", "18-item meal", "Dessert", "Service staff"] },
      { id: "PKG-C2", name: "Grand wedding feast", description: "Expanded wedding menu with live counters.", price: 1050, includes: ["Two welcome drinks", "28-item meal", "Two live counters", "Three desserts"] }
    ],
    reviews: [
      { id: "VREV-81", customerName: "Harini S.", rating: 5, eventType: "Wedding", comment: "The tasting matched the final menu exactly and service stayed smooth for all 600 guests.", eventDate: "12 May 2026", verifiedService: true },
      { id: "VREV-76", customerName: "Rahul K.", rating: 5, eventType: "Reception", comment: "Excellent live counters and a very responsive coordination team.", eventDate: "20 April 2026", verifiedService: true }
    ]
  },
  {
    id: "bloom-story-decor",
    businessName: "Bloom Story Decor",
    ownerName: "Nivedha Raman",
    category: "DECORATION",
    city: "Chennai",
    area: "Anna Nagar",
    rating: 4.8,
    reviewCount: 61,
    startingPrice: 45000,
    imageUrl: image("photo-1519167758481-83f550bb49b3"),
    galleryUrls: [image("photo-1464366400600-7168b8af9bc3", 900), image("photo-1507504031003-b417219a0fde", 900)],
    verified: true,
    responseTime: "Within 3 hours",
    completedEvents: 146,
    services: ["Stage decor", "Floral entrance", "Mandap design", "Table styling", "Lighting"],
    description: "Thoughtful floral and stage styling designed around the venue, event palette, and practical guest movement.",
    packages: [{ id: "PKG-D1", name: "Signature stage", description: "Main stage and entrance styling.", price: 45000, includes: ["Backdrop", "Fresh florals", "Entrance arch", "Warm lighting"] }],
    reviews: [{ id: "VREV-66", customerName: "Meera P.", rating: 5, eventType: "Engagement", comment: "The team translated our references beautifully without making the stage feel crowded.", eventDate: "2 June 2026", verifiedService: true }]
  },
  {
    id: "framecraft-weddings",
    businessName: "Framecraft Weddings",
    ownerName: "Akhil Srinivasan",
    category: "PHOTOGRAPHY",
    city: "Coimbatore",
    area: "RS Puram",
    rating: 4.9,
    reviewCount: 109,
    startingPrice: 85000,
    imageUrl: image("photo-1486916856992-e4db22c8df33"),
    galleryUrls: [image("photo-1519741497674-611481863552", 900), image("photo-1522673607200-164d1b6ce486", 900)],
    verified: true,
    responseTime: "Within 1 hour",
    completedEvents: 238,
    services: ["Candid photography", "Traditional photography", "Wedding films", "Albums", "Drone coverage"],
    description: "Candid wedding stories, cinematic films, and reliable delivery timelines from a compact in-house team.",
    packages: [{ id: "PKG-P1", name: "Wedding story", description: "Full-day photo and film coverage.", price: 85000, includes: ["Two photographers", "Cinematographer", "Highlight film", "Online gallery"] }],
    reviews: [{ id: "VREV-92", customerName: "Dinesh V.", rating: 5, eventType: "Wedding", comment: "Natural photographs, calm team, and the promised delivery date was honored.", eventDate: "18 March 2026", verifiedService: true }]
  },
  {
    id: "radiant-bridal-studio",
    businessName: "Radiant Bridal Studio",
    ownerName: "Aparna Devi",
    category: "BRIDAL_MAKEUP",
    city: "Chennai",
    area: "T Nagar",
    rating: 4.7,
    reviewCount: 73,
    startingPrice: 18000,
    imageUrl: image("photo-1522335789203-aabd1fc54bc9"),
    galleryUrls: [image("photo-1487412947147-5cebf100ffc2", 900), image("photo-1516975080664-ed2fc6a32937", 900)],
    verified: true,
    responseTime: "Within 4 hours",
    completedEvents: 174,
    services: ["Bridal makeup", "Hair styling", "Saree draping", "Family makeup", "Trial session"],
    description: "Camera-ready bridal makeup with pre-event trials, skin preparation guidance, and on-time venue service.",
    packages: [{ id: "PKG-M1", name: "Bridal complete", description: "Complete bridal styling for one event.", price: 18000, includes: ["Makeup", "Hair styling", "Draping", "Lashes"] }],
    reviews: [{ id: "VREV-57", customerName: "Swetha N.", rating: 5, eventType: "Wedding", comment: "Aparna listened carefully and the makeup remained comfortable throughout the ceremony.", eventDate: "8 May 2026", verifiedService: true }]
  },
  {
    id: "pulse-and-beat-events",
    businessName: "Pulse and Beat Events",
    ownerName: "Jeeva Prakash",
    category: "MUSIC_AND_DJ",
    city: "Chennai",
    area: "Nungambakkam",
    rating: 4.6,
    reviewCount: 48,
    startingPrice: 32000,
    imageUrl: image("photo-1493225457124-a3eb161ffa5f"),
    galleryUrls: [image("photo-1429962714451-bb934ecdc4ec", 900), image("photo-1501386761578-eac5c94b800a", 900)],
    verified: false,
    responseTime: "Within 5 hours",
    completedEvents: 96,
    services: ["DJ", "Sound system", "Dance floor lighting", "Emcee", "Live band"],
    description: "Reception music, professional sound, and event pacing for celebrations from 100 to 1,000 guests.",
    packages: [{ id: "PKG-DJ1", name: "Reception energy", description: "DJ and sound for a five-hour reception.", price: 32000, includes: ["DJ", "PA system", "Console", "Basic lighting"] }],
    reviews: [{ id: "VREV-43", customerName: "Nitin A.", rating: 4, eventType: "Reception", comment: "Good crowd reading and clean sound throughout the evening.", eventDate: "30 April 2026", verifiedService: true }]
  },
  {
    id: "the-celebration-office",
    businessName: "The Celebration Office",
    ownerName: "Farah Ali",
    category: "EVENT_PLANNING",
    city: "Bengaluru",
    area: "Indiranagar",
    rating: 4.8,
    reviewCount: 55,
    startingPrice: 75000,
    imageUrl: image("photo-1519741497674-611481863552"),
    galleryUrls: [image("photo-1507504031003-b417219a0fde", 900), image("photo-1464366400600-7168b8af9bc3", 900)],
    verified: true,
    responseTime: "Within 2 hours",
    completedEvents: 121,
    services: ["Wedding planning", "Vendor coordination", "Guest logistics", "Budget tracking", "Event production"],
    description: "Structured planning and calm event-day coordination for couples who want one accountable point of contact.",
    packages: [{ id: "PKG-E1", name: "Event-day management", description: "Planning handover and complete event-day coordination.", price: 75000, includes: ["Timeline", "Vendor calls", "Guest desk", "Two coordinators"] }],
    reviews: [{ id: "VREV-38", customerName: "Anusha R.", rating: 5, eventType: "Wedding", comment: "Every vendor knew where to be and our families could simply enjoy the day.", eventDate: "22 February 2026", verifiedService: true }]
  }
];

export function getVendorById(id: string) {
  return vendors.find((vendor) => vendor.id === id);
}
