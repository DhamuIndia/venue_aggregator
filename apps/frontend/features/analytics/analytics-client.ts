import { apiRequest } from "@/lib/api-client";

const useMockAnalytics = process.env.NEXT_PUBLIC_ANALYTICS_MODE === "mock";

export type TrendPoint = {
  label: string;
  enquiries: number;
  bookings: number;
  revenue: number;
};

export type AdminAnalytics = {
  totalUsers: number;
  activeListings: number;
  monthlyEnquiries: number;
  confirmedBookings: number;
  bookingRevenue: number;
  vendorRevenue: number;
  conversionRate: number;
  trends: TrendPoint[];
  topCities: Array<{ city: string; enquiries: number; bookings: number }>;
};

export type OwnerAnalytics = {
  enquiries: number;
  confirmedBookings: number;
  completedBookings: number;
  estimatedRevenue: number;
  conversionRate: number;
  averageRating: number;
  occupancyRate: number;
  trends: TrendPoint[];
  eventMix: Array<{ eventType: string; count: number }>;
};

export type VendorAnalytics = {
  leads: number;
  contacted: number;
  quotesSent: number;
  booked: number;
  bookedValue: number;
  conversionRate: number;
  averageBudget: number;
  responseRate: number;
  trends: TrendPoint[];
  serviceMix: Array<{ service: string; count: number }>;
};

export const fallbackAdminAnalytics: AdminAnalytics = {
  totalUsers: 1248,
  activeListings: 186,
  monthlyEnquiries: 284,
  confirmedBookings: 74,
  bookingRevenue: 8420000,
  vendorRevenue: 284000,
  conversionRate: 26,
  trends: [
    { label: "Jan", enquiries: 132, bookings: 32, revenue: 3180000 },
    { label: "Feb", enquiries: 148, bookings: 36, revenue: 3620000 },
    { label: "Mar", enquiries: 176, bookings: 44, revenue: 4280000 },
    { label: "Apr", enquiries: 201, bookings: 52, revenue: 5110000 },
    { label: "May", enquiries: 238, bookings: 61, revenue: 6920000 },
    { label: "Jun", enquiries: 284, bookings: 74, revenue: 8420000 }
  ],
  topCities: [
    { city: "Chennai", enquiries: 148, bookings: 42 },
    { city: "Coimbatore", enquiries: 52, bookings: 14 },
    { city: "Madurai", enquiries: 38, bookings: 9 }
  ]
};

export const fallbackOwnerAnalytics: OwnerAnalytics = {
  enquiries: 42,
  confirmedBookings: 11,
  completedBookings: 5,
  estimatedRevenue: 1385000,
  conversionRate: 26,
  averageRating: 4.8,
  occupancyRate: 62,
  trends: [
    { label: "Jan", enquiries: 5, bookings: 1, revenue: 125000 },
    { label: "Feb", enquiries: 6, bookings: 2, revenue: 240000 },
    { label: "Mar", enquiries: 7, bookings: 2, revenue: 250000 },
    { label: "Apr", enquiries: 8, bookings: 2, revenue: 260000 },
    { label: "May", enquiries: 7, bookings: 2, revenue: 250000 },
    { label: "Jun", enquiries: 9, bookings: 2, revenue: 260000 }
  ],
  eventMix: [
    { eventType: "Wedding", count: 6 },
    { eventType: "Reception", count: 3 },
    { eventType: "Engagement", count: 2 }
  ]
};

export const fallbackVendorAnalytics: VendorAnalytics = {
  leads: 34,
  contacted: 24,
  quotesSent: 18,
  booked: 7,
  bookedValue: 2140000,
  conversionRate: 21,
  averageBudget: 305714,
  responseRate: 71,
  trends: [
    { label: "Jan", enquiries: 3, bookings: 1, revenue: 280000 },
    { label: "Feb", enquiries: 4, bookings: 1, revenue: 320000 },
    { label: "Mar", enquiries: 5, bookings: 1, revenue: 260000 },
    { label: "Apr", enquiries: 6, bookings: 1, revenue: 360000 },
    { label: "May", enquiries: 7, bookings: 1, revenue: 420000 },
    { label: "Jun", enquiries: 9, bookings: 2, revenue: 500000 }
  ],
  serviceMix: [
    { service: "Wedding catering", count: 13 },
    { service: "Reception", count: 9 },
    { service: "Corporate event", count: 5 }
  ]
};

