package com.staminal.venue.notifications.whatsapp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WhatsAppWebhookForwarderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final WhatsAppForwardingRepository repository = mock(WhatsAppForwardingRepository.class);
    private WhatsAppWebhookForwarder forwarder(boolean enabled) {
        return new WhatsAppWebhookForwarder(mapper, new WhatsAppForwardingProperties(enabled,
                URI.create("https://crm.staminal.in/webhooks/notifications/whatsapp"), "sender"), repository);
    }
    private byte[] payload(String phone, String fields) {
        return ("{\"object\":\"whatsapp_business_account\",\"entry\":[{\"id\":\"waba\",\"changes\":[{\"field\":\"messages\",\"value\":{\"metadata\":{\"phone_number_id\":\""
                + phone + "\"}," + fields + "}}]}]}").getBytes(StandardCharsets.UTF_8);
    }
    @Test void excludesOtherSendersAndDisabledForwarding() throws Exception {
        byte[] body = payload("other", "\"statuses\":[{\"id\":\"m1\",\"recipient_id\":\"123\",\"status\":\"delivered\"}]");
        forwarder(true).enqueue(body);
        forwarder(false).enqueue(payload("sender", "\"statuses\":[]"));
        verifyNoInteractions(repository);
    }
    @Test void onlyForwardsReceiptFieldsAndProducesStableDeduplicationKey() throws Exception {
        byte[] body = payload("sender", "\"contacts\":[{\"profile\":{\"name\":\"private\"}}],\"statuses\":[{\"id\":\"m1\",\"recipient_id\":\"123\",\"status\":\"delivered\",\"biz_opaque_callback_data\":\"delivery-id\",\"pricing\":{\"billable\":true}}]");
        forwarder(true).enqueue(body);
        forwarder(true).enqueue(body);
        var ids = ArgumentCaptor.forClass(String.class);
        var bodies = ArgumentCaptor.forClass(String.class);
        verify(repository, times(2)).enqueue(ids.capture(), bodies.capture());
        assertEquals(ids.getAllValues().get(0), ids.getAllValues().get(1));
        assertEquals(64, ids.getValue().length());
        assertFalse(bodies.getValue().contains("private"));
        assertFalse(bodies.getValue().contains("pricing"));
        assertTrue(bodies.getValue().contains("delivery-id"));
        assertEquals("sender", mapper.readTree(bodies.getValue()).at("/entry/0/changes/0/value/metadata/phone_number_id").asText());
    }
    @Test void forwardsOnlyOptOutMessages() throws Exception {
        forwarder(true).enqueue(payload("sender", "\"messages\":[{\"id\":\"a\",\"from\":\"123\",\"type\":\"text\",\"text\":{\"body\":\" stop \"}},{\"id\":\"b\",\"from\":\"123\",\"type\":\"text\",\"text\":{\"body\":\"private enquiry\"}}]"));
        var body = ArgumentCaptor.forClass(String.class);
        verify(repository).enqueue(anyString(), body.capture());
        assertTrue(body.getValue().contains("STOP"));
        assertFalse(body.getValue().contains("private enquiry"));
    }
    @Test void signingMatchesExistingMetaVerifier() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String signature = WhatsAppForwardingWorker.signature(body, "secret");
        assertTrue(new WhatsAppWebhookSignatureVerifier().isValid(body, signature, "secret"));
        assertFalse(new WhatsAppWebhookSignatureVerifier().isValid("different".getBytes(), signature, "secret"));
    }
    @Test void requiresHttpsAndSenderWhenEnabled() {
        assertThrows(IllegalStateException.class, () -> new WhatsAppForwardingProperties(true, URI.create("http://localhost/"), "sender"));
        assertThrows(IllegalStateException.class, () -> new WhatsAppForwardingProperties(true, URI.create("https://example.com/"), ""));
    }
}
