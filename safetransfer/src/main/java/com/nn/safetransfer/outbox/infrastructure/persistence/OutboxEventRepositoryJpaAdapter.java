package com.nn.safetransfer.outbox.infrastructure.persistence;

import com.nn.safetransfer.outbox.domain.OutboxEvent;
import com.nn.safetransfer.outbox.domain.OutboxEventRepository;
import com.nn.safetransfer.outbox.infrastructure.mapper.OutboxEventMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class OutboxEventRepositoryJpaAdapter implements OutboxEventRepository {

    private final SpringDataOutboxEventRepository springDataOutboxEventRepository;
    private final OutboxEventMapper outboxEventMapper;
    private final EntityManager entityManager;

    @Override
    public OutboxEvent save(OutboxEvent outboxEvent) {
        var saved = springDataOutboxEventRepository.save(outboxEventMapper.toEntity(outboxEvent));
        return outboxEventMapper.toDomain(saved);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<OutboxEvent> claimTopRetryableOrderByOccurredAtAsc(int limit, int maxRetries) {
        var query = entityManager.createNativeQuery("""
                select *
                from outbox_event
                where status in ('NEW', 'FAILED')
                  and retry_count < :maxRetries
                order by occurred_at asc
                for update skip locked
                limit :limit
                """, OutboxEventJpa.class);
        query.setParameter("maxRetries", maxRetries);
        query.setParameter("limit", limit);

        return ((List<OutboxEventJpa>) query.getResultList())
                .stream()
                .map(outboxEventMapper::toDomain)
                .toList();
    }
}
