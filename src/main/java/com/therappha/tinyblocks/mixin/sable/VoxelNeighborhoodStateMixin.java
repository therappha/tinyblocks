package com.therappha.tinyblocks.mixin.sable;

import com.therappha.tinyblocks.subgrid.SubgridBlock;
import dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sable decides once per block state whether a block is solid, from its collision shape, and gives a block
 * that is not solid no mass. A SubgridBlock's collision shape comes from its block entity, which that one-off
 * check never has, so every SubgridBlock was weightless. It is a solid cube to Sable — the same full cube
 * SubgridBlock#getSubLevelCollisionShape gives it — and so weighs what an untagged full block does, a Create
 * chassis among them, whatever its pieces are.
 */
@Mixin(VoxelNeighborhoodState.class)
public abstract class VoxelNeighborhoodStateMixin {

    @Inject(method = "isSolid", at = @At("HEAD"), cancellable = true)
    private static void tinyblocks$subgridIsSolid(BlockGetter level, BlockPos pos, BlockState state,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (state.getBlock() instanceof SubgridBlock) cir.setReturnValue(true);
    }
}
