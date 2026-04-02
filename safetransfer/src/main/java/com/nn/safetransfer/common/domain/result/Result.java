package com.nn.safetransfer.common.domain.result;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public sealed interface Result<E, A> {

    record Success<E, A>(A value) implements Result<E, A> {
    }

    record Failure<E, A>(E error) implements Result<E, A> {
    }

    static <E, A> Result<E, A> success(final A successResponse) {
        return new Success<>(successResponse);
    }

    static <E, A> Result<E, A> failure(final E error) {
        return new Failure<>(error);
    }

    static <E> Result<E, Empty> emptySuccessResult() {
        return success(Empty.getInstance());
    }

    default boolean isSuccess() {
        return this instanceof Success<E, A>(A value) && Objects.nonNull(value);
    }

    default boolean isFailure() {
        return this instanceof Failure<E, A>(E error) && Objects.nonNull(error);
    }

    default Optional<A> getValue() {
        return this instanceof Success<E, A>(A value) ? Optional.ofNullable(value) : Optional.empty();
    }

    default Optional<E> getError() {
        return this instanceof Failure<E, A>(E error) ? Optional.ofNullable(error) : Optional.empty();
    }

    default <B> Result<E, B> map(final Function<? super A, ? extends B> mapper) {
        return this instanceof Success<E, A>(A value)
                ? success(mapper.apply(value))
                : failure(((Failure<E, A>) this).error);
    }

    default <B> Result<E, B> flatMap(final Function<? super A, Result<E, B>> mapper) {
        return this instanceof Success<E, A>(A value)
                ? mapper.apply(value)
                : failure(((Failure<E, A>) this).error);
    }

    default <F> Result<F, A> mapError(final Function<? super E, ? extends F> mapper) {
        return this instanceof Failure<E, A>(E error)
                ? failure(mapper.apply(error))
                : success(((Success<E, A>) this).value());
    }

}
