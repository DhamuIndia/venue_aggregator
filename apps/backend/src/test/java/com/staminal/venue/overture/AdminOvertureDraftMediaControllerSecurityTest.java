package com.staminal.venue.overture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import com.staminal.venue.auth.JwtAuthenticationFilter;
import com.staminal.venue.auth.SecurityConfig;
import com.staminal.venue.auth.service.JwtService;
import com.staminal.venue.overture.OvertureDraftMediaResponse.*;

@WebMvcTest(AdminOvertureDraftMediaController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AdminOvertureDraftMediaControllerSecurityTest {
    private static final String BASE = "/v1/admin/overture-onboarding/drafts/55/media";
    @Autowired MockMvc mvc;
    @MockitoBean JwtService jwt;
    @MockitoBean OvertureDraftMediaService service;
    @ParameterizedTest @ValueSource(strings = {"gallery", "upload", "review", "arrange", "archive", "content"})
    void allEndpointsRequireAuthentication(String endpoint) throws Exception {
        mvc.perform(request(endpoint)).andExpect(status().isUnauthorized()); verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings = {"CUSTOMER", "HALL_OWNER", "VENDOR"})
    void allEndpointsRejectNonAdminRoles(String role) throws Exception {
        for (String endpoint : List.of("gallery", "upload", "review", "arrange", "archive", "content"))
            mvc.perform(request(endpoint).with(user("7").roles(role))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings = {"ADMIN", "SUPER_ADMIN"})
    void authenticatedMetadataAndCanonicalContentAreUncacheable(String role) throws Exception {
        Gallery gallery = new Gallery(0, null, List.of(), new Limits(8388608,4194304,20,67108864,100),0,0);
        when(service.gallery(anyLong(), any())).thenReturn(gallery);
        when(service.upload(anyLong(), any(), any(), any())).thenReturn(gallery);
        when(service.review(anyLong(), anyLong(), any(), any())).thenReturn(gallery);
        when(service.arrange(anyLong(), any(), any())).thenReturn(gallery);
        when(service.archive(anyLong(), anyLong(), any(), any())).thenReturn(gallery);
        when(service.content(anyLong(), anyLong(), any())).thenReturn(new byte[] {1,2,3});
        for (String endpoint : List.of("gallery", "upload", "review", "arrange", "archive"))
            mvc.perform(request(endpoint).with(user("7").roles(role))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(request("content").with(user("7").roles(role))).andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg")).andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"));
    }
    @ParameterizedTest @ValueSource(strings = {"storageKey", "publicUrl", "status", "uploadedBy", "hallId"})
    void multipartMetadataRejectsServerOwnedKeys(String field) throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(OvertureDraftMediaFixtures.upload()); body.put(field, "Injected");
        mvc.perform(upload(body.toString()).with(user("7").roles("ADMIN"))).andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Test void globalOversizedMultipartHandlerIsControlledAndPrivate() throws Exception {
        when(service.upload(anyLong(), any(), any(), any())).thenThrow(new MaxUploadSizeExceededException(8388608));
        mvc.perform(request("upload").with(user("7").roles("ADMIN"))).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.detail").value("Photo upload exceeds the permitted size"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }
    @Test void archiveAndArrangementRejectAuthorityInjectionAndScalarCoercion() throws Exception {
        for (String body : List.of("{\"expectedVersion\":0,\"reason\":\"A clear reason\",\"status\":\"APPROVED\"}",
                "{\"expectedVersion\":\"0\",\"reason\":\"A clear reason\"}", "{\"expectedVersion\":0,\"reason\":\"A clear reason\"} {}"))
            mvc.perform(post(BASE + "/1/archive").with(user("7").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    private static MockHttpServletRequestBuilder request(String endpoint) {
        return switch(endpoint) {
            case "gallery" -> get(BASE);
            case "content" -> get(BASE + "/1/content");
            case "upload" -> upload(OvertureDraftMediaFixtures.json(OvertureDraftMediaFixtures.upload()));
            case "review" -> put(BASE + "/1/review").contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"status\":\"APPROVED\",\"reason\":\"Confirmed usage rights\"}");
            case "arrange" -> put(BASE + "/arrangement").contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"coverMediaId\":null,\"orderedMediaIds\":[]}");
            case "archive" -> post(BASE + "/1/archive").contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"reason\":\"Replacement photo planned\"}");
            default -> throw new IllegalArgumentException(endpoint);
        };
    }
    private static MockHttpServletRequestBuilder upload(String metadata) {
        return multipart(BASE + "/upload").file(new MockMultipartFile("file","photo.png","image/png",new byte[]{1}))
                .file(new MockMultipartFile("metadata","metadata.json","application/json",metadata.getBytes(StandardCharsets.UTF_8)));
    }
}
