package com.lhy.jeict.api;

/** A removable registration or listener subscription. This operation is idempotent. */
@FunctionalInterface
public interface ApiRegistration extends AutoCloseable {
    ApiRegistration NOOP = () -> {
    };

    void unregister();

    @Override
    default void close() {
        unregister();
    }
}