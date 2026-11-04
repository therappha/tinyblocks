package com.therappha.tinyblocks.compat.create;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/** What Create keeps in a phantom's saved data that turning or mirroring its grid (SubgridBlockEntity#transform) must change. */
public final class CreatePhantomTransform {

    private CreatePhantomTransform() {}

    /**
     * A kinetic block entity's speed, source and network are dropped, even for a grid only moved, not turned: the source is a position, where some other cell
     * now is, and the network was built for the old layout. Read without them, it starts at rest and joins whatever
     * drives it on its first tick, as Create does with a block a contraption has moved (KineticBlockEntity#wasMoved).
     * IRotate, not KineticBlock: it is what Create's propagation itself asks of a kinetic block, and some (other mods'
     * among them) implement it without extending KineticBlock.
     */
    public static void transformData(BlockState state, CompoundTag data) {
        if (!(state.getBlock() instanceof IRotate)) return;
        data.remove("Speed");
        data.remove("Source");
        data.remove("Network");
        // A belt keeps its controller as a position too, and with a length left it never asks again: past the segment
        // given power nothing turned. With no length, its next tick works the whole belt out as a new one (initBelt).
        if (state.getBlock() instanceof BeltBlock) {
            data.remove("Controller");
            data.putInt("Length", 0);
        }
    }
}
