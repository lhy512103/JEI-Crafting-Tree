package com.lhy.jeict.network;

import com.lhy.jeict.JeiCraftingTreeMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BatchCraftResultPayload(long requestId, int containerId, int crafted, int stored, int playerRemainder,
        boolean accepted) implements CustomPacketPayload {
    public static final Type<BatchCraftResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(JeiCraftingTreeMod.MOD_ID, "batch_craft_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BatchCraftResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, BatchCraftResultPayload::requestId,
            ByteBufCodecs.VAR_INT, BatchCraftResultPayload::containerId,
            ByteBufCodecs.VAR_INT, BatchCraftResultPayload::crafted,
            ByteBufCodecs.VAR_INT, BatchCraftResultPayload::stored,
            ByteBufCodecs.VAR_INT, BatchCraftResultPayload::playerRemainder,
            ByteBufCodecs.BOOL, BatchCraftResultPayload::accepted,
            BatchCraftResultPayload::new);

    public static BatchCraftResultPayload rejected(long requestId, int containerId) {
        return new BatchCraftResultPayload(requestId, containerId, 0, 0, 0, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BatchCraftResultPayload payload, IPayloadContext context) {
        BatchCraftResultDispatch.handle(payload);
    }
}