package com.nn.safetransfer.common.api;

import com.nn.safetransfer.wallet.application.exception.WalletCurrencyMismatchException;
import com.nn.safetransfer.wallet.application.exception.WalletNotFoundException;
import com.nn.safetransfer.wallet.application.exception.WalletOperationNotAllowedException;
import com.nn.safetransfer.wallet.domain.CurrencyCode;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.http.HttpStatus.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler handler;

    @Test
    void shouldHandleResponseStatusException() {
        // given
        var exception = new ResponseStatusException(UNPROCESSABLE_CONTENT, "Transfer rejected");

        // when
        var response = handler.handleResponseStatusException(exception);

        // then
        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(UNPROCESSABLE_CONTENT),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().errorId()).isNotNull(),
                () -> assertThat(response.getBody().errorMessage()).isEqualTo("Transfer rejected"),
                () -> assertThat(response.getBody().errors()).isNull()
        );
    }

    @Test
    void shouldHandleWalletNotFoundException() {
        // given
        var walletId = WalletId.create();
        var tenantId = TenantId.create();
        var exception = new WalletNotFoundException(walletId, tenantId);
        var expectedErrorMessage = "Wallet with id '%s' was not found for tenant '%s'"
                .formatted(walletId.value(), tenantId.value());

        // when
        var response = handler.handleWalletNotFound(exception);

        // then
        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().errorId()).isNotNull(),
                () -> assertThat(response.getBody().errorMessage()).isEqualTo(expectedErrorMessage),
                () -> assertThat(response.getBody().errors()).isNull()
        );
    }

    @Test
    void shouldHandleWalletOperationNotAllowedException() {
        // given
        var exception = new WalletOperationNotAllowedException("Wallet is blocked");

        // when
        var response = handler.handleWalletOperationNotAllowed(exception);

        // then
        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(CONFLICT),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().errorId()).isNotNull(),
                () -> assertThat(response.getBody().errorMessage()).isEqualTo("Wallet is blocked"),
                () -> assertThat(response.getBody().errors()).isNull()
        );
    }

    @Test
    void shouldHandleWalletCurrencyMismatchException() {
        // given
        var walletCurrency = CurrencyCode.EUR;
        var requestCurrency = CurrencyCode.USD;
        var exception = new WalletCurrencyMismatchException(walletCurrency, requestCurrency);
        var expectedErrorMessage = "Wallet currency is '%s' but request currency is '%s'"
                .formatted(walletCurrency, requestCurrency);

        // when
        var response = handler.handleWalletCurrencyMismatch(exception);

        // then
        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().errorId()).isNotNull(),
                () -> assertThat(response.getBody().errorMessage()).isEqualTo(expectedErrorMessage),
                () -> assertThat(response.getBody().errors()).isNull()
        );
    }

    @Test
    void shouldHandleIllegalArgumentException() {
        // given
        var exception = new IllegalArgumentException("Invalid input");

        // when
        var response = handler.handleIllegalArgument(exception);

        // then
        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().errorId()).isNotNull(),
                () -> assertThat(response.getBody().errorMessage()).isEqualTo("Invalid input"),
                () -> assertThat(response.getBody().errors()).isNull()
        );
    }

    @Test
    void shouldHandleMethodArgumentNotValidException() {
        // given
        var bindingResult = mock(BindingResult.class);
        var exception = mock(MethodArgumentNotValidException.class);
        given(bindingResult.getFieldErrors()).willReturn(java.util.List.of(
                new FieldError("request", "amount", "must be positive"),
                new FieldError("request", "currency", "must not be blank")
        ));
        given(exception.getBindingResult()).willReturn(bindingResult);
        given(exception.getMessage()).willReturn("Validation failed");

        // when
        var response = handler.handleMethodArgumentNotValid(exception);

        // then
        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().errorId()).isNotNull(),
                () -> assertThat(response.getBody().errorMessage()).isEqualTo(exception.getMessage()),
                () -> assertThat(response.getBody().errors()).containsExactly("amount: must be positive, currency: must not be blank")
        );
    }

    @Test
    void shouldHandleConstraintViolationException() {
        // given
        var exception = new ConstraintViolationException("Validation failed", Set.of());

        // when
        var response = handler.handleConstraintViolation(exception);

        // then
        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().errorId()).isNotNull(),
                () -> assertThat(response.getBody().errorMessage()).isEqualTo("Validation failed"),
                () -> assertThat(response.getBody().errors()).isNull()
        );
    }

    @Test
    void shouldHandleUnexpectedException() {
        // given
        var exception = new RuntimeException("Something went wrong");

        // when
        var response = handler.handleUnexpected(exception);

        // then
        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().errorId()).isNotNull(),
                () -> assertThat(response.getBody().errorMessage()).isEqualTo("Unexpected error occurred"),
                () -> assertThat(response.getBody().errors()).isNull()
        );
    }
}
