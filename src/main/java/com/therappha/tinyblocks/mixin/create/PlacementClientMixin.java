package com.therappha.tinyblocks.mixin.create;

import com.therappha.tinyblocks.compat.create.client.SubgridPlacementPreview;
import net.createmod.catnip.placement.PlacementClient;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create's placement assist only asks about the block under the crosshair, which over a piece is the SubgridBlock.
 * When it found nothing, the piece gets asked (SubgridPlacementPreview), and its target drives Create's on-screen
 * arrow and fade-in as its own would.
 */
@Mixin(value = PlacementClient.class, remap = false)
public abstract class PlacementClientMixin {

    @Shadow static BlockPos target;

    @Shadow
    static void setTarget(BlockPos target) {
        throw new AssertionError();
    }

    @Inject(method = "checkHelpers", at = @At("RETURN"))
    private static void tinyblocks$askThePiece(CallbackInfo ci) {
        BlockPos found = SubgridPlacementPreview.tick();
        if (target == null && found != null) setTarget(found);
    }
}
