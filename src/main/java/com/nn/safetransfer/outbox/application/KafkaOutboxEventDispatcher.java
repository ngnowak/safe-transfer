package com.nn.safetransfer.outbox.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nn.safetransfer.outbox.domain.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "application.kafka", name = "publishing", havingValue = "true")
public class KafkaOutboxEventDispatcher implements OutboxEventDispatcher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final ApplicationKafkaProperties properties;

    @Override
    public void dispatch(OutboxEvent outboxEvent) throws OutboxProcessingException {
        try {
            var topic = properties.topicFor(outboxEvent.eventType());
            var payload = objectMapper.writeValueAsString(outboxEvent);
            log.debug("Publishing event: {} to topic: {}", outboxEvent.id(), topic);
            kafkaTemplate.send(topic, outboxEvent.aggregateId().toString(), payload).get();
        } catch (JsonProcessingException ex) {
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
