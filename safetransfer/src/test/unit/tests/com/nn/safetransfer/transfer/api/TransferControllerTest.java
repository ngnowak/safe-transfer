package com.nn.safetransfer.transfer.api;

import com.nn.safetransfer.common.domain.result.Result;
import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import com.nn.safetransfer.transfer.api.dto.TransferResponse;
import com.nn.safetransfer.transfer.api.mapper.TransferResultMapper;
import com.nn.safetransfer.transfer.application.TransferError;
import com.nn.safetransfer.transfer.application.TransferService;
import com.nn.safetransfer.transfer.domain.Transfer;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransferControllerTest {

    @Mock
    private TransferService transferService;

    @Mock
    private TransferResultMapper transferResultMapper;

    @InjectMocks
    private TransferController transferController;

    @Test
    void shouldCreateTransfer() {
        // given
        var tenantId = UUID.randomUUID();
        var idempotencyKey = "idem-key-123";
        var sourceWalletId = UUID.randomUUID();
        var destinationWalletId = UUID.randomUUID();
        var amount = new BigDecimal("100.00");
        var request = new CreateTransferRequest(sourceWalletId, destinationWalletId, amount, "EUR", "Payment");

        var transfer = Transfer.completed(
                new TenantId(tenantId),
                new WalletId(sourceWalletId),
                new WalletId(destinationWalletId),
                amount, EUR, idempotencyKey, "Payment"
        );

        var expectedResponse = TransferResponse.builder()
                .transferId(transfer.getId().toString())
                .tenantId(tenantId.toString())
                .sourceWalletId(sourceWalletId.toString())
                .destinationWalletId(destinationWalletId.toString())
                .amount(amount)
                .currency("EUR")
                .status("COMPLETED")
                .reference("Payment")
                .createdAt(Instant.now())
                .build();
        Result<TransferError, Transfer> result = Result.success(transfer);
        given(transferService.transfer(new TenantId(tenantId), idempotencyKey, request))
                .willReturn(result);
        given(transferResultMapper.toTransferResponse(result)).willReturn(expectedResponse);

        // when
        var response = transferController.createTransfer(tenantId, idempotencyKey, request);

        // then
        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody()).isEqualTo(expectedResponse),
                () -> verify(transferService).transfer(new TenantId(tenantId), idempotencyKey, request),
                () -> verify(transferResultMapper).toTransferResponse(result)
        );
    }

    @Test
    void shouldReturnNotFoundWhenWalletNotFound() {
        // given
        var tenantId = UUID.randomUUID();
        var idempotencyKey = "idem-key-456";
        var walletId = WalletId.create();
        var request = new CreateTransferRequest(
                walletId.value(), UUID.randomUUID(),
                new BigDecimal("50.00"), "EUR", null
        );

        Result<TransferError, Transfer> result = Result.failure(new TransferError.WalletNotFound(walletId, new TenantId(tenantId)));
        var errorMessage = result.getError().orElseThrow().getMessage();
        var exception = new ResponseStatusException(HttpStatus.NOT_FOUND, errorMessage);
        given(transferService.transfer(new TenantId(tenantId), idempotencyKey, request)).willReturn(result);
        given(transferResultMapper.toTransferResponse(result)).willThrow(exception);

        // when
        var thrown = assertThrows(
                ResponseStatusException.class,
                () -> transferController.createTransfer(tenantId, idempotencyKey, request)
        );

        // then
        assertAll(
                () -> assertThat(thrown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(thrown.getReason()).isEqualTo(errorMessage),
                () -> verify(transferService).transfer(new TenantId(tenantId), idempotencyKey, request),
                () -> verify(transferResultMapper).toTransferResponse(result)
        );
    }

    @Test
    void shouldReturnBadRequestWhenSameWalletTransfer() {
        // given
        var tenantId = UUID.randomUUID();
        var idempotencyKey = "idem-key-789";
        var walletId = UUID.randomUUID();
        var request = new CreateTransferRequest(
                walletId, walletId,
                new BigDecimal("50.00"), "EUR", null
        );

        Result<TransferError, Transfer> result = Result.failure(new TransferError.SameWalletTransfer());
        var errorMessage = result.getError().orElseThrow().getMessage();
        var exception = new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        given(transferService.transfer(new TenantId(tenantId), idempotencyKey, request))
                .willReturn(result);
        given(transferResultMapper.toTransferResponse(result)).willThrow(exception);

        // when
        var thrown = assertThrows(
                ResponseStatusException.class,
                () -> transferController.createTransfer(tenantId, idempotencyKey, request)
        );

        // then
        assertAll(
                () -> assertThat(thrown.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(thrown.getReason()).isEqualTo(errorMessage),
                () -> verify(transferService).transfer(new TenantId(tenantId), idempotencyKey, request),
                () -> verify(transferResultMapper).toTransferResponse(result)
        );
    }

    @Test
    void shouldReturnConflictWhenInsufficientFunds() {
        // given
        var tenantId = UUID.randomUUID();
        var idempotencyKey = "idem-key-101";
        var sourceWalletId = WalletId.create();
        var request = new CreateTransferRequest(
                sourceWalletId.value(), UUID.randomUUID(),
                new BigDecimal("500.00"), "EUR", null
        );

        Result<TransferError, Transfer> result = Result.failure(new TransferError.InsufficientFunds(
                sourceWalletId, new BigDecimal("100.00"), new BigDecimal("500.00")
        ));
        var errorMessage = result.getError().orElseThrow().getMessage();
        var exception = new ResponseStatusException(HttpStatus.CONFLICT, errorMessage);
        given(transferService.transfer(new TenantId(tenantId), idempotencyKey, request))
                .willReturn(result);
        given(transferResultMapper.toTransferResponse(result)).willThrow(exception);

        // when
        var thrown = assertThrows(
                ResponseStatusException.class,
                () -> transferController.createTransfer(tenantId, idempotencyKey, request)
        );

        // then
        assertAll(
                () -> assertThat(thrown.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(thrown.getReason()).isEqualTo(errorMessage),
                () -> verify(transferService).transfer(new TenantId(tenantId), idempotencyKey, request),
                () -> verify(transferResultMapper).toTransferResponse(result)
        );
    }
}
