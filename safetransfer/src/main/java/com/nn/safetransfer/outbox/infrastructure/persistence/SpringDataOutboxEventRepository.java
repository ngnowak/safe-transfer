package com.nn.safetransfer.outbox.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataOutboxEventRepository extends JpaRepository<OutboxEventJpa, UUID> {
}
