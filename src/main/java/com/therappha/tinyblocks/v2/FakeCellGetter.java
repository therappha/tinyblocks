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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
    private final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();
    private final Set<BlockPos> removedBlockEntities = new HashSet<>();
    private final LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(0, 16);

    public void set(BlockPos pos, BlockState state) {
        cells.put(pos, state);
    }

    /**
     * Reads/writes the raw cell map directly, bypassing any position aliasing a subclass applies
     * (see SubgridFakeCellGetter.resolve). For internal engine bookkeeping that already operates
     * in this cell space's own local coordinate frame — e.g. populateCells seeding sibling piece
     * states by their own anchor, or applyChanges' diff reading them back — as opposed to vanilla
     * call-through positions (realPos and its neighbor offsets), which DO need alias translation.
     * final so no subclass can intercept these and reintroduce the same bug.
     */
    public final void setRaw(BlockPos pos, BlockState state) {
        cells.put(pos, state);
    }

    public final BlockState getStateRaw(BlockPos pos) {
        BlockState state = cells.get(pos);
        return state != null ? state : fallback(pos);
    }

    /**
     * Translates pos the same way this cell space's own reads/writes are translated — a no-op
     * here (this base class has no aliasing), overridden by SubgridFakeCellGetter to expose its
     * private resolve() to callers outside VanillaBlockPiece (FakeLevel#blockEvent needs the
     * fake-local cell a real-world-position-based vanilla call actually landed on, to know which
     * piece to notify the client about).
     */
    public BlockPos resolveLocal(BlockPos pos) {
        return pos;
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

    /**
     * Captures a real setBlockEntity(...) call instead of letting it fall through to vanilla
     * Level.setBlockEntity, which would register into a real chunk at these small-integer fake
     * coordinates — a location nothing ever reads. Needed for any BlockState whose actual data
     * lives on a fully-parameterized BlockEntity handed over this way rather than built lazily
     * (e.g. PistonBaseBlock constructing a PistonMovingBlockEntity with real progress/moved-state
     * data; MovingPistonBlock.newBlockEntity, the lazy path, deliberately returns null).
     */
    public void setBlockEntity(BlockPos pos, BlockEntity blockEntity) {
        blockEntities.put(pos, blockEntity);
        removedBlockEntities.remove(pos);
    }

    /** Mirrors removeBlockEntity — see setBlockEntity above for why this needs capturing too. */
    public void removeBlockEntityAt(BlockPos pos) {
        blockEntities.remove(pos);
        removedBlockEntities.add(pos);
    }

    /** Every position explicitly given a real BlockEntity via setBlockEntity this call. */
    public Map<BlockPos, BlockEntity> touchedBlockEntities() {
        return java.util.Collections.unmodifiableMap(blockEntities);
    }

    /** Every position explicitly cleared via removeBlockEntityAt this call. */
    public Set<BlockPos> removedBlockEntityPositions() {
        return java.util.Collections.unmodifiableSet(removedBlockEntities);
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return blockEntities.get(pos);
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
