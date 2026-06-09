package com.lhy.jeict.client;

import com.lhy.jeict.Jeict;
import com.lhy.jeict.client.screen.RecipeTreeScreen;
import com.lhy.jeict.tree.RecipeGraphCache;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Jeict.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static final KeyMapping OPEN_TREE = new KeyMapping(
            "key.jeict.open_tree",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.jeict"
    );

    private ClientEvents() {
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_TREE);
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_TREE.consumeClick()) {
            if (minecraft.player == null || minecraft.level == null) {
                continue;
            }
            ItemStack goal = minecraft.player.getMainHandItem();
            if (goal.isEmpty()) {
                goal = minecraft.player.getOffhandItem();
            }
            if (goal.isEmpty()) {
                continue;
            }
            ItemStack finalGoal = goal.copy();
            RecipeGraphCache.get(minecraft)
                    .flatMap(cache -> cache.createTree(finalGoal))
                    .ifPresent(tree -> minecraft.setScreen(new RecipeTreeScreen(tree)));
        }
    }
}
