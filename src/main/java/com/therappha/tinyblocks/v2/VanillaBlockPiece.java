package com.therappha.tinyblocks.v2;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PieceDefinition;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v2: a single generic PieceDefinition that can hold ANY real vanilla BlockState, instead of
 * one hand-written PieceDefinition per block type (see PieceDefinitions.java for the old way).
 * Stores the actual BlockState in piece.runtimeState and runs interact/neighborChanged/hardness
 * through a fake, in-memory position space (FakeCellGetter/FakeLevel, or FakeServerLevel for the
 * Tier 3 tick methods) built from this piece's SubgridBlockEntity siblings — real vanilla
 * behavior, zero reimplementation.
 */
public final class VanillaBlockPiece extends PieceDefinition {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public static final VanillaBlockPiece INSTANCE = new VanillaBlockPiece();

    private VanillaBlockPiece() {
        super(ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "vanilla_block"), new Vec3i(1, 1, 1));
    }

    /**
     * Forces this class (and its PieceDefinition.REGISTRY registration) to load at mod setup,
     * same as PieceDefinitions.init(). Without this, INSTANCE only loads lazily on first use
     * (e.g. the first Minimizer/Phase C right-click) — but BlockEntity NBT loading can happen
     * before that, so PieceDefinition.getOrThrow("tinyblocks:vanilla_block") would throw and
     * silently drop any SubgridBlockEntity containing a saved VanillaBlockPiece.
     */
    public static void init() {}

    private static BlockState stateOf(PlacedPiece piece) {
        return (BlockState) piece.runtimeState;
    }

    /** Diagnostic-logging helper only — matches PISTON, STICKY_PISTON, MOVING_PISTON, PISTON_HEAD. */
    private static boolean isPistonRelated(@javax.annotation.Nullable BlockState state) {
        return state != null && (state.getBlock() instanceof net.minecraft.world.level.block.piston.PistonBaseBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.piston.MovingPistonBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.piston.PistonHeadBlock);
    }

    @Override
    public BlockState renderState(Direction facing) {
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public BlockState renderState(PlacedPiece piece) {
        BlockState state = stateOf(piece);
        return state != null ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    public void onSaving(PlacedPiece piece, HolderLookup.Provider registries) {
        BlockState state = stateOf(piece);
        if (state != null) {
            piece.extraData.put("state", NbtUtils.writeBlockState(state));
        }
        if (piece.runtimeBlockEntity instanceof BlockEntity be) {
            piece.extraData.put("be", be.saveCustomOnly(registries));
        }
    }

    @Override
    public void onLoaded(PlacedPiece piece, HolderLookup.Provider registries) {
        if (piece.extraData.contains("state")) {
            piece.runtimeState = NbtUtils.readBlockState(
                    registries.lookupOrThrow(Registries.BLOCK), piece.extraData.getCompound("state"));
        }
    }

    @Override
    public float destroyTime(PlacedPiece piece) {
        BlockState state = stateOf(piece);
        if (state == null) return super.destroyTime(piece);
        FakeCellGetter fake = new FakeCellGetter();
        fake.set(piece.anchor, state);
        return state.getDestroySpeed(fake, piece.anchor);
    }

    @Override
    public boolean requiresCorrectTool() {
        // SubgridBlock.getDestroyProgress already gates the real hasCorrectToolForDrops(...)
        // check on this flag being true, calling it against the real renderState — so this just
        // makes sure that real check actually runs instead of being skipped.
        return true;
    }

    @Override
    public boolean requiresTick() {
        // Checked per-piece inside tick() below (most blocks aren't EntityBlocks with a ticker,
        // e.g. hoppers/furnaces/brewing stands are, plain stone/dirt/etc. aren't) — this flag
        // itself can't vary per-instance since PieceDefinitions are shared singletons, so it's
        // unconditionally true and tick() does the real, cheap "does this one need it" check.
        return true;
    }

    @Override
    public boolean tick(PlacedPiece piece, ServerLevel level, BlockPos subgridPos, SubgridBlockEntity be) {
        BlockState state = stateOf(piece);
        if (state == null || !(state.getBlock() instanceof EntityBlock entityBlock)) return false;

        // Same real-position convention as blockEntityFor/onUse (checkpoint 6) — a ticker like
        // HopperBlockEntity's push/suck logic reads blockEntity.getBlockPos()/getLevel()
        // internally (not the pos parameter below) to scan its neighbors, so those neighbor
        // lookups land on whatever's really above/below the whole SubgridBlock in the real world,
        // not sibling pieces within the same subgrid. Same accepted degradation already
        // documented for onUse's bookshelf/double-chest-merge scanning — not a crash, just not
        // full intra-subgrid neighbor awareness for this one category of lookup. Getting hopper-
        // to-chest-within-one-subgrid working needs a second, local-anchor-based construction;
        // see issue #23 for why that's a deliberate follow-up, not done here.
        BlockPos realPos = be.getBlockPos();
        SubgridFakeCellGetter cells = new SubgridFakeCellGetter(be, realPos, piece.anchor);
        populateCells(cells, be);
        FakeServerLevel fakeLevel = FakeServerLevel.create(level, cells, realPos);
        cells.attachLevel(fakeLevel);

        BlockEntity phantom = blockEntityFor(piece, be, fakeLevel);
        if (phantom == null) return false;

        Map<BlockPos, BlockState> before = snapshot(be);
        // phantom.getBlockPos(), NOT realPos — matches whatever position THIS SPECIFIC BE instance
        // actually holds internally. For the generic "blank" BEs blockEntityFor lazily builds
        // (hopper, chest, ...), that's be.getBlockPos() (realPos) already, so this changes nothing
        // for them. But a captured BE like a piston's PistonMovingBlockEntity (see syncBlockEntity)
        // was constructed by vanilla's OWN code with the piece's fake-local anchor as its position
        // (that's what PistonBaseBlock.moveBlocks passed to newMovingBlockEntity in the first
        // place) — ticking it against realPos instead is a real mismatch: realPos only gets
        // resolve()d to piece.anchor for an EXACT match, so the piece's own read/write still landed
        // correctly, but any NEIGHBOR lookup (realPos.relative(dir)) fell through to a real,
        // unrelated world position instead of the sibling piece actually sitting there in fake-local
        // space. That's exactly why a piston head's natural finalize (Block.updateFromNeighbourShapes
        // checking "is my base still here") found nothing, concluded it had no support, and
        // destroyed itself instead of becoming a real PISTON_HEAD — found live via the new
        // [piston-anim] diagnostic logging ("went to air — treated as destroyed").
        BlockPos tickPos = phantom.getBlockPos();
        if (!tickTyped(entityBlock, fakeLevel, state, tickPos, phantom)) return false;
        applyChanges(be, fakeLevel, before, level);
        // applyChanges already handles its own notifyUpdate/notifyNeighbors for whatever actually
        // changed — returning true here would make SubgridBlockEntity.serverTick redundantly
        // notifyNeighbors(piece) again for just this one piece regardless of whether anything
        // about IT specifically changed.
        return false;
    }

    // --- Piston moving-block client animation ------------------------------------------------
    //
    // PistonMovingBlockEntity's progress/progressO/deathTicks fields are private with no public
    // setter (only the read-only getProgress(partialTick) getter) — vanilla's own real tick(Level,
    // BlockPos, BlockState, PistonMovingBlockEntity) static method is the only other way to drive
    // them, but that method's finalize branch calls level.setBlock/removeBlockEntity on whatever
    // position it's given, which would corrupt a REAL position if aimed at one (see
    // PistonAnimationPayload's doc comment for why the client copy can't safely reuse it). Direct
    // field access is the same technique FakeServerLevel already relies on elsewhere in this
    // package to bridge into vanilla internals with no other entry point.

    private static final java.lang.reflect.Field PISTON_PROGRESS;
    private static final java.lang.reflect.Field PISTON_PROGRESS_O;
    private static final java.lang.reflect.Field PISTON_DEATH_TICKS;

    static {
        try {
            Class<net.minecraft.world.level.block.piston.PistonMovingBlockEntity> type =
                    net.minecraft.world.level.block.piston.PistonMovingBlockEntity.class;
            PISTON_PROGRESS = type.getDeclaredField("progress");
            PISTON_PROGRESS.setAccessible(true);
            PISTON_PROGRESS_O = type.getDeclaredField("progressO");
            PISTON_PROGRESS_O.setAccessible(true);
            PISTON_DEATH_TICKS = type.getDeclaredField("deathTicks");
            PISTON_DEATH_TICKS.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * How many client ticks a fully-extended/retracted moving-block piece lingers before its
     * client-local animation copy is dropped — matches PistonMovingBlockEntity's own deathTicks
     * grace window (5), so this copy disappears on roughly the same schedule vanilla's would,
     * rather than popping away the instant progress hits 1.0.
     */
    private static final int PISTON_LINGER_TICKS = 5;
    /** Matches PistonMovingBlockEntity.tick's own per-tick step (TICKS_TO_EXTEND = 2). */
    private static final float PISTON_PROGRESS_PER_TICK = 0.5F;

    @Override
    public boolean requiresClientTick() {
        // Checked per-piece inside clientTick below, same reasoning as requiresTick() above —
        // cheap for the vast majority of pieces that aren't mid piston-animation.
        return true;
    }

    @Override
    public void clientTick(PlacedPiece piece, Level level, BlockPos subgridPos, SubgridBlockEntity be) {
        if (piece.runtimeBlockEntity instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity moving) {
            clientTickPiston(piece, subgridPos, moving);
            return;
        }
        // Any OTHER cached BlockEntity (a chest, an enchanting table, a bell, a sculk sensor, ...)
        // gets its own REAL client-side ticker driven forward every client tick, exactly like
        // vanilla's own per-chunk BE ticking would — that's what actually advances a chest's lid
        // openness, an enchanting table's book-flip clock, a bell's ring/shake state, etc. Nothing
        // block-specific here: same generic getTicker dispatch tickTyped already uses server-side,
        // just against the client Level instead. Piston is excluded and kept on its own reflection-
        // based path above deliberately — its real tick() method's finalize branch calls
        // level.setBlock/removeBlockEntity, which would be dangerous to invoke against a BE
        // constructed at the REAL subgrid position (see applyClientAnimation) on the client; these
        // other blocks' tickers are self-contained field/animation updates with no such risk.
        if (piece.runtimeBlockEntity instanceof BlockEntity generic) {
            BlockState state = stateOf(piece);
            if (state != null && state.getBlock() instanceof EntityBlock entityBlock) {
                tickClientTyped(entityBlock, level, state, generic.getBlockPos(), generic);
            }
        }
    }

    private static void clientTickPiston(PlacedPiece piece, BlockPos subgridPos,
                                          net.minecraft.world.level.block.piston.PistonMovingBlockEntity moving) {
        try {
            float progress = PISTON_PROGRESS.getFloat(moving);
            if (progress >= 1.0F) {
                int lingered = PISTON_DEATH_TICKS.getInt(moving) + 1;
                if (lingered >= PISTON_LINGER_TICKS) {
                    // By now the server's real finalize (same 2-ish ticks) has already landed the
                    // piece's real end state via the normal update path — dropping the animation
                    // copy just lets SubgridRenderer fall back to rendering that real state's
                    // static model instead of a BER for it.
                    LOGGER.info("[piston-anim] client animation finished at {}, dropping local copy", subgridPos);
                    piece.runtimeBlockEntity = null;
                } else {
                    PISTON_DEATH_TICKS.setInt(moving, lingered);
                }
                return;
            }
            PISTON_PROGRESS_O.setFloat(moving, progress);
            PISTON_PROGRESS.setFloat(moving, Math.min(1.0F, progress + PISTON_PROGRESS_PER_TICK));
        } catch (ReflectiveOperationException e) {
            piece.runtimeBlockEntity = null;
        }
    }

    /** Client-side twin of tickTyped below — same bridging trick, different (client) Level. */
    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> void tickClientTyped(EntityBlock entityBlock, Level level,
                                                                  BlockState state, BlockPos pos, T phantom) {
        var ticker = entityBlock.getTicker(level, state, (net.minecraft.world.level.block.entity.BlockEntityType<T>) phantom.getType());
        if (ticker == null) return;
        ticker.tick(level, pos, state, phantom);
    }

    /**
     * Client-side entry point for PistonAnimationPayload: builds a local, animation-only copy of
     * the real PistonMovingBlockEntity the server captured, purely for SubgridRenderer's BER
     * dispatch and clientTick's local progress advance above. Its finalize path (inside vanilla's
     * real tick(), which this class deliberately never calls) is never invoked on this copy — only
     * direct field mutation — so it can never write anywhere outside itself.
     */
    public static void applyClientAnimation(SubgridBlockEntity be, BlockPos cell, CompoundTag beNbt, Level level) {
        PlacedPiece piece = be.getPieceAt(cell.getX(), cell.getY(), cell.getZ());
        if (piece == null || piece.definition != INSTANCE) {
            // Can legitimately happen: this payload is sent right after placePieceCrossBoundary for
            // a BRAND NEW cell (piston head extending into empty space) — if the corresponding
            // SubgridPieceAddedPayload hasn't been processed on this client yet, the piece just
            // doesn't exist here yet. Logged rather than silently dropped so a still-missing
            // extend-animation is distinguishable from this specific race vs some other cause.
            LOGGER.info("[piston-anim] client got animation payload for {} (cell {}) but no matching piece exists yet — dropped",
                    be.getBlockPos(), cell);
            return;
        }
        // The outer MOVING_PISTON state embedded by syncBlockEntity — NOT stateOf(piece), which
        // reads whatever the piece's state happens to be RIGHT NOW on the client. That can already
        // differ from the MOVING_PISTON state true when this BE was captured server-side (a same-
        // tick full resync racing ahead of this payload), and PistonMovingBlockEntity's constructor
        // requires an exact match — see syncBlockEntity's comment for the crash this used to cause.
        BlockState state = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), beNbt.getCompound("outerState"));
        if (state.isAir()) return;
        var moving = new net.minecraft.world.level.block.piston.PistonMovingBlockEntity(be.getBlockPos(), state);
        moving.loadCustomOnly(beNbt, level.registryAccess());
        moving.setLevel(level);
        piece.runtimeBlockEntity = moving;
        LOGGER.info("[piston-anim] client built local animation copy at {} (cell {}) extending={} source={}",
                be.getBlockPos(), cell, moving.isExtending(), moving.isSourcePiston());
    }

    /**
     * EntityBlock.getTicker's type parameter has to match the BlockEntityType exactly (it's
     * generic, not wildcard-friendly) — phantom is only known as a plain BlockEntity at the call
     * site above, so bridge through a method with its own type variable the way vanilla's own
     * BaseEntityBlock.createTickerHelper does. Safe: phantom.getType() IS phantom's real type,
     * the unchecked cast just tells the compiler what's already true at runtime.
     */
    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> boolean tickTyped(EntityBlock entityBlock, FakeServerLevel fakeLevel,
                                                              BlockState state, BlockPos pos, T phantom) {
        var ticker = entityBlock.getTicker(fakeLevel, state, (net.minecraft.world.level.block.entity.BlockEntityType<T>) phantom.getType());
        if (ticker == null) return false;
        ticker.tick(fakeLevel, pos, state, phantom);
        return true;
    }

    @Override
    public List<ItemStack> drops(PlacedPiece piece) {
        // Fallback for callers that don't have a real ServerLevel/tool context — see the
        // (piece, level, subgridPos, be, tool) overload below for the real loot-table path.
        BlockState state = stateOf(piece);
        if (state == null) return List.of();
        Item item = state.getBlock().asItem();
        return item != Items.AIR ? List.of(new ItemStack(item)) : List.of();
    }

    @Override
    public List<ItemStack> drops(PlacedPiece piece, ServerLevel level, BlockPos subgridPos, SubgridBlockEntity be, ItemStack tool) {
        BlockState state = stateOf(piece);
        if (state == null) return List.of();
        // Block.getDrops takes the BlockState explicitly (not read from the real position), so
        // this can query the real loot table directly against the real level — no fake position
        // space needed, loot tables are global registry data, not tied to where the piece sits.
        return net.minecraft.world.level.block.Block.getDrops(state, level, be.getBlockPos(), null, null, tool);
    }

    @Override
    public InteractionResult onUse(PlacedPiece piece, Level level, BlockPos subgridPos, SubgridBlockEntity be,
                                    Player player, BlockHitResult hit) {
        BlockState state = stateOf(piece);
        if (state == null) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel serverLevel)) {
            // Client side: consume without side effects, per PieceDefinition's documented onUse contract.
            return InteractionResult.SUCCESS;
        }

        // stillValid (the check every open container menu runs each tick) validates two things
        // against ONE captured position: the block is still the right type, AND the player is
        // still close enough. The fake local anchor already satisfies the first (getBlockState
        // resolves correctly), but is a tiny integer nowhere near the player's real coordinates
        // — so any menu-opening piece (chest, anvil, enchanting table) auto-closes the instant it
        // opens. Using the piece's REAL world position as the hit position instead fixes the
        // distance check; aliasing that real position to piece.anchor in the fake cell space
        // keeps getBlockState/setBlock landing on the same cell either way, so basic state
        // read/write (lever flipping etc.) is unaffected. The one accepted cost: any OTHER
        // position-offset logic inside this same call (e.g. the enchanting table's bookshelf
        // scan) now reads real-world neighbors instead of fake subgrid siblings — degrades that
        // one feature gracefully rather than breaking correctness.
        BlockPos realPos = be.getBlockPos();
        SubgridFakeCellGetter cells = new SubgridFakeCellGetter(be, realPos, piece.anchor);
        populateCells(cells, be);
        FakeLevel fakeLevel = new FakeLevel(serverLevel, cells, realPos);
        cells.attachLevel(fakeLevel);
        Map<BlockPos, BlockState> before = snapshot(be);

        BlockHitResult fakeHit = new BlockHitResult(Vec3.atCenterOf(realPos), hit.getDirection(), realPos, hit.isInside());
        InteractionResult result = cells.getBlockState(realPos).useWithoutItem(fakeLevel, player, fakeHit);

        applyChanges(be, fakeLevel, before, serverLevel);
        return result;
    }

    @Override
    public void onNeighborChanged(PlacedPiece piece, ServerLevel level, BlockPos subgridPos, SubgridBlockEntity be,
                                   PlacedPiece changedNeighbor) {
        BlockState state = stateOf(piece);
        if (state == null) return;

        Direction dir = directionTo(be, piece, changedNeighbor);
        BlockPos fakeNeighborPos = dir != null ? piece.anchor.relative(dir) : piece.anchor;
        BlockState neighborState = changedNeighbor.definition.renderState(changedNeighbor);

        FakeLevel fakeLevel = buildFakeSpace(be, level);
        Map<BlockPos, BlockState> before = snapshot(be);

        fakeLevel.cells().getBlockState(piece.anchor)
                .handleNeighborChanged(fakeLevel, piece.anchor, neighborState.getBlock(), fakeNeighborPos, false);

        if (dir != null) {
            // handleNeighborChanged drives power-level-style self-mutation (a block reacting to
            // and updating itself via its own setBlock call); it does NOT recompute
            // connection-dependent shape state (which sides a fence/wall/wire visually connects
            // to). That's a separate vanilla hook — real placement already runs it once via
            // getStateForPlacement, but we don't have vanilla's automatic post-placement
            // shape-update cascade, so ongoing neighbor changes need it called explicitly too.
            BlockState reshaped = fakeLevel.cells().getBlockState(piece.anchor)
                    .updateShape(dir, neighborState, fakeLevel, piece.anchor, fakeNeighborPos);
            fakeLevel.cells().set(piece.anchor, reshaped);
        }

        applyChanges(be, fakeLevel, before, level);
    }

    @Override
    public void scheduledTick(PlacedPiece piece, ServerLevel level, BlockPos subgridPos, SubgridBlockEntity be) {
        BlockState state = stateOf(piece);
        if (state == null) return;

        FakeServerLevel fakeLevel = buildFakeServerSpace(be, level);
        Map<BlockPos, BlockState> before = snapshot(be);

        fakeLevel.cells().getBlockState(piece.anchor).tick(fakeLevel, piece.anchor, level.getRandom());
        // LiquidBlock doesn't override the regular block tick above at all — water/lava spread
        // exclusively through the SEPARATE fluid-tick schedule (LiquidBlock.onPlace/
        // neighborChanged, both already reached, call level.scheduleTick(pos, fluid, delay), now
        // captured by FakeLevel's fluid-tick overloads). drainScheduledTicks doesn't distinguish
        // which kind of tick was requested, so always attempt both here — FluidState.tick is a
        // real no-op for Fluids.EMPTY (every non-fluid BlockState), so this is inert for the vast
        // majority of pieces and only does something for genuine water/lava.
        fakeLevel.cells().getBlockState(piece.anchor).getFluidState().tick(fakeLevel, piece.anchor);

        applyChanges(be, fakeLevel, before, level);
    }

    @Override
    public void randomTick(PlacedPiece piece, ServerLevel level, BlockPos subgridPos, SubgridBlockEntity be) {
        BlockState state = stateOf(piece);
        if (state == null || !state.isRandomlyTicking()) return;

        FakeServerLevel fakeLevel = buildFakeServerSpace(be, level);
        Map<BlockPos, BlockState> before = snapshot(be);

        fakeLevel.cells().getBlockState(piece.anchor).randomTick(fakeLevel, piece.anchor, level.getRandom());

        applyChanges(be, fakeLevel, before, level);
    }

    // --- Fake space plumbing, shared with SubgridEventHandler's placement flow ---

    /**
     * Builds a FakeLevel whose cell space mirrors every VanillaBlockPiece sibling in be. Used for
     * interact/neighborChanged/placement — none of which actually need a genuine ServerLevel,
     * only a Level — so they stay on this simpler, proven path rather than the more fragile
     * FakeServerLevel (see buildFakeServerSpace), which is scoped to just the Tier 3 tick methods
     * that are statically typed to require one.
     */
    public static FakeLevel buildFakeSpace(SubgridBlockEntity be, ServerLevel serverLevel) {
        SubgridFakeCellGetter cells = (SubgridFakeCellGetter) cellsFor(be);
        FakeLevel fakeLevel = new FakeLevel(serverLevel, cells, be.getBlockPos());
        cells.attachLevel(fakeLevel);
        return fakeLevel;
    }

    /**
     * Same fake cell space as buildFakeSpace, but as a genuine ServerLevel — needed for
     * Block.tick/randomTick, and also for any item's useOn() that itself needs a real
     * ServerLevel internally (e.g. BonemealableBlock.performBonemeal), not just interact/
     * neighborChanged which only ever needed a plain Level.
     */
    public static FakeServerLevel buildFakeServerSpace(SubgridBlockEntity be, ServerLevel serverLevel) {
        SubgridFakeCellGetter cells = (SubgridFakeCellGetter) cellsFor(be);
        FakeServerLevel fakeLevel = FakeServerLevel.create(serverLevel, cells, be.getBlockPos());
        cells.attachLevel(fakeLevel);
        return fakeLevel;
    }

    private static FakeCellGetter cellsFor(SubgridBlockEntity be) {
        SubgridFakeCellGetter cells = new SubgridFakeCellGetter(be, null, null);
        populateCells(cells, be);
        return cells;
    }

    private static void populateCells(FakeCellGetter cells, SubgridBlockEntity be) {
        for (PlacedPiece p : be.getPieces()) {
            if (p.definition == INSTANCE) {
                BlockState s = stateOf(p);
                if (s != null) cells.set(p.anchor, s);
            }
        }
    }

    /**
     * Cells not explicitly touched this call fall back to the SubgridBlockEntity's real vanilla
     * view. Optionally aliases one position to another before every read/write — used by onUse
     * to let vanilla code address a piece by its REAL world BlockPos (needed for stillValid's
     * distance check, see onUse) while still landing on the same fake cell as piece.anchor.
     */
    private static final class SubgridFakeCellGetter extends FakeCellGetter {
        private final SubgridBlockEntity be;
        @javax.annotation.Nullable private final BlockPos aliasFrom;
        @javax.annotation.Nullable private final BlockPos aliasTo;
        /**
         * Set right after the FakeLevel/FakeServerLevel wrapping this cell space is constructed —
         * can't be known at this object's own construction time, since it's built first and
         * handed to the FakeLevel constructor. Only needed for getBlockEntity's phantom
         * BlockEntity.setLevel call below.
         */
        private Level attachedLevel;

        SubgridFakeCellGetter(SubgridBlockEntity be, @javax.annotation.Nullable BlockPos aliasFrom,
                               @javax.annotation.Nullable BlockPos aliasTo) {
            this.be = be;
            this.aliasFrom = aliasFrom;
            this.aliasTo = aliasTo;
        }

        void attachLevel(Level level) {
            this.attachedLevel = level;
        }

        // Offset translation, not just an exact-match swap: onUse aliases the piece's real-world
        // position (needed for stillValid's distance check) to its fake-local anchor, but vanilla
        // code frequently queries NEIGHBORS of the position it was given too, not just the position
        // itself — e.g. DoorBlock.useWithoutItem clicking the UPPER half needs to find its own
        // LOWER half at pos.below() to toggle/sound it. An exact-match-only swap left every such
        // neighbor query un-translated, falling through to fallback()'s real-world read instead of
        // the actual sibling piece sitting right there in fake-local space (found live: clicking a
        // door's top half sometimes had no sound — the bottom-half lookup silently found nothing).
        // Translating the WHOLE neighborhood by the same offset fixes this generically for any
        // block doing a relative lookup during onUse, not just doors.
        private BlockPos resolve(BlockPos pos) {
            if (aliasFrom == null) return pos;
            return aliasTo.offset(pos.getX() - aliasFrom.getX(), pos.getY() - aliasFrom.getY(), pos.getZ() - aliasFrom.getZ());
        }

        @Override
        public BlockPos resolveLocal(BlockPos pos) {
            return resolve(pos);
        }

        @Override
        public void set(BlockPos pos, BlockState state) {
            super.set(resolve(pos), state);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return super.getBlockState(resolve(pos));
        }

        @Override
        protected BlockState fallback(BlockPos pos) {
            return be.realBlockStateAt(pos.getX(), pos.getY(), pos.getZ());
        }

        @Override
        public void setBlockEntity(BlockPos pos, BlockEntity blockEntity) {
            super.setBlockEntity(resolve(pos), blockEntity);
        }

        @Override
        public void removeBlockEntityAt(BlockPos pos) {
            super.removeBlockEntityAt(resolve(pos));
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            BlockPos resolved = resolve(pos);
            // A setBlockEntity call captured earlier THIS SAME vanilla call takes priority over
            // the piece-backed lookup below — the piece for a brand-new position (e.g. a piston
            // head extending into empty space) doesn't exist yet at this point; it's only created
            // afterward, in applyChanges' touched-cells scan.
            BlockEntity captured = super.getBlockEntity(resolved);
            if (captured != null) return captured;
            if (be.inBounds(resolved.getX(), resolved.getY(), resolved.getZ())) {
                PlacedPiece piece = be.getPieceAt(resolved.getX(), resolved.getY(), resolved.getZ());
                if (piece == null || piece.definition != INSTANCE || attachedLevel == null) return null;
                return blockEntityFor(piece, be, attachedLevel);
            }
            // Out-of-bounds: resolved is a real-world position (same reasoning as fallback()'s
            // realBlockStateAt use below) — a piece scanning past the subgrid's edge (a funnel's
            // extraction target, a hopper's suck-from-above) needs to see whatever REAL
            // BlockEntity genuinely sits there, e.g. a real chest placed next to the subgrid
            // block, not the permanent null this used to return for every out-of-bounds query
            // regardless of what was actually there. getPieceAt used to be called here with these
            // same huge real-world coordinates and threw AIOOBE (indexOf has no range check),
            // silently caught by the piece-tick safety net — the funnel/hopper ticker looked like
            // it ran but never found anything, every tick, forever.
            Level real = be.getLevel();
            return real != null ? real.getBlockEntity(resolved) : null;
        }
    }

    /**
     * Lazily creates (once) and caches on the piece a real phantom BlockEntity (e.g. a genuine
     * ChestBlockEntity) for any EntityBlock — needed so vanilla code like
     * ChestBlock.useWithoutItem's `level.getBlockEntity(pos) instanceof ChestBlockEntity` check,
     * and the menu it opens, see something real instead of the permanent null a fake position
     * space would otherwise return. Constructed at the SubgridBlockEntity's own real BlockPos
     * (not the piece's tiny fake anchor) so stillValid's distance check — which reads the
     * BlockEntity's OWN stored position, set once at construction — works no matter which fake
     * space instance later calls setLevel on it.
     *
     * Public and safe to call client-side too (no server-only calls in here) — SubgridRenderer
     * uses this exact same method to lazily build a client-side phantom BE purely for
     * BlockEntityRenderer dispatch, generically, for any EntityBlock piece (a chest, an enchanting
     * table, ...), not just the piston-specific animation path. The synced extraData "be" blob this
     * reads from already reaches the client via the normal piece-sync payloads (PlacedPiece.save
     * includes extraData whenever non-empty) — nothing extra needs sending for this to work.
     */
    public static BlockEntity blockEntityFor(PlacedPiece piece, SubgridBlockEntity be, Level level) {
        if (piece.runtimeBlockEntity instanceof BlockEntity cached) {
            cached.setLevel(level);
            return cached;
        }
        BlockState state = stateOf(piece);
        if (state == null || !(state.getBlock() instanceof EntityBlock entityBlock)) return null;
        BlockEntity created = entityBlock.newBlockEntity(be.getBlockPos(), state);
        if (created == null) return null;
        created.setLevel(level);
        if (piece.extraData.contains("be")) {
            created.loadCustomOnly(piece.extraData.getCompound("be"), level.registryAccess());
        }
        piece.runtimeBlockEntity = created;
        return created;
    }

    private static Map<BlockPos, BlockState> snapshot(SubgridBlockEntity be) {
        Map<BlockPos, BlockState> map = new HashMap<>();
        for (PlacedPiece p : be.getPieces()) {
            if (p.definition == INSTANCE) map.put(p.anchor, stateOf(p));
        }
        return map;
    }

    /**
     * Writes any sibling states that changed during the fake-space call back into their pieces,
     * and merges any scheduleTick requests the vanilla logic made (e.g. a redstone lamp asking
     * to turn itself off in 4 ticks) into be's own persistent queue — both FakeLevel and
     * FakeServerLevel capture these instead of touching a real tick list, since scheduledTick(...)
     * is what actually drains and fires them later.
     */
    private static void applyChanges(SubgridBlockEntity be, FakeSpace fakeSpace,
                                      Map<BlockPos, BlockState> before, ServerLevel realLevel) {
        FakeCellGetter cells = fakeSpace.cells();
        boolean anyChanged = false;
        // Collected rather than removed inline — removePieceAt mutates be.getPieces() itself,
        // which we're still iterating.
        List<PlacedPiece> selfDestructed = new ArrayList<>();
        // A snapshot, not the live list: be.notifyNeighbors(p) below can synchronously cascade
        // into another piece's own onNeighborChanged, which can run its OWN applyChanges call and
        // add/remove pieces via placePieceCrossBoundary/removePieceAt — mutating be.getPieces()
        // while THIS loop's iterator is still walking it (a piston's moving-block BE finalizing is
        // exactly this: found live via a ConcurrentModificationException from onUse -> applyChanges
        // once the finalize path started actually completing instead of crashing before reaching
        // notifyNeighbors at all). Same fix as SubgridBlockEntity.serverTick's own loop.
        for (PlacedPiece p : new ArrayList<>(be.getPieces())) {
            if (p.definition != INSTANCE) continue;
            BlockState now = cells.getBlockState(p.anchor);
            if (!now.equals(before.get(p.anchor))) {
                if (now.isAir()) {
                    // A piece's own updateShape/handleNeighborChanged (e.g. a crop losing its
                    // farmland support) decided it can no longer exist here — matches vanilla's
                    // own "setBlock to air = this block was destroyed" semantics. Just storing
                    // air as this piece's new state would leave a ghost piece still occupying
                    // the cell (invisible, but still "there") — same real vanilla drop path any
                    // other removal uses instead.
                    selfDestructed.add(p);
                } else {
                    if (isPistonRelated(before.get(p.anchor)) || isPistonRelated(now)) {
                        LOGGER.info("[piston-anim] state transition at {} (cell {}): {} -> {}",
                                be.getBlockPos(), p.anchor, before.get(p.anchor), now);
                    }
                    p.runtimeState = now;
                    syncBlockEntity(be, p, p.anchor, cells, (Level) fakeSpace, realLevel);
                    be.notifyNeighbors(p);
                }
                anyChanged = true;
            }
        }
        for (PlacedPiece p : selfDestructed) {
            // A piston pushing/pulling a block vacates its ORIGINAL cell the exact same way as a
            // genuine destruction: a plain setBlock to air, via moveBlocks' own final "clear the
            // old positions" loop (PistonBaseBlock.moveBlocks) — vanilla itself only calls
            // dropResources for blocks it couldn't push (getToDestroy()), never for ones it
            // successfully moved (getToPush()). The diff loop above can't otherwise tell "destroyed"
            // from "moved elsewhere this same call" apart, since both look identical from here: an
            // existing piece's anchor going from some state to air. If that SAME state shows up at
            // a DIFFERENT position in this call's touchedCells (the moved block's destination,
            // about to become a new piece below), treat it as relocated — drop nothing, since the
            // block still exists, just at a new cell.
            BlockState previousState = before.get(p.anchor);
            boolean movedElsewhere = false;
            for (Map.Entry<BlockPos, BlockState> touched : cells.touchedCells().entrySet()) {
                if (!touched.getKey().equals(p.anchor) && previousState.equals(touched.getValue())) {
                    movedElsewhere = true;
                    break;
                }
            }
            if (isPistonRelated(previousState)) {
                LOGGER.info("[piston-anim] {} at {} (cell {}) went to air — {}",
                        previousState, be.getBlockPos(), p.anchor, movedElsewhere ? "moved elsewhere, no drop" : "treated as destroyed, dropping");
            }
            // realPositionOf, not be.getBlockPos()+0.5 — that would collapse every piece's drop
            // to the whole SubgridBlock's shared center regardless of where inside it the piece
            // actually was.
            Vec3 dropPos = be.realPositionOf(p.anchor);
            PlacedPiece removed = be.removePieceAt(p.anchor.getX(), p.anchor.getY(), p.anchor.getZ());
            if (removed != null && !movedElsewhere) {
                for (ItemStack drop : removed.definition.drops(removed, realLevel, be.getBlockPos(), be, ItemStack.EMPTY)) {
                    realLevel.addFreshEntity(new ItemEntity(realLevel, dropPos.x, dropPos.y, dropPos.z, drop));
                }
            }
        }
        // A cell that WASN'T an existing piece before this call but now holds real state — e.g.
        // water spreading into a previously-empty neighbor, or a falling block relocating, via
        // that logic's own setBlock calls. The diff loop above only ever looks at existing
        // pieces' anchors, so a genuinely new position like this is otherwise silently lost — the
        // write happens in the fake cell map and then the whole map is discarded when this call
        // ends. Turn it into a real piece instead, matching vanilla's actual "setBlock somewhere
        // new = a block now exists there" semantics, generic to whatever spread/moved it there.
        int max = be.getGridSize() - 1;
        for (Map.Entry<BlockPos, BlockState> touched : cells.touchedCells().entrySet()) {
            BlockPos pos = touched.getKey();
            BlockState touchedState = touched.getValue();
            if (touchedState.isAir() || before.containsKey(pos) || be.getPieceAt(pos.getX(), pos.getY(), pos.getZ()) != null) {
                continue;
            }
            boolean withinBounds = pos.getX() >= 0 && pos.getX() <= max
                    && pos.getY() >= 0 && pos.getY() <= max && pos.getZ() >= 0 && pos.getZ() <= max;
            // placePieceCrossBoundary finds/creates an adjacent same-size SubgridBlockEntity when
            // pos overflows past exactly one edge (e.g. water/a falling block/a growing tree
            // crossing this subgrid's boundary), instead of the write just being silently dropped.
            PlacedPiece placed = be.placePieceCrossBoundary(INSTANCE, pos, Direction.UP, touchedState, realLevel);
            if (placed != null) {
                if (isPistonRelated(touchedState)) {
                    LOGGER.info("[piston-anim] new piece at {} (cell {}, withinBounds={}): {}",
                            be.getBlockPos(), pos, withinBounds, touchedState);
                }
                if (withinBounds) {
                    syncBlockEntity(be, placed, pos, cells, (Level) fakeSpace, realLevel);
                } else {
                    // Landed on a different SubgridBlockEntity than be (crossed the subgrid
                    // boundary) — still safe to attach any captured BE directly (position-agnostic,
                    // just a field on the piece), but syncBlockEntity's animation-hint payload needs
                    // the correct OWNING SubgridBlockEntity + its own local anchor to address the
                    // client update to, neither of which is `be`/`pos` here. Narrow, accepted gap: a
                    // piston that extends exactly across a subgrid boundary still ends up in the
                    // right final state (server-authoritative), it just won't animate that specific
                    // hop client-side.
                    BlockEntity capturedElsewhere = cells.touchedBlockEntities().get(pos);
                    if (capturedElsewhere != null) {
                        capturedElsewhere.setLevel((Level) fakeSpace);
                        placed.runtimeBlockEntity = capturedElsewhere;
                    }
                }
                // placePiece already calls notifyNeighbors + the new piece's own onNeighborChanged
                // — but NOT onPlace, same gap BlockAccess was added for at the top-level placement
                // flow. A freshly-spread cell needs its own onPlace to schedule ITS next step, or
                // the spread/fall stops after one cell. Only valid to fire against fakeSpace when
                // the piece landed IN THIS grid — a cross-boundary placement lands in a different
                // SubgridBlockEntity, whose position semantics fakeSpace knows nothing about; that
                // piece will still react normally to its own future onNeighborChanged calls, just
                // not this exact tick.
                if (withinBounds) {
                    BlockAccess.onPlace(touchedState, (Level) fakeSpace, pos, Blocks.AIR.defaultBlockState(), false);
                }
                anyChanged = true;
            }
        }
        for (FakeSpace.ScheduledEntry entry : fakeSpace.scheduledTicks()) {
            be.scheduleTick(entry.pos().getX(), entry.pos().getY(), entry.pos().getZ(),
                    realLevel.getGameTime() + entry.delay());
            anyChanged = true;
        }
        if (anyChanged) be.notifyUpdate();
    }

    /**
     * A vanilla setBlockEntity/removeBlockEntity call captured this vanilla call at pos (e.g.
     * PistonBaseBlock attaching a fully-parameterized PistonMovingBlockEntity to a freshly-placed
     * MOVING_PISTON piece) — mirror it onto the piece's own runtimeBlockEntity cache so
     * blockEntityFor picks up the real instance next time instead of lazily building a blank one.
     * MovingPistonBlock.newBlockEntity deliberately returns null for exactly this reason: the real
     * instance is only ever handed over via setBlockEntity, never built lazily.
     *
     * A freshly captured PistonMovingBlockEntity also gets broadcast to nearby clients as a
     * PistonAnimationPayload — see that class for why this can't just ride the normal piece-state
     * resync. This only fires once per animation: subsequent server ticks advance progress via the
     * SAME cached BE instance without another setBlockEntity call (PistonMovingBlockEntity.tick
     * mutates its own fields in place), so touchedBlockEntities() is empty on those ticks and this
     * whole method no-ops via the captured == null check below.
     */
    private static void syncBlockEntity(SubgridBlockEntity be, PlacedPiece piece, BlockPos pos,
                                         FakeCellGetter cells, Level fakeLevel, ServerLevel realLevel) {
        if (cells.removedBlockEntityPositions().contains(pos)) {
            if (piece.runtimeBlockEntity instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity) {
                LOGGER.info("[piston-anim] server finalized MOVING_PISTON BE at {} (cell {})", be.getBlockPos(), pos);
            }
            piece.runtimeBlockEntity = null;
            return;
        }
        BlockEntity captured = cells.touchedBlockEntities().get(pos);
        if (captured == null) return;
        captured.setLevel(fakeLevel);
        piece.runtimeBlockEntity = captured;
        if (captured instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity moving) {
            LOGGER.info("[piston-anim] captured MOVING_PISTON BE at {} (cell {}) extending={} source={} — sending animation payload",
                    be.getBlockPos(), pos, moving.isExtending(), moving.isSourcePiston());
            CompoundTag beNbt = moving.saveCustomOnly(realLevel.registryAccess());
            // PistonMovingBlockEntity's constructor requires its OWN outer BlockState (MOVING_PISTON,
            // matching BlockEntityType.PISTON) — saveCustomOnly above doesn't include it (that's the
            // BE's own NBT, not the block state it sits on). Embed piece.runtimeState explicitly
            // (known correct right here, synchronously, on the server) rather than having the client
            // re-derive it from the live piece when the payload is processed — that piece may already
            // have moved on to a DIFFERENT state by then (e.g. a same-tick full resync racing ahead
            // of this payload), which is exactly what threw "Invalid block entity" client-side before
            // this fix: the client tried to build a PistonMovingBlockEntity out of whatever state the
            // piece already held at that later moment, not the MOVING_PISTON state that was true when
            // this BE was actually captured.
            beNbt.put("outerState", NbtUtils.writeBlockState(stateOf(piece)));
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingChunk(realLevel,
                    new net.minecraft.world.level.ChunkPos(be.getBlockPos()),
                    new com.therappha.tinyblocks.network.PistonAnimationPayload(be.getBlockPos(), pos, beNbt));
        }
    }

    /** Which face of piece the already-placed changedNeighbor touches, or null if it can't be found. */
    private static Direction directionTo(SubgridBlockEntity be, PlacedPiece piece, PlacedPiece changedNeighbor) {
        // changedNeighbor may already be detached from the grid (removed) by the time this runs
        // — its OWN piece.definition.onNeighborChanged already fired synchronously, updating its
        // state or removing it, before its notifyNeighbors() cascade reaches here — so a live
        // neighborFacing lookup would always find nothing for the removed case and this would
        // always return null, silently skipping updateShape (the hook that decides "can I still
        // survive without what's now missing") for every single "neighbor removed" notification.
        // The removed piece's own anchor is still valid data even after detachment, so compute
        // the direction geometrically first — works regardless of whether it's still placed.
        BlockPos delta = changedNeighbor.anchor.subtract(piece.anchor);
        for (Direction dir : Direction.values()) {
            if (dir.getStepX() == delta.getX() && dir.getStepY() == delta.getY() && dir.getStepZ() == delta.getZ()) {
                return dir;
            }
        }
        // Anchors only compare directly within the same SubgridBlockEntity's coordinate space —
        // for a cross-grid neighbor the geometric delta is meaningless, so fall back to the live
        // lookup (only resolvable if changedNeighbor is still actually placed there).
        for (Direction dir : Direction.values()) {
            SubgridBlockEntity.Neighbor n = be.neighborFacing(piece, dir);
            if (n != null && n.piece() == changedNeighbor) return dir;
        }
        return null;
    }
}
