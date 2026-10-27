package com.therappha.tinyblocks.mixin.create;

import com.therappha.tinyblocks.compat.create.client.VirtualView;
import net.createmod.catnip.outliner.Outliner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Outlines shown while the crosshair points into a grid are in its virtual frame: they go to VirtualView's outliner. */
@Mixin(value = Outliner.class, remap = false)
public abstract class OutlinerMixin {

    @Inject(method = "getInstance", at = @At("HEAD"), cancellable = true)
    private static void tinyblocks$insideAGrid(CallbackInfoReturnable<Outliner> cir) {
        if (VirtualView.active()) cir.setReturnValue(VirtualView.outliner());
    }
}
