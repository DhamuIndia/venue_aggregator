package com.staminal.venue.notifications.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookProcessorTest {

    @Mock
    private WhatsAppDeliveryStateService deliveryStateService;

    private WhatsAppCloudApiProperties properties;
    private WhatsAppWebhookProcessor processor;

    @BeforeEach
    void setUp() {
        properties = new WhatsAppCloudApiProperties();
        properties.setPhoneNumberId("1234567890");
        processor = new WhatsAppWebhookProcessor(
                new ObjectMapper(),
                properties,
                new WhatsAppFailureClassifier(),
                deliveryStateService);
    }

    @Test
    void extractsFailureReasonAndTemporaryClassificationFromMetaStatus() throws Exception {
        when(deliveryStateService.apply(
                eq("wamid.failure-1"),
                eq(MetaMessageDeliveryStatus.FAILED),
                eq(Instant.ofEpochSecond(1785052800L)),
                eq(new MetaWebhookFailure(
                        130429,
                        "Rate limit reached",
                        "Cloud API throughput reached",
                        true))))
                .thenReturn(true);

        WhatsAppWebhookProcessingResult result = processor.process(json("""
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "changes": [{
                      "field": "messages",
                      "value": {
                        "metadata": {"phone_number_id": "1234567890"},
                        "statuses": [{
                          "id": "wamid.failure-1",
                          "status": "failed",
                          "timestamp": "1785052800",
                          "errors": [{
                            "code": 130429,
                            "title": "Rate limit reached",
                            "message": "Message failed",
                            "error_data": {
                              "details": "Cloud API throughput reached"
                            }
                          }]
                        }]
                      }
                    }]
                  }]
                }
                """));

        assertThat(result).isEqualTo(new WhatsAppWebhookProcessingResult(1, 1, 0));
    }

    @Test
    void parsesAllSupportedStatusesAndIgnoresUnknownOnes() throws Exception {
        when(deliveryStateService.apply(
                "wamid.sent-1",
                MetaMessageDeliveryStatus.SENT,
                Instant.ofEpochSecond(1785052800L),
                null))
                .thenReturn(true);

        WhatsAppWebhookProcessingResult result = processor.process(json("""
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "changes": [{
                      "field": "messages",
                      "value": {
                        "metadata": {"phone_number_id": "1234567890"},
                        "statuses": [
                          {
                            "id": "wamid.sent-1",
                            "status": "sent",
                            "timestamp": "1785052800"
                          },
                          {
                            "id": "wamid.deleted-1",
                            "status": "deleted",
                            "timestamp": "1785052801"
                          }
                        ]
                      }
                    }]
                  }]
                }
                """));

        assertThat(result).isEqualTo(new WhatsAppWebhookProcessingResult(2, 1, 1));
        verify(deliveryStateService).apply(
                "wamid.sent-1",
                MetaMessageDeliveryStatus.SENT,
                Instant.ofEpochSecond(1785052800L),
                null);
    }

    @Test
    void ignoresPayloadForAnotherConfiguredPhoneNumber() throws Exception {
        WhatsAppWebhookProcessingResult result = processor.process(json("""
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "changes": [{
                      "field": "messages",
                      "value": {
                        "metadata": {"phone_number_id": "9999999999"},
                        "statuses": [{
                          "id": "wamid.other",
                          "status": "sent"
                        }]
                      }
                    }]
                  }]
                }
                """));

        assertThat(result).isEqualTo(new WhatsAppWebhookProcessingResult(0, 0, 0));
        verifyNoInteractions(deliveryStateService);
    }

    private byte[] json(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
