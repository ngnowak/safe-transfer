package com.nn.safetransfer.outbox.application;

import com.nn.safetransfer.outbox.domain.OutboxEvent;
import com.nn.safetransfer.outbox.domain.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static com.nn.safetransfer.outbox.domain.EventType.TRANSFER_COMPLETED;
import static com.nn.safetransfer.outbox.domain.OutboxAggregateType.TRANSFER;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KafkaOutboxEventDispatcherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private ApplicationKafkaProperties applicationKafkaProperties;

    @InjectMocks
    private KafkaOutboxEventDispatcher dispatcher;

    @Test
    void shouldPublishSerializedOutboxEventToKafka() throws OutboxProcessingException {
        // given
        var outboxEvent = buildOutboxEvent();
        var topicName = "wallet.transfer.completed";
        given(applicationKafkaProperties.topicFor(outboxEvent.eventType())).willReturn(topicName);
        given(kafkaTemplate.send(topicName, outboxEvent.aggregateId().toString(), jsonMapper.writeValueAsString(outboxEvent)))
                .willReturn(CompletableFuture.completedFuture(null));

        // when
        dispatcher.dispatch(outboxEvent);

        // then
        verify(kafkaTemplate).send(
                topicName,
                outboxEvent.aggregateId().toString(),
                jsonMapper.writeValueAsString(outboxEvent)
        );
    }

    @Test
    void shouldWrapKafkaPublishingFailure() {
        var outboxEvent = buildOutboxEvent();
        var topicName = "wallet.transfer.completed";
        given(applicationKafkaProperties.topicFor(outboxEvent.eventType())).willReturn(topicName);
        var failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new IllegalStateException("boom"));

        given(kafkaTemplate.send(topicName, outboxEvent.aggregateId().toString(), jsonMapper.writeValueAsString(outboxEvent)))
                .willReturn((CompletableFuture) failedFuture);

        assertThatThrownBy(() -> dispatcher.dispatch(outboxEvent))
                .isInstanceOf(OutboxProcessingException.class)
                .hasMessage("Failed to publish outbox event %s".formatted(outboxEvent.id()));
    }

    private OutboxEvent buildOutboxEvent() {
        return OutboxEvent.builder()
                .id(randomUUID())
                .tenantId(randomUUID())
                .aggregateType(TRANSFER)
                .aggregateId(randomUUID())
                .eventType(TRANSFER_COMPLETED)
                .payload("{\"transferId\":\"123\"}")
                .status(OutboxStatus.NEW)
                .occurredAt(Instant.now())
                .retryCount(0)
                .correlationId("corr-123")
                .causationId("cause-123")
                .build();
    }
}
