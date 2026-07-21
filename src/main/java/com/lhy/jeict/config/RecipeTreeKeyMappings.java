package com.lhy.jeict.config;

import com.lhy.jeict.client.RecipeTreeOverviewScreen;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import com.mojang.blaze3d.platform.InputConstants;

/** User-editable keyboard shortcuts for tree navigation and history. */
public final class RecipeTreeKeyMappings {
    public static final KeyMapping UNDO = new KeyMapping("key.jeict.undo", KeyConflictContext.GUI,
            KeyModifier.CONTROL, InputConstants.Type.KEYSYM, InputConstants.KEY_Z, "key.categories.jeict");
    public static final KeyMapping REDO = new KeyMapping("key.jeict.redo", KeyConflictContext.GUI,
            KeyModifier.CONTROL, InputConstants.Type.KEYSYM, InputConstants.KEY_Y, "key.categories.jeict");
    public static final KeyMapping FOCUS_SEARCH = new KeyMapping("key.jeict.focus_search", KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM, InputConstants.KEY_F, "key.categories.jeict");

    private RecipeTreeKeyMappings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(UNDO);
        event.register(REDO);
        event.register(FOCUS_SEARCH);
    }

    public static boolean handle(Screen screen) {
        if (!(screen instanceof RecipeTreeOverviewScreen overview)) return false;
        if (UNDO.consumeClick()) {
            overview.undoLastEdit();
            return true;
        }
        if (REDO.consumeClick()) {
            overview.redoLastEdit();
            return true;
        }
        if (FOCUS_SEARCH.consumeClick()) {
            overview.focusSearchField();
            return true;
        }
        return false;
    }
}
