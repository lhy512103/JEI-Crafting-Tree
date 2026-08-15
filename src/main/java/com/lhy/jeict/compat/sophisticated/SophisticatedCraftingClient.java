package com.lhy.jeict.compat.sophisticated;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IStackHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.common.transfer.RecipeTransferUtil;
import mezz.jei.common.transfer.TransferOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.p3pp3rf1y.sophisticatedcore.common.gui.ICraftingContainer;
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;
import com.lhy.jeict.debug.AutoCraftDebug;

/** Client-only vanilla-click fallback for servers that do not have JEICT installed. */
public final class SophisticatedCraftingClient {
    private SophisticatedCraftingClient() {}

    public static boolean supports(AbstractContainerMenu menu) {
        return menu instanceof StorageContainerMenuBase<?> storageMenu
                && storageMenu.getOpenOrFirstCraftingContainer(RecipeType.CRAFTING).isPresent();
    }

    public static boolean transfer(AbstractContainerMenu menu, Player player, IRecipeSlotsView slotsView,
            IStackHelper stackHelper) {
        if (!(menu instanceof StorageContainerMenuBase<?> storageMenu)) return false;
        var upgrade = storageMenu.getOpenOrFirstCraftingContainer(RecipeType.CRAFTING).orElse(null);
        ICraftingContainer crafting = (ICraftingContainer) upgrade;
        if (crafting == null) {
            AutoCraftDebug.log("no crafting upgrade: menu={} upgrades={}", menu.getClass().getName(),
                    storageMenu.getUpgradeContainers().size());
            return false;
        }
        List<Slot> craftingSlots = crafting.getRecipeSlots();
        List<IRecipeSlotView> inputs = slotsView.getSlotViews(RecipeIngredientRole.INPUT);
        AutoCraftDebug.log("crafting upgrade={} gridSlots={} inputViews={} indexes={}",
                upgrade.getClass().getName(), craftingSlots.size(), inputs.size(),
                craftingSlots.stream().map(slot -> slot.index).toList());
        if (craftingSlots.isEmpty() || inputs.size() > craftingSlots.size()) return false;

        for (Slot slot : craftingSlots) {
            if (slot.hasItem()) click(menu, player, slot.index, 0, ClickType.QUICK_MOVE);
        }

        Map<Slot, ItemStack> available = new HashMap<>();
        for (Slot slot : menu.slots) {
            if (slot.mayPickup(player) && slot.hasItem()) available.put(slot, slot.getItem().copy());
        }
        var operations = RecipeTransferUtil.getRecipeTransferOperations(stackHelper, available, inputs, craftingSlots);
        AutoCraftDebug.log("transfer operations={} missing={}", operations.results.size(), operations.missingItems.size());
        if (!operations.missingItems.isEmpty()) return false;
        for (TransferOperation operation : operations.results) {
            Slot source = menu.getSlot(operation.inventorySlotId());
            Slot destination = menu.getSlot(operation.craftingSlotId());
            AutoCraftDebug.log("click source={} {}x{} -> destination={} {} count={}", source.index,
                    source.getItem().getHoverName().getString(), source.getItem().getCount(), destination.index,
                    destination.getItem().getCount(), operation.craftingSlotId());
            click(menu, player, source.index, 0, ClickType.PICKUP);
            for (int count = 0; count < operation.craftingSlotId(); count++) {
                click(menu, player, destination.index, 1, ClickType.PICKUP);
            }
            if (!menu.getCarried().isEmpty()) click(menu, player, source.index, 0, ClickType.PICKUP);
            AutoCraftDebug.log("after click source={}={} destination={}={} carried={}", source.index,
                    source.getItem().getCount(), destination.index, destination.getItem().getCount(),
                    menu.getCarried().getCount());
        }
        return true;
    }

    public static @Nullable Slot findResultSlot(AbstractContainerMenu menu, Player player, ItemStack expectedOutput) {
        if (expectedOutput == null || expectedOutput.isEmpty()
                || !(menu instanceof StorageContainerMenuBase<?> storageMenu)) return null;
        for (Slot slot : storageMenu.upgradeSlots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty() && slot.mayPickup(player)
                    && ItemStack.isSameItemSameTags(stack, expectedOutput)) return slot;
        }
        return null;
    }

    private static void click(AbstractContainerMenu menu, Player player, int slot, int button, ClickType type) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, type, player);
        }
    }
}
