package com.staminal.venue.notifications.whatsapp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.staminal.venue.auth.JwtAuthenticationFilter;
import com.staminal.venue.auth.SecurityConfig;
import com.staminal.venue.auth.service.JwtService;

@WebMvcTest(WhatsAppWebhookController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class WhatsAppWebhookControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private WhatsAppCloudApiProperties properties;

    @MockitoBean
    private WhatsAppWebhookSignatureVerifier signatureVerifier;

    @MockitoBean
    private WhatsAppWebhookProcessor webhookProcessor;

    @Test
    void metaCanVerifyPublicCallbackWithoutVenueMartLogin() throws Exception {
        when(properties.isWebhookEnabled()).thenReturn(true);
        when(properties.getWebhookVerifyToken()).thenReturn("verify-me");

        mockMvc.perform(get("/v1/integrations/meta/whatsapp/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "verify-me")
                        .param("hub.challenge", "123456"))
                .andExpect(status().isOk())
                .andExpect(content().string("123456"));
    }

    @Test
    void validSignedNotificationIsAcceptedWithoutVenueMartLogin() throws Exception {
        when(properties.isWebhookEnabled()).thenReturn(true);
        when(properties.getAppSecret()).thenReturn("app-secret");
        when(signatureVerifier.isValid(any(byte[].class), eq("sha256=valid"), eq("app-secret")))
                .thenReturn(true);

        mockMvc.perform(post("/v1/integrations/meta/whatsapp/webhook")
                        .header("X-Hub-Signature-256", "sha256=valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"object\":\"whatsapp_business_account\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("EVENT_RECEIVED"));
    }

    @Test
    void unsignedNotificationIsRejected() throws Exception {
        when(properties.isWebhookEnabled()).thenReturn(true);
        when(properties.getAppSecret()).thenReturn("app-secret");

        mockMvc.perform(post("/v1/integrations/meta/whatsapp/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
