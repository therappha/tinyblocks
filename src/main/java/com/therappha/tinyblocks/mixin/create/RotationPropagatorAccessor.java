package com.therappha.tinyblocks.mixin.create;

import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RotationPropagator.class)
public interface RotationPropagatorAccessor {

    @Invoker("getAxisModifier")
    static float tinyblocks$getAxisModifier(KineticBlockEntity be, Direction direction) {
        throw new AssertionError();
    }

    @Invoker("propagateMissingSource")
    static void tinyblocks$propagateMissingSource(KineticBlockEntity updateTE) {
        throw new AssertionError();
    }
}
