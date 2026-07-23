package com.staminal.venue.vendorbookings;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.vendorbookings.dto.VendorServiceBookingResponse;
import com.staminal.venue.vendorbookings.dto.CancelVendorBookingRequest;
import com.staminal.venue.vendorbookings.dto.UpdateVendorBookingStatusRequest;
import com.staminal.venue.vendorbookings.dto.VendorBookingPaymentOrderResponse;
import com.staminal.venue.vendorbookings.dto.VerifyVendorBookingPaymentRequest;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class VendorServiceBookingController {

    private final VendorServiceBookingService bookingService;
    private final VendorBookingLifecycleService lifecycleService;
    private final VendorBookingPaymentService paymentService;

    @GetMapping("/customer/vendor-bookings")
    public List<VendorServiceBookingResponse> getCustomerBookings(Authentication authentication) {
        return bookingService.getCustomerBookings(authentication);
    }

    @GetMapping("/vendor/bookings")
    public List<VendorServiceBookingResponse> getVendorBookings(Authentication authentication) {
        return bookingService.getVendorBookings(authentication);
    }

    @PatchMapping("/vendor/bookings/{bookingId}/status")
    public VendorServiceBookingResponse updateVendorStatus(
            @PathVariable String bookingId,
            @Valid @RequestBody UpdateVendorBookingStatusRequest request,
            Authentication authentication) {
        return lifecycleService.updateVendorStatus(bookingId, request.status(), request.reason(), authentication);
    }

    @PostMapping("/customer/vendor-bookings/{bookingId}/cancel")
    public VendorServiceBookingResponse cancelCustomerBooking(
            @PathVariable String bookingId,
            @Valid @RequestBody CancelVendorBookingRequest request,
            Authentication authentication) {
        return lifecycleService.cancelCustomerBooking(bookingId, request.reason(), authentication);
    }

    @PostMapping("/customer/vendor-bookings/{bookingId}/payments/advance-order")
    public VendorBookingPaymentOrderResponse createAdvanceOrder(
            @PathVariable String bookingId,
            Authentication authentication) {
        return paymentService.createAdvanceOrder(bookingId, authentication);
    }

    @PostMapping("/customer/vendor-bookings/{bookingId}/payments/verify")
    public VendorServiceBookingResponse verifyAdvancePayment(
            @PathVariable String bookingId,
            @Valid @RequestBody VerifyVendorBookingPaymentRequest request,
            Authentication authentication) {
        return paymentService.verify(bookingId, request, authentication);
    }
}
