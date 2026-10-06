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
import com.staminal.venue.auth.JwtAuthenticationFilter;
import com.staminal.venue.auth.SecurityConfig;
import com.staminal.venue.auth.service.JwtService;
import com.staminal.venue.overture.OvertureDraftMediaResponse.*;

@WebMvcTest(AdminOverturePhotoCreditController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class AdminOverturePhotoCreditControllerSecurityTest {
    static final String BASE="/v1/admin/overture-onboarding/drafts/55/media/1/credits";
    @Autowired MockMvc mvc;
    @MockitoBean JwtService jwt;
    @MockitoBean OverturePhotoCreditService service;
    @Test void saveAndReviewRequireAuthentication() throws Exception {
        mvc.perform(put(BASE).contentType(MediaType.APPLICATION_JSON).content(OverturePhotoCreditRequestTest.SAVE)).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE+"/review").contentType(MediaType.APPLICATION_JSON).content(OverturePhotoCreditRequestTest.REVIEW)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"CUSTOMER","HALL_OWNER","VENDOR"})
    void nonAdminRolesCannotWritePublicCredits(String role) throws Exception {
        mvc.perform(put(BASE).with(user("7").roles(role)).contentType(MediaType.APPLICATION_JSON).content(OverturePhotoCreditRequestTest.SAVE)).andExpect(status().isForbidden());
        mvc.perform(post(BASE+"/review").with(user("7").roles(role)).contentType(MediaType.APPLICATION_JSON).content(OverturePhotoCreditRequestTest.REVIEW)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","SUPER_ADMIN"})
    void galleryResultIsPrivateAndNoStore(String role) throws Exception {
        Gallery gallery=new Gallery(1,null,List.of(),new Limits(8388608,4194304,20,67108864,100),0,0);
        when(service.save(eq(55L),eq(1L),any(),any())).thenReturn(gallery);
        when(service.review(eq(55L),eq(1L),any(),any())).thenReturn(gallery);
        mvc.perform(put(BASE).with(user("7").roles(role)).contentType(MediaType.APPLICATION_JSON).content(OverturePhotoCreditRequestTest.SAVE))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(post(BASE+"/review").with(user("7").roles(role)).contentType(MediaType.APPLICATION_JSON).content(OverturePhotoCreditRequestTest.REVIEW))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void serverOwnedLicenseUrlsAndStatusCannotBeInjected() throws Exception {
        for(String json:List.of("null",OverturePhotoCreditRequestTest.SAVE+" {}",OverturePhotoCreditRequestTest.SAVE.replace("}",",\"licenseUrl\":\"https://evil.example\"}")))
            mvc.perform(put(BASE).with(user("7").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isBadRequest());
        mvc.perform(post(BASE+"/review").with(user("7").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content(OverturePhotoCreditRequestTest.REVIEW.replace("\"attributionConfirmed\":true","\"attributionConfirmed\":false"))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
