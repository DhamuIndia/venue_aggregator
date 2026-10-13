package com.staminal.venue.discovery.places;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.staminal.venue.discovery.VenueDiscoveryProperties;

@Component
public class GoogleVenuePlacesClient implements VenuePlacesClient {

    static final String BASE_URL = "https://places.googleapis.com/v1";
    static final String DETAIL_FIELDS =
            "id,displayName,formattedAddress,businessStatus,googleMapsUri,attributions";
    static final String SEARCH_FIELDS =
            "places.id,places.displayName,places.formattedAddress,places.businessStatus,"
                    + "places.googleMapsUri,places.attributions";
    private static final Pattern PLACE_ID = Pattern.compile("[A-Za-z0-9_-]{1,255}");
    private static final Set<String> MAP_HOSTS = Set.of("maps.google.com", "maps.google.co.in");
    private static final Set<String> GOOGLE_HOSTS = Set.of(
            "google.com", "www.google.com", "google.co.in", "www.google.co.in");

    private final VenueDiscoveryProperties properties;
    private final RestClient restClient;

    @Autowired
    public GoogleVenuePlacesClient(VenueDiscoveryProperties properties, RestClient.Builder builder) {
        this(properties, configuredClient(properties, builder));
    }

    // Package-private injection keeps tests fully local without changing production URL or transport.
    GoogleVenuePlacesClient(VenueDiscoveryProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    private static RestClient configuredClient(VenueDiscoveryProperties properties, RestClient.Builder builder) {
        Duration timeout = Duration.ofSeconds(Math.max(1, Math.min(30, properties.getTimeoutSeconds())));
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        return builder.clone().baseUrl(BASE_URL).requestFactory(factory).build();
    }

    @Override
    public List<PlacePreview> search(String textQuery, int maxResults) {
        requireEnabled();
        if (textQuery == null || textQuery.isBlank() || textQuery.length() > 500
                || maxResults < 1 || maxResults > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid venue discovery search");
        }
        try {
            JsonNode response = restClient.post()
                    .uri("/places:searchText")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Goog-Api-Key", properties.getGoogleApiKey().trim())
                    .header("X-Goog-FieldMask", SEARCH_FIELDS)
                    .body(Map.of(
                            "textQuery", textQuery.trim(),
                            "pageSize", maxResults,
                            "languageCode", "en",
                            "regionCode", "IN"))
                    .retrieve()
                    .body(JsonNode.class);
            requireObject(response);
            if (response.has("error")) {
                throw invalidResponse();
            }
            JsonNode places = response.get("places");
            // A valid zero-result Google response omits the places field.
            if (places == null) {
                return List.of();
            }
            if (!places.isArray() || places.size() > maxResults) {
                throw invalidResponse();
            }
            Map<String, PlacePreview> uniquePlaces = new LinkedHashMap<>();
            for (JsonNode place : places) {
                PlacePreview preview = parsePlace(place);
                uniquePlaces.putIfAbsent(preview.placeId(), preview);
            }
            return List.copyOf(uniquePlaces.values());
        } catch (RestClientResponseException exception) {
            throw providerFailure(exception, false);
        } catch (ResourceAccessException exception) {
            throw unavailable();
        } catch (RestClientException exception) {
            throw invalidResponse();
        }
    }

    @Override
    public PlacePreview details(String placeId) {
        requireEnabled();
        if (placeId == null || !PLACE_ID.matcher(placeId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Google Place ID");
        }
        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.pathSegment("places", placeId)
                            .queryParam("languageCode", "en")
                            .queryParam("regionCode", "IN")
                            .build())
                    .header("X-Goog-Api-Key", properties.getGoogleApiKey().trim())
                    .header("X-Goog-FieldMask", DETAIL_FIELDS)
                    .retrieve()
                    .body(JsonNode.class);
            PlacePreview preview = parsePlace(response);
            if (!placeId.equals(preview.placeId())) {
                throw invalidResponse();
            }
            return preview;
        } catch (RestClientResponseException exception) {
            throw providerFailure(exception, true);
        } catch (ResourceAccessException exception) {
            throw unavailable();
        } catch (RestClientException exception) {
            throw invalidResponse();
        }
    }

    private void requireEnabled() {
        if (!properties.isEnabled() || !properties.isLiveApiEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Live venue discovery is disabled");
        }
        String apiKey = properties.getGoogleApiKey();
        if (apiKey == null || apiKey.isBlank() || apiKey.length() > 500
                || apiKey.chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Venue discovery provider is not configured");
        }
    }

    private PlacePreview parsePlace(JsonNode place) {
        requireObject(place);
        String placeId = text(place.get("id"), 255);
        if (placeId == null || !PLACE_ID.matcher(placeId).matches()) {
            throw invalidResponse();
        }
        JsonNode name = place.get("displayName");
        String displayName = null;
        if (name != null && !name.isNull()) {
            requireObject(name);
            displayName = text(name.get("text"), 1000);
        }
        List<PlaceAttribution> attributions = new ArrayList<>();
        JsonNode providerAttributions = place.get("attributions");
        if (providerAttributions != null && !providerAttributions.isNull()) {
            if (!providerAttributions.isArray() || providerAttributions.size() > 100) {
                throw invalidResponse();
            }
            for (JsonNode attribution : providerAttributions) {
                requireObject(attribution);
                String attributionName = text(attribution.get("provider"), 1000);
                String uri = safeHttpsUrl(text(attribution.get("providerUri"), 4000), false);
                if (attributionName != null || uri != null) {
                    attributions.add(new PlaceAttribution(attributionName == null ? "Source" : attributionName, uri));
                }
            }
        }
        return new PlacePreview(
                placeId,
                displayName,
                text(place.get("formattedAddress"), 4000),
                text(place.get("businessStatus"), 100),
                safeHttpsUrl(text(place.get("googleMapsUri"), 4000), true),
                attributions);
    }

    private static String text(JsonNode node, int maxLength) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual() || node.textValue().length() > maxLength) {
            throw invalidResponse();
        }
        String value = node.textValue().trim();
        return value.isEmpty() ? null : value;
    }

    private static void requireObject(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw invalidResponse();
        }
    }

    private static String safeHttpsUrl(String value, boolean googleMapsOnly) {
        if (value == null) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
                return null;
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (googleMapsOnly && !MAP_HOSTS.contains(host)
                    && !(GOOGLE_HOSTS.contains(host)
                            && ("/maps".equals(uri.getPath()) || uri.getPath().startsWith("/maps/")))) {
                return null;
            }
            return value;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static ResponseStatusException providerFailure(RestClientResponseException exception, boolean details) {
        int status = exception.getStatusCode().value();
        if (details && status == 404) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "This Google place is no longer available");
        }
        if (status == 429) {
            return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Venue discovery provider is temporarily rate limited; try again later");
        }
        if (status == 401 || status == 403) {
            return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Venue discovery provider configuration needs attention");
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Venue discovery provider request failed");
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Venue discovery provider is temporarily unavailable");
    }

    private static ResponseStatusException invalidResponse() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Venue discovery provider returned an invalid response");
    }
}
