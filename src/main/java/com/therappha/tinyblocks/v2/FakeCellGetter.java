package com.therappha.tinyblocks.v2;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

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

    /**
     * BlockGetter#getFluidState has no vanilla default (abstract) — every implementer must derive
     * it themselves. Delegating to getBlockState(pos).getFluidState() is the standard mapping
     * (matches e.g. LevelChunk's own implementation) and, critically, automatically inherits
     * whatever boundary/fallback logic a subclass's getBlockState override provides (e.g.
     * SubgridBlockEntity's real-world neighbor lookups at a subgrid's edge) — a hand-rolled
     * fluid-specific fallback would have to duplicate that logic and could drift out of sync.
     *
     * Previously hardcoded to Fluids.EMPTY unconditionally, a leftover from when this class only
     * needed to prove hardness/shape queries worked (before fluid support existed at all) — never
     * updated once FlowingFluid ticking was added. Since FlowingFluid#spreadTo, #getNewLiquid, and
     * effectively all of vanilla's own flow-decision logic read neighbor state exclusively through
     * Level#getFluidState (not BlockState#getFluidState), the fake space looked permanently
     * fluid-free everywhere — including at a piece's own anchor — which is why water placed via
     * this engine never actually flowed.
     */
    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
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
