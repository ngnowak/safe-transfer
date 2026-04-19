package com.nn.safetransfer.common.api;

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
