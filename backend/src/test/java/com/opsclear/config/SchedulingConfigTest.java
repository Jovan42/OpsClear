package com.opsclear.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SchedulingConfig")
class SchedulingConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfig.class);

    @Test
    @DisplayName("enables scheduling when the property is missing (production default)")
    void shouldEnableScheduling_whenPropertyMissing() {
        runner.run(context -> assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    @DisplayName("enables scheduling when opsclear.scheduling.enabled=true")
    void shouldEnableScheduling_whenPropertyTrue() {
        runner.withPropertyValues("opsclear.scheduling.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    @DisplayName("disables scheduling when opsclear.scheduling.enabled=false (test profile)")
    void shouldDisableScheduling_whenPropertyFalse() {
        runner.withPropertyValues("opsclear.scheduling.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
    }
}
