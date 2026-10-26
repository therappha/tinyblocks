package com.therappha.tinyblocks.mixin.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.therappha.tinyblocks.compat.create.KineticBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** See KineticBridge: a rotation source on the other side of a connector lives in the other frame. */
@Mixin(KineticBlockEntity.class)
public abstract class KineticBlockEntityMixin {

    @WrapOperation(method = {"setSource", "validateKinetics"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity tinyblocks$sourceAcrossConnector(Level level, BlockPos source, Operation<BlockEntity> original) {
        return KineticBridge.resolveSource((KineticBlockEntity) (Object) this, source, original.call(level, source));
    }

    /** A virtual source position is usually in an unloaded chunk of the real level; validate it anyway. */
    @WrapOperation(method = "validateKinetics", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;isLoaded(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean tinyblocks$sourceLoadedAcrossConnector(Level level, BlockPos source, Operation<Boolean> original) {
        return original.call(level, source)
                || KineticBridge.resolveSource((KineticBlockEntity) (Object) this, source, null) != null;
    }
}
