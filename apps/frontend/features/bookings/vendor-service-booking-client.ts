import { ApiError, apiRequest } from "@/lib/api-client";
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
  advanceAmount: number;
  balanceAmount: number;
  advanceDueDate: string;
  startedAt?: string;
  completedAt?: string;
  cancelledAt?: string;
  cancelledBy?: string;
  cancellationReason?: string;
  refundableAmount: number;
  payments: VendorBookingPayment[];
  timeline: VendorBookingTimeline[];
  reviewEligible: boolean;
  confirmedAt?: string;
  createdAt: string;
  updatedAt: string;
};

export type VendorBookingPayment = {
  id: string;
  paymentType: string;
  amount: number;
  currency: string;
  status: "CREATED" | "PAID" | "FAILED";
  orderId: string;
  paymentId?: string;
  receiptNumber?: string;
  paidAt?: string;
  refundStatus: "NOT_REQUESTED" | "PENDING" | "PARTIALLY_REFUNDED" | "REFUNDED" | "NOT_ELIGIBLE";
  refundAmount: number;
  refundedAt?: string;
  createdAt: string;
};

export type VendorBookingTimeline = {
  id: string;
  eventType: string;
  fromStatus?: string;
  toStatus?: string;
  actorRole: string;
  message: string;
  createdAt: string;
};

export type VendorBookingPaymentOrder = {
  orderId: string;
  bookingId: string;
  amount: number;
  currency: string;
  status: string;
  keyId?: string;
};

