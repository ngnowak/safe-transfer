package com.nn.safetransfer.outbox.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisherJob {

    private final OutboxPublisher outboxPublisher;
    private final OutboxPublisherProperties properties;

    @Scheduled(fixedDelayString = "${safetransfer.outbox.publisher.fixed-delay}")
    public void publishPending() {
        var published = outboxPublisher.publishPending(properties.batchSize());
        if (published > 0) {
            log.debug("Published {} outbox events", published);
        }
    }
}
