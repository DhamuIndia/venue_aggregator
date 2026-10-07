package com.staminal.venue.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class VenueDiscoveryPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(VenueDiscoveryProperties.class);

    @Test
    void defaultsDisableBothFeatureAndLiveGoogleRequests() {
        contextRunner.run(context -> {
            VenueDiscoveryProperties properties = context.getBean(VenueDiscoveryProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.isLiveApiEnabled()).isFalse();
            assertThat(properties.isReady()).isFalse();
            assertThat(properties.getGoogleApiKey()).isEmpty();
            assertThat(properties.getMaxResults()).isEqualTo(10);
            assertThat(properties.getDailyRequestLimit()).isEqualTo(100);
            assertThat(properties.getTimeoutSeconds()).isEqualTo(10);
        });
    }

    @Test
    void configuredFlagsLimitsAndTrimmedKeyBind() {
        contextRunner.withPropertyValues(
                "app.features.venue-discovery-enabled=true",
                "app.venue-discovery.live-api-enabled=true",
                "app.venue-discovery.google-api-key= test-only-key ",
                "app.venue-discovery.max-results=6",
                "app.venue-discovery.daily-request-limit=45",
                "app.venue-discovery.timeout-seconds=4").run(context -> {
                    VenueDiscoveryProperties properties = context.getBean(VenueDiscoveryProperties.class);
                    assertThat(properties.isReady()).isTrue();
                    assertThat(properties.getGoogleApiKey()).isEqualTo("test-only-key");
                    assertThat(properties.getMaxResults()).isEqualTo(6);
                    assertThat(properties.getDailyRequestLimit()).isEqualTo(45);
                    assertThat(properties.getTimeoutSeconds()).isEqualTo(4);
                });
    }

    @Test
    void limitsAreClampedAtBothEnds() {
        VenueDiscoveryProperties minimum = new VenueDiscoveryProperties(true, true, "key", -1, -1, -1);
        assertThat(minimum.getMaxResults()).isEqualTo(1);
        assertThat(minimum.getDailyRequestLimit()).isZero();
        assertThat(minimum.getTimeoutSeconds()).isEqualTo(1);
        assertThat(minimum.isReady()).isFalse();

        VenueDiscoveryProperties maximum = new VenueDiscoveryProperties(true, true, "key",
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertThat(maximum.getMaxResults()).isEqualTo(20);
        assertThat(maximum.getDailyRequestLimit()).isEqualTo(10000);
        assertThat(maximum.getTimeoutSeconds()).isEqualTo(30);
    }

    @Test
    void readinessRequiresEverySafetyGate() {
        assertThat(new VenueDiscoveryProperties(false, true, "key", 10, 100, 10).isReady()).isFalse();
        assertThat(new VenueDiscoveryProperties(true, false, "key", 10, 100, 10).isReady()).isFalse();
        assertThat(new VenueDiscoveryProperties(true, true, null, 10, 100, 10).isReady()).isFalse();
        assertThat(new VenueDiscoveryProperties(true, true, "  ", 10, 100, 10).isReady()).isFalse();
        assertThat(new VenueDiscoveryProperties(true, true, "key", 10, 0, 10).isReady()).isFalse();
        assertThat(new VenueDiscoveryProperties(true, true, "key", 10, 1, 10).isReady()).isTrue();
    }
}
