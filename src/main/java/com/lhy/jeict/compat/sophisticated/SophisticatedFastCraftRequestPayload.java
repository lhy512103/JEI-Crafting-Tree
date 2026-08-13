package com.lhy.jeict.compat.sophisticated;

import com.lhy.jeict.JeiCraftingTreeMod;
import com.lhy.jeict.network.BatchCraftResultPayload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SophisticatedFastCraftRequestPayload(long requestId, int containerId, int resultSlot,
        ItemStack expectedOutput) implements CustomPacketPayload {
    public static final Type<SophisticatedFastCraftRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(JeiCraftingTreeMod.MOD_ID, "sophisticated_fast_craft"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SophisticatedFastCraftRequestPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_LONG, SophisticatedFastCraftRequestPayload::requestId,
                    ByteBufCodecs.VAR_INT, SophisticatedFastCraftRequestPayload::containerId,
                    ByteBufCodecs.VAR_INT, SophisticatedFastCraftRequestPayload::resultSlot,
                    ItemStack.OPTIONAL_STREAM_CODEC, SophisticatedFastCraftRequestPayload::expectedOutput,
                    SophisticatedFastCraftRequestPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SophisticatedFastCraftRequestPayload payload, IPayloadContext context) {
        BatchCraftResultPayload result = context.player() instanceof ServerPlayer player
                && ModList.get().isLoaded("sophisticatedcore")
                ? SophisticatedFastCraftServer.execute(player, payload)
                : BatchCraftResultPayload.rejected(payload.requestId(), payload.containerId());
        context.reply(result);
    }
}
