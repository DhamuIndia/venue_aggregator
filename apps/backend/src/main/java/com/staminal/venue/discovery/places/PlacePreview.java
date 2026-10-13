package com.staminal.venue.discovery.places;

import java.util.List;

/** Google content for transient display only; never persist this record. */
public record PlacePreview(
        String placeId,
        String displayName,
        String formattedAddress,
        String businessStatus,
        String googleMapsUri,
        List<PlaceAttribution> attributions) {

    public PlacePreview {
        attributions = attributions == null ? List.of() : List.copyOf(attributions);
    }
}
