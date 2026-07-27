package com.staminal.venue.notifications.preferences;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.staminal.venue.auth.JwtAuthenticationFilter;
import com.staminal.venue.auth.SecurityConfig;
import com.staminal.venue.auth.service.JwtService;

@WebMvcTest(VendorNotificationPreferenceController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class VendorNotificationPreferenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private VendorNotificationPreferenceService preferenceService;

    @Test
    void preferenceEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/v1/vendor/notification-preferences"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "301", roles = "CUSTOMER")
    void customerCannotAccessVendorPreferences() throws Exception {
        mockMvc.perform(get("/v1/vendor/notification-preferences"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "301", roles = "VENDOR")
    void vendorCanReadOwnPreferences() throws Exception {
        when(preferenceService.getMyPreferences(any(Authentication.class)))
                .thenReturn(new VendorNotificationPreferenceResponse(
                        501L,
                        false,
                        false,
                        false,
                        "9884012346",
                        null,
                        null,
                        null,
                        null,
                        null));

        mockMvc.perform(get("/v1/vendor/notification-preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendorId").value(501))
                .andExpect(jsonPath("$.whatsAppLeadNotificationsEnabled").value(false));
    }

    @Test
    @WithMockUser(username = "301", roles = "VENDOR")
    void updateRequiresExplicitEnabledAndPausedSelections() throws Exception {
        mockMvc.perform(put("/v1/vendor/notification-preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "whatsAppNumber": "9884012346",
                                  "consentConfirmed": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.whatsAppLeadNotificationsEnabled").exists())
                .andExpect(jsonPath("$.errors.whatsAppLeadNotificationsPaused").exists());
    }
}
