package com.staminal.venue.notifications.whatsapp;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class WhatsAppForwardingWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(WhatsAppForwardingWorker.class);
    private final WhatsAppForwardingProperties config;
    private final WhatsAppCloudApiProperties whatsapp;
    private final WhatsAppForwardingRepository repository;
    private final TransactionTemplate transaction;
    private final HttpClient client;

    @org.springframework.beans.factory.annotation.Autowired
    public WhatsAppForwardingWorker(WhatsAppForwardingProperties config, WhatsAppCloudApiProperties whatsapp,
            WhatsAppForwardingRepository repository, TransactionTemplate transaction) {
        this(config, whatsapp, repository, transaction, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build());
    }

    WhatsAppForwardingWorker(WhatsAppForwardingProperties config, WhatsAppCloudApiProperties whatsapp,
            WhatsAppForwardingRepository repository, TransactionTemplate transaction, HttpClient client) {
        this.client = client;
        this.config = config;
        this.whatsapp = whatsapp;
        this.repository = repository;
        this.transaction = transaction;
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 10000)
    public void forward() {
        if (!config.enabled()) return;
        try {
            // One bounded HTTP call per transaction. SKIP LOCKED supports multiple instances;
            // a crash releases the row and retries safely against LeadCat's idempotent receiver.
            transaction.executeWithoutResult(tx -> {
                for (var pending : repository.claim()) {
                    String failure;
                    try {
                        byte[] body = pending.payload().getBytes(StandardCharsets.UTF_8);
                        HttpRequest request = HttpRequest.newBuilder(config.endpoint()).timeout(Duration.ofSeconds(5))
                                .header("Content-Type", "application/json")
                                .header("X-Hub-Signature-256", signature(body, whatsapp.getAppSecret()))
                                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
                        int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
                        if (status >= 200 && status < 300) {
                            repository.delivered(pending.id());
                            return;
                        }
                        failure = "HTTP_" + status;
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        failure = "INTERRUPTED";
                    } catch (Exception exception) {
                        failure = "TRANSPORT_OR_SIGNING_ERROR";
                    }
                    repository.retry(pending.id(), pending.attempts(), failure);
                    LOGGER.warn("WhatsApp shared forwarding retry scheduled: reason={}, attempt={}", failure, pending.attempts() + 1);
                }
            });
        } catch (RuntimeException exception) {
            // Never log payloads, phone numbers, secrets, URLs or response bodies.
            LOGGER.error("WhatsApp shared forwarding queue unavailable");
        }
    }

    @Scheduled(fixedDelay = 3600000, initialDelay = 60000)
    public void prune() {
        if (config.enabled()) repository.prune();
    }

    static String signature(byte[] body, String secret) throws java.security.GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }
}
