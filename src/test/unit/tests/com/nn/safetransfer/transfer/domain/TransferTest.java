package com.nn.safetransfer.transfer.domain;

import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;
import com.nn.safetransfer.wallet.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static com.nn.safetransfer.transfer.domain.TransferStatus.COMPLETED;
import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class TransferTest {

    @Test
    void shouldCreateCompletedTransfer() {
        // given
        var tenantId = TenantId.create();
        var sourceWalletId = WalletId.create();
        var destinationWalletId = WalletId.create();
        var amount = new BigDecimal("100.00");
        var idempotencyKey = "idem-key-123";
        var reference = "Test transfer";

        // when
        var transfer = Transfer.completed(
                tenantId, sourceWalletId, destinationWalletId,
                amount, EUR, idempotencyKey, reference
        );

        // then
        assertAll(
                () -> assertThat(transfer.getId()).isNotNull(),
                () -> assertThat(transfer.getTenantId()).isEqualTo(tenantId),
                () -> assertThat(transfer.getSourceWalletId()).isEqualTo(sourceWalletId),
                () -> assertThat(transfer.getDestinationWalletId()).isEqualTo(destinationWalletId),
                () -> assertThat(transfer.getMoney()).isEqualTo(Money.of(amount, EUR)),
                () -> assertThat(transfer.getAmount()).isEqualByComparingTo(amount),
                () -> assertThat(transfer.getCurrency()).isEqualTo(EUR),
                () -> assertThat(transfer.getStatus()).isEqualTo(COMPLETED),
                () -> assertThat(transfer.getIdempotencyKey()).isEqualTo(idempotencyKey),
                () -> assertThat(transfer.getRequestHash()).isEqualTo("request-hash"),
                () -> assertThat(transfer.getReference()).isEqualTo(reference),
                () -> assertThat(transfer.getCreatedAt()).isNotNull(),
                () -> assertThat(transfer.isNewlyCreated()).isTrue()
        );
    }

    @Test
    void shouldCreateCompletedTransferWithNullReference() {
        // given
        var sourceWalletId = WalletId.create();
        var destinationWalletId = WalletId.create();

        // when
        var transfer = Transfer.completed(
                TenantId.create(), sourceWalletId, destinationWalletId,
                new BigDecimal("50.00"), EUR, "key", null
        );

        // then
        assertThat(transfer.getReference()).isNull();
        assertThat(transfer.isNewlyCreated()).isTrue();
    }

    @Test
    void shouldThrowWhenAmountIsZero() {
        // given
        var sourceWalletId = WalletId.create();
        var destinationWalletId = WalletId.create();

        // when / then
        assertThatThrownBy(() -> Transfer.completed(
                TenantId.create(), sourceWalletId, destinationWalletId,
                BigDecimal.ZERO, EUR, "key", null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must be greater than zero");
    }

    @Test
    void shouldThrowWhenAmountIsNegative() {
        // given
        var sourceWalletId = WalletId.create();
        var destinationWalletId = WalletId.create();

        // when / then
        assertThatThrownBy(() -> Transfer.completed(
                TenantId.create(), sourceWalletId, destinationWalletId,
                new BigDecimal("-10.00"), EUR, "key", null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must be greater than zero");
    }

    @Test
    void shouldThrowWhenSourceAndDestinationWalletsAreTheSame() {
        // given
        var walletId = WalletId.create();

        // when / then
        assertThatThrownBy(() -> Transfer.completed(
                TenantId.create(), walletId, walletId,
                new BigDecimal("10.00"), EUR, "key", null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Source and destination wallets must be different");
    }

    @Test
    void shouldThrowWhenTenantIdIsNull() {
        // when / then
        assertThatThrownBy(() -> Transfer.builder()
                .id(TransferId.newId())
                .tenantId(null)
                .sourceWalletId(WalletId.create())
                .destinationWalletId(WalletId.create())
                .money(Money.of(new BigDecimal("10.00"), EUR))
                .status(COMPLETED)
                .idempotencyKey("key")
                .requestHash("request-hash")
                .createdAt(Instant.now())
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("tenantId must not be null");
    }

    @Test
    void shouldThrowWhenAmountIsNull() {
        // when / then
        assertThatThrownBy(() -> Transfer.builder()
                .id(TransferId.newId())
                .tenantId(TenantId.create())
                .sourceWalletId(WalletId.create())
                .destinationWalletId(WalletId.create())
                .money(null)
                .status(COMPLETED)
                .idempotencyKey("key")
                .requestHash("request-hash")
                .createdAt(Instant.now())
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("money must not be null");
    }

    @Test
    void shouldThrowWhenIdempotencyKeyIsNull() {
        // when / then
        assertThatThrownBy(() -> Transfer.builder()
                .id(TransferId.newId())
                .tenantId(TenantId.create())
                .sourceWalletId(WalletId.create())
                .destinationWalletId(WalletId.create())
                .money(Money.of(new BigDecimal("10.00"), EUR))
                .status(COMPLETED)
                .idempotencyKey(null)
                .requestHash("request-hash")
                .createdAt(Instant.now())
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("idempotencyKey must not be null");
    }
}
