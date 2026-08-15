package com.lhy.jeict.network;

import com.lhy.jeict.JeiCraftingTreeMod;
import com.lhy.jeict.compat.sophisticated.SophisticatedFastCraftRequestPayload;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class JeictNetworking {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(JeiCraftingTreeMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION),
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION));

    private JeictNetworking() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, BatchCraftRequestPayload.class, BatchCraftRequestPayload::encode,
                BatchCraftRequestPayload::decode, BatchCraftRequestPayload::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, SophisticatedFastCraftRequestPayload.class,
                SophisticatedFastCraftRequestPayload::encode, SophisticatedFastCraftRequestPayload::decode,
                SophisticatedFastCraftRequestPayload::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, CreativeRefillRequestPayload.class, CreativeRefillRequestPayload::encode,
                CreativeRefillRequestPayload::decode, CreativeRefillRequestPayload::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id, BatchCraftResultPayload.class, BatchCraftResultPayload::encode,
                BatchCraftResultPayload::decode, BatchCraftResultPayload::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
