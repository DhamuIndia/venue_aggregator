package com.staminal.venue.notifications.whatsapp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WhatsAppWebhookForwarder {
    private final ObjectMapper mapper;
    private final WhatsAppForwardingProperties config;
    private final WhatsAppForwardingRepository repository;

    // Called only after Meta's signature and the existing webhook processor succeed.
    @Transactional
    public void enqueue(byte[] payload) throws IOException {
        if (!config.enabled()) return;
        JsonNode root = mapper.readTree(payload);
        if (root == null || !"whatsapp_business_account".equals(root.path("object").asText())) return;
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                JsonNode value = change.path("value");
                if (!"messages".equals(change.path("field").asText())
                        || !config.phoneNumberId().equals(value.path("metadata").path("phone_number_id").asText())) continue;
                for (JsonNode status : value.path("statuses")) {
                    if (status.path("id").asText().isBlank() || status.path("recipient_id").asText().isBlank()
                            || !Set.of("sent", "delivered", "read", "failed").contains(status.path("status").asText())) continue;
                    ObjectNode receipt = mapper.createObjectNode();
                    for (String field : new String[]{"id", "recipient_id", "status", "timestamp", "biz_opaque_callback_data"}) {
                        if (status.has(field)) receipt.set(field, status.get(field));
                    }
                    // Failure codes suffice for LeadCat; omit provider free text and pricing/contact data.
                    if (status.path("errors").isArray()) {
                        var errors = receipt.putArray("errors");
                        for (JsonNode error : status.path("errors")) {
                            if (error.has("code")) errors.addObject().set("code", error.get("code"));
                        }
                    }
                    save(entry.path("id").asText(), "statuses", receipt);
                }
                for (JsonNode message : value.path("messages")) {
                    String command = message.path("text").path("body").asText().trim().toUpperCase(Locale.ROOT);
                    if (!"text".equals(message.path("type").asText())
                            || !Set.of("STOP", "UNSUBSCRIBE", "CANCEL").contains(command)
                            || message.path("from").asText().isBlank()) continue;
                    ObjectNode stop = mapper.createObjectNode();
                    for (String field : new String[]{"id", "from", "timestamp"}) {
                        if (message.has(field)) stop.set(field, message.get(field));
                    }
                    stop.put("type", "text").putObject("text").put("body", command);
                    save(entry.path("id").asText(), "messages", stop);
                }
            }
        }
    }

    private void save(String account, String field, JsonNode event) throws IOException {
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("object", "whatsapp_business_account");
        ObjectNode entry = envelope.putArray("entry").addObject();
        entry.put("id", account);
        ObjectNode change = entry.putArray("changes").addObject();
        change.put("field", "messages");
        ObjectNode value = change.putObject("value");
        value.putObject("metadata").put("phone_number_id", config.phoneNumberId());
        value.putArray(field).add(event);
        String body = mapper.writeValueAsString(envelope);
        try {
            String id = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(body.getBytes(StandardCharsets.UTF_8)));
            repository.enqueue(id, body);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }
}
