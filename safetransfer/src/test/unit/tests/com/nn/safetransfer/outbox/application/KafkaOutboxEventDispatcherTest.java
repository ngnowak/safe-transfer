package com.nn.safetransfer.outbox.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nn.safetransfer.outbox.domain.EventType;
import com.nn.safetransfer.outbox.domain.OutboxAggregateType;
import com.nn.safetransfer.outbox.domain.OutboxEvent;
import com.nn.safetransfer.outbox.domain.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KafkaOutboxEventDispatcherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void shouldPublishSerializedOutboxEventToKafka() throws Exception {
        var properties = new ApplicationKafkaProperties(true, new ApplicationKafkaProperties.Topics("wallet.transfer.completed"));
        var objectMapper = new ObjectMapper().findAndRegisterModules();
        var dispatcher = new KafkaOutboxEventDispatcher(kafkaTemplate, objectMapper, properties);
        var outboxEvent = buildOutboxEvent();

        given(kafkaTemplate.send(eq("wallet.transfer.completed"), eq(outboxEvent.aggregateId().toString()), eq(objectMapper.writeValueAsString(outboxEvent))))
                .willReturn(CompletableFuture.completedFuture(null));

        dispatcher.dispatch(outboxEvent);

        verify(kafkaTemplate).send(
                "wallet.transfer.completed",
                outboxEvent.aggregateId().toString(),
                objectMapper.writeValueAsString(outboxEvent)
        );
    }

    @Test
    void shouldWrapKafkaPublishingFailure() {
        var properties = new ApplicationKafkaProperties(true, new ApplicationKafkaProperties.Topics("wallet.transfer.completed"));
        var objectMapper = new ObjectMapper().findAndRegisterModules();
        var dispatcher = new KafkaOutboxEventDispatcher(kafkaTemplate, objectMapper, properties);
        var outboxEvent = buildOutboxEvent();
        var failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new IllegalStateException("boom"));

        given(kafkaTemplate.send(eq("wallet.transfer.completed"), eq(outboxEvent.aggregateId().toString()), eq(writeValueAsString(objectMapper, outboxEvent))))
                .willReturn((CompletableFuture) failedFuture);

        assertThatThrownBy(() -> dispatcher.dispatch(outboxEvent))
                .isInstanceOf(OutboxProcessingException.class)
                .hasMessage("Failed to publish outbox event %s".formatted(outboxEvent.id()));
    }

    private String writeValueAsString(ObjectMapper objectMapper, OutboxEvent outboxEvent) {
        try {
            return objectMapper.writeValueAsString(outboxEvent);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private OutboxEvent buildOutboxEvent() {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .aggregateType(OutboxAggregateType.TRANSFER)
                .aggregateId(UUID.randomUUID())
                .eventType(EventType.TRANSFER_COMPLETED)
                .payload("{\"transferId\":\"123\"}")
                .status(OutboxStatus.NEW)
                .occurredAt(Instant.parse("2026-04-02T10:15:30Z"))
                .retryCount(0)
                .correlationId("corr-123")
                .causationId("cause-123")
                .build();
    }
}
