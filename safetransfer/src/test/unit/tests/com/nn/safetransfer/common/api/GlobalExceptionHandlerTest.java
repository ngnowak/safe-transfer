package com.nn.safetransfer.common.api;

import com.nn.safetransfer.transfer.application.exception.InsufficientFundsException;
import com.nn.safetransfer.transfer.application.exception.SameWalletTransferNotAllowedException;
import com.nn.safetransfer.wallet.application.exception.WalletCurrencyMismatchException;
import com.nn.safetransfer.wallet.application.exception.WalletNotFoundException;
import com.nn.safetransfer.wallet.application.exception.WalletOperationNotAllowedException;
import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpStatus.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private GlobalExceptionHandler handler;

    private static final String REQUEST_URI = "/api/v1/tenants/123/wallets";

    @Test
    void shouldHandleWalletNotFoundException() {
        // given
        var walletId = WalletId.create();
        var tenantId = TenantId.create();
        var exception = new WalletNotFoundException(walletId, tenantId);
        given(request.getRequestURI()).willReturn(REQUEST_URI);

        // when
        var problem = handler.handleWalletNotFound(exception, request);

        // then
        assertAll(
                () -> assertThat(problem.getStatus()).isEqualTo(NOT_FOUND.value()),
                () -> assertThat(problem.getTitle()).isEqualTo("Wallet not found"),
                () -> assertThat(problem.getDetail()).contains(walletId.value().toString()),
                () -> assertThat(problem.getProperties()).containsEntry("path", REQUEST_URI)
        );
    }

    @Test
    void shouldHandleWalletOperationNotAllowedException() {
        // given
        var exception = new WalletOperationNotAllowedException("Wallet is blocked");
        given(request.getRequestURI()).willReturn(REQUEST_URI);

        // when
        var problem = handler.handleWalletOperationNotAllowed(exception, request);

        // then
        assertAll(
                () -> assertThat(problem.getStatus()).isEqualTo(CONFLICT.value()),
                () -> assertThat(problem.getTitle()).isEqualTo("Wallet operation not allowed"),
                () -> assertThat(problem.getDetail()).isEqualTo("Wallet is blocked"),
                () -> assertThat(problem.getProperties()).containsEntry("path", REQUEST_URI)
        );
    }

    @Test
    void shouldHandleWalletCurrencyMismatchException() {
        // given
        var exception = new WalletCurrencyMismatchException(CurrencyCode.EUR, CurrencyCode.USD);
        given(request.getRequestURI()).willReturn(REQUEST_URI);

        // when
        var problem = handler.handleWalletCurrencyMismatch(exception, request);

        // then
        assertAll(
                () -> assertThat(problem.getStatus()).isEqualTo(BAD_REQUEST.value()),
                () -> assertThat(problem.getTitle()).isEqualTo("Wallet currency mismatch"),
                () -> assertThat(problem.getDetail()).contains("EUR").contains("USD"),
                () -> assertThat(problem.getProperties()).containsEntry("path", REQUEST_URI)
        );
    }

    @Test
    void shouldHandleInsufficientFundsException() {
        // given
        var walletId = WalletId.create();
        var exception = new InsufficientFundsException(walletId, new BigDecimal("50.00"), new BigDecimal("100.00"));
        given(request.getRequestURI()).willReturn(REQUEST_URI);

        // when
        var problem = handler.handleInsufficientFunds(exception, request);

        // then
        assertAll(
                () -> assertThat(problem.getStatus()).isEqualTo(CONFLICT.value()),
                () -> assertThat(problem.getTitle()).isEqualTo("Insufficient funds"),
                () -> assertThat(problem.getDetail()).contains("50").contains("100"),
                () -> assertThat(problem.getProperties()).containsEntry("path", REQUEST_URI)
        );
    }

    @Test
    void shouldHandleSameWalletTransferNotAllowedException() {
        // given
        var exception = new SameWalletTransferNotAllowedException();
        given(request.getRequestURI()).willReturn(REQUEST_URI);

        // when
        var problem = handler.handleSameWalletTransfer(exception, request);

        // then
        assertAll(
                () -> assertThat(problem.getStatus()).isEqualTo(BAD_REQUEST.value()),
                () -> assertThat(problem.getTitle()).isEqualTo("Invalid transfer"),
                () -> assertThat(problem.getProperties()).containsEntry("path", REQUEST_URI)
        );
    }

    @Test
    void shouldHandleIllegalArgumentException() {
        // given
        var exception = new IllegalArgumentException("Invalid input");
        given(request.getRequestURI()).willReturn(REQUEST_URI);

        // when
        var problem = handler.handleIllegalArgument(exception, request);

        // then
        assertAll(
                () -> assertThat(problem.getStatus()).isEqualTo(BAD_REQUEST.value()),
                () -> assertThat(problem.getTitle()).isEqualTo("Invalid request"),
                () -> assertThat(problem.getDetail()).isEqualTo("Invalid input"),
                () -> assertThat(problem.getProperties()).containsEntry("path", REQUEST_URI)
        );
    }

    @Test
    void shouldHandleUnexpectedException() {
        // given
        var exception = new RuntimeException("Something went wrong");
        given(request.getRequestURI()).willReturn(REQUEST_URI);

        // when
        var problem = handler.handleUnexpected(exception, request);

        // then
        assertAll(
                () -> assertThat(problem.getStatus()).isEqualTo(INTERNAL_SERVER_ERROR.value()),
                () -> assertThat(problem.getTitle()).isEqualTo("Internal server error"),
                () -> assertThat(problem.getDetail()).isEqualTo("Unexpected internal server error"),
                () -> assertThat(problem.getProperties()).containsEntry("path", REQUEST_URI)
        );
    }
}
