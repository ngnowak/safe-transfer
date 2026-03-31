package com.nn.safetransfer.wallet.application.mapper;

import com.nn.safetransfer.wallet.api.dto.CreateWalletRequest;
import com.nn.safetransfer.wallet.application.CreateWalletCommand;
import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.domain.CustomerId;
import com.nn.safetransfer.wallet.domain.TenantId;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CreateWalletCommandMapper {

    public CreateWalletCommand toCreateWalletCommand(UUID tenantId, CreateWalletRequest request) {
        return new CreateWalletCommand(
                new TenantId(tenantId),
                new CustomerId(request.customerId()),
                CurrencyCode.from(request.currency())
        );
    }
}