export async function getAdminAnalytics(accessToken?: string | null): Promise<AdminAnalytics> {
  if (useMockAnalytics || !accessToken) return fallbackAdminAnalytics;

  try {
    const response = await apiRequest<unknown>("/admin/reports/summary", { token: accessToken });
    return toAdminAnalytics(response) ?? fallbackAdminAnalytics;
  } catch {
    return fallbackAdminAnalytics;
  }
}

export async function getOwnerAnalytics(hallId: string, accessToken?: string | null): Promise<OwnerAnalytics> {
  if (useMockAnalytics || !accessToken) return fallbackOwnerAnalytics;

  try {
    const response = await apiRequest<unknown>(`/owner/halls/${encodeURIComponent(hallId)}/reports/summary`, { token: accessToken });
    return toOwnerAnalytics(response) ?? fallbackOwnerAnalytics;
  } catch {
    return fallbackOwnerAnalytics;
  }
}

export async function getVendorAnalytics(vendorId: string, accessToken?: string | null): Promise<VendorAnalytics> {
  if (useMockAnalytics || !accessToken) return fallbackVendorAnalytics;

  try {
    const response = await apiRequest<unknown>(`/vendor/reports/summary?vendorId=${encodeURIComponent(vendorId)}`, { token: accessToken });
    return toVendorAnalytics(response) ?? fallbackVendorAnalytics;
  } catch {
    return fallbackVendorAnalytics;
  }
}

function toAdminAnalytics(value: unknown): AdminAnalytics | undefined {
  const record = unwrapRecord(value);
  if (!record) return undefined;

  return {
    totalUsers: numberValue(record, ["totalUsers", "total_users"]) ?? fallbackAdminAnalytics.totalUsers,
    activeListings: numberValue(record, ["activeListings", "active_listings"]) ?? fallbackAdminAnalytics.activeListings,
    monthlyEnquiries: numberValue(record, ["monthlyEnquiries", "monthly_enquiries", "enquiries"]) ?? fallbackAdminAnalytics.monthlyEnquiries,
    confirmedBookings: numberValue(record, ["confirmedBookings", "confirmed_bookings", "bookings"]) ?? fallbackAdminAnalytics.confirmedBookings,
    bookingRevenue: numberValue(record, ["bookingRevenue", "booking_revenue"]) ?? fallbackAdminAnalytics.bookingRevenue,
    vendorRevenue: numberValue(record, ["vendorRevenue", "vendor_revenue", "subscriptionRevenue"]) ?? fallbackAdminAnalytics.vendorRevenue,
    conversionRate: numberValue(record, ["conversionRate", "conversion_rate"]) ?? fallbackAdminAnalytics.conversionRate,
    trends: trendList(record.trends) ?? fallbackAdminAnalytics.trends,
    topCities: cityList(record.topCities) ?? fallbackAdminAnalytics.topCities
  };
}

function toOwnerAnalytics(value: unknown): OwnerAnalytics | undefined {
  const record = unwrapRecord(value);
  if (!record) return undefined;

  return {
    enquiries: numberValue(record, ["enquiries", "totalEnquiries", "total_enquiries"]) ?? fallbackOwnerAnalytics.enquiries,
    confirmedBookings: numberValue(record, ["confirmedBookings", "confirmed_bookings"]) ?? fallbackOwnerAnalytics.confirmedBookings,
    completedBookings: numberValue(record, ["completedBookings", "completed_bookings"]) ?? fallbackOwnerAnalytics.completedBookings,
    estimatedRevenue: numberValue(record, ["estimatedRevenue", "estimated_revenue", "revenue"]) ?? fallbackOwnerAnalytics.estimatedRevenue,
    conversionRate: numberValue(record, ["conversionRate", "conversion_rate"]) ?? fallbackOwnerAnalytics.conversionRate,
    averageRating: numberValue(record, ["averageRating", "average_rating", "rating"]) ?? fallbackOwnerAnalytics.averageRating,
    occupancyRate: numberValue(record, ["occupancyRate", "occupancy_rate"]) ?? fallbackOwnerAnalytics.occupancyRate,
    trends: trendList(record.trends) ?? fallbackOwnerAnalytics.trends,
    eventMix: eventMix(record.eventMix) ?? fallbackOwnerAnalytics.eventMix
  };
}

