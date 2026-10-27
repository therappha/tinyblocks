package com.therappha.tinyblocks.mixin.create;

import com.simibubi.create.content.redstone.link.LinkRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueRenderer;
import com.therappha.tinyblocks.compat.create.client.VirtualView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Create's value box outlines and hover tips for the block under the crosshair look into a grid: see VirtualView. */
@Mixin(value = {ScrollValueRenderer.class, FilteringRenderer.class, LinkRenderer.class}, remap = false)
public abstract class ValueBoxTickMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private static void tinyblocks$lookIntoTheGrid(CallbackInfo ci) {
        VirtualView.begin();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private static void tinyblocks$lookBack(CallbackInfo ci) {
        VirtualView.end();
    }
}
