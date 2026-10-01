package com.staminal.venue.discovery.places;

import java.util.List;

public interface VenuePlacesClient {

    List<PlacePreview> search(String textQuery, int maxResults);

    PlacePreview details(String placeId);
}
