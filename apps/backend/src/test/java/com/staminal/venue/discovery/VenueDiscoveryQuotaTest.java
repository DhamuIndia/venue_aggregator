package com.staminal.venue.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class VenueDiscoveryQuotaTest {
    @Mock private JdbcTemplate jdbc;
    @Mock private VenueDiscoveryProperties properties;
    private VenueDiscoveryQuota quota;

    @BeforeEach
    void setUp() {
        quota = new VenueDiscoveryQuota(jdbc, properties);
    }

    @Test
    void reservationUsesSingleAtomicUpsertWithLimitGuard() {
        when(properties.getDailyRequestLimit()).thenReturn(3);
        when(jdbc.update(anyString(), any(LocalDate.class), eq(3))).thenReturn(1);
        LocalDate before = LocalDate.now(ZoneOffset.UTC);

        assertThatCode(quota::reserve).doesNotThrowAnyException();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDate> day = ArgumentCaptor.forClass(LocalDate.class);
        verify(jdbc).update(sql.capture(), day.capture(), eq(3));
        assertThat(sql.getValue()).contains(
                "insert into venue_discovery_api_usage (usage_date, request_count) values (?, 1)",
                "on conflict (usage_date) do update",
                "set request_count = venue_discovery_api_usage.request_count + 1",
                "where venue_discovery_api_usage.request_count < ?");
        assertThat(day.getValue()).isBetween(before, LocalDate.now(ZoneOffset.UTC));
    }

    @Test
    void exhaustedAtomicReservationReturns429() {
        when(properties.getDailyRequestLimit()).thenReturn(3);
        when(jdbc.update(anyString(), any(LocalDate.class), eq(3))).thenReturn(0);
        assertExhausted();
    }

    @Test
    void zeroConfiguredLimitRejectsWithoutWriting() {
        when(properties.getDailyRequestLimit()).thenReturn(0);
        assertExhausted();
        verifyNoInteractions(jdbc);
    }

    @Test
    void negativeConfiguredLimitAlsoFailsClosed() {
        when(properties.getDailyRequestLimit()).thenReturn(-1);
        assertExhausted();
        verifyNoInteractions(jdbc);
    }

    @Test
    void usageIsZeroWhenNoReservationExistsToday() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Integer>>any(), any(LocalDate.class)))
                .thenReturn(List.of());
        assertThat(quota.usedToday()).isZero();
    }

    @Test
    void usageReturnsPersistedCountForUtcDay() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Integer>>any(), any(LocalDate.class)))
                .thenReturn(List.of(3));
        LocalDate before = LocalDate.now(ZoneOffset.UTC);
        assertThat(quota.usedToday()).isEqualTo(3);
        ArgumentCaptor<LocalDate> day = ArgumentCaptor.forClass(LocalDate.class);
        verify(jdbc).query(eq("select request_count from venue_discovery_api_usage where usage_date = ?"),
                org.mockito.ArgumentMatchers.<RowMapper<Integer>>any(), day.capture());
        assertThat(day.getValue()).isBetween(before, LocalDate.now(ZoneOffset.UTC));
    }

    @Test
    void reservationCommitsIndependentlyOfProviderOrSearchFailure() throws Exception {
        Transactional transactional = VenueDiscoveryQuota.class.getMethod("reserve")
                .getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    private void assertExhausted() {
        assertThatThrownBy(quota::reserve).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(exception.getReason()).contains("daily request limit reached", "00:00 UTC");
        });
    }
}
