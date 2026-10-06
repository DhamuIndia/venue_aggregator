package com.staminal.venue.enquiries;

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
import com.staminal.venue.auth.JwtAuthenticationFilter;
import com.staminal.venue.auth.SecurityConfig;
import com.staminal.venue.auth.service.JwtService;
import com.staminal.venue.enquiries.dto.ApplicationVenueEnquiryPage;

@WebMvcTest(AdminApplicationVenueEnquiryController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AdminApplicationVenueEnquiryControllerSecurityTest {
    private static final String BASE = "/v1/admin/application-venue-enquiries";
    private static final String VALID = "{\"expectedVersion\":0,\"status\":\"CONTACTED\",\"responseMessage\":\"We are checking availability.\",\"reason\":\"Customer follow-up started\"}";
    @Autowired MockMvc mvc;
    @MockitoBean JwtService jwt;
    @MockitoBean ApplicationVenueEnquiryService service;

    @Test void anonymousReadsAndWritesAreDenied() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(put(BASE + "/55").contentType(MediaType.APPLICATION_JSON).content(VALID)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings={"CUSTOMER", "HALL_OWNER", "VENDOR"})
    void customerOwnerAndVendorCannotSeeTeamQueue(String role) throws Exception {
        mvc.perform(get(BASE).with(user("8").roles(role))).andExpect(status().isForbidden());
        mvc.perform(put(BASE + "/55").with(user("8").roles(role)).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings={"ADMIN", "SUPER_ADMIN"})
    void permittedRoutesAreUncacheableAndStillDelegateDatabaseAuthorization(String role) throws Exception {
        when(service.list(eq(0), eq(20), isNull(), any())).thenReturn(new ApplicationVenueEnquiryPage(List.of(),0,20,0,0));
        mvc.perform(get(BASE).with(user("8").roles(role))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.items").isArray());
        mvc.perform(put(BASE + "/55").with(user("8").roles(role)).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        verify(service).list(eq(0),eq(20),isNull(),any()); verify(service).update(eq("55"),any(),any());
    }

    @Test void strictRequestRejectsRoleRoutingAndBookingInjection() throws Exception {
        for (String json : List.of(VALID.replace("}",",\"routingTarget\":\"OWNER\"}"),
                VALID.replace("\"CONTACTED\"", "\"CONFIRMED\""), VALID.replace("expectedVersion\":0", "expectedVersion\":\"0\""),
                VALID + " {}"))
            mvc.perform(put(BASE + "/55").with(user("8").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
