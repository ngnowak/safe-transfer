package com.nn.safetransfer.outbox.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherJobTest {

    @Mock
    private OutboxPublisher outboxPublisher;

    private final OutboxPublisherProperties properties = new OutboxPublisherProperties(5000, 25, 3);

    @Test
    void shouldPublishPendingOutboxEventsUsingConfiguredBatchSize() {
        // given
        var outboxPublisherJob = new OutboxPublisherJob(outboxPublisher, properties);

        // when
        outboxPublisherJob.publishPending();

        // then
        verify(outboxPublisher).publishPending(25);
    }
}
