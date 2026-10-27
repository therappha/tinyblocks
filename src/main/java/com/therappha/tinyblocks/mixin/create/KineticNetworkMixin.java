package com.therappha.tinyblocks.mixin.create;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.therappha.tinyblocks.compat.create.KineticBridge;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** See KineticBridge#networkLevel: the network map an emptied network is dropped from. */
@Mixin(KineticNetwork.class)
public abstract class KineticNetworkMixin {

    @Redirect(method = "remove", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/kinetics/base/KineticBlockEntity;getLevel()Lnet/minecraft/world/level/Level;"))
    private Level tinyblocks$networkLevel(KineticBlockEntity be) {
        return KineticBridge.networkLevel(be.getLevel());
    }
}
