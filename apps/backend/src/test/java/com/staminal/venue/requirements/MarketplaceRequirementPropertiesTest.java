package com.staminal.venue.requirements;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MarketplaceRequirementPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MarketplaceRequirementProperties.class);

    @Test
    void featureIsDisabledByDefault() {
        contextRunner.run(context ->
                assertThat(context.getBean(MarketplaceRequirementProperties.class).isEnabled()).isFalse());
    }

    @Test
    void featureCanBeEnabledByConfiguration() {
        contextRunner
                .withPropertyValues("app.features.marketplace-requirements-enabled=true")
                .run(context ->
                        assertThat(context.getBean(MarketplaceRequirementProperties.class).isEnabled()).isTrue());
    }
}
