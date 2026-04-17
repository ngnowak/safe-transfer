package com.nn.safetransfer.ledger.infrastructure.mapper;

import com.nn.safetransfer.ledger.domain.LedgerEntry;
import com.nn.safetransfer.ledger.domain.LedgerEntryType;
import com.nn.safetransfer.ledger.infrastructure.persistence.LedgerEntryJpa;
import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.domain.Money;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class LedgerEntryMapperTest {

    private final LedgerEntryMapper mapper = new LedgerEntryMapper();

    @Test
    void shouldMapLedgerEntryToEntity() {
        var entry = LedgerEntry.credit(TenantId.create(), WalletId.create(), new BigDecimal("50.00"), CurrencyCode.PLN, "ref");

        var entity = mapper.toEntity(entry);

        assertAll(
                () -> assertThat(entity.getId()).isEqualTo(entry.getId().value()),
                () -> assertThat(entity.getAmount()).isEqualByComparingTo("50.00"),
                () -> assertThat(entity.getCurrency()).isEqualTo("PLN"),
                () -> assertThat(entity.getType()).isEqualTo("CREDIT")
        );
    }

    @Test
    void shouldMapEntityToLedgerEntryWithMoney() {
        var entity = LedgerEntryJpa.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .walletId(UUID.randomUUID())
                .type("DEBIT")
                .amount(new BigDecimal("50.00"))
                .currency("PLN")
                .reference("ref")
                .createdAt(Instant.parse("2026-04-13T10:15:30Z"))
                .build();

        var entry = mapper.toDomain(entity);

        assertAll(
                () -> assertThat(entry.getMoney()).isEqualTo(Money.of(new BigDecimal("50.00"), CurrencyCode.PLN)),
                () -> assertThat(entry.getType()).isEqualTo(LedgerEntryType.DEBIT),
                () -> assertThat(entry.getReference()).isEqualTo("ref")
        );
    }
}
