package com.nn.safetransfer.audit.application;

import com.nn.safetransfer.audit.domain.AuditEventRepository;
import com.nn.safetransfer.outbox.application.OutboxProcessingException;
import com.nn.safetransfer.outbox.domain.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import static com.nn.safetransfer.outbox.domain.EventType.TRANSFER_COMPLETED;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditConsumerImpl implements AuditConsumer {
    private final AuditEventFactory auditEventFactory;
    private final AuditEventRepository auditEventRepository;

    @Override
    public void consume(OutboxEvent outboxEvent) throws OutboxProcessingException {
        if (outboxEvent.eventType() != TRANSFER_COMPLETED) {
            log.debug("Event with id {} and type {} is not supported by audit consumer", outboxEvent.id(), outboxEvent.eventType().name());
            return;
        }

        try {
            var auditEvent = auditEventFactory.from(outboxEvent);
            auditEventRepository.save(auditEvent);
            log.debug("Event with id {} was successfully processed", outboxEvent.id());
        } catch (DataIntegrityViolationException ex) {
            log.debug("Audit event for source event {} already exists", outboxEvent.id());
        } catch (DataAccessException ex) {
            throw new OutboxProcessingException("Failed to persist audit event for outbox event %s".formatted(outboxEvent.id()), ex);
        }
    }
}
