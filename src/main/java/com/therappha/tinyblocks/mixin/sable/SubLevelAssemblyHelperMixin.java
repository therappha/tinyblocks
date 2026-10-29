package com.therappha.tinyblocks.mixin.sable;

import com.mojang.logging.LogUtils;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sable moves blocks between the world and its sub-levels (a car assembled, or set down snapped to a quarter turn)
 * by turning each block's state and reloading its block entity's saved data unchanged at the new place. A grid moved
 * so has its pieces turned with it here, and is rearranged even unturned: its pieces' Create kinetic data and phantoms
 * belong where it was, as with a Create contraption set down (SubgridBlockEntity#transformMoved). At the end, once
 * every block is in place: the grids moved together are against each other again before any of them looks across.
 */
@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public abstract class SubLevelAssemblyHelperMixin {

    @Unique
    private static final Logger tinyblocks$LOGGER = LogUtils.getLogger();

    @Inject(method = "moveBlocks(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/api/SubLevelAssemblyHelper$AssemblyTransform;Ljava/lang/Iterable;)V",
            at = @At("TAIL"))
    private static void tinyblocks$turnMovedGrids(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform,
                                                 Iterable<BlockPos> blocks, CallbackInfo ci) {
        for (BlockPos old : blocks) {
            if (!(transform.getLevel().getBlockEntity(transform.apply(old)) instanceof SubgridBlockEntity grid)) continue;
            if (!grid.transformMoved(transform.getRotation(), Mirror.NONE)) {
                tinyblocks$LOGGER.warn("A grid moved by Sable to {} was left unturned: a piston in it is moving something",
                        grid.getBlockPos());
            }
        }
    }
}
