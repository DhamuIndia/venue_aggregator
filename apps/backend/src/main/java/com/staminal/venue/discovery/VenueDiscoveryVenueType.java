package com.staminal.venue.discovery;

public enum VenueDiscoveryVenueType {
    WEDDING_HALL("Wedding halls"),
    BANQUET_HALL("Banquet halls"),
    CONVENTION_CENTER("Convention centers"),
    PARTY_HALL("Party halls");

    private final String label;
    VenueDiscoveryVenueType(String label) { this.label = label; }
    public String getLabel() { return label; }
}
