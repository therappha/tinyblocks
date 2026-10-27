package com.therappha.tinyblocks.mixin.create;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.therappha.tinyblocks.compat.create.KineticBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** See KineticBridge: connector pairs across a SubgridBlock face. */
@Mixin(RotationPropagator.class)
public abstract class RotationPropagatorMixin {

    @Inject(method = "findConnectedNeighbour", at = @At("HEAD"), cancellable = true)
    private static void tinyblocks$connectorBehindSubgrid(KineticBlockEntity currentTE, BlockPos neighbourPos,
                                                          CallbackInfoReturnable<KineticBlockEntity> cir) {
        KineticBlockEntity connector = KineticBridge.connectorBehind(currentTE, neighbourPos);
        if (connector == null) connector = KineticBridge.acrossGrids(currentTE, neighbourPos);
        if (connector != null && (RotationPropagator.isConnected(currentTE, connector)
                || RotationPropagator.isConnected(connector, currentTE))) {
            cir.setReturnValue(connector);
        }
    }

    @Inject(method = "getRotationSpeedModifier", at = @At("HEAD"), cancellable = true)
    private static void tinyblocks$bridgedModifier(KineticBlockEntity from, KineticBlockEntity to,
                                                   CallbackInfoReturnable<Float> cir) {
        Float modifier = KineticBridge.bridgedModifier(from, to);
        if (modifier != null) cir.setReturnValue(modifier);
    }

    @Inject(method = "handleRemoved", at = @At("HEAD"))
    private static void tinyblocks$platesLoseRemovedSource(Level worldIn, BlockPos pos, KineticBlockEntity removedBE,
                                                           CallbackInfo ci) {
        KineticBridge.platesDrivenBy(worldIn, pos, removedBE)
                .forEach(RotationPropagatorAccessor::tinyblocks$propagateMissingSource);
    }

    @Inject(method = "propagateMissingSource", at = @At("HEAD"))
    private static void tinyblocks$beginWalk(KineticBlockEntity updateTE, CallbackInfo ci) {
        KineticBridge.bindPhantom(updateTE);
        KineticBridge.beginWalk();
    }

    @Inject(method = "propagateNewSource", at = @At("HEAD"))
    private static void tinyblocks$bindPhantomToPropagate(KineticBlockEntity be, CallbackInfo ci) {
        KineticBridge.bindPhantom(be);
    }

    @Inject(method = {"getConnectedNeighbours", "getPotentialNeighbourLocations"}, at = @At("HEAD"))
    private static void tinyblocks$bindPhantomToLookAround(KineticBlockEntity be, CallbackInfoReturnable<List<?>> cir) {
        KineticBridge.bindPhantom(be);
    }

    @Inject(method = "propagateMissingSource", at = @At("RETURN"))
    private static void tinyblocks$endWalk(KineticBlockEntity updateTE, CallbackInfo ci) {
        KineticBridge.endWalk();
    }

    @ModifyReturnValue(method = "getPotentialNeighbourLocations", at = @At("RETURN"))
    private static List<BlockPos> tinyblocks$funnelEnds(List<BlockPos> locations, KineticBlockEntity be) {
        return KineticBridge.withFunnelEnds(be, locations);
    }

    @ModifyReturnValue(method = "getConnectedNeighbours", at = @At("RETURN"))
    private static List<KineticBlockEntity> tinyblocks$rememberNeighbours(List<KineticBlockEntity> neighbours) {
        KineticBridge.rememberNeighbours(neighbours);
        return neighbours;
    }

    @WrapOperation(method = "propagateMissingSource", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private static BlockEntity tinyblocks$walkAcrossConnectors(Level level, BlockPos pos, Operation<BlockEntity> original) {
        return KineticBridge.walkLookup(pos, original.call(level, pos));
    }
}
