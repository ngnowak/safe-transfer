package com.nn.safetransfer.wallet.api.mapper;

import com.nn.safetransfer.ledger.domain.LedgerEntry;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class DepositResponseMapperTest {

    private final DepositResponseMapper mapper = new DepositResponseMapper();

    @Test
    void shouldMapLedgerEntryToDepositResponse() {
        // given
        var walletId = WalletId.create();
        var amount = new BigDecimal("200.50");
        var entry = LedgerEntry.credit(TenantId.create(), walletId, amount, EUR, "Deposit ref");

        // when
        var response = mapper.toDepositResponse(entry);

        // then
        assertAll(
                () -> assertThat(response.ledgerEntryId()).isEqualTo(entry.getId().value().toString()),
                () -> assertThat(response.walletId()).isEqualTo(walletId.value().toString()),
                () -> assertThat(response.amount()).isEqualByComparingTo(amount),
                () -> assertThat(response.currency()).isEqualTo("EUR"),
                () -> assertThat(response.entryType()).isEqualTo("CREDIT"),
                () -> assertThat(response.reference()).isEqualTo("Deposit ref"),
                () -> assertThat(response.createdAt()).isNotNull()
        );
    }

    @Test
    void shouldMapLedgerEntryWithNullReference() {
        // given
        var entry = LedgerEntry.credit(TenantId.create(), WalletId.create(), new BigDecimal("10.00"), EUR, null);

        // when
        var response = mapper.toDepositResponse(entry);

        // then
        assertThat(response.reference()).isNull();
    }
}
