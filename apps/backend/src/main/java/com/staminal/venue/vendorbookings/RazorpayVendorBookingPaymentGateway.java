package com.staminal.venue.vendorbookings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
public class RazorpayVendorBookingPaymentGateway implements VendorBookingPaymentGateway {

    private final String keyId;
    private final String keySecret;
    private final RestClient restClient;

    public RazorpayVendorBookingPaymentGateway(
            @Value("${app.payments.razorpay.key-id:}") String keyId,
            @Value("${app.payments.razorpay.key-secret:}") String keySecret,
            @Value("${app.payments.razorpay.api-base:https://api.razorpay.com/v1}") String apiBase) {
        this.keyId = keyId == null ? "" : keyId.trim();
        this.keySecret = keySecret == null ? "" : keySecret.trim();
        this.restClient = RestClient.builder()
                .baseUrl(apiBase)
                .defaultHeaders(headers -> headers.setBasicAuth(this.keyId, this.keySecret))
                .build();
    }

    @Override
    public PaymentOrder createOrder(String receipt, BigDecimal amount) {
        requireConfigured();
        Map<?, ?> response;
        try {
            response = restClient.post()
                    .uri("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "amount", amount.multiply(new BigDecimal("100"))
                                    .setScale(0, RoundingMode.HALF_UP)
                                    .longValueExact(),
                            "currency", "INR",
                            "receipt", receipt,
                            "notes", Map.of("purpose", "vendor_booking_advance")))
                    .retrieve()
                    .body(Map.class);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Payment provider could not create the order",
                    exception);
        }
        Object orderId = response == null ? null : response.get("id");
        if (!(orderId instanceof String value) || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment provider returned an invalid order");
        }
        return new PaymentOrder(value, keyId, "INR");
    }

    @Override
    public boolean verify(String orderId, String paymentId, String signature) {
        requireConfigured();
        if (orderId == null || paymentId == null || signature == null) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of()
                    .formatHex(mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)));
            return java.security.MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    signature.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not verify payment signature", exception);
        }
    }

    @Override
    public RefundResult refund(String paymentId, BigDecimal amount) {
        requireConfigured();
        Map<?, ?> response;
        try {
            response = restClient.post()
                    .uri("/payments/{paymentId}/refund", paymentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "amount", amount.multiply(new BigDecimal("100"))
                                    .setScale(0, RoundingMode.HALF_UP)
                                    .longValueExact(),
                            "notes", Map.of("purpose", "vendor_booking_cancellation")))
                    .retrieve()
                    .body(Map.class);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Payment provider could not process the refund",
                    exception);
        }
        Object refundId = response == null ? null : response.get("id");
        Object status = response == null ? null : response.get("status");
        if (!(refundId instanceof String value) || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment provider returned an invalid refund");
        }
        return new RefundResult(value, status instanceof String text ? text : "pending");
    }

    @Override
    public boolean isConfigured() {
        return !keyId.isBlank() && !keySecret.isBlank();
    }

    @Override
    public String publicKey() {
        return isConfigured() ? keyId : null;
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Online payments are not configured yet");
        }
    }
}
