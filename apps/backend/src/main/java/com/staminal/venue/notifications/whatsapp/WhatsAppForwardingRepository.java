package com.staminal.venue.notifications.whatsapp;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class WhatsAppForwardingRepository {
    private final JdbcTemplate jdbc;

    public void enqueue(String id, String payload) {
        jdbc.update("INSERT INTO whatsapp_webhook_forwards (id, payload) VALUES (?, ?) ON CONFLICT (id) DO NOTHING", id, payload);
    }

    public List<Pending> claim() {
        return jdbc.query("SELECT id, payload, attempts FROM whatsapp_webhook_forwards WHERE delivered_at IS NULL AND next_attempt_at <= now() ORDER BY next_attempt_at LIMIT 1 FOR UPDATE SKIP LOCKED",
                (rs, row) -> new Pending(rs.getString(1), rs.getString(2), rs.getInt(3)));
    }

    public void delivered(String id) {
        // Retain only the digest for seven days to deduplicate Meta retries.
        jdbc.update("UPDATE whatsapp_webhook_forwards SET delivered_at=now(), payload=NULL, attempts=attempts+1, last_failure=NULL WHERE id=?", id);
    }

    public void retry(String id, int attempts, String failure) {
        long seconds = Math.min(3600L, 15L * (1L << Math.min(attempts, 8)));
        jdbc.update("UPDATE whatsapp_webhook_forwards SET attempts=attempts+1, last_failure=?, next_attempt_at=now() + (? * interval '1 second') WHERE id=?", failure, seconds, id);
    }

    public void prune() {
        jdbc.update("DELETE FROM whatsapp_webhook_forwards WHERE delivered_at < now() - interval '7 days'");
    }

    public record Pending(String id, String payload, int attempts) {}
}
