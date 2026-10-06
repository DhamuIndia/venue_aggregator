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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.staminal.venue.auth.JwtAuthenticationFilter;
import com.staminal.venue.auth.SecurityConfig;
import com.staminal.venue.auth.service.JwtService;

@WebMvcTest({AdminOverturePublicationController.class,PublicApplicationPhotoController.class})
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class OverturePublicationControllerSecurityTest {
    private static final String BASE="/v1/admin/overture-onboarding/publications";
    @Autowired MockMvc mvc;
    @MockitoBean JwtService jwt;
    @MockitoBean OverturePublicationService service;
    @ParameterizedTest @ValueSource(strings={"list","detail","publish","unpublish"})
    void adminEndpointsRequireAuthentication(String endpoint) throws Exception {
        mvc.perform(request(endpoint)).andExpect(status().isUnauthorized()); verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"CUSTOMER","HALL_OWNER","VENDOR"})
    void existingBusinessRolesCannotPublishOrReadReviewHistory(String role) throws Exception {
        for(String endpoint:List.of("list","detail","publish","unpublish")) mvc.perform(request(endpoint).with(user("7").roles(role))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","SUPER_ADMIN"})
    void publicationMetadataAndMutationsAreUncacheable(String role) throws Exception {
        for(String endpoint:List.of("list","detail","publish","unpublish")) mvc.perform(request(endpoint).with(user("7").roles(role)))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(service).publish(eq(55L),any(),any()); verify(service).unpublish(eq(55L),any(),any());
    }
    @Test void publicPhotoAllowsAnonymousGetOnlyAndSendsBoundedCanonicalHeaders() throws Exception {
        when(service.publicPhoto(55,1,1)).thenReturn(new byte[]{1,2,3});
        mvc.perform(get("/v1/halls/55/application-photos/1").param("publicationVersion","1"))
            .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"))
            .andExpect(header().string("Cache-Control","private, no-store"))
            .andExpect(header().string("Content-Length","3"))
            .andExpect(header().string("X-Content-Type-Options","nosniff"))
            .andExpect(header().string("Content-Security-Policy","default-src 'none'; sandbox"));
        mvc.perform(post("/v1/halls/55/application-photos/1").param("publicationVersion","1")).andExpect(status().isUnauthorized());
    }
    @Test void omittedOrMalformedPublicationRevisionNeverInvokesContentService() throws Exception {
        mvc.perform(get("/v1/halls/55/application-photos/1")).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/halls/55/application-photos/1").param("publicationVersion","wrong")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void unpublishedOrOldRevisionReturnsNotFoundNotOriginalObjectLocation() throws Exception {
        when(service.publicPhoto(anyLong(),anyLong(),anyLong())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,"Application venue not found"));
        mvc.perform(get("/v1/halls/55/application-photos/1").param("publicationVersion","1"))
            .andExpect(status().isNotFound()).andExpect(header().doesNotExist("Location"));
    }
    @Test void authorityInjectionAndScalarCoercionAreRejectedBeforePublishing() throws Exception {
        for(String body:List.of("{\"expectedPublicationVersion\":0,\"expectedReviewVersion\":2,\"expectedMediaVersion\":3,\"reason\":\"Clear reason\",\"status\":\"APPROVED\"}",
                "{\"expectedPublicationVersion\":\"0\",\"expectedReviewVersion\":2,\"expectedMediaVersion\":3,\"reason\":\"Clear reason\"}",
                "{\"expectedPublicationVersion\":0,\"expectedReviewVersion\":2,\"expectedMediaVersion\":3,\"reason\":\"Clear reason\"} {}"))
            mvc.perform(post(BASE+"/55/publish").with(user("7").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    private static MockHttpServletRequestBuilder request(String endpoint) {
        return switch(endpoint) {
            case "list"->get(BASE);
            case "detail"->get(BASE+"/55");
            case "publish"->post(BASE+"/55/publish").contentType(MediaType.APPLICATION_JSON).content("{\"expectedPublicationVersion\":0,\"expectedReviewVersion\":2,\"expectedMediaVersion\":3,\"reason\":\"Ready for controlled publication\"}");
            case "unpublish"->post(BASE+"/55/unpublish").contentType(MediaType.APPLICATION_JSON).content("{\"expectedPublicationVersion\":1,\"reason\":\"Withdraw for factual correction\"}");
            default->throw new IllegalArgumentException(endpoint);
        };
    }
}
