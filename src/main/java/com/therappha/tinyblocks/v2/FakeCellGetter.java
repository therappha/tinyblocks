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
 * A fake, in-memory position space that vanilla's own BlockGetter-level block API (hardness,
 * shape, redstone signal) runs against instead of a real chunk — the same trick WorldGenRegion
 * uses for chunk generation.
 *
 * Deliberately minimal: no real Level, no ServerLevel. What needs those (interact,
 * neighborChanged, tick) goes through FakeLevel and FakeServerLevel, which wrap this.
 */
public class FakeCellGetter implements BlockGetter {

    private final Map<BlockPos, BlockState> cells = new HashMap<>();
    private final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();
    private final Set<BlockPos> removedBlockEntities = new HashSet<>();
    /** What each written cell held right before its first write this use (null: nothing worth diffing against). */
    private final Map<BlockPos, BlockState> originals = new HashMap<>();
    /** Local cells written through a level's setBlock, whose onRemove, onPlace and updates have run (FakeWrites). */
    private final Set<BlockPos> settled = new HashSet<>();
    private final LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(0, 16);

    /** Forgets every write and captured block entity, so the space can be used again from scratch. */
    public void clear() {
        cells.clear();
        blockEntities.clear();
        removedBlockEntities.clear();
        originals.clear();
        settled.clear();
    }

    /** Where in the real level the point {@code virtual} of this space is. */
    public net.minecraft.world.phys.Vec3 toReal(net.minecraft.world.phys.Vec3 virtual) {
        return virtual;
    }

    /** {@code world} was written through a level's setBlock: everything that follows from the write has run. */
    public void markSettled(BlockPos world) {
        settled.add(resolveLocal(world));
    }

    public boolean isSettled(BlockPos local) {
        return settled.contains(local);
    }

    /**
     * A phantom keeps the last space it was bound to as its level, long after the call using that space ended. Something
     * done through such a space (a player's item going into a chest, a hopper filling a chest that never ticks) would be
     * written to a space nobody reads again. {@code work} then runs in a space of the grid, applied right after, and this
     * answers true. While the space's own call is running this answers false: the caller just goes on in it.
     */
    public boolean afterItsCall(java.util.function.Consumer<net.minecraft.world.level.Level> work) {
        return false;
    }

    /** Whether {@code world} is a block of the real world, not a cell of this grid or of one running on from it. */
    public boolean outsideGrids(BlockPos world) {
        return false;
    }

    /** Queues a tick of {@code type} (a Block or a Fluid) with whoever owns this space; false if nobody does. */
    public boolean scheduleTick(BlockPos world, Object type, int delay, int priority) {
        return false;
    }

    public boolean tickPending(BlockPos world, Object type) {
        return false;
    }

    public boolean tickDue(BlockPos world, Object type) {
        return false;
    }

    /** Queues a block event to run when the level runs them; false if nobody owns this space. */
    public boolean blockEvent(BlockPos world, net.minecraft.world.level.block.Block block, int id, int param) {
        return false;
    }

    public void set(BlockPos pos, BlockState state) {
        if (!originals.containsKey(pos)) originals.put(pos, originalAt(pos));
        cells.put(pos, state);
    }

    /**
     * The state local cell {@code pos} held before this use first wrote it, or null when it wasn't written or held
     * nothing worth diffing against. A caller diffing the writes compares with this rather than with what the cell
     * holds when the diff runs: a write that reached the cell from outside in the meantime (another grid's call
     * placing a piece here) must not look like this use's own change and be undone.
     */
    @Nullable
    public BlockState originalState(BlockPos pos) {
        return originals.get(pos);
    }

    /** What local cell {@code pos} holds outside this space, recorded on its first write; null here. */
    @Nullable
    protected BlockState originalAt(BlockPos pos) {
        return null;
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
     * pos translated by whatever offset maps aliasFrom onto aliasTo — identity when aliasFrom is
     * null. Pulled out of SubgridFakeCellGetter.resolve (which just delegates here) so this exact
     * math — the source of three separate bugs in one session (#33, #37): feeding an
     * ALREADY-LOCAL coordinate like a piece's own anchor through this when aliasFrom is non-null
     * silently produces a nonsense position, since it's only meant for vanilla call-through
     * positions relative to aliasFrom — has direct unit test coverage without needing a real
     * SubgridBlockEntity to construct the aliasing cell getter around.
     */
    public static BlockPos aliasOffset(@Nullable BlockPos aliasFrom, BlockPos aliasTo, BlockPos pos) {
        if (aliasFrom == null) return pos;
        return aliasTo.offset(pos.getX() - aliasFrom.getX(), pos.getY() - aliasFrom.getY(), pos.getZ() - aliasFrom.getZ());
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
     * The vanilla-visible ("world") position of local cell {@code local}. Identity here; the subgrid-backed
     * subclass maps its cells into a virtual cube around the SubgridBlock's real position, so every phantom
     * block entity gets its own unique, consistent BlockPos (Create identifies and orients kinetic blocks purely
     * by BlockPos differences) and open container menus stay within interaction range of the player.
     */
    public BlockPos toWorld(BlockPos local) {
        return local;
    }

    /**
     * The redstone signal at vanilla-visible position {@code world} towards {@code direction}, when it lies outside
     * this cell space and is better answered by the world around it; null to answer it from the cells as usual.
     */
    @Nullable
    public Integer outsideSignal(BlockPos world, net.minecraft.core.Direction direction, boolean strong) {
        return null;
    }

    /** A game event at a vanilla-visible position: whoever owns this space passes it to what listens. */
    public void gameEvent(net.minecraft.core.Holder<net.minecraft.world.level.gameevent.GameEvent> event,
                          net.minecraft.world.phys.Vec3 pos, net.minecraft.world.level.gameevent.GameEvent.Context context) {}

    /** A block entity at this vanilla-visible position asked its clients to resync; no-op without an owning grid. */
    public void blockUpdatedAt(BlockPos world) {}

    /** Inverse of {@link #toWorld}. */
    public BlockPos toLocal(BlockPos world) {
        return world;
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
