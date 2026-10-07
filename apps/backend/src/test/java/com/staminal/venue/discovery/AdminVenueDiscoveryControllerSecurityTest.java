package com.staminal.venue.discovery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.staminal.venue.auth.JwtAuthenticationFilter;
import com.staminal.venue.auth.SecurityConfig;
import com.staminal.venue.auth.service.JwtService;
import com.staminal.venue.discovery.VenueDiscoveryResponse.Candidate;
import com.staminal.venue.discovery.VenueDiscoveryResponse.Page;
import com.staminal.venue.discovery.VenueDiscoveryResponse.Run;
import com.staminal.venue.discovery.VenueDiscoveryResponse.RunDetail;
import com.staminal.venue.discovery.VenueDiscoveryResponse.SearchResult;
import com.staminal.venue.discovery.VenueDiscoveryResponse.Settings;
import com.staminal.venue.discovery.places.PlacePreview;

@WebMvcTest(AdminVenueDiscoveryController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AdminVenueDiscoveryControllerSecurityTest {
    private static final String BASE = "/v1/admin/venue-discovery";
    private static final String SEARCH = """
            {"city":"Chennai","area":"Adyar","venueType":"WEDDING_HALL"}
            """;
    private static final String REVIEW = """
            {"status":"SHORTLISTED","expectedStatus":"DISCOVERED"}
            """;

    @Autowired private MockMvc mvc;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private AdminVenueDiscoveryService service;

    @ParameterizedTest
    @ValueSource(strings = {"settings", "search", "runs", "run", "candidates", "preview", "review"})
    void everyEndpointRequiresAuthentication(String endpoint) throws Exception {
        mvc.perform(request(endpoint)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "VENDOR", "HALL_OWNER"})
    void nonAdminsCannotAccessAnyDiscoveryEndpoint(String role) throws Exception {
        for (String endpoint : endpoints()) {
            mvc.perform(request(endpoint).with(user("301").roles(role)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "SUPER_ADMIN"})
    void bothAdminRolesCanAccessAllEndpointsAndResponsesMustNotBeCached(String role) throws Exception {
        Instant now = Instant.parse("2026-09-30T12:00:00Z");
        Run run = new Run(1L, "Chennai", "Adyar", "WEDDING_HALL", "COMPLETED", now, now, now, null, 1);
        Candidate candidate = new Candidate(2L, "place-id", "DISCOVERED", now, now, now, null);
        PlacePreview preview = new PlacePreview("place-id", "Example venue", "Adyar", "OPERATIONAL",
                "https://maps.google.com/?cid=123", List.of());
        when(service.settings(any(Authentication.class)))
                .thenReturn(new Settings(false, false, false, 10, 100, 0, List.of()));
        when(service.search(any(), any(Authentication.class)))
                .thenReturn(new SearchResult(run, List.of(candidate), List.of(preview)));
        when(service.runs(anyInt(), anyInt(), any(Authentication.class)))
                .thenReturn(new Page<>(List.of(run), 0, 20, 1, 1));
        when(service.run(eq(1L), any(Authentication.class))).thenReturn(new RunDetail(run, List.of(candidate)));
        when(service.candidates(any(), anyInt(), anyInt(), any(Authentication.class)))
                .thenReturn(new Page<>(List.of(candidate), 0, 20, 1, 1));
        when(service.preview(eq(2L), any(Authentication.class))).thenReturn(preview);
        when(service.review(eq(2L), any(), any(Authentication.class))).thenReturn(candidate);

        for (String endpoint : endpoints()) {
            mvc.perform(request(endpoint).with(user("301").roles(role)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
        verify(service).search(eq(new VenueDiscoveryRequest.Search("Chennai", "Adyar",
                VenueDiscoveryVenueType.WEDDING_HALL)), any(Authentication.class));
        verify(service).review(eq(2L), eq(new VenueDiscoveryRequest.Review(
                VenueDiscoveryCandidateStatus.SHORTLISTED, VenueDiscoveryCandidateStatus.DISCOVERED)),
                any(Authentication.class));
    }

    @Test
    void settingsExposeSafeDisabledState() throws Exception {
        when(service.settings(any(Authentication.class)))
                .thenReturn(new Settings(false, false, false, 10, 100, 0, List.of()));
        mvc.perform(get(BASE + "/settings").with(user("301").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.liveApiEnabled").value(false))
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.requestsUsedToday").value(0))
                .andExpect(jsonPath("$.googleApiKey").doesNotExist());
    }

    @ParameterizedTest
    @MethodSource("invalidSearchRequests")
    void invalidSearchInputIsRejectedBeforeService(String body) throws Exception {
        mvc.perform(post(BASE + "/search").with(user("301").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":\"SHORTLISTED\"}",
            "{\"expectedStatus\":\"DISCOVERED\"}",
            "{\"status\":\"UNKNOWN\",\"expectedStatus\":\"DISCOVERED\"}"})
    void invalidReviewInputIsRejectedBeforeService(String body) throws Exception {
        mvc.perform(patch(BASE + "/candidates/2").with(user("301").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    static Stream<Arguments> invalidSearchRequests() {
        return Stream.of(
                Arguments.of("{}"),
                Arguments.of("{\"city\":\" \u0020\",\"venueType\":\"WEDDING_HALL\"}"),
                Arguments.of("{\"city\":\"Chennai\"}"),
                Arguments.of("{\"city\":\"Chennai\",\"venueType\":\"UNKNOWN\"}"),
                Arguments.of("{\"city\":\"" + "c".repeat(121) + "\",\"venueType\":\"WEDDING_HALL\"}"),
                Arguments.of("{\"city\":\"Chennai\",\"area\":\"" + "a".repeat(121)
                        + "\",\"venueType\":\"WEDDING_HALL\"}"),
                Arguments.of("{\"city\":\"Chennai\\n\",\"venueType\":\"WEDDING_HALL\"}"),
                Arguments.of("{\"city\":\"Chennai\",\"area\":\"Adyar\\t\",\"venueType\":\"WEDDING_HALL\"}"));
    }

    private static List<String> endpoints() {
        return List.of("settings", "search", "runs", "run", "candidates", "preview", "review");
    }

    private static MockHttpServletRequestBuilder request(String endpoint) {
        return switch (endpoint) {
            case "settings" -> get(BASE + "/settings");
            case "search" -> post(BASE + "/search").contentType(MediaType.APPLICATION_JSON).content(SEARCH);
            case "runs" -> get(BASE + "/runs");
            case "run" -> get(BASE + "/runs/1");
            case "candidates" -> get(BASE + "/candidates");
            case "preview" -> post(BASE + "/candidates/2/preview");
            case "review" -> patch(BASE + "/candidates/2").contentType(MediaType.APPLICATION_JSON).content(REVIEW);
            default -> throw new IllegalArgumentException(endpoint);
        };
    }
}
