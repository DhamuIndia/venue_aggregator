package com.staminal.venue.enquiries.dto;

import java.util.List;

public record ApplicationVenueEnquiryPage(List<EnquiryResponse> items, int page, int size,
        long totalItems, int totalPages) { }
