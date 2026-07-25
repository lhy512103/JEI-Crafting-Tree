package com.lhy.jeict.api;

/**
 * Client-only provider for the non-player stock exposed by the currently open menu.
 *
 * <p>Methods run on the client render/tick thread. A provider must return a detached immutable snapshot and
 * must not retain the supplied menu or JEI manager beyond the current call.
 */
public interface MenuInventorySource {
    String id();

    default int priority() {
        return 0;
    }

    boolean supports(net.minecraft.world.inventory.AbstractContainerMenu menu);

    long version(net.minecraft.world.inventory.AbstractContainerMenu menu,
            mezz.jei.api.runtime.IIngredientManager ingredientManager);

    java.util.List<InventoryAmount> snapshot(net.minecraft.world.inventory.AbstractContainerMenu menu,
            mezz.jei.api.runtime.IIngredientManager ingredientManager);
}