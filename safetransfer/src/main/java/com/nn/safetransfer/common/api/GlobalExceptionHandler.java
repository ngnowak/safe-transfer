package com.nn.safetransfer.common.api;

import com.nn.safetransfer.wallet.application.exception.WalletCurrencyMismatchException;
import com.nn.safetransfer.wallet.application.exception.WalletNotFoundException;
import com.nn.safetransfer.wallet.application.exception.WalletOperationNotAllowedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

import static java.util.stream.Collectors.joining;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WalletNotFoundException.class)
    public ProblemDetail handleWalletNotFound(WalletNotFoundException ex, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(NOT_FOUND, ex.getMessage());
        problem.setTitle("Wallet not found");
        problem.setType(URI.create("https://api.safetransfer.local/errors/wallet-not-found"));
        problem.setProperty("path", request.getRequestURI());
        return problem;
    }

    @ExceptionHandler(WalletOperationNotAllowedException.class)
    public ProblemDetail handleWalletOperationNotAllowed(WalletOperationNotAllowedException ex, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(CONFLICT, ex.getMessage());
        problem.setTitle("Wallet operation not allowed");
        problem.setType(URI.create("https://api.safetransfer.local/errors/wallet-operation-not-allowed"));
        problem.setProperty("path", request.getRequestURI());
        return problem;
    }

    @ExceptionHandler(WalletCurrencyMismatchException.class)
    public ProblemDetail handleWalletCurrencyMismatch(WalletCurrencyMismatchException ex, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(BAD_REQUEST, ex.getMessage());
        problem.setTitle("Wallet currency mismatch");
        problem.setType(URI.create("https://api.safetransfer.local/errors/wallet-currency-mismatch"));
        problem.setProperty("path", request.getRequestURI());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid request");
        problem.setType(URI.create("https://api.safetransfer.local/errors/invalid-request"));
        problem.setProperty("path", request.getRequestURI());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        var detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(joining(", "));
        var problem = ProblemDetail.forStatusAndDetail(BAD_REQUEST, detail);
        problem.setTitle("Validation failed");
        problem.setType(URI.create("https://api.safetransfer.local/errors/validation-failed"));
        problem.setProperty("path", request.getRequestURI());
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(BAD_REQUEST, ex.getMessage());
        problem.setTitle("Constraint violation");
        problem.setType(URI.create("https://api.safetransfer.local/errors/constraint-violation"));
        problem.setProperty("path", request.getRequestURI());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected internal server error");
        problem.setTitle("Internal server error");
        problem.setType(URI.create("https://api.safetransfer.local/errors/internal-server-error"));
        problem.setProperty("path", request.getRequestURI());
        return problem;
    }
}
