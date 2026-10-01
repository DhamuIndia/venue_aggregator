package com.staminal.venue.discovery.places;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.discovery.VenueDiscoveryProperties;

class GoogleVenuePlacesClientTest {

    private static final String SEARCH_URL = "https://places.googleapis.com/v1/places:searchText";
    private static final String DETAILS_URL =
            "https://places.googleapis.com/v1/places/ChIJ_test-123?languageCode=en&regionCode=IN";
    private static final String VALID_PLACE = """
            {
              "id": "ChIJ_test-123",
              "displayName": {"text": "Sample Function Hall", "languageCode": "en"},
              "formattedAddress": "Adyar, Chennai, India",
              "businessStatus": "OPERATIONAL",
              "googleMapsUri": "https://maps.google.com/?cid=123",
              "attributions": [{"provider": "Sample provider", "providerUri": "https://example.org/credit"}]
            }
            """;

    private VenueDiscoveryProperties properties;
    private MockRestServiceServer server;
    private GoogleVenuePlacesClient client;

    @BeforeEach
    void setUp() {
        properties = mock(VenueDiscoveryProperties.class);
        when(properties.isEnabled()).thenReturn(true);
        when(properties.isLiveApiEnabled()).thenReturn(true);
        when(properties.getGoogleApiKey()).thenReturn("fake-unit-test-key");
        RestClient.Builder builder = RestClient.builder().baseUrl(GoogleVenuePlacesClient.BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GoogleVenuePlacesClient(properties, builder.build());
    }

    @Test
    void searchesOfficialEndpointWithMinimalFieldMaskAndNoPersistedContent() {
        server.expect(requestTo(SEARCH_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "fake-unit-test-key"))
                .andExpect(header("X-Goog-FieldMask", GoogleVenuePlacesClient.SEARCH_FIELDS))
                .andExpect(jsonPath("$.textQuery").value("function halls in Adyar Chennai"))
                .andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.languageCode").value("en"))
                .andExpect(jsonPath("$.regionCode").value("IN"))
                .andRespond(withSuccess("{\"places\":[" + VALID_PLACE + "]}", MediaType.APPLICATION_JSON));

        List<PlacePreview> results = client.search(" function halls in Adyar Chennai ", 10);

        assertThat(results).containsExactly(new PlacePreview(
                "ChIJ_test-123", "Sample Function Hall", "Adyar, Chennai, India", "OPERATIONAL",
                "https://maps.google.com/?cid=123",
                List.of(new PlaceAttribution("Sample provider", "https://example.org/credit"))));
        server.verify();
    }

    @Test
    void detailsRequestsOnlySelectedPlaceWithMinimalFields() {
        server.expect(requestTo(DETAILS_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Goog-Api-Key", "fake-unit-test-key"))
                .andExpect(header("X-Goog-FieldMask", GoogleVenuePlacesClient.DETAIL_FIELDS))
                .andRespond(withSuccess(VALID_PLACE, MediaType.APPLICATION_JSON));

        assertThat(client.details("ChIJ_test-123").displayName()).isEqualTo("Sample Function Hall");
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"false,true", "true,false", "false,false"})
    void bothFeatureFlagsAreRequiredBeforeAnyRequest(boolean enabled, boolean liveEnabled) {
        when(properties.isEnabled()).thenReturn(enabled);
        when(properties.isLiveApiEnabled()).thenReturn(liveEnabled);

        assertFailure(() -> client.search("halls Chennai", 10), HttpStatus.SERVICE_UNAVAILABLE);
        assertFailure(() -> client.details("ChIJ_test-123"), HttpStatus.SERVICE_UNAVAILABLE);
        server.verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "invalid\nkey"})
    void missingKeyPreventsAllRequests(String key) {
        when(properties.getGoogleApiKey()).thenReturn(key);

        assertFailure(() -> client.search("halls Chennai", 10), HttpStatus.SERVICE_UNAVAILABLE);
        assertFailure(() -> client.details("ChIJ_test-123"), HttpStatus.SERVICE_UNAVAILABLE);
        server.verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"../secret", "https://evil.example/", "ChIJ?key=leak", "ChIJ%2Fsecret", " ChIJ123"})
    void invalidPlaceIdsNeverReachTheProvider(String placeId) {
        assertFailure(() -> client.details(placeId), HttpStatus.BAD_REQUEST);
        server.verify();
    }

