package com.therappha.tinyblocks.mixin.sable;

import com.therappha.tinyblocks.subgrid.SubgridBlock;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * For the beta, a SubgridBlock all but weighs nothing on a Sable sub-level: it stays a solid cube to collide with
 * (VoxelNeighborhoodStateMixin), so its mass goes here and not through isSolid. Not none: a sub-level of nothing but
 * grids would have no mass, and Sable drops it. Remove this mixin to weigh it as a chassis again.
 */
@Mixin(PhysicsBlockPropertyHelper.class)
public abstract class PhysicsBlockPropertyHelperMixin {

    /** What a SubgridBlock weighs for now. */
    @Unique
    private static final double TINYBLOCKS$MASS = 0.01;

    @Inject(method = "getMass", at = @At("HEAD"), cancellable = true)
    private static void tinyblocks$nearlyWeightless(BlockGetter level, BlockPos pos, BlockState state, CallbackInfoReturnable<Double> cir) {
        if (state.getBlock() instanceof SubgridBlock) cir.setReturnValue(TINYBLOCKS$MASS);
    }
}
