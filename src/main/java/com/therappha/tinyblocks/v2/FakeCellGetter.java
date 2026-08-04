package com.therappha.tinyblocks.v2;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * v2 prototype (see issue tracker): proves that vanilla's own BlockGetter-level block API
 * (hardness, shape, redstone signal) runs correctly against a fake, in-memory position space
 * instead of a real chunk — the same trick WorldGenRegion uses for chunk generation.
 *
 * Deliberately minimal: no real Level, no ServerLevel. Anything that needs those (interact,
 * neighborChanged, tick) is out of scope for this checkpoint.
 */
public class FakeCellGetter implements BlockGetter {

    private final Map<BlockPos, BlockState> cells = new HashMap<>();
    private final LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(0, 16);

    public void set(BlockPos pos, BlockState state) {
        cells.put(pos, state);
    }

    /**
     * Every position explicitly written this call (via set, i.e. real vanilla code calling
     * setBlock), NOT including positions only ever read via fallback(). Lets a caller notice a
     * write to a position that isn't an already-tracked piece — e.g. water spreading into a
     * previously-empty neighbor cell, or a falling block relocating — which the normal
     * before/after diff over existing pieces alone can't see, since there was no piece there to
     * diff in the first place.
     */
    public Map<BlockPos, BlockState> touchedCells() {
        return java.util.Collections.unmodifiableMap(cells);
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockState state = cells.get(pos);
        return state != null ? state : fallback(pos);
    }

    /**
     * Called when no explicit state has been set for pos. Defaults to air; a subclass backed by
     * a real SubgridBlockEntity overrides this to consult the wider context (sibling cells not
     * yet touched this call, or the real world beyond the subgrid's bounds) instead.
     */
    protected BlockState fallback(BlockPos pos) {
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public int getHeight() {
        return heightAccessor.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return heightAccessor.getMinBuildHeight();
    }
}
