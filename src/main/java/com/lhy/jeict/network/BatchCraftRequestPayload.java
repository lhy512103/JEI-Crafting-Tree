package com.lhy.jeict.network;

import com.lhy.jeict.JeiCraftingTreeMod;
import com.lhy.jeict.compat.ae2.Ae2BatchCraftBootstrap;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record BatchCraftRequestPayload(long requestId, int containerId, int resultSlot, ItemStack expectedOutput) {
    public static final ResourceLocation TYPE = new ResourceLocation(JeiCraftingTreeMod.MOD_ID, "batch_craft_request");

    public static void encode(BatchCraftRequestPayload payload, FriendlyByteBuf buffer) {
        buffer.writeVarLong(payload.requestId());
        buffer.writeVarInt(payload.containerId());
        buffer.writeVarInt(payload.resultSlot());
        buffer.writeItem(payload.expectedOutput());
    }

    public static BatchCraftRequestPayload decode(FriendlyByteBuf buffer) {
        return new BatchCraftRequestPayload(buffer.readVarLong(), buffer.readVarInt(), buffer.readVarInt(), buffer.readItem());
    }

    public static void handle(BatchCraftRequestPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
        ServerPlayer player = context.getSender();
        if (player == null) return;
        BatchCraftResultPayload result = ModList.get().isLoaded("ae2")
                ? Ae2BatchCraftBootstrap.execute(player, payload)
                : BatchCraftResultPayload.rejected(payload.requestId(), payload.containerId());
        JeictNetworking.CHANNEL.reply(result, context);
        });
        context.setPacketHandled(true);
    }
}
