package com.nn.safetransfer.transfer.api;

import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import com.nn.safetransfer.transfer.api.dto.TransferResponse;
import com.nn.safetransfer.transfer.api.mapper.TransferResponseMapper;
import com.nn.safetransfer.transfer.application.TransferService;
import com.nn.safetransfer.transfer.domain.Transfer;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransferControllerTest {

    @Mock
    private TransferService transferService;

    @Mock
    private TransferResponseMapper transferResponseMapper;

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

        given(transferService.transfer(new TenantId(tenantId), idempotencyKey, request))
                .willReturn(transfer);
        given(transferResponseMapper.toResponse(transfer))
                .willReturn(expectedResponse);

        // when
        var response = transferController.createTransfer(tenantId, idempotencyKey, request);

        // then
        assertAll(
                () -> assertThat(response).isEqualTo(expectedResponse),
                () -> verify(transferService).transfer(new TenantId(tenantId), idempotencyKey, request),
                () -> verify(transferResponseMapper).toResponse(transfer)
        );
    }
}
