package com.nn.safetransfer.common.api;

public final class ApiHeaders {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final int IDEMPOTENCY_KEY_MAX_LENGTH = 100;
    public static final String IDEMPOTENCY_KEY_BLANK_MESSAGE = "Idempotency-Key header must not be blank";
    public static final String IDEMPOTENCY_KEY_TOO_LONG_MESSAGE = "Idempotency-Key header must not be longer than 100 characters";

    private ApiHeaders() {
    }
}
