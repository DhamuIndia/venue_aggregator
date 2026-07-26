package com.staminal.venue.admin.notifications;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.staminal.venue.admin.notifications.AdminLeadNotificationMonitoringResponse.RequirementList;
import com.staminal.venue.auth.JwtAuthenticationFilter;
import com.staminal.venue.auth.SecurityConfig;
import com.staminal.venue.auth.service.JwtService;

@WebMvcTest(AdminLeadNotificationMonitoringController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AdminLeadNotificationMonitoringControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AdminLeadNotificationMonitoringService monitoringService;

    @Test
    void monitoringRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/v1/admin/requirements/notification-monitoring"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "501", roles = "VENDOR")
    void vendorCannotReadAdminMonitoring() throws Exception {
        mockMvc.perform(get("/v1/admin/requirements/notification-monitoring"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "301", roles = "ADMIN")
    void adminCanReadMonitoring() throws Exception {
        when(monitoringService.getRequirements(any(Authentication.class)))
                .thenReturn(new RequirementList(List.of(), false, 3, List.of()));

        mockMvc.perform(get("/v1/admin/requirements/notification-monitoring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sendingEnabled").value(false))
                .andExpect(jsonPath("$.maxAttempts").value(3))
                .andExpect(jsonPath("$.rolloutAllowedVendorIds").isEmpty());
    }

    @Test
    @WithMockUser(username = "501", roles = "VENDOR")
    void vendorCannotScheduleManualRetry() throws Exception {
        mockMvc.perform(post("/v1/admin/notification-jobs/601/retry"))
                .andExpect(status().isForbidden());
    }
}
