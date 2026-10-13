package com.staminal.venue.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class VenueDiscoveryConfigurationSafetyTest {

    @Test
    void discoveryFeatureIsDisabledByDefault() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/application.yml")) {
            assertThat(stream).isNotNull();
            String yaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(yaml)
                    .contains("venue-discovery-enabled: ${VENUE_DISCOVERY_ENABLED:false}");
        }
    }
}