    @Test
    void invalidSearchArgumentsNeverReachTheProvider() {
        assertFailure(() -> client.search(" ", 10), HttpStatus.BAD_REQUEST);
        assertFailure(() -> client.search("hall", 0), HttpStatus.BAD_REQUEST);
        assertFailure(() -> client.search("hall", 21), HttpStatus.BAD_REQUEST);
        assertFailure(() -> client.search("a".repeat(501), 10), HttpStatus.BAD_REQUEST);
        assertFailure(() -> client.details("a".repeat(256)), HttpStatus.BAD_REQUEST);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"places\":[]}"})
    void noResultsAreSuccessful(String response) {
        server.expect(requestTo(SEARCH_URL)).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        assertThat(client.search("halls Chennai", 10)).isEmpty();
        server.verify();
    }

    @Test
    void duplicateIdsAreReturnedOnlyOnce() {
        server.expect(requestTo(SEARCH_URL)).andRespond(withSuccess(
                "{\"places\":[" + VALID_PLACE + "," + VALID_PLACE + "]}", MediaType.APPLICATION_JSON));

        assertThat(client.search("halls Chennai", 10)).hasSize(1);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-json", "[]", "null", "{\"error\":\"unexpected\"}", "{\"places\":null}", "{\"places\":{}}",
            "{\"places\":[{}]}", "{\"places\":[{\"id\":\"../unexpected\"}]}",
            "{\"places\":[{\"id\":\"valid-id\",\"displayName\":\"not-an-object\"}]}"
    })
    void malformedProviderResponsesAreSanitized(String response) {
        server.expect(requestTo(SEARCH_URL)).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        assertFailure(() -> client.search("halls Chennai", 10), HttpStatus.BAD_GATEWAY);
        server.verify();
    }

    @Test
    void providerCannotExceedRequestedResultLimit() {
        server.expect(requestTo(SEARCH_URL)).andRespond(withSuccess(
                "{\"places\":[" + VALID_PLACE + "," + VALID_PLACE + "]}", MediaType.APPLICATION_JSON));

        assertFailure(() -> client.search("halls Chennai", 1), HttpStatus.BAD_GATEWAY);
        server.verify();
    }

    @Test
    void detailsMustMatchRequestedPlaceId() {
        server.expect(requestTo(DETAILS_URL)).andRespond(withSuccess(
                VALID_PLACE.replace("ChIJ_test-123", "different-place"), MediaType.APPLICATION_JSON));

        assertFailure(() -> client.details("ChIJ_test-123"), HttpStatus.BAD_GATEWAY);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://maps.google.com/?cid=123", "javascript:alert(1)", "https://maps.google.com.evil.example/",
            "https://maps.google.com@evil.example/", "https://www.google.com/url?q=https://evil.example",
            "https://maps.google.com:8080/", "//maps.google.com/", "https://evil.example/maps/123"
    })
    void unsafeMapsLinksAreRemoved(String unsafeUrl) {
        server.expect(requestTo(DETAILS_URL)).andRespond(withSuccess(
                VALID_PLACE.replace("https://maps.google.com/?cid=123", unsafeUrl), MediaType.APPLICATION_JSON));

        assertThat(client.details("ChIJ_test-123").googleMapsUri()).isNull();
        server.verify();
    }

    @Test
    void unsafeAttributionLinksAreRemovedButCreditIsRetained() {
        server.expect(requestTo(DETAILS_URL)).andRespond(withSuccess(
                VALID_PLACE.replace("https://example.org/credit", "javascript:alert(1)"),
                MediaType.APPLICATION_JSON));

        assertThat(client.details("ChIJ_test-123").attributions())
                .containsExactly(new PlaceAttribution("Sample provider", null));
        server.verify();
    }

    @Test
    void safeGoogleMapsPathIsAllowed() {
        server.expect(requestTo(DETAILS_URL)).andRespond(withSuccess(
                VALID_PLACE.replace("https://maps.google.com/?cid=123", "https://www.google.com/maps/place/hall"),
                MediaType.APPLICATION_JSON));

        assertThat(client.details("ChIJ_test-123").googleMapsUri())
                .isEqualTo("https://www.google.com/maps/place/hall");
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"401,503", "403,503", "429,503", "500,502", "503,502", "400,502"})
    void providerErrorsAreSanitizedWithoutRetry(int providerStatus, int expectedStatus) {
        server.expect(requestTo(SEARCH_URL)).andRespond(withStatus(HttpStatus.valueOf(providerStatus))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"private provider detail fake-unit-test-key\"}"));

        assertFailure(() -> client.search("halls Chennai", 10), HttpStatus.valueOf(expectedStatus));
        server.verify();
    }

    @Test
    void missingPlaceIsReportedAsNotFound() {
        server.expect(requestTo(DETAILS_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertFailure(() -> client.details("ChIJ_test-123"), HttpStatus.NOT_FOUND);
        server.verify();
    }

    @Test
    void networkFailureIsSanitizedWithoutRetry() {
        server.expect(requestTo(SEARCH_URL))
                .andRespond(withException(new IOException("private provider detail fake-unit-test-key")));

        assertFailure(() -> client.search("halls Chennai", 10), HttpStatus.SERVICE_UNAVAILABLE);
        server.verify();
    }

    private static void assertFailure(Runnable operation, HttpStatus expectedStatus) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException response = (ResponseStatusException) exception;
                    assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
                    assertThat(response.getReason()).doesNotContain("fake-unit-test-key", "private provider detail");
                    assertThat(response.getCause()).isNull();
                });
    }
}
