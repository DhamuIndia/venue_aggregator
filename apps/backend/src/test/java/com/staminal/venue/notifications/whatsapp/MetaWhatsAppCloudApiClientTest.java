package com.staminal.venue.notifications.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MetaWhatsAppCloudApiClientTest {

    @Test
    void sendsOfficialTemplateContractAndReturnsMetaMessageId() {
        WhatsAppCloudApiProperties properties = WhatsAppCloudApiPropertiesTest.readyProperties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MetaWhatsAppCloudApiClient client = new MetaWhatsAppCloudApiClient(properties, builder);

        server.expect(once(), requestTo("https://graph.facebook.com/v99.0/1234567890/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-system-user-token"))
                .andExpect(jsonPath("$.messaging_product").value("whatsapp"))
                .andExpect(jsonPath("$.recipient_type").value("individual"))
                .andExpect(jsonPath("$.to").value("919884012346"))
                .andExpect(jsonPath("$.type").value("template"))
                .andExpect(jsonPath("$.template.name").value("new_matching_lead_v1"))
                .andExpect(jsonPath("$.template.language.code").value("en"))
                .andExpect(jsonPath("$.template.components.length()").value(1))
                .andExpect(jsonPath("$.template.components[0].type").value("body"))
                .andExpect(jsonPath("$.template.components[0].parameters[0].text")
                        .value("Saffron Leaf Catering"))
                .andExpect(jsonPath("$.template.components[0].parameters[1].text")
                        .value("Photography"))
                .andExpect(jsonPath("$.template.components[0].parameters[2].text")
                        .value("12 September 2026"))
                .andExpect(jsonPath("$.template.components[0].parameters[3].text")
                        .value("Adyar, Chennai"))
                .andExpect(jsonPath("$.template.components[0].parameters[4].text")
                        .value("₹75,000–₹1,50,000"))
                .andRespond(withSuccess(
                        """
                                {
                                  "messaging_product": "whatsapp",
                                  "messages": [
                                    {"id": "wamid.test-message-123"}
                                  ]
                                }
                                """,
                        MediaType.APPLICATION_JSON));

        WhatsAppCloudApiSendResult result = client.sendTemplate(message());

        assertThat(result.messageId()).isEqualTo("wamid.test-message-123");
        server.verify();
    }

    @Test
    void wrapsMetaHttpFailureWithoutReturningFalseSuccess() {
        WhatsAppCloudApiProperties properties = WhatsAppCloudApiPropertiesTest.readyProperties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MetaWhatsAppCloudApiClient client = new MetaWhatsAppCloudApiClient(properties, builder);
        server.expect(requestTo("https://graph.facebook.com/v99.0/1234567890/messages"))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> client.sendTemplate(message()))
                .isInstanceOf(WhatsAppCloudApiException.class)
                .hasMessage("Meta WhatsApp template submission failed");
        server.verify();
    }

    private WhatsAppTemplateMessage message() {
        return new WhatsAppTemplateMessage(
                "+919884012346",
                "new_matching_lead_v1",
                "en",
                List.of(
                        "Saffron Leaf Catering",
                        "Photography",
                        "12 September 2026",
                        "Adyar, Chennai",
                        "₹75,000–₹1,50,000"));
    }
}
