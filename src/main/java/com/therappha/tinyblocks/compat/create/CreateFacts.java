package com.therappha.tinyblocks.compat.create;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;

/** What SpaceDiff compares of Create's block entities: how fast it turns and, on a belt, which part of which belt it is. */
public final class CreateFacts {

    private CreateFacts() {}

    public static void add(BlockEntity be, String prefix, Map<String, String> facts) {
        if (be instanceof KineticBlockEntity kinetic) facts.put(prefix + "speed", Float.toString(kinetic.getSpeed()));
        if (be instanceof BeltBlockEntity belt) facts.put(prefix + "belt", belt.index + " of " + belt.beltLength);
    }
}
