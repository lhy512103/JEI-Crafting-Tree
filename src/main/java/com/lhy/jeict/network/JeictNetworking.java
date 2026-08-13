package com.lhy.jeict.network;

import com.lhy.jeict.JeiCraftingTreeMod;
import com.lhy.jeict.compat.sophisticated.SophisticatedFastCraftRequestPayload;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

public final class JeictNetworking {
    private JeictNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(JeiCraftingTreeMod.MOD_ID)
                .optional()
                .executesOn(HandlerThread.MAIN)
                .playToServer(BatchCraftRequestPayload.TYPE, BatchCraftRequestPayload.STREAM_CODEC,
                        BatchCraftRequestPayload::handle)
                .playToServer(SophisticatedFastCraftRequestPayload.TYPE,
                        SophisticatedFastCraftRequestPayload.STREAM_CODEC,
                        SophisticatedFastCraftRequestPayload::handle)
                .playToServer(CreativeRefillRequestPayload.TYPE, CreativeRefillRequestPayload.STREAM_CODEC,
                        CreativeRefillRequestPayload::handle)
                .playToClient(BatchCraftResultPayload.TYPE, BatchCraftResultPayload.STREAM_CODEC,
                        BatchCraftResultPayload::handle);
    }
}