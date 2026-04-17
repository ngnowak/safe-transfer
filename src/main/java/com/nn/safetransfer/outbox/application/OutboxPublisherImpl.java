package com.nn.safetransfer.outbox.application;

import com.nn.safetransfer.common.metrics.TransferMetrics;
import com.nn.safetransfer.outbox.domain.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static com.nn.safetransfer.outbox.domain.OutboxStatus.FAILED;
import static com.nn.safetransfer.outbox.domain.OutboxStatus.FATAL;
import static com.nn.safetransfer.outbox.domain.OutboxStatus.PUBLISHED;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisherImpl implements OutboxPublisher {

    private final OutboxPublishingTransactions outboxPublishingTransactions;
    private final OutboxEventDispatcher outboxEventDispatcher;
    private final OutboxPublisherProperties properties;
    private final TransferMetrics transferMetrics;

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int publishPending(int batchSize) {
        var pendingEvents = outboxPublishingTransactions.claimPending(batchSize, properties.maxRetries());
        var publishedCount = 0;

        for (var outboxEvent : pendingEvents) {
            var start = System.nanoTime();
            try {
                outboxEventDispatcher.dispatch(outboxEvent);
                var publishedEvent = outboxEvent
                        .withStatus(PUBLISHED)
                        .withClaimedAt(null)
                        .withPublishedAt(Instant.now());
                outboxPublishingTransactions.save(publishedEvent);
                transferMetrics.recordOutboxPublished(outboxEvent.eventType(), System.nanoTime() - start);
                publishedCount++;
                log.debug("Published outbox event {}", outboxEvent.id());
            } catch (OutboxProcessingException ex) {
                var failedEvent = markFailed(outboxEvent);
                outboxPublishingTransactions.save(failedEvent);
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
                .withClaimedAt(null)
                .withStatus(nextStatus);
    }
}
