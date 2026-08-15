package com.lhy.jeict.network;

import com.lhy.jeict.JeiCraftingTreeMod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-validated insertion of one requested stack into the currently open menu. */
public record CreativeRefillRequestPayload(int containerId, int slot, ItemStack stack) {
    public static final ResourceLocation TYPE = new ResourceLocation(JeiCraftingTreeMod.MOD_ID, "creative_refill_request");

    public static void encode(CreativeRefillRequestPayload payload, FriendlyByteBuf buffer) {
        buffer.writeVarInt(payload.containerId());
        buffer.writeVarInt(payload.slot());
        buffer.writeItem(payload.stack());
    }

    public static CreativeRefillRequestPayload decode(FriendlyByteBuf buffer) {
        return new CreativeRefillRequestPayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readItem());
    }

    public static void handle(CreativeRefillRequestPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
        ServerPlayer player = context.getSender();
        if (player == null || !player.gameMode.isCreative()) return;
        if (payload.stack().isEmpty() || payload.stack().getCount() > payload.stack().getMaxStackSize()
                || player.containerMenu.containerId != payload.containerId()) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (payload.slot() < 0 || payload.slot() >= menu.slots.size()) return;
        Slot slot = menu.getSlot(payload.slot());
        if (slot.container == player.getInventory() || !slot.mayPlace(payload.stack())) return;
        ItemStack current = slot.getItem();
        if (!current.isEmpty() && !ItemStack.isSameItemSameTags(current, payload.stack())) return;
        int max = Math.min(slot.getMaxStackSize(), payload.stack().getMaxStackSize());
        if (!current.isEmpty() && current.getCount() + payload.stack().getCount() > max) return;
        slot.setByPlayer(payload.stack().copy());
        menu.broadcastChanges();
        });
        context.setPacketHandled(true);
    }
}
