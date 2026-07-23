package com.staminal.venue.quotes.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateQuoteShortlistRequest(
        @NotNull(message = "Shortlist selection is required")
        Boolean shortlisted) {
}
