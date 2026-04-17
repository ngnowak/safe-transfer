package com.nn.safetransfer.audit.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataAuditEventRepository extends JpaRepository<AuditEventJpa, UUID> {
}
