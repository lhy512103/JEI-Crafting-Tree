package com.lhy.jeict.integration;

import mezz.jei.api.runtime.IJeiRuntime;

import java.util.Optional;

public final class JeiRuntimeAccess {
    private static IJeiRuntime runtime;

    private JeiRuntimeAccess() {
    }

    public static void set(IJeiRuntime runtime) {
        JeiRuntimeAccess.runtime = runtime;
    }

    public static void clear() {
        runtime = null;
    }

    public static Optional<IJeiRuntime> get() {
        return Optional.ofNullable(runtime);
    }
}
