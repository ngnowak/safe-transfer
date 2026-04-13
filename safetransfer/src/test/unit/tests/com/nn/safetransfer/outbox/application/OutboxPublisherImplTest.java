package com.nn.safetransfer.outbox.application;

import com.nn.safetransfer.audit.application.AuditConsumer;
import com.nn.safetransfer.common.metrics.TransferMetrics;
import com.nn.safetransfer.outbox.domain.EventType;
import com.nn.safetransfer.outbox.domain.OutboxAggregateType;
import com.nn.safetransfer.outbox.domain.OutboxEvent;
import com.nn.safetransfer.outbox.domain.OutboxEventRepository;
import com.nn.safetransfer.outbox.domain.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.nn.safetransfer.outbox.domain.EventType.TRANSFER_COMPLETED;
import static com.nn.safetransfer.outbox.domain.OutboxStatus.FAILED;
import static com.nn.safetransfer.outbox.domain.OutboxStatus.FATAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherImplTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private AuditConsumer auditConsumer;

    @Mock
    private TransferMetrics transferMetrics;

    private final OutboxPublisherProperties properties = new OutboxPublisherProperties(5000, 100, 3);

    @Test
    void shouldPublishPendingOutboxEvents() throws Exception {
        // given
        var publisher = new OutboxPublisherImpl(outboxEventRepository, auditConsumer, properties, transferMetrics);
        var event = buildOutboxEvent();
        given(outboxEventRepository.claimTopRetryableOrderByOccurredAtAsc(10, 3))
                .willReturn(List.of(event));
        given(outboxEventRepository.save(any(OutboxEvent.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // when
        var published = publisher.publishPending(10);

        // then
        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(auditConsumer).consume(event);
        assertAll(
                () -> assertThat(published).isEqualTo(1),
                () -> verify(outboxEventRepository).save(captor.capture()),
                () -> assertThat(captor.getValue().status()).isEqualTo(OutboxStatus.PUBLISHED),
                () -> assertThat(captor.getValue().publishedAt()).isNotNull()
        );
        verify(transferMetrics).recordOutboxPublished(eq(TRANSFER_COMPLETED), anyLong());
    }

    @Test
    void shouldDoNothingWhenThereAreNoPendingEvents() throws Exception {
        // given
        var publisher = new OutboxPublisherImpl(outboxEventRepository, auditConsumer, properties, transferMetrics);
        given(outboxEventRepository.claimTopRetryableOrderByOccurredAtAsc(10, 3))
                .willReturn(List.of());

        // when
        var published = publisher.publishPending(10);

        // then
        assertThat(published).isZero();
        verify(auditConsumer, never()).consume(any());
    }

    @Test
    void shouldMarkEventAsFailedAndIncreaseRetryCountWhenConsumerThrows() throws Exception {
        // given
        var publisher = new OutboxPublisherImpl(outboxEventRepository, auditConsumer, properties, transferMetrics);
        var event = buildOutboxEvent();
        given(outboxEventRepository.claimTopRetryableOrderByOccurredAtAsc(10, 3))
                .willReturn(List.of(event));
        given(outboxEventRepository.save(any(OutboxEvent.class)))
                .willAnswer(inv -> inv.getArgument(0));
        doThrow(new OutboxProcessingException("boom", new IllegalStateException("boom"))).when(auditConsumer).consume(event);

        // when
        var published = publisher.publishPending(10);

        // then
        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        assertAll(
                () -> assertThat(published).isZero(),
                () -> verify(outboxEventRepository).save(captor.capture()),
                () -> assertThat(captor.getValue().status()).isEqualTo(OutboxStatus.FAILED),
                () -> assertThat(captor.getValue().retryCount()).isEqualTo(1),
                () -> assertThat(captor.getValue().publishedAt()).isNull()
        );
        verify(transferMetrics).recordOutboxFailed(eq(TRANSFER_COMPLETED), eq(FAILED), anyLong());
    }

    @Test
    void shouldMarkEventAsFatalWhenMaxRetriesReached() throws Exception {
        // given
        var publisher = new OutboxPublisherImpl(outboxEventRepository, auditConsumer, properties, transferMetrics);
        var event = buildOutboxEvent().withRetryCount(2).withStatus(OutboxStatus.FAILED);
        given(outboxEventRepository.claimTopRetryableOrderByOccurredAtAsc(10, 3))
                .willReturn(List.of(event));
        given(outboxEventRepository.save(any(OutboxEvent.class)))
                .willAnswer(inv -> inv.getArgument(0));
        doThrow(new OutboxProcessingException("boom", new IllegalStateException("boom"))).when(auditConsumer).consume(event);

        // when
        var published = publisher.publishPending(10);

        // then
        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        assertAll(
                () -> assertThat(published).isZero(),
                () -> verify(outboxEventRepository).save(captor.capture()),
                () -> assertThat(captor.getValue().status()).isEqualTo(OutboxStatus.FATAL),
                () -> assertThat(captor.getValue().retryCount()).isEqualTo(3)
        );
        verify(transferMetrics).recordOutboxFailed(eq(TRANSFER_COMPLETED), eq(FATAL), anyLong());
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
