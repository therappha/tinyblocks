package com.therappha.tinyblocks.mixin.create;

import com.simibubi.create.content.kinetics.base.DirectionalShaftHalvesBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.therappha.tinyblocks.compat.create.KineticBridge;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** See KineticBridge#sourceFaceAcross: a clutch or gearshift driven through a connector. */
@Mixin(DirectionalShaftHalvesBlockEntity.class)
public abstract class DirectionalShaftHalvesBlockEntityMixin {

    @Inject(method = "getSourceFacing", at = @At("HEAD"), cancellable = true, remap = false)
    private void tinyblocks$sourceAcrossConnector(CallbackInfoReturnable<Direction> cir) {
        Direction face = KineticBridge.sourceFaceAcross((KineticBlockEntity) (Object) this);
        if (face != null) cir.setReturnValue(face);
    }
}
