package com.therappha.tinyblocks.mixin.create;

import com.simibubi.create.content.kinetics.KineticNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** What a network counts for members not loaded, for SpaceDiff (CreateFacts). */
@Mixin(KineticNetwork.class)
public interface KineticNetworkAccessor {

    @Accessor(value = "unloadedCapacity", remap = false)
    float tinyblocks$unloadedCapacity();

    @Accessor(value = "unloadedStress", remap = false)
    float tinyblocks$unloadedStress();
}
