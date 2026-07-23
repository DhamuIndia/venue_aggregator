package com.staminal.venue.vendorbookings;

import java.math.BigDecimal;

public interface VendorBookingPaymentGateway {
    PaymentOrder createOrder(String receipt, BigDecimal amount);
    RefundResult refund(String paymentId, BigDecimal amount);
    boolean verify(String orderId, String paymentId, String signature);
    boolean isConfigured();
    String publicKey();

    record PaymentOrder(String orderId, String keyId, String currency) {
    }

    record RefundResult(String refundId, String status) {
    }
}
