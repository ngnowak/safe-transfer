package com.nn.safetransfer.common.api;

import com.nn.safetransfer.wallet.application.exception.WalletCurrencyMismatchException;
import com.nn.safetransfer.wallet.application.exception.WalletNotFoundException;
import com.nn.safetransfer.wallet.application.exception.WalletOperationNotAllowedException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.joining;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus(BAD_REQUEST)
    public ResponseEntity<ErrorDto> handleResponseStatusException(ResponseStatusException statusException) {
        log.warn("The result was error: {}", statusException.getMessage(), statusException);
        return new ResponseEntity<>(buildErrorDto(statusException.getReason()), statusException.getStatusCode());
    }

    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<ErrorDto> handleWalletNotFound(WalletNotFoundException ex) {
        log.warn(ex.getMessage(), ex);
        return new ResponseEntity<>(buildErrorDto(ex.getMessage()), NOT_FOUND);
    }

    @ExceptionHandler(WalletOperationNotAllowedException.class)
    public ResponseEntity<ErrorDto> handleWalletOperationNotAllowed(WalletOperationNotAllowedException ex) {
        log.warn(ex.getMessage(), ex);
        return new ResponseEntity<>(buildErrorDto(ex.getMessage()), CONFLICT);
    }

    @ExceptionHandler(WalletCurrencyMismatchException.class)
    public ResponseEntity<ErrorDto> handleWalletCurrencyMismatch(WalletCurrencyMismatchException ex) {
        log.warn(ex.getMessage(), ex);
        return new ResponseEntity<>(buildErrorDto(ex.getMessage()), BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorDto> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn(ex.getMessage(), ex);
        return new ResponseEntity<>(buildErrorDto(ex.getMessage()), BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDto> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        var detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(joining(", "));
        log.warn(detail, ex);
        return new ResponseEntity<>(buildErrorDto(ex.getMessage(), List.of(detail)), BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorDto> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn(ex.getMessage(), ex);
        return new ResponseEntity<>(buildErrorDto(ex.getMessage()), BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> handleUnexpected(Exception ex) {
        var errorMsg = "Unexpected error occurred";
        log.warn(errorMsg, ex);
        return new ResponseEntity<>(buildErrorDto(errorMsg), INTERNAL_SERVER_ERROR);
    }

    private ErrorDto buildErrorDto(final String message) {
        return ErrorDto.builder()
                .errorId(randomUUID())
                .errorMessage(message)
                .build();
    }

    private ErrorDto buildErrorDto(final String message, final List<String> detailedErrors) {
        return ErrorDto.builder()
                .errorId(randomUUID())
                .errorMessage(message)
                .errors(detailedErrors)
                .build();
    }
}
