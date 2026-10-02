package com.lhy.jeict.mixin.jei;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.lhy.jeict.client.JeiRecipeTreeShortcutOverlay;
import com.lhy.jeict.jei.BookmarkTreeButtonController;

import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.elements.IconButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Same hook ExtendedAE Plus uses: live inside JEI's bookmark overlay so bounds, dimming,
 * tooltips and clicks follow the native bookmark/history buttons.
 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.bookmarks.BookmarkOverlay", remap = false)
public abstract class BookmarkOverlayMixin {
    @Shadow
    @Final
    private IconButton historyButton;

    @Unique
    private IconButton jeict$treeButton;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void jeict$createTreeButton(CallbackInfo ci) {
        this.jeict$treeButton = new IconButton(new BookmarkTreeButtonController());
    }

    @Inject(method = "updateBounds", at = @At("TAIL"))
    private void jeict$updateTreeButtonBounds(CallbackInfo ci) {
        if (this.jeict$treeButton == null || this.historyButton == null) {
            return;
        }
        ImmutableRect2i history = this.historyButton.getArea();
        if (history.isEmpty()) {
            this.jeict$treeButton.updateBounds(ImmutableRect2i.EMPTY);
            JeiRecipeTreeShortcutOverlay.bind(null);
            return;
        }
        this.jeict$treeButton.updateBounds(history.moveRight(JeiRecipeTreeShortcutOverlay.offsetFromHistoryButton()));
        JeiRecipeTreeShortcutOverlay.bind(this.jeict$treeButton::isMouseOver);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void jeict$tickTreeButton(CallbackInfo ci) {
        if (this.jeict$treeButton != null) {
            this.jeict$treeButton.tick();
        }
    }

    @Inject(method = "drawForeground", at = @At(value = "INVOKE",
            target = "Lmezz/jei/gui/elements/IconButton;draw(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            ordinal = 1, shift = At.Shift.AFTER), require = 0)
    private void jeict$drawTreeButton(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY,
            float partialTicks, CallbackInfo ci) {
        if (this.jeict$treeButton != null) {
            this.jeict$treeButton.draw(guiGraphics, mouseX, mouseY, partialTicks);
        }
    }

    @Inject(method = "drawScreen", at = @At(value = "INVOKE",
            target = "Lmezz/jei/gui/elements/IconButton;draw(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            ordinal = 1, shift = At.Shift.AFTER), require = 0)
    private void jeict$drawTreeButtonLegacy(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY,
            float partialTicks, CallbackInfo ci) {
        if (this.jeict$treeButton != null) {
            this.jeict$treeButton.draw(guiGraphics, mouseX, mouseY, partialTicks);
        }
    }

    @Inject(method = "drawTooltips", at = @At("TAIL"))
    private void jeict$drawTreeButtonTooltip(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY,
            CallbackInfo ci) {
        if (this.jeict$treeButton != null) {
            this.jeict$treeButton.drawTooltips(guiGraphics, mouseX, mouseY);
        }
    }
}
