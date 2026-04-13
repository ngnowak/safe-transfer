package com.nn.safetransfer.common.api.mapper;

import com.nn.safetransfer.common.domain.result.Result;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Function;

public abstract class AbstractResultMapper<E, V, R> {

    protected R mapResult(Result<E, V> result, Function<V, R> successMapper) {
        return switch (result) {
            case Result.Success<E, V> success -> mapSuccess(success, successMapper);
            case Result.Failure<E, ?> failure -> throw mapFailure(
                    failure.getError().orElseThrow(() -> new IllegalStateException("Expected error for failed result"))
            );
        };
    }

    protected R mapSuccess(Result<?, V> result, Function<V, R> successMapper) {
        return result.getValue()
                .map(successMapper)
                .orElseThrow(() -> new IllegalStateException("Expected value for successful result"));
    }

    protected abstract ResponseStatusException mapFailure(E error);
}
