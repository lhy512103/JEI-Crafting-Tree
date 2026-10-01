package com.lhy.jeict.network;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

/**
 * Server-validated batch of slot fills for the currently open menu. Every {@link Fill} carries the desired final
 * stack of a slot; the server only ever grows a slot, never removes or replaces what is already in it.
 */
public record CreativeRefillRequestPayload(int containerId, List<Fill> fills) implements CustomPacketPayload {
    /** Fills per packet; keeps each packet well below the serverbound custom payload size limit. */
    public static final int MAX_FILLS_PER_PACKET = 64;

    public static final Type<CreativeRefillRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(JeiCraftingTreeMod.MOD_ID, "creative_refill_batch"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CreativeRefillRequestPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, CreativeRefillRequestPayload::containerId,
                    Fill.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_FILLS_PER_PACKET)),
                    CreativeRefillRequestPayload::fills,
                    CreativeRefillRequestPayload::new);

    public CreativeRefillRequestPayload {
        fills = List.copyOf(fills);
    }

    public record Fill(int slot, ItemStack stack) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Fill> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, Fill::slot,
                        ItemStack.OPTIONAL_STREAM_CODEC, Fill::stack,
                        Fill::new);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CreativeRefillRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !player.gameMode.isCreative()) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != payload.containerId() || payload.fills().size() > menu.slots.size()) return;
        Set<Integer> seen = new HashSet<>();
        boolean changed = false;
        for (Fill fill : payload.fills()) {
            if (fill.slot() < 0 || fill.slot() >= menu.slots.size() || !seen.add(fill.slot())) continue;
            if (apply(player, menu.getSlot(fill.slot()), fill.stack())) changed = true;
        }
        if (changed) menu.broadcastChanges();
    }

    private static boolean apply(ServerPlayer player, Slot slot, ItemStack wanted) {
        if (wanted.isEmpty() || !isRefillTarget(player, slot) || !slot.mayPlace(wanted)) return false;
        ItemStack current = slot.getItem();
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, wanted)) return false;
        // The stack-aware overload is what container mods (Sophisticated Storage, ...) override to apply
        // slot-size upgrades; the no-arg one stays at the vanilla 64.
        if (wanted.getCount() > slot.getMaxStackSize(wanted) || wanted.getCount() <= current.getCount()) return false;
        slot.setByPlayer(wanted.copy());
        return true;
    }

    /** Menu slots, plus the 36 main/hotbar slots of the player inventory but not armor or off-hand. */
    public static boolean isRefillTarget(net.minecraft.world.entity.player.Player player, Slot slot) {
        return slot.container != player.getInventory()
                || slot.getContainerSlot() < player.getInventory().items.size();
    }
}
