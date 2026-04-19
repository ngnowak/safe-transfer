package com.nn.safetransfer.outbox.application;

import com.nn.safetransfer.outbox.domain.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.ExecutionException;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "application.kafka", name = "publishing", havingValue = "true")
public class KafkaOutboxEventDispatcher implements OutboxEventDispatcher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final ApplicationKafkaProperties properties;

    @Override
    public void dispatch(OutboxEvent outboxEvent) throws OutboxProcessingException {
        try {
            var topic = properties.topicFor(outboxEvent.eventType());
            var payload = jsonMapper.writeValueAsString(outboxEvent);
            log.debug("Publishing event: {} to topic: {}", outboxEvent.id(), topic);
            kafkaTemplate.send(topic, outboxEvent.aggregateId().toString(), payload).get();
        } catch (JacksonException ex) {
            var errorMsg = "Failed to serialize outbox event %s".formatted(outboxEvent.id());
            log.warn(errorMsg, ex);
            throw new OutboxProcessingException(errorMsg, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            var errorMsg = "Interrupted while publishing outbox event %s".formatted(outboxEvent.id());
            log.warn(errorMsg, ex);
            throw new OutboxProcessingException(errorMsg, ex);
        } catch (ExecutionException | KafkaException ex) {
            var errorMsg = "Failed to publish outbox event %s".formatted(outboxEvent.id());
            log.warn(errorMsg);
            throw new OutboxProcessingException(errorMsg, ex);
        }
    }
}
