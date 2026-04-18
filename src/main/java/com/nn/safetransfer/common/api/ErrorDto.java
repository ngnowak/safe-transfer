package com.nn.safetransfer.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
@Schema(description = "Error response returned by the API.")
public record ErrorDto(
        @Schema(description = "Unique identifier of this error occurrence.", example = "018f1254-2f2c-7b54-9e6b-f30da9b0721e")
        UUID errorId,
        @Schema(description = "Human-readable error message.", example = "Validation failed")
        String errorMessage,
        @Schema(description = "Field-level validation errors, when available.", example = "[\"amount: must be greater than or equal to 0.01\"]")
        List<String> errors
) {
}
