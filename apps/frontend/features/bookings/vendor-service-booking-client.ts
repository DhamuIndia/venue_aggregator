import { apiRequest } from "@/lib/api-client";
import type { BookingItem, BookingPaymentStatus, BookingStatus } from "./booking-client";

const storageKey = "venuemart-vendor-service-bookings";
const useMockBookings = process.env.NEXT_PUBLIC_VENDOR_LEADS_MODE === "mock";

export type VendorServiceBooking = {
  id: string;
  quoteId: string;
  leadId: string;
  requirementId?: string;
  vendorId: string;
  vendorName: string;
  customerId: string;
  customerName: string;
  customerPhone?: string;
  customerEmail?: string;
  service: string;
  packageName: string;
  eventType: string;
  eventDate: string;
  location: string;
  amount: number;
  status: BookingStatus;
  paymentStatus: BookingPaymentStatus;
  confirmedAt?: string;
  createdAt: string;
  updatedAt: string;
};

export async function getCustomerVendorServiceBookings(
  accessToken?: string | null
): Promise<VendorServiceBooking[]> {
  if (useMockBookings || !accessToken) return readLocalVendorServiceBookings();
  try {
    const response = await apiRequest<unknown>("/customer/vendor-bookings", { token: accessToken });
    const bookings = Array.isArray(response)
      ? response.map(normalizeVendorServiceBooking).filter((booking) => booking.id)
      : [];
    bookings.forEach(cacheVendorServiceBooking);
    return bookings;
  } catch {
    return readLocalVendorServiceBookings();
  }
}

export function cacheVendorServiceBooking(booking: VendorServiceBooking) {
  if (typeof window === "undefined") return;
  const existing = readLocalVendorServiceBookings().filter((item) => item.id !== booking.id);
  window.localStorage.setItem(storageKey, JSON.stringify([booking, ...existing]));
}

export function vendorServiceBookingToBookingItem(booking: VendorServiceBooking): BookingItem {
  return {
    id: booking.id,
    enquiryId: `VLEAD-${booking.leadId.replace(/^V?LEAD-/, "")}`,
    hallId: booking.vendorId,
    hallName: booking.vendorName,
    customerId: booking.customerId,
    customerName: booking.customerName,
    eventDate: booking.eventDate,
    eventType: `${booking.service} | ${booking.packageName}`,
    guestCount: 0,
    slot: "FULL_DAY",
    status: booking.status,
    amount: booking.amount,
    paymentStatus: booking.paymentStatus,
    notes: booking.location,
    confirmedAt: booking.confirmedAt,
    updatedAt: booking.updatedAt
  };
}

export function normalizeVendorServiceBooking(value: unknown): VendorServiceBooking {
  const record = asRecord(value);
  return {
    id: stringValue(record.id) ?? "",
    quoteId: stringValue(record.quoteId) ?? "",
    leadId: stringValue(record.leadId) ?? "",
    requirementId: stringValue(record.requirementId),
    vendorId: stringValue(record.vendorId) ?? "",
    vendorName: stringValue(record.vendorName) ?? "Vendor",
    customerId: stringValue(record.customerId) ?? "",
    customerName: stringValue(record.customerName) ?? "Customer",
    customerPhone: stringValue(record.customerPhone),
    customerEmail: stringValue(record.customerEmail),
    service: stringValue(record.service) ?? "Event service",
    packageName: stringValue(record.packageName) ?? "Custom package",
    eventType: stringValue(record.eventType) ?? "Event",
    eventDate: stringValue(record.eventDate) ?? "",
    location: stringValue(record.location) ?? "",
    amount: numberValue(record.amount) ?? 0,
    status: bookingStatus(record.status),
    paymentStatus: paymentStatus(record.paymentStatus),
    confirmedAt: stringValue(record.confirmedAt),
    createdAt: stringValue(record.createdAt) ?? new Date().toISOString(),
    updatedAt: stringValue(record.updatedAt) ?? new Date().toISOString()
  };
}

function bookingStatus(value: unknown): BookingStatus {
  return value === "CANCELLED" || value === "COMPLETED" || value === "REQUESTED"
    ? value
    : "CONFIRMED";
}

function paymentStatus(value: unknown): BookingPaymentStatus {
  return value === "ADVANCE_PENDING" || value === "ADVANCE_PAID" || value === "REFUNDED"
    ? value
    : "NOT_STARTED";
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? value as Record<string, unknown> : {};
}

function stringValue(value: unknown) {
  if (typeof value === "string" && value.trim()) return value;
  if (typeof value === "number") return String(value);
  return undefined;
}

function numberValue(value: unknown) {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim() && Number.isFinite(Number(value))) return Number(value);
  return undefined;
}

function readLocalVendorServiceBookings(): VendorServiceBooking[] {
  if (typeof window === "undefined") return [];
  try {
    const value = JSON.parse(window.localStorage.getItem(storageKey) ?? "[]") as unknown;
    return Array.isArray(value)
      ? value.map(normalizeVendorServiceBooking).filter((booking) => booking.id)
      : [];
  } catch {
    return [];
  }
}
