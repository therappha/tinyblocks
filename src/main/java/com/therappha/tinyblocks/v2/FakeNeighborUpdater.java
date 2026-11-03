package com.therappha.tinyblocks.v2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.level.redstone.NeighborUpdater;

import javax.annotation.Nullable;

/**
 * Vanilla's neighbour updates, kept inside the fake space. An update reaching a real block past the grid's edge stops
 * there: running that block's logic here would act at a virtual position. The real block hears of what the grid's
 * faces give through GridSignals#updateRedstoneOutputs, in the real level.
 */
final class FakeNeighborUpdater extends CollectingNeighborUpdater {

    private final FakeCellGetter cells;

    FakeNeighborUpdater(Level level, FakeCellGetter cells, int maxChained) {
        super(level, maxChained);
        this.cells = cells;
    }

    @Override
    public void shapeUpdate(Direction direction, BlockState state, BlockPos pos, BlockPos neighborPos, int flags, int recursionLeft) {
        if (!cells.outsideGrids(pos)) super.shapeUpdate(direction, state, pos, neighborPos, flags, recursionLeft);
    }

    @Override
    public void neighborChanged(BlockPos pos, Block block, BlockPos from) {
        if (!cells.outsideGrids(pos)) super.neighborChanged(pos, block, from);
    }

    @Override
    public void neighborChanged(BlockState state, BlockPos pos, Block block, BlockPos from, boolean moved) {
        if (!cells.outsideGrids(pos)) super.neighborChanged(state, pos, block, from, moved);
    }

    /** One by one, in vanilla's order, so each can be left out; nested updates run first either way. */
    @Override
    public void updateNeighborsAtExceptFromFacing(BlockPos pos, Block block, @Nullable Direction skip) {
        for (Direction direction : NeighborUpdater.UPDATE_ORDER) {
            if (direction != skip) neighborChanged(pos.relative(direction), block, pos);
        }
    }
}
