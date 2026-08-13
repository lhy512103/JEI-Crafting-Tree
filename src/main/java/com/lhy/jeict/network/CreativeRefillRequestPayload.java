package com.lhy.jeict.network;

import com.lhy.jeict.JeiCraftingTreeMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-validated insertion of one requested stack into the currently open menu. */
public record CreativeRefillRequestPayload(int containerId, int slot, ItemStack stack)
        implements CustomPacketPayload {
    public static final Type<CreativeRefillRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(JeiCraftingTreeMod.MOD_ID, "creative_refill_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CreativeRefillRequestPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, CreativeRefillRequestPayload::containerId,
                    ByteBufCodecs.VAR_INT, CreativeRefillRequestPayload::slot,
                    ItemStack.OPTIONAL_STREAM_CODEC, CreativeRefillRequestPayload::stack,
                    CreativeRefillRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CreativeRefillRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !player.gameMode.isCreative()) return;
        if (payload.stack().isEmpty() || payload.stack().getCount() > payload.stack().getMaxStackSize()
                || player.containerMenu.containerId != payload.containerId()) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (payload.slot() < 0 || payload.slot() >= menu.slots.size()) return;
        Slot slot = menu.getSlot(payload.slot());
        if (slot.container == player.getInventory() || !slot.mayPlace(payload.stack())) return;
        ItemStack current = slot.getItem();
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, payload.stack())) return;
        int max = Math.min(slot.getMaxStackSize(), payload.stack().getMaxStackSize());
        if (!current.isEmpty() && current.getCount() + payload.stack().getCount() > max) return;
        slot.setByPlayer(payload.stack().copy());
        menu.broadcastChanges();
    }
}