package com.nn.safetransfer.common.metrics;

import com.nn.safetransfer.transfer.application.TransferError;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TransferMetricOutcome {
    SUCCESS("success"),
    INSUFFICIENT_FUNDS("insufficient_funds"),
    WALLET_NOT_FOUND("wallet_not_found"),
    WALLET_NOT_ACTIVE("wallet_not_active"),
    CURRENCY_MISMATCH("currency_mismatch"),
    SAME_WALLET("same_wallet"),
    TRANSFER_LIMIT_EXCEEDED("transfer_limit_exceeded"),
    IDEMPOTENCY_CONFLICT("idempotency_conflict"),
    OTHER_ERROR("other_error");

    private final String tagValue;

    public static TransferMetricOutcome from(TransferError error) {
        return switch (error) {
            case TransferError.InsufficientFunds ignored -> INSUFFICIENT_FUNDS;
            case TransferError.WalletNotFound ignored -> WALLET_NOT_FOUND;
            case TransferError.TransferNotFound ignored -> OTHER_ERROR;
            case TransferError.WalletNotActive ignored -> WALLET_NOT_ACTIVE;
            case TransferError.CurrencyMismatch ignored -> CURRENCY_MISMATCH;
            case TransferError.SameWalletTransfer ignored -> SAME_WALLET;
            case TransferError.TransferLimitExceeded ignored -> TRANSFER_LIMIT_EXCEEDED;
            case TransferError.IdempotencyKeyConflict ignored -> IDEMPOTENCY_CONFLICT;
            case TransferError.OtherError ignored -> OTHER_ERROR;
        };
    }
}
