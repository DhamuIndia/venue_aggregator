package com.staminal.venue.vendorbookings;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.vendorbookings.dto.VendorServiceBookingResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class VendorServiceBookingController {

    private final VendorServiceBookingService bookingService;

    @GetMapping("/customer/vendor-bookings")
    public List<VendorServiceBookingResponse> getCustomerBookings(Authentication authentication) {
        return bookingService.getCustomerBookings(authentication);
    }

    @GetMapping("/vendor/bookings")
    public List<VendorServiceBookingResponse> getVendorBookings(Authentication authentication) {
        return bookingService.getVendorBookings(authentication);
    }
}
