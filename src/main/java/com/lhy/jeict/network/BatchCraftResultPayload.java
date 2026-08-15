package com.lhy.jeict.network;

import com.lhy.jeict.JeiCraftingTreeMod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record BatchCraftResultPayload(long requestId, int containerId, int crafted, int stored, int playerRemainder,
        boolean accepted) {
    public static final ResourceLocation TYPE = new ResourceLocation(JeiCraftingTreeMod.MOD_ID, "batch_craft_result");

    public static void encode(BatchCraftResultPayload payload, FriendlyByteBuf buffer) {
        buffer.writeVarLong(payload.requestId());
        buffer.writeVarInt(payload.containerId());
        buffer.writeVarInt(payload.crafted());
        buffer.writeVarInt(payload.stored());
        buffer.writeVarInt(payload.playerRemainder());
        buffer.writeBoolean(payload.accepted());
    }

    public static BatchCraftResultPayload decode(FriendlyByteBuf buffer) {
        return new BatchCraftResultPayload(buffer.readVarLong(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
    }

    public static BatchCraftResultPayload rejected(long requestId, int containerId) {
        return new BatchCraftResultPayload(requestId, containerId, 0, 0, 0, false);
    }

    public static void handle(BatchCraftResultPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> BatchCraftResultDispatch.handle(payload));
        context.setPacketHandled(true);
    }
}
