package com.lhy.jeict.network;

import java.util.function.Consumer;

/** Keeps the common payload type independent from client-only session classes. */
public final class BatchCraftResultDispatch {
    private static Consumer<BatchCraftResultPayload> handler = ignored -> { };

    private BatchCraftResultDispatch() {
    }

    public static void registerClientHandler(Consumer<BatchCraftResultPayload> clientHandler) {
        handler = clientHandler;
    }

    public static void handle(BatchCraftResultPayload result) {
        handler.accept(result);
    }
}