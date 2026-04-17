package com.nn.safetransfer.outbox.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "safetransfer.outbox.publisher")
public record OutboxPublisherProperties(
        long fixedDelay,
        int batchSize,
        int maxRetries
) {
}
