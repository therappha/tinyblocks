package com.therappha.tinyblocks.mixin.create;

import com.simibubi.create.content.equipment.goggles.GoggleOverlayRenderer;
import com.therappha.tinyblocks.compat.create.client.VirtualView;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The goggles' information panel (speed, stress, ...) for a piece under the crosshair: see VirtualView. */
@Mixin(value = GoggleOverlayRenderer.class, remap = false)
public abstract class GoggleOverlayRendererMixin {

    @Inject(method = "renderOverlay", at = @At("HEAD"))
    private static void tinyblocks$lookIntoTheGrid(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        VirtualView.begin();
    }

    @Inject(method = "renderOverlay", at = @At("RETURN"))
    private static void tinyblocks$lookBack(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        VirtualView.end();
    }
}
