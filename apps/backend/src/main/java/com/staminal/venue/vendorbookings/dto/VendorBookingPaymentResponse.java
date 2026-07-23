package com.staminal.venue.vendorbookings.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.staminal.venue.vendorbookings.VendorBookingPaymentStatus;
import com.staminal.venue.vendorbookings.VendorBookingRefundStatus;

public record VendorBookingPaymentResponse(
        String id,
        String paymentType,
        BigDecimal amount,
        String currency,
        VendorBookingPaymentStatus status,
        String provider,
        String orderId,
        String paymentId,
        String receiptNumber,
        Instant paidAt,
        VendorBookingRefundStatus refundStatus,
        BigDecimal refundAmount,
        Instant refundedAt,
        Instant createdAt) {
}
