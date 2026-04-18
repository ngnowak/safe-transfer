package com.nn.safetransfer.wallet.api;

import com.nn.safetransfer.common.api.ErrorDto;
import com.nn.safetransfer.wallet.api.dto.BalanceResponse;
import com.nn.safetransfer.wallet.api.dto.CreateWalletRequest;
import com.nn.safetransfer.wallet.api.dto.DepositRequest;
import com.nn.safetransfer.wallet.api.dto.DepositResponse;
import com.nn.safetransfer.wallet.api.dto.WalletResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Tag(name = "Wallets", description = "Tenant-scoped wallet management, deposits, and balance queries.")
@RequestMapping("/api/v1/tenants/{tenantId}/wallets")
public interface WalletApi {

    @Operation(
            summary = "Create wallet",
            description = "Creates a new wallet for a tenant/customer pair in the requested currency."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Wallet created",
                    content = @Content(schema = @Schema(implementation = WalletResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorDto.class),
                            examples = @ExampleObject(name = "Validation error", value = """
                                    {"errorId":"018f1254-2f2c-7b54-9e6b-f30da9b0721e","errorMessage":"Validation failed","errors":["currency: must not be blank"]}
                                    """)))
    })
    @PostMapping
    WalletResponse createWallet(
            @Parameter(description = "Tenant identifier.", example = "11111111-1111-1111-1111-111111111111")
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateWalletRequest request
    );

    @Operation(
            summary = "Get wallet",
            description = "Returns wallet metadata for the given tenant and wallet id."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Wallet found",
                    content = @Content(schema = @Schema(implementation = WalletResponse.class))),
            @ApiResponse(responseCode = "404", description = "Wallet not found",
                    content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    })
    @GetMapping(path = "/{walletId}")
    WalletResponse getWallet(
            @Parameter(description = "Tenant identifier.", example = "11111111-1111-1111-1111-111111111111")
            @PathVariable UUID tenantId,
            @Parameter(description = "Wallet identifier.", example = "22222222-2222-2222-2222-222222222222")
            @PathVariable UUID walletId
    );

    @Operation(
            summary = "Get wallet balance",
            description = "Calculates the current balance from immutable ledger entries."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Balance calculated",
                    content = @Content(schema = @Schema(implementation = BalanceResponse.class))),
            @ApiResponse(responseCode = "404", description = "Wallet not found",
                    content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    })
    @GetMapping("/{walletId}/balance")
    BalanceResponse getBalance(
            @Parameter(description = "Tenant identifier.", example = "11111111-1111-1111-1111-111111111111")
            @PathVariable UUID tenantId,
            @Parameter(description = "Wallet identifier.", example = "22222222-2222-2222-2222-222222222222")
            @PathVariable UUID walletId
    );

    @Operation(
            summary = "Deposit funds",
            description = "Creates a credit ledger entry for the wallet. Balances are derived from ledger entries."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deposit accepted",
                    content = @Content(schema = @Schema(implementation = DepositResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid amount or currency",
                    content = @Content(schema = @Schema(implementation = ErrorDto.class))),
            @ApiResponse(responseCode = "404", description = "Wallet not found",
                    content = @Content(schema = @Schema(implementation = ErrorDto.class)))
    })
    @PostMapping("/{walletId}/deposits")
    DepositResponse deposit(
            @Parameter(description = "Tenant identifier.", example = "11111111-1111-1111-1111-111111111111")
            @PathVariable UUID tenantId,
            @Parameter(description = "Wallet identifier.", example = "22222222-2222-2222-2222-222222222222")
            @PathVariable UUID walletId,
            @Valid @RequestBody DepositRequest request
    );
}
