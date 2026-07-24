package com.staminal.venue.requirements;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.staminal.venue.enums.CustomerRequirementStatus;
import com.staminal.venue.enums.PreferredContactChannel;
import com.staminal.venue.leads.VendorLead;
import com.staminal.venue.vendors.Entity.VendorCategory;

class CustomerRequirementTest {

    @Test
    void appliesSafeDefaultsBeforeFirstPersistence() {
        CustomerRequirement requirement = new CustomerRequirement();

        requirement.onCreate();

        assertThat(requirement.getStatus()).isEqualTo(CustomerRequirementStatus.OPEN);
        assertThat(requirement.getPreferredContactChannel()).isEqualTo(PreferredContactChannel.IN_APP);
        assertThat(requirement.isShareContactDetails()).isFalse();
        assertThat(requirement.getCreatedAt()).isNotNull();
        assertThat(requirement.getUpdatedAt()).isEqualTo(requirement.getCreatedAt());
    }

    @Test
    void preservesExplicitStatusAndContactPreference() {
        CustomerRequirement requirement = new CustomerRequirement();
        requirement.setStatus(CustomerRequirementStatus.CANCELLED);
        requirement.setPreferredContactChannel(PreferredContactChannel.WHATSAPP);

        requirement.onCreate();

        assertThat(requirement.getStatus()).isEqualTo(CustomerRequirementStatus.CANCELLED);
        assertThat(requirement.getPreferredContactChannel()).isEqualTo(PreferredContactChannel.WHATSAPP);
    }

    @Test
    void supportsMultipleServiceCategoriesAndAnOptionalRequirementLeadLink() {
        VendorCategory photography = category(1L, "Photography");
        VendorCategory makeup = category(2L, "Makeup");
        CustomerRequirement requirement = new CustomerRequirement();
        requirement.setId(41L);
        requirement.setServiceCategories(Set.of(photography, makeup));

        VendorLead marketplaceLead = new VendorLead();
        marketplaceLead.setRequirement(requirement);
        VendorLead legacyDirectLead = new VendorLead();

        assertThat(requirement.getServiceCategories()).containsExactlyInAnyOrder(photography, makeup);
        assertThat(marketplaceLead.getRequirement()).isSameAs(requirement);
        assertThat(legacyDirectLead.getRequirement()).isNull();
    }

    @Test
    void updateRefreshesTimestampWithoutChangingCreationTime() {
        CustomerRequirement requirement = new CustomerRequirement();
        requirement.onCreate();
        Instant createdAt = requirement.getCreatedAt();

        requirement.onUpdate();

        assertThat(requirement.getCreatedAt()).isEqualTo(createdAt);
        assertThat(requirement.getUpdatedAt()).isAfterOrEqualTo(createdAt);
    }

    private VendorCategory category(Long id, String name) {
        VendorCategory category = new VendorCategory();
        category.setId(id);
        category.setCategoryName(name);
        return category;
    }
}