function toVendorAnalytics(value: unknown): VendorAnalytics | undefined {
  const record = unwrapRecord(value);
  if (!record) return undefined;

  return {
    leads: numberValue(record, ["leads", "totalLeads", "total_leads"]) ?? fallbackVendorAnalytics.leads,
    contacted: numberValue(record, ["contacted"]) ?? fallbackVendorAnalytics.contacted,
    quotesSent: numberValue(record, ["quotesSent", "quotes_sent"]) ?? fallbackVendorAnalytics.quotesSent,
    booked: numberValue(record, ["booked", "bookedLeads", "booked_leads"]) ?? fallbackVendorAnalytics.booked,
    bookedValue: numberValue(record, ["bookedValue", "booked_value", "revenue"]) ?? fallbackVendorAnalytics.bookedValue,
    conversionRate: numberValue(record, ["conversionRate", "conversion_rate"]) ?? fallbackVendorAnalytics.conversionRate,
    averageBudget: numberValue(record, ["averageBudget", "average_budget"]) ?? fallbackVendorAnalytics.averageBudget,
    responseRate: numberValue(record, ["responseRate", "response_rate"]) ?? fallbackVendorAnalytics.responseRate,
    trends: trendList(record.trends) ?? fallbackVendorAnalytics.trends,
    serviceMix: serviceMix(record.serviceMix) ?? fallbackVendorAnalytics.serviceMix
  };
}

function trendList(value: unknown): TrendPoint[] | undefined {
  if (!Array.isArray(value)) return undefined;
  const trends = value.map((item) => {
    if (!isRecord(item)) return undefined;
    const label = stringValue(item, ["label", "month", "period"]);
    if (!label) return undefined;
    return {
      label,
      enquiries: numberValue(item, ["enquiries", "leads"]) ?? 0,
      bookings: numberValue(item, ["bookings", "booked"]) ?? 0,
      revenue: numberValue(item, ["revenue", "value"]) ?? 0
    };
  }).filter(Boolean) as TrendPoint[];
  return trends.length ? trends : undefined;
}

function cityList(value: unknown) {
  if (!Array.isArray(value)) return undefined;
  const cities = value.map((item) => {
    if (!isRecord(item)) return undefined;
    const city = stringValue(item, ["city", "name"]);
    if (!city) return undefined;
    return {
      city,
      enquiries: numberValue(item, ["enquiries", "leads"]) ?? 0,
      bookings: numberValue(item, ["bookings"]) ?? 0
    };
  }).filter(Boolean) as AdminAnalytics["topCities"];
  return cities.length ? cities : undefined;
}

function eventMix(value: unknown) {
  return labelCountList(value, ["eventType", "event_type", "label", "name"], "eventType") as OwnerAnalytics["eventMix"] | undefined;
}

function serviceMix(value: unknown) {
  return labelCountList(value, ["service", "serviceType", "label", "name"], "service") as VendorAnalytics["serviceMix"] | undefined;
}

function labelCountList(value: unknown, labelKeys: string[], outputKey: "eventType" | "service") {
  if (!Array.isArray(value)) return undefined;
  const items = value.map((item) => {
    if (!isRecord(item)) return undefined;
    const label = stringValue(item, labelKeys);
    if (!label) return undefined;
    return { [outputKey]: label, count: numberValue(item, ["count", "total"]) ?? 0 };
  }).filter(Boolean);
  return items.length ? items : undefined;
}

function unwrapRecord(value: unknown): Record<string, unknown> | undefined {
  if (!isRecord(value)) return undefined;
  if (isRecord(value.data)) return unwrapRecord(value.data);
  if (isRecord(value.summary)) return value.summary;
  if (isRecord(value.analytics)) return value.analytics;
  return value;
}

function stringValue(record: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string" && value.trim()) return value;
    if (typeof value === "number") return String(value);
  }
  return undefined;
}

function numberValue(record: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "number") return value;
    if (typeof value === "string" && value.trim() && !Number.isNaN(Number(value))) return Number(value);
  }
  return undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}
