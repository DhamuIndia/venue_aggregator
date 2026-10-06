package com.staminal.venue.overture;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class OverturePublicationPropertiesTest {
    @Test void existingConstructorNeverEnablesLicensedPhotos() {
        var properties = new OverturePublicationProperties(true);
        assertTrue(properties.isEnabled());
        assertFalse(properties.isLicensedPhotosEnabled());
    }
    @Test void licensedSwitchDoesNotEnableGeneralPublication() {
        var properties = new OverturePublicationProperties(false, true);
        assertFalse(properties.isEnabled());
        assertTrue(properties.isLicensedPhotosEnabled());
    }
}
