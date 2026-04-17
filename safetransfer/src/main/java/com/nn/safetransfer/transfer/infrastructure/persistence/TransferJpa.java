package com.nn.safetransfer.transfer.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(
        name = "transfer",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_transfer_tenant_idempotency_key",
                        columnNames = {"tenant_id", "idempotency_key"}
                )
        },
        indexes = {
                @Index(name = "idx_transfer_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_transfer_source_wallet_id", columnList = "source_wallet_id"),
                @Index(name = "idx_transfer_destination_wallet_id", columnList = "destination_wallet_id")
        }
)
@Getter
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@AllArgsConstructor
@NoArgsConstructor(access = PROTECTED)
public class TransferJpa {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "source_wallet_id")
    private UUID sourceWalletId;

    @Column(name = "destination_wallet_id")
    private UUID destinationWalletId;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "currency")
    private String currency;

    @Column(name = "status")
    private String status;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "request_hash")
    private String requestHash;

    @Column(name = "reference")
    private String reference;

    @Column(name = "created_at")
    private Instant createdAt;
}
