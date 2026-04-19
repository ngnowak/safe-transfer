package com.nn.safetransfer.outbox.application;

import com.nn.safetransfer.audit.application.AuditConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OutboxEventDispatcherConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void shouldUseInProcessDispatcherWhenKafkaPublishingIsDisabled() {
        contextRunner
                .withPropertyValues(
                        "application.kafka.publishing=false",
                        "application.kafka.topics.transfer-completed=wallet.transfer.completed"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(OutboxEventDispatcher.class);
                    assertThat(context).hasSingleBean(InProcessOutboxEventDispatcher.class);
                    assertThat(context).doesNotHaveBean(KafkaOutboxEventDispatcher.class);
                });
    }

    @Test
    void shouldUseKafkaDispatcherWhenKafkaPublishingIsEnabled() {
        contextRunner
                .withPropertyValues(
                        "application.kafka.publishing=true",
                        "application.kafka.topics.transfer-completed=wallet.transfer.completed"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(OutboxEventDispatcher.class);
                    assertThat(context).hasSingleBean(KafkaOutboxEventDispatcher.class);
                    assertThat(context).doesNotHaveBean(InProcessOutboxEventDispatcher.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ApplicationKafkaProperties.class)
    @Import({InProcessOutboxEventDispatcher.class, KafkaOutboxEventDispatcher.class})
    static class TestConfiguration {

        @Bean
        AuditConsumer auditConsumer() {
            return mock(AuditConsumer.class);
        }

        @Bean
        KafkaTemplate<String, String> kafkaTemplate() {
            return mock(KafkaTemplate.class);
        }

        @Bean
        JsonMapper jsonMapper() {
            return new JsonMapper();
        }
    }
}
