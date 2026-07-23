package com.staminal.venue.vendorbookings.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyVendorBookingPaymentRequest(
        @NotBlank String orderId,
        @NotBlank String razorpayPaymentId,
        @NotBlank String razorpaySignature) {
}
