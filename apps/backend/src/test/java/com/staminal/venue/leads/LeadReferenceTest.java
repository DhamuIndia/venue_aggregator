package com.staminal.venue.leads;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class LeadReferenceTest {

    @Test
    void generatedReferencesAreOpaqueAndUrlSafe() {
        Set<String> references = new HashSet<>();

        for (int index = 0; index < 500; index++) {
            String reference = LeadReference.create();
            assertThat(reference).matches("^LEAD-[0-9A-F]{20}$");
            references.add(reference);
        }

        assertThat(references).hasSize(500);
    }

    @Test
    void entityAssignsReferenceBeforeItsFirstInsert() {
        VendorLead lead = new VendorLead();

        lead.onCreate();

        assertThat(lead.getPublicReference()).matches("^LEAD-[0-9A-F]{20}$");
    }
}