const fallbackVendorServiceBooking: VendorServiceBooking = {
  id: "VBOOK-000901",
  quoteId: "901",
  leadId: "701",
  requirementId: "801",
  vendorId: "501",
  vendorName: "Wedding Stories Photography",
  customerId: "101",
  customerName: "Priya Raman",
  customerPhone: "9876543210",
  customerEmail: "priya@example.com",
  service: "Wedding photography",
  packageName: "Wedding Stories Premium",
  eventType: "Wedding",
  eventDate: "2026-08-18",
  location: "Chennai",
  amount: 100000,
  status: "CONFIRMED",
  paymentStatus: "ADVANCE_PENDING",
  advanceAmount: 20000,
  balanceAmount: 80000,
  advanceDueDate: "2026-07-25",
  refundableAmount: 0,
  payments: [],
  timeline: [{
    id: "VBT-000001",
    eventType: "BOOKING_CREATED",
    fromStatus: undefined,
    toStatus: "CONFIRMED",
    actorRole: "CUSTOMER",
    message: "Quotation accepted and booking confirmed",
    createdAt: "2026-07-23T09:30:00Z"
  }],
  reviewEligible: false,
  confirmedAt: "2026-07-23T09:30:00Z",
  createdAt: "2026-07-23T09:30:00Z",
  updatedAt: "2026-07-23T09:30:00Z"
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

export async function getVendorServiceBookings(accessToken?: string | null): Promise<VendorServiceBooking[]> {
  if (useMockBookings || !accessToken) return readLocalVendorServiceBookings();
  const response = await apiRequest<unknown>("/vendor/bookings", { token: accessToken });
  return Array.isArray(response)
    ? response.map(normalizeVendorServiceBooking).filter((booking) => booking.id)
    : [];
}

export async function updateVendorServiceBookingStatus(
  bookingId: string,
  status: "IN_PROGRESS" | "COMPLETED" | "CANCELLED",
  accessToken?: string | null,
  reason?: string
) {
  if (useMockBookings || !accessToken) return updateLocalVendorBooking(bookingId, {
    status,
    cancellationReason: status === "CANCELLED" ? reason : undefined
  });
  const response = await apiRequest<unknown>(`/vendor/bookings/${encodeURIComponent(bookingId)}/status`, {
    method: "PATCH",
    token: accessToken,
    body: JSON.stringify({ status, reason })
  });
  const booking = normalizeVendorServiceBooking(response);
  cacheVendorServiceBooking(booking);
  return booking;
}

export async function cancelCustomerVendorServiceBooking(
  bookingId: string,
  reason: string,
  accessToken?: string | null
) {
  if (useMockBookings || !accessToken) return updateLocalVendorBooking(bookingId, {
    status: "CANCELLED",
    cancellationReason: reason
  });
  const response = await apiRequest<unknown>(`/customer/vendor-bookings/${encodeURIComponent(bookingId)}/cancel`, {
    method: "POST",
    token: accessToken,
    body: JSON.stringify({ reason })
  });
  const booking = normalizeVendorServiceBooking(response);
  cacheVendorServiceBooking(booking);
  return booking;
}

export async function createVendorBookingAdvanceOrder(
  booking: VendorServiceBooking,
  accessToken?: string | null
): Promise<VendorBookingPaymentOrder> {
  if (useMockBookings || !accessToken) {
    return {
      orderId: `VORDER-${Date.now()}`,
      bookingId: booking.id,
      amount: booking.advanceAmount,
      currency: "INR",
      status: "CREATED"
    };
  }
  return apiRequest<VendorBookingPaymentOrder>(
    `/customer/vendor-bookings/${encodeURIComponent(booking.id)}/payments/advance-order`,
    { method: "POST", token: accessToken }
  );
}

export async function verifyVendorBookingAdvance(
  bookingId: string,
  payload: { orderId: string; razorpayPaymentId: string; razorpaySignature: string },
  accessToken?: string | null
) {
  if (useMockBookings || !accessToken) {
    const booking = readLocalVendorServiceBookings().find((item) => item.id === bookingId);
    if (!booking) throw new Error("Vendor booking not found.");
    const paidAt = new Date().toISOString();
    return updateLocalVendorBooking(bookingId, {
      paymentStatus: "ADVANCE_PAID",
      payments: [{
        id: "VPAY-DEMO",
        paymentType: "ADVANCE",
        amount: booking.advanceAmount,
        currency: "INR",
        status: "PAID",
        orderId: payload.orderId,
        paymentId: payload.razorpayPaymentId,
        receiptNumber: `VM-DEMO-${booking.id}`,
        paidAt,
        refundStatus: "NOT_REQUESTED",
        refundAmount: 0,
        createdAt: paidAt
      }, ...booking.payments.filter((item) => item.paymentType !== "ADVANCE")],
      timeline: [{
        id: `VBT-${Date.now()}`,
        eventType: "PAYMENT_RECEIVED",
        actorRole: "CUSTOMER",
        message: "Advance payment received",
        createdAt: paidAt
      }, ...booking.timeline]
    });
  }
  const response = await apiRequest<unknown>(
    `/customer/vendor-bookings/${encodeURIComponent(bookingId)}/payments/verify`,
    { method: "POST", token: accessToken, body: JSON.stringify(payload) }
  );
  const booking = normalizeVendorServiceBooking(response);
  cacheVendorServiceBooking(booking);
  return booking;
}

export async function openVendorBookingCheckout(
  booking: VendorServiceBooking,
  accessToken?: string | null
) {
  const order = await createVendorBookingAdvanceOrder(booking, accessToken);
  if (!order.keyId) {
    if (useMockBookings || !accessToken) {
      return verifyVendorBookingAdvance(booking.id, {
        orderId: order.orderId,
        razorpayPaymentId: `VPAY-${Date.now()}`,
        razorpaySignature: "mock-signature"
      }, accessToken);
    }
    throw new ApiError(503, "Online checkout is not configured");
  }
  await loadRazorpay();
  const result = await new Promise<{ razorpay_order_id: string; razorpay_payment_id: string; razorpay_signature: string }>((resolve, reject) => {
    const Razorpay = (window as unknown as { Razorpay?: new (options: Record<string, unknown>) => { open: () => void } }).Razorpay;
    if (!Razorpay) return reject(new Error("Razorpay checkout could not be loaded."));
    new Razorpay({
      key: order.keyId,
      amount: Math.round(order.amount * 100),
      currency: order.currency,
      name: "VenueMart",
      description: `${booking.service} advance`,
      order_id: order.orderId,
      prefill: { name: booking.customerName, contact: booking.customerPhone, email: booking.customerEmail },
      handler: resolve,
      modal: { ondismiss: () => reject(new Error("Payment was cancelled.")) }
    }).open();
  });
  return verifyVendorBookingAdvance(booking.id, {
    orderId: result.razorpay_order_id,
    razorpayPaymentId: result.razorpay_payment_id,
    razorpaySignature: result.razorpay_signature
  }, accessToken);
}

export function cacheVendorServiceBooking(booking: VendorServiceBooking) {
  if (typeof window === "undefined") return;
  const existing = readLocalVendorServiceBookings().filter((item) => item.id !== booking.id);
  window.localStorage.setItem(storageKey, JSON.stringify([booking, ...existing]));
}

export function vendorServiceBookingToBookingItem(booking: VendorServiceBooking): BookingItem {
  return {
    id: booking.id,
    bookingKind: "VENDOR_SERVICE",
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
    advanceAmount: booking.advanceAmount,
    balanceAmount: booking.balanceAmount,
    advanceDueDate: booking.advanceDueDate,
    cancellationReason: booking.cancellationReason,
    refundableAmount: booking.refundableAmount,
    receiptNumber: booking.payments.find((payment) => payment.status === "PAID")?.receiptNumber,
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
    advanceAmount: numberValue(record.advanceAmount) ?? Math.round((numberValue(record.amount) ?? 0) * 0.2),
    balanceAmount: numberValue(record.balanceAmount) ?? Math.round((numberValue(record.amount) ?? 0) * 0.8),
    advanceDueDate: stringValue(record.advanceDueDate) ?? stringValue(record.eventDate) ?? "",
    startedAt: stringValue(record.startedAt),
    completedAt: stringValue(record.completedAt),
    cancelledAt: stringValue(record.cancelledAt),
    cancelledBy: stringValue(record.cancelledBy),
    cancellationReason: stringValue(record.cancellationReason),
    refundableAmount: numberValue(record.refundableAmount) ?? 0,
    payments: Array.isArray(record.payments) ? record.payments.map(normalizePayment) : [],
    timeline: Array.isArray(record.timeline) ? record.timeline.map(normalizeTimeline) : [],
    reviewEligible: record.reviewEligible === true,
    confirmedAt: stringValue(record.confirmedAt),
    createdAt: stringValue(record.createdAt) ?? new Date().toISOString(),
    updatedAt: stringValue(record.updatedAt) ?? new Date().toISOString()
  };
}

function bookingStatus(value: unknown): BookingStatus {
  return value === "CANCELLED" || value === "COMPLETED" || value === "IN_PROGRESS" || value === "REQUESTED"
    ? value
    : "CONFIRMED";
}

function paymentStatus(value: unknown): BookingPaymentStatus {
  return value === "ADVANCE_PENDING" || value === "ADVANCE_PAID" || value === "REFUND_PENDING" || value === "PARTIALLY_REFUNDED" || value === "REFUNDED"
    ? value
    : "NOT_STARTED";
}

function normalizePayment(value: unknown): VendorBookingPayment {
  const record = asRecord(value);
  const status = record.status === "PAID" || record.status === "FAILED" ? record.status : "CREATED";
  const refundStatus = record.refundStatus === "PENDING" || record.refundStatus === "PARTIALLY_REFUNDED"
    || record.refundStatus === "REFUNDED" || record.refundStatus === "NOT_ELIGIBLE"
    ? record.refundStatus
    : "NOT_REQUESTED";
  return {
    id: stringValue(record.id) ?? "",
    paymentType: stringValue(record.paymentType) ?? "ADVANCE",
    amount: numberValue(record.amount) ?? 0,
    currency: stringValue(record.currency) ?? "INR",
    status,
    orderId: stringValue(record.orderId) ?? "",
    paymentId: stringValue(record.paymentId),
    receiptNumber: stringValue(record.receiptNumber),
    paidAt: stringValue(record.paidAt),
    refundStatus,
    refundAmount: numberValue(record.refundAmount) ?? 0,
    refundedAt: stringValue(record.refundedAt),
    createdAt: stringValue(record.createdAt) ?? new Date().toISOString()
  };
}

function normalizeTimeline(value: unknown): VendorBookingTimeline {
  const record = asRecord(value);
  return {
    id: stringValue(record.id) ?? "",
    eventType: stringValue(record.eventType) ?? "UPDATED",
    fromStatus: stringValue(record.fromStatus),
    toStatus: stringValue(record.toStatus),
    actorRole: stringValue(record.actorRole) ?? "SYSTEM",
    message: stringValue(record.message) ?? "Booking updated",
    createdAt: stringValue(record.createdAt) ?? new Date().toISOString()
  };
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
  if (typeof window === "undefined") return useMockBookings ? [fallbackVendorServiceBooking] : [];
  try {
    const value = JSON.parse(window.localStorage.getItem(storageKey) ?? "[]") as unknown;
    const bookings = Array.isArray(value)
      ? value.map(normalizeVendorServiceBooking).filter((booking) => booking.id)
      : [];
    return bookings.length > 0 ? bookings : useMockBookings ? [fallbackVendorServiceBooking] : [];
  } catch {
    return useMockBookings ? [fallbackVendorServiceBooking] : [];
  }
}

function updateLocalVendorBooking(bookingId: string, changes: Partial<VendorServiceBooking>) {
  const bookings = readLocalVendorServiceBookings();
  const current = bookings.find((booking) => booking.id === bookingId);
  if (!current) throw new Error("Vendor booking not found.");
  const updated = { ...current, ...changes, updatedAt: new Date().toISOString() };
  window.localStorage.setItem(storageKey, JSON.stringify([updated, ...bookings.filter((booking) => booking.id !== bookingId)]));
  return updated;
}

function loadRazorpay() {
  if (typeof window === "undefined") return Promise.reject(new Error("Checkout is unavailable."));
  if ((window as unknown as { Razorpay?: unknown }).Razorpay) return Promise.resolve();
  return new Promise<void>((resolve, reject) => {
    const script = document.createElement("script");
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("Could not load secure checkout."));
    document.head.appendChild(script);
  });
}
