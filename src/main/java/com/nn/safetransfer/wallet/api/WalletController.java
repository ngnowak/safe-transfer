package com.nn.safetransfer.wallet.api;

import com.nn.safetransfer.wallet.api.dto.BalanceResponse;
import com.nn.safetransfer.wallet.api.dto.CreateWalletRequest;
import com.nn.safetransfer.wallet.api.dto.DepositRequest;
import com.nn.safetransfer.wallet.api.dto.DepositResponse;
import com.nn.safetransfer.wallet.api.dto.WalletResponse;
import com.nn.safetransfer.wallet.api.mapper.BalanceResponseMapper;
import com.nn.safetransfer.wallet.api.mapper.DepositResponseMapper;
import com.nn.safetransfer.wallet.api.mapper.WalletResultMapper;
import com.nn.safetransfer.wallet.application.CreateWalletUseCase;
import com.nn.safetransfer.wallet.application.DepositService;
import com.nn.safetransfer.wallet.application.GetBalanceQuery;
import com.nn.safetransfer.wallet.application.GetWalletQuery;
import com.nn.safetransfer.wallet.application.QueryBalanceUseCase;
import com.nn.safetransfer.wallet.application.QueryWalletUseCase;
import com.nn.safetransfer.wallet.application.mapper.CreateWalletCommandMapper;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@RestController
public class WalletController implements WalletApi {

    private final CreateWalletUseCase createWalletUseCase;
    private final WalletResultMapper walletResultMapper;
    private final CreateWalletCommandMapper createWalletCommandMapper;
    private final QueryWalletUseCase queryWalletUseCase;
    private final QueryBalanceUseCase queryBalanceUseCase;
    private final DepositService depositService;
    private final DepositResponseMapper depositResponseMapper;
    private final BalanceResponseMapper balanceResponseMapper;

    @Override
    public WalletResponse createWallet(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateWalletRequest request
    ) {
        log.debug("Creating wallet for tenantId={}, customerId={}, currency={}", tenantId, request.customerId(), request.currency());
        var command = createWalletCommandMapper.toCreateWalletCommand(tenantId, request);
        var wallet = createWalletUseCase.handle(command);
        var response = walletResultMapper.toWalletResponse(wallet);
        log.info("Wallet created: walletId={}, tenantId={}", response.walletId(), tenantId);
        return response;
    }

    @Override
    public WalletResponse getWallet(
            @PathVariable UUID tenantId,
            @PathVariable UUID walletId
    ) {
        log.debug("Getting wallet: walletId={}, tenantId={}", walletId, tenantId);
        var command = getGetWalletQuery(tenantId, walletId);
        var wallet = queryWalletUseCase.handle(command);

        return walletResultMapper.toWalletResponse(wallet);
    }

    @Override
    public BalanceResponse getBalance(
            @PathVariable UUID tenantId,
            @PathVariable UUID walletId
    ) {
        log.debug("Getting balance: walletId={}, tenantId={}", walletId, tenantId);
        var query = getBalanceQuery(tenantId, walletId);
        var result = queryBalanceUseCase.handle(query);

        return balanceResponseMapper.toBalanceResponse(result);
    }

    @Override
    public DepositResponse deposit(
            @PathVariable UUID tenantId,
            @PathVariable UUID walletId,
            @Valid @RequestBody DepositRequest request
    ) {
        log.debug("Depositing: walletId={}, tenantId={}, amount={}, currency={}", walletId, tenantId, request.amount(), request.currency());
        var result = depositService.deposit(
                new TenantId(tenantId),
                new WalletId(walletId),
                request
        );
        result.getValue().ifPresent(ledgerEntry -> log.info("Deposit completed: ledgerEntryId={}, walletId={}", ledgerEntry.getId(), walletId));
        return depositResponseMapper.toDepositResponse(result);
    }

    private static GetWalletQuery getGetWalletQuery(UUID tenantId, UUID walletId) {
        return GetWalletQuery.builder()
                .tenantId(new TenantId(tenantId))
                .walletId(new WalletId(walletId))
                .build();
    }

    private GetBalanceQuery getBalanceQuery(UUID tenantId, UUID walletId) {
        return GetBalanceQuery.builder()
                .tenantId(new TenantId(tenantId))
                .walletId(new WalletId(walletId))
                .build();
    }
}
