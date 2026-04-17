package com.nn.safetransfer.wallet.application.mapper;

import com.nn.safetransfer.wallet.api.dto.CreateWalletRequest;
import com.nn.safetransfer.wallet.domain.CurrencyCode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class CreateWalletCommandMapperTest {

    private final CreateWalletCommandMapper mapper = new CreateWalletCommandMapper();

    @Test
    void shouldMapToCreateWalletCommand() {
        // given
        var tenantId = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var request = new CreateWalletRequest(customerId, "PLN");

        // when
        var command = mapper.toCreateWalletCommand(tenantId, request);

        // then
        assertAll(
                () -> assertThat(command.tenantId().value()).isEqualTo(tenantId),
                () -> assertThat(command.customerId().value()).isEqualTo(customerId),
                () -> assertThat(command.currency()).isEqualTo(CurrencyCode.PLN)
        );
    }

    @Test
    void shouldMapCurrencyCodeCaseInsensitively() {
        // given
        var request = new CreateWalletRequest(UUID.randomUUID(), "eur");

        // when
        var command = mapper.toCreateWalletCommand(UUID.randomUUID(), request);

        // then
        assertThat(command.currency()).isEqualTo(CurrencyCode.EUR);
    }

    @Test
    void shouldThrowWhenCurrencyCodeIsInvalid() {
        // given
        var request = new CreateWalletRequest(UUID.randomUUID(), "INVALID");

        // when / then
        assertThatThrownBy(() -> mapper.toCreateWalletCommand(UUID.randomUUID(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID");
    }
}
