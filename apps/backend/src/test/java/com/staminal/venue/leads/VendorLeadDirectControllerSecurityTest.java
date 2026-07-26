package com.staminal.venue.leads;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.staminal.venue.auth.JwtAuthenticationFilter;
import com.staminal.venue.auth.SecurityConfig;
import com.staminal.venue.auth.service.JwtService;
import com.staminal.venue.leads.Dto.VendorLeadResponse;

@WebMvcTest(VendorLeadController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class VendorLeadDirectControllerSecurityTest {

    private static final String REFERENCE = "LEAD-0123456789ABCDEF0123";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private VendorLeadService vendorLeadService;

    @Test
    void directLeadEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/v1/vendor/leads/reference/{reference}", REFERENCE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "101", roles = "CUSTOMER")
    void customerCannotOpenVendorDirectLeadEndpoint() throws Exception {
        mockMvc.perform(get("/v1/vendor/leads/reference/{reference}", REFERENCE))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "301", roles = "VENDOR")
    void vendorCanReachOwnedLeadLookup() throws Exception {
        VendorLeadResponse response = new VendorLeadResponse();
        response.setId(901L);
        response.setLeadReference(REFERENCE);
        when(vendorLeadService.getLeadByReference(
                eq(REFERENCE),
                any(Authentication.class)))
                .thenReturn(response);

        mockMvc.perform(get("/v1/vendor/leads/reference/{reference}", REFERENCE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(901))
                .andExpect(jsonPath("$.leadReference").value(REFERENCE));
    }
}
