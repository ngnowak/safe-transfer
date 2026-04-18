package com.nn.safetransfer.transfer.infrastructure.mapper;

import com.nn.safetransfer.transfer.domain.Transfer;
import com.nn.safetransfer.transfer.domain.TransferStatus;
import com.nn.safetransfer.transfer.infrastructure.persistence.TransferJpa;
import com.nn.safetransfer.wallet.domain.Money;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class TransferMapperTest {

    private final TransferMapper mapper = new TransferMapper();

    @Test
    void shouldMapTransferToEntity() {
        var transfer = Transfer.completed(
                TenantId.create(),
                WalletId.create(),
                WalletId.create(),
                new BigDecimal("25.00"),
                EUR,
                "idem",
                "ref"
        );

        var entity = mapper.toEntity(transfer);

        assertAll(
                () -> assertThat(entity.getId()).isEqualTo(transfer.getId().value()),
                () -> assertThat(entity.getTenantId()).isEqualTo(transfer.getTenantId().value()),
                () -> assertThat(entity.getAmount()).isEqualByComparingTo("25.00"),
                () -> assertThat(entity.getCurrency()).isEqualTo(transfer.getCurrency().name()),
                () -> assertThat(entity.getIdempotencyKey()).isEqualTo("idem"),
                () -> assertThat(entity.getRequestHash()).isEqualTo("request-hash")
        );
    }

    @Test
    void shouldMapEntityToDomainWithDefaultNewlyCreatedFalse() {
        var entity = TransferJpa.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .sourceWalletId(UUID.randomUUID())
                .destinationWalletId(UUID.randomUUID())
                .amount(new BigDecimal("25.00"))
                .currency(EUR.name())
                .status("COMPLETED")
                .idempotencyKey("idem")
                .requestHash("request-hash")
                .reference("ref")
                .createdAt(Instant.parse("2026-04-13T10:15:30Z"))
                .build();

        var transfer = mapper.toDomain(entity);

        assertAll(
                () -> assertThat(transfer.getMoney()).isEqualTo(Money.of(new BigDecimal("25.00"), EUR)),
                () -> assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED),
                () -> assertThat(transfer.isNewlyCreated()).isFalse()
        );
    }

    @Test
    void shouldMapEntityToDomainPreservingNewlyCreatedFlag() {
        var entity = TransferJpa.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .sourceWalletId(UUID.randomUUID())
                .destinationWalletId(UUID.randomUUID())
                .amount(new BigDecimal("25.00"))
                .currency(EUR.name())
                .status("COMPLETED")
                .idempotencyKey("idem")
                .requestHash("request-hash")
                .reference("ref")
                .createdAt(Instant.parse("2026-04-13T10:15:30Z"))
                .build();

        var transfer = mapper.toDomain(entity, true);

        assertThat(transfer.isNewlyCreated()).isTrue();
    }
}
