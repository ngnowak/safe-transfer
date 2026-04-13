package com.nn.safetransfer.common.metrics;

import com.nn.safetransfer.transfer.application.TransferError;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TransferMetricOutcomeTest {

    @Test
    void shouldMapEveryTransferErrorToExpectedOutcome() {
        var walletId = WalletId.create();
        var tenantId = TenantId.create();

        assertThat(TransferMetricOutcome.from(new TransferError.SameWalletTransfer()))
                .isEqualTo(TransferMetricOutcome.SAME_WALLET);
        assertThat(TransferMetricOutcome.from(new TransferError.WalletNotFound(walletId, tenantId)))
                .isEqualTo(TransferMetricOutcome.WALLET_NOT_FOUND);
        assertThat(TransferMetricOutcome.from(new TransferError.WalletNotActive("blocked")))
                .isEqualTo(TransferMetricOutcome.WALLET_NOT_ACTIVE);
        assertThat(TransferMetricOutcome.from(new TransferError.CurrencyMismatch(
                com.nn.safetransfer.wallet.domain.CurrencyCode.EUR,
                com.nn.safetransfer.wallet.domain.CurrencyCode.USD)))
                .isEqualTo(TransferMetricOutcome.CURRENCY_MISMATCH);
        assertThat(TransferMetricOutcome.from(new TransferError.InsufficientFunds(
                walletId,
                new BigDecimal("10.00"),
                new BigDecimal("20.00"))))
                .isEqualTo(TransferMetricOutcome.INSUFFICIENT_FUNDS);
        assertThat(TransferMetricOutcome.from(new TransferError.OtherError("boom")))
                .isEqualTo(TransferMetricOutcome.OTHER_ERROR);
    }
}
