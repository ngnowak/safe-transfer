package com.nn.safetransfer.audit.application;

import com.nn.safetransfer.outbox.application.OutboxProcessingException;
import com.nn.safetransfer.outbox.domain.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "application.kafka", name = "publishing", havingValue = "true")
public class AuditKafkaListener {

    private final JsonMapper jsonMapper;
    private final AuditConsumer auditConsumer;

    @KafkaListener(
            topics = "${application.kafka.topics.transfer-completed}",
            groupId = "${spring.application.name}-audit"
    )
    public void consume(String message) throws OutboxProcessingException {
        try {
            var outboxEvent = jsonMapper.readValue(message, OutboxEvent.class);
            auditConsumer.consume(outboxEvent);
        } catch (Exception ex) {
            var errorMsg = "Failed to deserialize Kafka outbox event";
            log.error(errorMsg, ex);
            throw new OutboxProcessingException(errorMsg, ex);
        }
    }
}
