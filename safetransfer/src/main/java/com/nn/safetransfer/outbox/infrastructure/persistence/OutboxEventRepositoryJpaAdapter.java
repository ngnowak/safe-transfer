package com.nn.safetransfer.outbox.infrastructure.persistence;

import com.nn.safetransfer.outbox.domain.OutboxEvent;
import com.nn.safetransfer.outbox.domain.OutboxEventRepository;
import com.nn.safetransfer.outbox.infrastructure.mapper.OutboxEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OutboxEventRepositoryJpaAdapter implements OutboxEventRepository {

    private final SpringDataOutboxEventRepository springDataOutboxEventRepository;
    private final OutboxEventMapper outboxEventMapper;

    @Override
    public OutboxEvent save(OutboxEvent outboxEvent) {
        var saved = springDataOutboxEventRepository.save(outboxEventMapper.toEntity(outboxEvent));
        return outboxEventMapper.toDomain(saved);
    }
}
