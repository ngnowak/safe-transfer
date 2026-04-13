package com.nn.safetransfer.outbox.application;

import com.nn.safetransfer.audit.application.AuditConsumer;
import com.nn.safetransfer.common.metrics.TransferMetrics;
import com.nn.safetransfer.outbox.domain.OutboxEvent;
import com.nn.safetransfer.outbox.domain.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static com.nn.safetransfer.outbox.domain.OutboxStatus.FAILED;
import static com.nn.safetransfer.outbox.domain.OutboxStatus.FATAL;
import static com.nn.safetransfer.outbox.domain.OutboxStatus.PUBLISHED;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisherImpl implements OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final AuditConsumer auditConsumer;
    private final OutboxPublisherProperties properties;
    private final TransferMetrics transferMetrics;

    @Override
    @Transactional
    public int publishPending(int batchSize) {
        var pendingEvents = outboxEventRepository.claimTopRetryableOrderByOccurredAtAsc(batchSize, properties.maxRetries());
        var publishedCount = 0;

        for (var outboxEvent : pendingEvents) {
            var start = System.nanoTime();
            try {
                auditConsumer.consume(outboxEvent);
                var publishedEvent = outboxEvent
                        .withStatus(PUBLISHED)
                        .withPublishedAt(Instant.now());
                outboxEventRepository.save(publishedEvent);
                transferMetrics.recordOutboxPublished(outboxEvent.eventType(), System.nanoTime() - start);
                publishedCount++;
                log.debug("Published outbox event {}", outboxEvent.id());
            } catch (OutboxProcessingException ex) {
                var failedEvent = markFailed(outboxEvent);
                outboxEventRepository.save(failedEvent);
                transferMetrics.recordOutboxFailed(
                        outboxEvent.eventType(),
                        failedEvent.status(),
                        System.nanoTime() - start
                );
                log.warn("Failed to publish outbox event {}", outboxEvent.id(), ex);
            }
        }

        return publishedCount;
    }

    private OutboxEvent markFailed(OutboxEvent outboxEvent) {
        var nextRetryCount = outboxEvent.retryCount() + 1;
        var nextStatus = nextRetryCount >= properties.maxRetries() ? FATAL : FAILED;

        return outboxEvent
                .withRetryCount(nextRetryCount)
                .withStatus(nextStatus);
    }
}
