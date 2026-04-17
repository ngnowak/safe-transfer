package com.nn.safetransfer.outbox.application;

import com.nn.safetransfer.outbox.domain.EventType;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "application.kafka")
public record ApplicationKafkaProperties(
        boolean publishing,
        Topics topics
) {
    public String topicFor(EventType eventType) {
        return switch (eventType) {
            case TRANSFER_COMPLETED -> topics.transferCompleted();
        };
    }

    public record Topics(
            String transferCompleted
    ) {
    }
}
