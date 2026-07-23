package com.staminal.venue.vendorbookings.dto;

import java.math.BigDecimal;

public record VendorBookingPaymentOrderResponse(
        String orderId,
        String bookingId,
        BigDecimal amount,
        String currency,
        String status,
        String keyId) {
}
