package com.staminal.venue.notifications.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

class MetaWhatsAppCloudApiClientTest {

    @Test
    void sendsOfficialTemplateContractAndReturnsMetaMessageId() {
        WhatsAppCloudApiProperties properties = WhatsAppCloudApiPropertiesTest.readyProperties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MetaWhatsAppCloudApiClient client = new MetaWhatsAppCloudApiClient(
                properties,
                builder,
                new ObjectMapper(),
                new WhatsAppFailureClassifier());

        server.expect(once(), requestTo("https://graph.facebook.com/v99.0/1234567890/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-system-user-token"))
                .andExpect(jsonPath("$.messaging_product").value("whatsapp"))
                .andExpect(jsonPath("$.recipient_type").value("individual"))
                .andExpect(jsonPath("$.to").value("919884012346"))
                .andExpect(jsonPath("$.type").value("template"))
                .andExpect(jsonPath("$.template.name").value("new_matching_lead_v1"))
                .andExpect(jsonPath("$.template.language.code").value("en"))
                .andExpect(jsonPath("$.template.components.length()").value(2))
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
                .andExpect(jsonPath("$.template.components[1].type").value("button"))
                .andExpect(jsonPath("$.template.components[1].sub_type").value("url"))
                .andExpect(jsonPath("$.template.components[1].index").value("0"))
                .andExpect(jsonPath("$.template.components[1].parameters[0].type").value("text"))
                .andExpect(jsonPath("$.template.components[1].parameters[0].text")
                        .value("LEAD-0123456789ABCDEF0123"))
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
    void preservesStructuredTemporaryMetaFailureForSafeRetry() {
        WhatsAppCloudApiProperties properties = WhatsAppCloudApiPropertiesTest.readyProperties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MetaWhatsAppCloudApiClient client = new MetaWhatsAppCloudApiClient(
                properties,
                builder,
                new ObjectMapper(),
                new WhatsAppFailureClassifier());
        server.expect(requestTo("https://graph.facebook.com/v99.0/1234567890/messages"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "error": {
                                    "code": 130429,
                                    "type": "OAuthException",
                                    "message": "Rate limit reached",
                                    "is_transient": true,
                                    "error_data": {
                                      "details": "Cloud API throughput reached"
                                    }
                                  }
                                }
                                """));

        assertThatThrownBy(() -> client.sendTemplate(message()))
                .isInstanceOf(WhatsAppCloudApiException.class)
                .satisfies(exception -> {
                    WhatsAppCloudApiException apiException =
                            (WhatsAppCloudApiException) exception;
                    assertThat(apiException.getProviderCode()).isEqualTo(130429);
                    assertThat(apiException.isTemporary()).isTrue();
                    assertThat(apiException.isRetrySafe()).isTrue();
                    assertThat(apiException).hasMessage("Cloud API throughput reached");
                });
        server.verify();
    }

    @Test
    void unstructuredHttpFailureIsAmbiguousAndNotRetrySafe() {
        WhatsAppCloudApiProperties properties = WhatsAppCloudApiPropertiesTest.readyProperties();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MetaWhatsAppCloudApiClient client = new MetaWhatsAppCloudApiClient(
                properties,
                builder,
                new ObjectMapper(),
                new WhatsAppFailureClassifier());
        server.expect(requestTo("https://graph.facebook.com/v99.0/1234567890/messages"))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> client.sendTemplate(message()))
                .isInstanceOf(WhatsAppCloudApiException.class)
                .satisfies(exception -> {
                    WhatsAppCloudApiException apiException =
                            (WhatsAppCloudApiException) exception;
                    assertThat(apiException.isTemporary()).isFalse();
                    assertThat(apiException.isRetrySafe()).isFalse();
                });
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
                        "₹75,000–₹1,50,000"),
                "LEAD-0123456789ABCDEF0123");
    }
}
