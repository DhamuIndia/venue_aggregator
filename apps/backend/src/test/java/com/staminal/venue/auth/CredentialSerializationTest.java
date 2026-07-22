package com.staminal.venue.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staminal.venue.admin.Admin;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.vendors.Entity.Vendors;

class CredentialSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void passwordHashesAreNeverSerialized() throws Exception {
        Admin admin = new Admin();
        admin.setPasswordHash("admin-hash");

        User user = new User();
        user.setPasswordHash("user-hash");

        Vendors vendor = new Vendors();
        vendor.setPasswordHash("vendor-hash");

        assertThat(objectMapper.writeValueAsString(admin)).doesNotContain("passwordHash", "admin-hash");
        assertThat(objectMapper.writeValueAsString(user)).doesNotContain("passwordHash", "user-hash");
        assertThat(objectMapper.writeValueAsString(vendor)).doesNotContain("passwordHash", "vendor-hash");
    }
}
