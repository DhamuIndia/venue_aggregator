package com.staminal.venue.bookings;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.bookings.dto.BookingListResponse;
import com.staminal.venue.bookings.dto.BookingResponse;
import com.staminal.venue.bookings.dto.UpdateBookingStatusRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @GetMapping("/customer/bookings")
    public BookingListResponse getCustomerBookings(Authentication authentication) {
        return bookingService.getCustomerBookings(authentication);
    }

    @GetMapping("/customer/bookings/{bookingId}")
    public BookingResponse getCustomerBooking(
            @PathVariable String bookingId,
            Authentication authentication) {
        return bookingService.getCustomerBooking(bookingId, authentication);
    }

    @GetMapping("/owner/halls/{hallId}/bookings")
    public BookingListResponse getOwnerHallBookings(
            @PathVariable String hallId,
            Authentication authentication) {
        return bookingService.getOwnerHallBookings(hallId, authentication);
    }

    @PatchMapping("/owner/bookings/{bookingId}/status")
    public BookingResponse updateOwnerBookingStatus(
            @PathVariable String bookingId,
            @Valid @RequestBody UpdateBookingStatusRequest request,
            Authentication authentication) {
        return bookingService.updateOwnerBookingStatus(bookingId, request, authentication);
    }
}
