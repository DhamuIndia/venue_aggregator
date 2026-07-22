package com.staminal.venue.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.staminal.venue.admin.AdminController;
import com.staminal.venue.admin.AdminService;
import com.staminal.venue.auth.service.JwtService;
import com.staminal.venue.halls.Controller.HallController;
import com.staminal.venue.halls.Service.HallsService;
import com.staminal.venue.users.UserController;
import com.staminal.venue.users.UserService;
import com.staminal.venue.vendors.Hall.VendorHallController;
import com.staminal.venue.vendors.Hall.VendorHallService;

@WebMvcTest(controllers = {
        AdminController.class,
        UserController.class,
        VendorHallController.class,
        HallController.class
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private VendorHallService vendorHallService;

    @MockitoBean
    private HallsService hallsService;

    @Test
    void publicMarketplaceReadRemainsPublic() throws Exception {
        mockMvc.perform(get("/v1/public/halls"))
                .andExpect(status().isOk());
    }

    @Test
    void legacyAdminListRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyAdminCreationRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyVendorMutationRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/vendor-hall"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void ordinaryUserCannotReadLegacyUserEntities() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanUseProtectedLegacyAdminRoutes() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/users"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "VENDOR")
    void vendorCanUseProtectedLegacyVendorRoutes() throws Exception {
        mockMvc.perform(post("/vendor-hall")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }
}
