package com.staminal.venue.availability.Dto;

import java.util.List;

public class AvailabilityResponse {

    private List<BlockedDateResponse> blockedDates;

    private List<BookingAvailabilityResponse> bookings;

    public List<BlockedDateResponse> getBlockedDates() {
        return blockedDates;
    }

    public void setBlockedDates(List<BlockedDateResponse> blockedDates) {
        this.blockedDates = blockedDates;
    }

    public List<BookingAvailabilityResponse> getBookings() {
        return bookings;
    }

    public void setBookings(List<BookingAvailabilityResponse> bookings) {
        this.bookings = bookings;
    }

}