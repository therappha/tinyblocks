package com.therappha.tinyblocks.diff;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** A box of cells to compare, read the same way whether they are real blocks or the pieces of tiny grids. */
public interface SpaceView {

    /** What the block at {@code cell} is. */
    BlockState state(BlockPos cell);

    /** What a client was last told to draw at {@code cell}. */
    BlockState drawn(BlockPos cell);

    /** The block entity at {@code cell}, if its block has one. */
    @Nullable
    BlockEntity blockEntity(BlockPos cell);

    /** What a comparator would read from {@code cell}, whose state has an analog output. */
    int analogSignal(BlockPos cell);
}
