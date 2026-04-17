package com.nn.safetransfer.transfer.api.mapper;

import com.nn.safetransfer.transfer.api.dto.TransferResponse;
import com.nn.safetransfer.transfer.domain.Transfer;
import org.springframework.stereotype.Component;

@Component
public class TransferResponseMapper {

    public TransferResponse toResponse(Transfer transfer) {
        return TransferResponse.builder()
                .transferId(transfer.getId().toString())
                .tenantId(transfer.getTenantId().toString())
                .sourceWalletId(transfer.getSourceWalletId().toString())
                .destinationWalletId(transfer.getDestinationWalletId().toString())
                .amount(transfer.getAmount())
                .currency(transfer.getCurrency().name())
                .status(transfer.getStatus().name())
                .reference(transfer.getReference())
                .createdAt(transfer.getCreatedAt())
                .build();
    }
}
