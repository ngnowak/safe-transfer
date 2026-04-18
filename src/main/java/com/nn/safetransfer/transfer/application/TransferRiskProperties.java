package com.nn.safetransfer.transfer.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "safetransfer.transfer.risk")
public record TransferRiskProperties(
        boolean enabled,
        BigDecimal maxSingleTransferAmount
) {
}
