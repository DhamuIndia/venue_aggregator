package com.staminal.venue.notifications.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WhatsAppFailureClassifierTest {

    private final WhatsAppFailureClassifier classifier = new WhatsAppFailureClassifier();

    @Test
    void explicitProviderClassificationWinsAndUnknownCodesStayPermanent() {
        assertThat(classifier.isTemporary(131026, true)).isTrue();
        assertThat(classifier.isTemporary(130429, false)).isFalse();
        assertThat(classifier.isTemporary(130429, null)).isTrue();
        assertThat(classifier.isTemporary(999999, null)).isFalse();
        assertThat(classifier.isTemporary(null, null)).isFalse();
    }
}
