package com.therappha.tinyblocks.compat.create;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/** What Create keeps in a phantom's saved data that turning or mirroring its grid (SubgridBlockEntity#transform) must change. */
public final class CreatePhantomTransform {

    private CreatePhantomTransform() {}

    /**
     * A kinetic block entity's speed, source and network are dropped: the source is a position, where some other cell
     * now is, and the network was built for the old layout. Read without them, it starts at rest and joins whatever
     * drives it on its first tick, as Create does with a block a contraption has moved (KineticBlockEntity#wasMoved).
     */
    public static void transformData(BlockState state, CompoundTag data) {
        if (!(state.getBlock() instanceof KineticBlock)) return;
        data.remove("Speed");
        data.remove("Source");
        data.remove("Network");
    }
}
