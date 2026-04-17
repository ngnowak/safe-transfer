package com.nn.safetransfer.wallet.domain;

import java.util.Arrays;

public enum CurrencyCode {
    PLN, EUR, USD, GBP;

    public static CurrencyCode from(String currencyCode) {
        return Arrays.stream(values())
                .filter(value -> value.name().equalsIgnoreCase(currencyCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No currency code for code: `%s`".formatted(currencyCode)));
    }
}
