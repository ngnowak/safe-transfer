package com.nn.safetransfer.transfer.application;

import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TransferRiskPolicy {

    private final TransferRiskProperties properties;

    public Optional<TransferError> validate(CreateTransferRequest request) {
        if (!properties.enabled()) {
            return Optional.empty();
        }

        if (request.amount().compareTo(properties.maxSingleTransferAmount()) > 0) {
            return Optional.of(new TransferError.TransferLimitExceeded(
                    request.amount(),
                    properties.maxSingleTransferAmount()
            ));
        }

        return Optional.empty();
    }
}
