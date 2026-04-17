package com.nn.safetransfer.wallet.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrencyCodeTest {

    @ParameterizedTest
    @EnumSource(CurrencyCode.class)
    void shouldResolveCurrencyCodeFromMatchingName(CurrencyCode currencyCode) {
        // when
        var result = CurrencyCode.from(currencyCode.name());

        // then
        assertThat(result).isEqualTo(currencyCode);
    }

    @Test
    void shouldResolveCurrencyCodeCaseInsensitively() {
        // when / then
        assertThat(CurrencyCode.from("eur")).isEqualTo(CurrencyCode.EUR);
        assertThat(CurrencyCode.from("Pln")).isEqualTo(CurrencyCode.PLN);
        assertThat(CurrencyCode.from("usd")).isEqualTo(CurrencyCode.USD);
        assertThat(CurrencyCode.from("gbp")).isEqualTo(CurrencyCode.GBP);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVALID", "XYZ", "CHF"})
    void shouldThrowForUnsupportedCurrencyCode(String code) {
        // when / then
        assertThatThrownBy(() -> CurrencyCode.from(code))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(code);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldThrowForNullAndEmptyCurrencyCode(String code) {
        // when / then
        assertThatThrownBy(() -> CurrencyCode.from(code))
                .isInstanceOf(Exception.class);
    }
}
