package com.nn.safetransfer.wallet.api.mapper;

import com.nn.safetransfer.wallet.api.dto.BalanceResponse;
import com.nn.safetransfer.wallet.application.BalanceResult;
import org.springframework.stereotype.Component;

@Component
public class BalanceResponseMapper {

    public BalanceResponse toBalanceResponse(BalanceResult result) {
        return BalanceResponse.builder()
                .walletId(result.wallet().getId().toString())
                .tenantId(result.wallet().getTenantId().toString())
                .currency(result.wallet().getCurrency().name())
                .balance(result.balance())
                .build();
    }
}
