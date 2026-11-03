package com.therappha.tinyblocks.mixin.create;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.TorquePropagator;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Create's networks, to read one without making it (CreateFacts). */
@Mixin(TorquePropagator.class)
public interface TorquePropagatorAccessor {

    @Accessor(value = "networks", remap = false)
    static Map<LevelAccessor, Map<Long, KineticNetwork>> tinyblocks$networks() {
        throw new AssertionError();
    }
}
