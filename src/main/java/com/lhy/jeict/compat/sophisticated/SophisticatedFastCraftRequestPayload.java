package com.lhy.jeict.compat.sophisticated;

import com.lhy.jeict.JeiCraftingTreeMod;
import com.lhy.jeict.network.BatchCraftResultPayload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SophisticatedFastCraftRequestPayload(long requestId, int containerId, int resultSlot,
        ItemStack expectedOutput) {
    public static final ResourceLocation TYPE = new ResourceLocation(JeiCraftingTreeMod.MOD_ID, "sophisticated_fast_craft");

    public static void encode(SophisticatedFastCraftRequestPayload payload, FriendlyByteBuf buffer) {
        buffer.writeVarLong(payload.requestId());
        buffer.writeVarInt(payload.containerId());
        buffer.writeVarInt(payload.resultSlot());
        buffer.writeItem(payload.expectedOutput());
    }

    public static SophisticatedFastCraftRequestPayload decode(FriendlyByteBuf buffer) {
        return new SophisticatedFastCraftRequestPayload(buffer.readVarLong(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readItem());
    }

    public static void handle(SophisticatedFastCraftRequestPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
        ServerPlayer player = context.getSender();
        BatchCraftResultPayload result = player != null
                && ModList.get().isLoaded("sophisticatedcore")
                ? SophisticatedFastCraftServer.execute(player, payload)
                : BatchCraftResultPayload.rejected(payload.requestId(), payload.containerId());
        com.lhy.jeict.network.JeictNetworking.CHANNEL.reply(result, context);
        });
        context.setPacketHandled(true);
    }
}
