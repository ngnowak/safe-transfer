package com.nn.safetransfer.outbox.application;

public class OutboxProcessingException extends Exception {

    public OutboxProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
