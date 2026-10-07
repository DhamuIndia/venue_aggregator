package com.staminal.venue.discovery;

import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VenueDiscoveryQuota {
    private final JdbcTemplate jdbc;
    private final VenueDiscoveryProperties properties;

    // A committed reservation counts even if Google fails; never retry automatically.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve() {
        int limit = properties.getDailyRequestLimit();
        if (limit <= 0) {
            throw exhausted();
        }
        int changed = jdbc.update("""
                insert into venue_discovery_api_usage (usage_date, request_count) values (?, 1)
                on conflict (usage_date) do update
                set request_count = venue_discovery_api_usage.request_count + 1
                where venue_discovery_api_usage.request_count < ?
                """, LocalDate.now(ZoneOffset.UTC), limit);
        if (changed != 1) {
            throw exhausted();
        }
    }

    @Transactional(readOnly = true)
    public int usedToday() {
        return jdbc.query("select request_count from venue_discovery_api_usage where usage_date = ?",
                (rs, row) -> rs.getInt(1), LocalDate.now(ZoneOffset.UTC)).stream().findFirst().orElse(0);
    }

    private ResponseStatusException exhausted() {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Venue discovery daily request limit reached; resets at 00:00 UTC");
    }
}
