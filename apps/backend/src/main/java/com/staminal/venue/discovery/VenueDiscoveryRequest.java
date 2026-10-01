package com.staminal.venue.discovery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class VenueDiscoveryRequest {
    private VenueDiscoveryRequest() { }

    public record Search(
            @NotBlank @Size(max = 120) @Pattern(regexp = "[^\\p{Cntrl}]+") String city,
            @Size(max = 120) @Pattern(regexp = "[^\\p{Cntrl}]*") String area,
            @NotNull VenueDiscoveryVenueType venueType) { }

    public record Review(
            @NotNull VenueDiscoveryCandidateStatus status,
            @NotNull VenueDiscoveryCandidateStatus expectedStatus) { }
}
