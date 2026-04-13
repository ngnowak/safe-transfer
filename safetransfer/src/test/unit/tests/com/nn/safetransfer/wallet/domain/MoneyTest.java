package com.nn.safetransfer.wallet.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void shouldCreateMoney() {
        var money = Money.of(new BigDecimal("12.34"), CurrencyCode.EUR);

        assertThat(money.amount()).isEqualByComparingTo("12.34");
        assertThat(money.currency()).isEqualTo(CurrencyCode.EUR);
    }

    @Test
    void shouldThrowWhenAmountIsZero() {
        assertThatThrownBy(() -> Money.of(BigDecimal.ZERO, CurrencyCode.EUR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must be greater than zero");
    }

    @Test
    void shouldThrowWhenAmountIsNegative() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("-1.00"), CurrencyCode.EUR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must be greater than zero");
    }

    @Test
    void shouldThrowWhenAmountIsNull() {
        assertThatThrownBy(() -> Money.of(null, CurrencyCode.EUR))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("amount must not be null");
    }

    @Test
    void shouldThrowWhenCurrencyIsNull() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("1.00"), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("currency must not be null");
    }
}
