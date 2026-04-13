package com.nn.safetransfer.transfer.application;

import com.nn.safetransfer.transfer.domain.TransferId;
import com.nn.safetransfer.wallet.domain.TenantId;
import lombok.Builder;

@Builder
public record GetTransferQuery(
        TenantId tenantId,
        TransferId transferId
) {
}
