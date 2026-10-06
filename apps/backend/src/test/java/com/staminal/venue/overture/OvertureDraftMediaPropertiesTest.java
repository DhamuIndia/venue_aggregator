package com.staminal.venue.overture;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class OvertureDraftMediaPropertiesTest {
    @Test void keepsMediaDisabledAndTheBucketSeparateByDefault() {
        var properties = new OvertureDraftMediaProperties(false, "venue-draft-media-private");
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getBucket()).isEqualTo("venue-draft-media-private");
    }
    @Test void normalizesOnlyConfiguredBucketWhitespace() {
        assertThat(new OvertureDraftMediaProperties(true, "  private-draft-photos  ").getBucket()).isEqualTo("private-draft-photos");
        assertThat(new OvertureDraftMediaProperties(false, null).getBucket()).isEmpty();
    }
}
