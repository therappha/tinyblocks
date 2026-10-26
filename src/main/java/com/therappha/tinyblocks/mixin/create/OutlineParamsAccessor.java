package com.therappha.tinyblocks.mixin.create;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The raw line width of an outline (its getter fades it), for VirtualView to thicken outlines it draws shrunk. */
@Mixin(targets = "net.createmod.catnip.outliner.Outline$OutlineParams", remap = false)
public interface OutlineParamsAccessor {

    @Accessor("lineWidth")
    float tinyblocks$lineWidth();

    @Accessor("lineWidth")
    void tinyblocks$setLineWidth(float width);
}
