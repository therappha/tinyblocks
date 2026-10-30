package com.therappha.tinyblocks.mixin.sable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sable moves blocks between the world and its sub-levels (a car assembled, or set down snapped to a quarter turn)
 * by turning each block's state and reloading its block entity's saved data unchanged at the new place. A grid moved
 * so has its pieces turned with it here, and is rearranged even unturned: its pieces' Create kinetic data and phantoms
 * belong where it was, as with a Create contraption set down (SubgridBlockEntity#transformMoved). Each grid is turned
 * quietly as soon as its data is in, before Sable tells any block around that it is there; at the end, every grid
 * moved together being turned, their pieces look across, against each other again.
 */
@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public abstract class SubLevelAssemblyHelperMixin {

    @WrapOperation(method = "moveBlocks(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/api/SubLevelAssemblyHelper$AssemblyTransform;Ljava/lang/Iterable;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/BlockEntity;loadWithComponents(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V"))
    private static void tinyblocks$turnMovedGrid(BlockEntity be, CompoundTag saved, HolderLookup.Provider registries,
                                                Operation<Void> original, ServerLevel level,
                                                SubLevelAssemblyHelper.AssemblyTransform transform, Iterable<BlockPos> blocks) {
        original.call(be, saved, registries);
        if (be instanceof SubgridBlockEntity grid) grid.turnMovedQuietly(transform.getRotation(), Mirror.NONE);
    }

    @Inject(method = "moveBlocks(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/api/SubLevelAssemblyHelper$AssemblyTransform;Ljava/lang/Iterable;)V",
            at = @At("TAIL"))
    private static void tinyblocks$lookAroundMovedGrids(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform,
                                                       Iterable<BlockPos> blocks, CallbackInfo ci) {
        for (BlockPos old : blocks) {
            if (transform.getLevel().getBlockEntity(transform.apply(old)) instanceof SubgridBlockEntity grid) {
                grid.lookAroundMoved();
            }
        }
    }
}
