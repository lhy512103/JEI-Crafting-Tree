package com.lhy.jeict.network;

import com.lhy.jeict.JeiCraftingTreeMod;
import com.lhy.jeict.compat.ae2.Ae2BatchCraftBootstrap;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BatchCraftRequestPayload(long requestId, int containerId, int resultSlot, ItemStack expectedOutput)
        implements CustomPacketPayload {
    public static final Type<BatchCraftRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(JeiCraftingTreeMod.MOD_ID, "batch_craft_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BatchCraftRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, BatchCraftRequestPayload::requestId,
            ByteBufCodecs.VAR_INT, BatchCraftRequestPayload::containerId,
            ByteBufCodecs.VAR_INT, BatchCraftRequestPayload::resultSlot,
            ItemStack.OPTIONAL_STREAM_CODEC, BatchCraftRequestPayload::expectedOutput,
            BatchCraftRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BatchCraftRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        BatchCraftResultPayload result = ModList.get().isLoaded("ae2")
                ? Ae2BatchCraftBootstrap.execute(player, payload)
                : BatchCraftResultPayload.rejected(payload.requestId(), payload.containerId());
        context.reply(result);
    }
}