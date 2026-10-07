package com.staminal.venue.overture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.staminal.venue.auth.JwtAuthenticationFilter;
import com.staminal.venue.auth.SecurityConfig;
import com.staminal.venue.auth.service.JwtService;
import com.staminal.venue.overture.OvertureResponse.*;

@WebMvcTest(AdminOvertureOnboardingController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AdminOvertureOnboardingControllerSecurityTest {
    private static final String BASE = "/v1/admin/overture-onboarding";
    @Autowired MockMvc mvc;
    @MockitoBean JwtService jwt;
    @MockitoBean AdminOvertureOnboardingService service;
    @MockitoBean OvertureDraftReviewService reviews;

    @ParameterizedTest @ValueSource(strings = {"settings", "catalog", "preview", "import", "drafts", "detail", "update"})
    void endpointsRequireAuthentication(String endpoint) throws Exception {
        mvc.perform(request(endpoint)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
        verifyNoInteractions(reviews);
    }

    @ParameterizedTest @ValueSource(strings = {"CUSTOMER", "VENDOR", "HALL_OWNER"})
    void nonAdminRolesCannotAccessReadOrImport(String role) throws Exception {
        for (String endpoint : List.of("settings", "catalog", "preview", "import", "drafts", "detail", "update"))
            mvc.perform(request(endpoint).with(user("7").roles(role))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
        verifyNoInteractions(reviews);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "SUPER_ADMIN"})
    void bothAdminRolesReceiveUncacheableResponses(String role) throws Exception {
        when(service.settings(any())).thenReturn(new Settings(false, false, null, null, null, null, 0, 20));
        when(service.catalog(anyInt(), anyInt(), any(), any())).thenReturn(new CatalogPage("0".repeat(64),
                "2026-09-23.1", "Chennai", null, List.of(), 0, 20, 0, 0));
        when(service.preview(any(), any())).thenReturn(new Preview("0".repeat(64), "2026-09-23.1", List.of()));
        when(service.importDrafts(any(), any())).thenReturn(new ImportResult(0, 0, List.of()));
        when(service.drafts(anyInt(), anyInt(), any())).thenReturn(new Page<>(List.of(), 0, 20, 0, 0));
        for (String endpoint : List.of("settings", "catalog", "preview", "import", "drafts", "detail", "update"))
            mvc.perform(request(endpoint).with(user("7").roles(role))).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"));
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"ids\":[]}", "{\"ids\":[null]}", "{\"ids\":[\"\"]}"})
    void invalidSelectionsAreRejectedBeforeService(String body) throws Exception {
        mvc.perform(post(BASE + "/preview").with(user("7").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void importRequiresExactVersionHashAndBoundedIds() throws Exception {
        mvc.perform(post(BASE + "/import").with(user("7").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"catalogVersion\":\"old\",\"ids\":[\"" + OvertureFixtures.ID + "\"]}"))
                .andExpect(status().isBadRequest());
        String ids = String.join(",", java.util.Collections.nCopies(21, "\"" + OvertureFixtures.ID + "\""));
        mvc.perform(post(BASE + "/preview").with(user("7").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[" + ids + "]}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"status", "ownerUserId", "ownerName", "listingOrigin", "price", "coverImageUrl", "approvalStatus", "sourceFacts"})
    void reviewCannotReceiveAuthorityMediaPriceOrSourceFields(String field) throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(OvertureDraftReviewTestFixtures.json());
        ((com.fasterxml.jackson.databind.node.ObjectNode) body.get("facts")).put(field, "APPROVED");
        mvc.perform(put(BASE + "/drafts/55").with(user("7").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isBadRequest());
        verifyNoInteractions(reviews);
    }

    @Test void reviewRejectsDuplicateKeysAndDoesNotCoerceScalarTypes() throws Exception {
        String body = OvertureDraftReviewTestFixtures.json();
        for (String invalid : List.of(body + " {}", body + " true", body.replace("\"expectedVersion\":0", "\"expectedVersion\":0,\"expectedVersion\":99"),
                body.replace("\"capacity\":250", "\"capacity\":\"250\""),
                body.replace("\"verifiedFields\":[]", "\"verifiedFields\":[\"name\",\"name\"]"),
                body.replace("\"reviewStatus\":\"IN_REVIEW\"", "\"reviewStatus\":\"APPROVED\""),
                body.replace("\"amenities\":", "\"status\":\"APPROVED\",\"amenities\":"))) {
            mvc.perform(put(BASE + "/drafts/55").with(user("7").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(reviews);
    }

    @Test void staleDuplicateAssessmentIsReportedAsConflictForExplicitClientReload() throws Exception {
        when(reviews.update(eq(55L), any(), any())).thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "Duplicate matches have changed; reload the latest draft before saving"));
        mvc.perform(put(BASE + "/drafts/55").with(user("7").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(OvertureDraftReviewTestFixtures.json()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("reload")));
        verify(reviews).update(eq(55L), any(), any());
    }

    private static MockHttpServletRequestBuilder request(String endpoint) {
        return switch (endpoint) {
            case "settings" -> get(BASE + "/settings");
            case "catalog" -> get(BASE + "/catalog");
            case "drafts" -> get(BASE + "/drafts");
            case "detail" -> get(BASE + "/drafts/55");
            case "update" -> put(BASE + "/drafts/55").contentType(MediaType.APPLICATION_JSON)
                    .content(OvertureDraftReviewTestFixtures.json());
            case "preview" -> post(BASE + "/preview").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ids\":[\"" + OvertureFixtures.ID + "\"]}");
            case "import" -> post(BASE + "/import").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"catalogVersion\":\"" + "0".repeat(64) + "\",\"ids\":[\"" + OvertureFixtures.ID + "\"]}");
            default -> throw new IllegalArgumentException(endpoint);
        };
    }
}
