import { ApiError, apiRequest } from "@/lib/api-client";
import { updateLocalBookingPaymentStatus, type BookingItem } from "./booking-client";

const useMockBookingPayments = process.env.NEXT_PUBLIC_BOOKING_PAYMENTS_MODE === "mock";

export type BookingPaymentOrder = {
  orderId: string;
  bookingId: string;
  amount: number;
  currency: "INR";
  status: "CREATED" | "PENDING_PAYMENT" | "PAID";
  keyId?: string;
  checkoutUrl?: string;
};

export type VerifyBookingPaymentPayload = {
  bookingId: string;
  orderId: string;
  razorpayPaymentId: string;
  razorpaySignature: string;
};

export async function createBookingAdvanceOrder(booking: BookingItem, accessToken?: string | null) {
  if (useMockBookingPayments || !accessToken) return createLocalOrder(booking);

  try {
    const response = await apiRequest<unknown>(`/customer/bookings/${encodeURIComponent(booking.id)}/payments/advance-order`, {
      method: "POST",
      token: accessToken,
      body: JSON.stringify({ bookingId: booking.id })
    });
    return toBookingPaymentOrder(response, booking) ?? createLocalOrder(booking);
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 404, 409].includes(exception.status)) {
      throw exception;
    }
    return createLocalOrder(booking);
  }
}

export async function verifyBookingAdvancePayment(payload: VerifyBookingPaymentPayload, accessToken?: string | null) {
  if (useMockBookingPayments || !accessToken) {
    return updateLocalBookingPaymentStatus(payload.bookingId, "ADVANCE_PAID");
  }

  try {
    const response = await apiRequest<unknown>(`/customer/bookings/${encodeURIComponent(payload.bookingId)}/payments/verify`, {
      method: "POST",
      token: accessToken,
      body: JSON.stringify(payload)
    });
    return toPaidBooking(response) ?? updateLocalBookingPaymentStatus(payload.bookingId, "ADVANCE_PAID");
  } catch (exception) {
    if (exception instanceof ApiError && [400, 401, 403, 404, 409].includes(exception.status)) {
      throw exception;
    }
    return updateLocalBookingPaymentStatus(payload.bookingId, "ADVANCE_PAID");
  }
}

function createLocalOrder(booking: BookingItem): BookingPaymentOrder {
  return {
    orderId: `ORDER-${Date.now().toString().slice(-6)}`,
    bookingId: booking.id,
    amount: advanceAmount(booking),
    currency: "INR",
    status: "PENDING_PAYMENT"
  };
}

function advanceAmount(booking: BookingItem) {
  if (booking.amount && booking.amount > 0) return Math.max(5000, Math.round(booking.amount * 0.2));
  return 25000;
}

function toBookingPaymentOrder(value: unknown, booking: BookingItem): BookingPaymentOrder | undefined {
  const record = unwrapRecord(value, "order");
  if (!record) return undefined;

  const orderId = stringValue(record, ["orderId", "order_id", "razorpayOrderId", "razorpay_order_id", "id"]);
  const amount = numberValue(record, ["amount", "advanceAmount", "advance_amount"]);
  if (!orderId || typeof amount !== "number") return undefined;

  return {
    orderId,
    bookingId: stringValue(record, ["bookingId", "booking_id"]) ?? booking.id,
    amount,
    currency: "INR",
    status: orderStatus(record) ?? "CREATED",
    keyId: stringValue(record, ["keyId", "key_id", "razorpayKeyId", "razorpay_key_id"]),
    checkoutUrl: stringValue(record, ["checkoutUrl", "checkout_url", "paymentUrl", "payment_url"])
  };
}

function toPaidBooking(value: unknown): BookingItem | undefined {
  const record = unwrapRecord(value, "booking");
  if (!record) return undefined;

  const id = stringValue(record, ["id", "bookingId", "booking_id"]);
  const hallId = stringValue(record, ["hallId", "hall_id"]);
  const hallName = stringValue(record, ["hallName", "hall_name", "venue", "venueName"]);
  const eventDate = stringValue(record, ["eventDate", "event_date"]);
  const eventType = stringValue(record, ["eventType", "event_type"]);
  const slot = slotValue(record, ["slot", "slotType", "slot_type"]);
  if (!id || !hallId || !hallName || !eventDate || !eventType || !isSlot(slot)) return undefined;

  return {
    id,
    enquiryId: stringValue(record, ["enquiryId", "enquiry_id"]),
    hallId,
    hallName,
    customerId: stringValue(record, ["customerId", "customer_id"]),
    customerName: stringValue(record, ["customerName", "customer_name"]),
    eventDate,
    eventType,
    guestCount: numberValue(record, ["guestCount", "guest_count", "guests"]) ?? 0,
    slot,
    status: bookingStatus(record),
    amount: numberValue(record, ["amount", "totalAmount", "total_amount", "bookingAmount"]),
    paymentStatus: paymentStatus(record),
    notes: stringValue(record, ["notes", "message"]),
    confirmedAt: stringValue(record, ["confirmedAt", "confirmed_at"]),
    updatedAt: stringValue(record, ["updatedAt", "updated_at", "createdAt", "created_at"]) ?? new Date().toISOString()
  };
}

function unwrapRecord(value: unknown, nestedKey: string): Record<string, unknown> | undefined {
  if (!isRecord(value)) return undefined;
  if (isRecord(value[nestedKey])) return value[nestedKey];
  if (isRecord(value.data)) return unwrapRecord(value.data, nestedKey);
  return value;
}

function orderStatus(record: Record<string, unknown>): BookingPaymentOrder["status"] | undefined {
  const value = stringValue(record, ["status"]);
  const normalized = value?.trim().toUpperCase();
  if (normalized === "CREATED" || normalized === "PENDING_PAYMENT" || normalized === "PAID") return normalized;
  if (normalized === "PENDING") return "PENDING_PAYMENT";
  return undefined;
}

function bookingStatus(record: Record<string, unknown>): BookingItem["status"] {
  const value = stringValue(record, ["status", "bookingStatus", "booking_status"]);
  const normalized = value?.trim().toUpperCase();
  if (normalized === "REQUESTED" || normalized === "CONFIRMED" || normalized === "CANCELLED" || normalized === "COMPLETED") return normalized;
  return "CONFIRMED";
}

function paymentStatus(record: Record<string, unknown>): BookingItem["paymentStatus"] {
  const value = stringValue(record, ["paymentStatus", "payment_status"]);
  const normalized = value?.trim().toUpperCase();
  if (normalized === "NOT_STARTED" || normalized === "ADVANCE_PENDING" || normalized === "ADVANCE_PAID" || normalized === "REFUNDED") return normalized;
  if (normalized === "PAID") return "ADVANCE_PAID";
  return "ADVANCE_PAID";
}

function slotValue(record: Record<string, unknown>, keys: string[]) {
  const value = stringValue(record, keys);
  const normalized = value?.trim().toUpperCase().replace(/[\s-]+/g, "_");
  return isSlot(normalized) ? normalized : undefined;
}

function isSlot(value: unknown): value is BookingItem["slot"] {
  return value === "MORNING" || value === "EVENING" || value === "FULL_DAY";
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
