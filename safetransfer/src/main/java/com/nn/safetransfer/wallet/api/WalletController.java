package com.nn.safetransfer.wallet.api;

import com.nn.safetransfer.wallet.api.dto.BalanceResponse;
import com.nn.safetransfer.wallet.api.dto.CreateWalletRequest;
import com.nn.safetransfer.wallet.api.dto.DepositRequest;
import com.nn.safetransfer.wallet.api.dto.DepositResponse;
import com.nn.safetransfer.wallet.api.dto.WalletResponse;
import com.nn.safetransfer.wallet.api.mapper.BalanceResponseMapper;
import com.nn.safetransfer.wallet.api.mapper.DepositResponseMapper;
import com.nn.safetransfer.wallet.api.mapper.WalletResponseMapper;
import com.nn.safetransfer.wallet.application.CreateWalletUseCase;
import com.nn.safetransfer.wallet.application.DepositService;
import com.nn.safetransfer.wallet.application.GetBalanceQuery;
import com.nn.safetransfer.wallet.application.GetWalletQuery;
import com.nn.safetransfer.wallet.application.QueryBalanceUseCase;
import com.nn.safetransfer.wallet.application.QueryWalletUseCase;
import com.nn.safetransfer.wallet.application.mapper.CreateWalletCommandMapper;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Wallet", description = "Wallet management endpoints")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/wallets")
public class WalletController {

    private final CreateWalletUseCase createWalletUseCase;
    private final WalletResponseMapper walletResponseMapper;
    private final CreateWalletCommandMapper createWalletCommandMapper;
    private final QueryWalletUseCase queryWalletUseCase;
    private final QueryBalanceUseCase queryBalanceUseCase;
    private final DepositService depositService;
    private final DepositResponseMapper depositResponseMapper;
    private final BalanceResponseMapper balanceResponseMapper;

    @Operation(summary = "Create wallet")
    @PostMapping
    public WalletResponse createWallet(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateWalletRequest request
    ) {
        var command = createWalletCommandMapper.toCreateWalletCommand(tenantId, request);
        var wallet = createWalletUseCase.handle(command);

        return walletResponseMapper.toWalletResponse(wallet);
    }

    @Operation(summary = "Get wallet by id")
    @GetMapping(path = "/{walletId}")
    public WalletResponse getWallet(
            @PathVariable UUID tenantId,
            @PathVariable UUID walletId
    ) {
        var command = GetWalletQuery.builder()
                .tenantId(new TenantId(tenantId))
                .walletId(new WalletId(walletId))
                .build();
        var wallet = queryWalletUseCase.handle(command);

        return walletResponseMapper.toWalletResponse(wallet);
    }

    @Operation(summary = "Get wallet balance")
    @GetMapping("/{walletId}/balance")
    public BalanceResponse getBalance(
            @PathVariable UUID tenantId,
            @PathVariable UUID walletId
    ) {
        var query = GetBalanceQuery.builder()
                .tenantId(new TenantId(tenantId))
                .walletId(new WalletId(walletId))
                .build();
        var result = queryBalanceUseCase.handle(query);

        return balanceResponseMapper.toBalanceResponse(result);
    }

    @Operation(summary = "Deposits")
    @PostMapping("/{walletId}/deposits")
    public DepositResponse deposit(
            @PathVariable UUID tenantId,
            @PathVariable UUID walletId,
            @Valid @RequestBody DepositRequest request
    ) {
        var ledgerEntry = depositService.deposit(
                new TenantId(tenantId),
                new WalletId(walletId),
                request
        );

        return depositResponseMapper.toDepositResponse(ledgerEntry);
    }
}
