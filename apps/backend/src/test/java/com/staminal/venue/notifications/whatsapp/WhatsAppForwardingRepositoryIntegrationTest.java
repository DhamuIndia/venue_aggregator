package com.staminal.venue.notifications.whatsapp;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "LEADCAT_FORWARD_TEST_DB", matches = ".+")
class WhatsAppForwardingRepositoryIntegrationTest {
    @Test void durableDedupeClaimRetryAndRedactionAgainstPostgres() throws Exception {
        var ds = new DriverManagerDataSource(System.getenv("LEADCAT_FORWARD_TEST_DB"), "test", "test");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("DROP TABLE IF EXISTS whatsapp_webhook_forwards");
        try (var migration = getClass().getResourceAsStream("/db/migration/V38__whatsapp_webhook_forwarding.sql")) {
            jdbc.execute(new String(migration.readAllBytes(), StandardCharsets.UTF_8));
        }
        var repository = new WhatsAppForwardingRepository(jdbc);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        repository.enqueue("digest", "minimal payload");
        repository.enqueue("digest", "duplicate");
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM whatsapp_webhook_forwards", Integer.class));
        tx.executeWithoutResult(state -> {
            assertEquals("minimal payload", repository.claim().getFirst().payload());
            // An independent worker must not claim an already-locked event.
            try (var connection = ds.getConnection(); var statement = connection.createStatement()) {
                connection.setAutoCommit(false);
                try (var rows = statement.executeQuery("SELECT id FROM whatsapp_webhook_forwards FOR UPDATE SKIP LOCKED")) {
                    assertFalse(rows.next());
                }
                connection.rollback();
            } catch (Exception exception) { throw new RuntimeException(exception); }
            repository.retry("digest", 0, "HTTP_503");
        });
        assertTrue(repository.claim().isEmpty());
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM whatsapp_webhook_forwards", Integer.class));
        jdbc.execute("UPDATE whatsapp_webhook_forwards SET next_attempt_at=now()");
        tx.executeWithoutResult(state -> {
            assertEquals(1, repository.claim().size());
            repository.delivered("digest");
        });
        assertNull(jdbc.queryForObject("SELECT payload FROM whatsapp_webhook_forwards", String.class));
        assertTrue(repository.claim().isEmpty());
        repository.enqueue("digest", "duplicate retry after success");
        assertNull(jdbc.queryForObject("SELECT payload FROM whatsapp_webhook_forwards", String.class));
        repository.prune();
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM whatsapp_webhook_forwards", Integer.class));
        jdbc.execute("UPDATE whatsapp_webhook_forwards SET delivered_at=now()-interval '8 days'");
        repository.prune();
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM whatsapp_webhook_forwards", Integer.class));
    }
}
