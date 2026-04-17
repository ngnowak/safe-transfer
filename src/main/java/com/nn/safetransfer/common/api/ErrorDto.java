package com.nn.safetransfer.common.api;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record ErrorDto(
        UUID errorId,
        String errorMessage,
        List<String> errors
) {
}
