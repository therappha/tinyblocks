package com.therappha.tinyblocks.v2;

import com.mojang.logging.LogUtils;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.compat.create.CreatePhantomTransform;
import com.therappha.tinyblocks.subgrid.GridTickScheduler;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * v2: a single generic PieceDefinition that can hold ANY real vanilla BlockState, instead of
 * one hand-written PieceDefinition per block type (see PieceDefinitions.java for the old way).
 * Stores the actual BlockState in piece.runtimeState and runs interact/neighborChanged/hardness
 * through a fake, in-memory position space (FakeCellGetter/FakeLevel, or FakeServerLevel for the
 * tick methods) built from this piece's SubgridBlockEntity siblings — real vanilla
 * behavior, zero reimplementation.
 */
public class VanillaBlockPiece extends PieceDefinition {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final VanillaBlockPiece INSTANCE = new VanillaBlockPiece();

    private VanillaBlockPiece() {
        this(ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "vanilla_block"), new Vec3i(1, 1, 1));
    }

    /**
     * A real block spread over more than one cell (com.therappha.tinyblocks.compat.create.ShaftConnectorPiece).
     * Its state is seeded at the anchor only; every other cell it covers reads back the same state through
     * realBlockStateAt, and a lookup of any of them returns its one phantom block entity, which sits at the
     * anchor's virtual position.
     */
    protected VanillaBlockPiece(ResourceLocation id, Vec3i footprint) {
        super(id, footprint);
    }

    /**
     * Forces this class (and its PieceDefinition.REGISTRY registration) to load at mod setup,
     * same as PieceDefinitions.init(). Without this, INSTANCE only loads lazily on first use
     * (e.g. the first Minimizer or block-item right-click) — but BlockEntity NBT loading can happen
     * before that, so PieceDefinition.getOrThrow("tinyblocks:vanilla_block") would throw and
     * silently drop any SubgridBlockEntity containing a saved VanillaBlockPiece.
     */
    public static void init() {}

    static BlockState stateOf(PlacedPiece piece) {
        return (BlockState) piece.runtimeState;
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

    /**
     * The state turns as a vanilla structure turns it. The block entity data saved for it is reread into a new phantom
     * on its next tick, built with the turned state, as when its chunk loads.
     */
    @Override
    public void transform(PlacedPiece piece, Rotation rotation, Mirror mirror, HolderLookup.Provider registries) {
        BlockState state = stateOf(piece);
        if (state == null) return;
        BlockState turned = state.mirror(mirror).rotate(rotation);
        piece.runtimeState = turned;
        piece.extraData.put("state", NbtUtils.writeBlockState(turned));
        if (piece.extraData.contains("be")) {
            CreatePhantomTransform.transformData(turned, piece.extraData.getCompound("be"));
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

        // Cheap ticker check FIRST (issue #50): most EntityBlocks (chest, sign, bed, ...) have no
        // server ticker at all, and everything below — a reflection-allocated FakeServerLevel, a
        // field transplant, a copy of every sibling — was being built and
        // thrown away every single tick just to find that out. blockEntityFor caches the phantom
        // on the piece, so the call further down reuses it instead of building a second one.
        //
        // Must NOT re-point an already-cached phantom at the real level (blockEntityFor does that on
        // every call): a menu opened earlier (chest, anvil, ...) validates itself through
        // phantom.getLevel().getBlockEntity(phantom.getBlockPos()) == phantom, which only holds in
        // a fake space — against the real level that position holds the SubgridBlockEntity, so the
        // menu would close on the very next tick. Only a not-yet-built phantom goes through
        // blockEntityFor (once); onUse/the full path below set the fake level when it matters.
        //
        // Built in a space of its grid, not on the real level, where its position means nothing: a change to a phantom that
        // never ticks (a chest a hopper fills) would tell the real level, and no comparator next to it would hear.
        BlockEntity candidate = piece.runtimeBlockEntity instanceof BlockEntity cached
                ? cached : blockEntityFor(piece, be, operatingServerSpace(piece, be, level));
        if (candidate == null || !hasServerTicker(entityBlock, level, state, candidate)) return false;

        // The ticker runs at the phantom's own unique virtual position (see SubgridBlockEntity#virtualOrigin);
        // every block entity in the grid shares one constant coordinate frame, so a ticker that reads
        // blockEntity.getBlockPos() and its neighbours (hopper, Create's rotation propagation) sees the
        // real layout, and a captured BE built by vanilla at a world-frame position needs no special case.
        BlockPos tickPos = be.virtualPosOf(piece.anchor);
        TickSpace space = TickSpace.acquire(be, level, piece.anchor);
        try {
            FakeServerLevel fakeLevel = space.level;
            BlockEntity phantom = blockEntityFor(piece, be, fakeLevel);
            if (phantom == null) return false;

            if (!tickTyped(entityBlock, fakeLevel, state, tickPos, phantom)) return false;
            GridWriteBack.applyChanges(be, fakeLevel, level);
        } finally {
            // Phantoms stay bound to the space between ticks, and code outside the grid reads through them
            // (boundPhantom): it must see the pieces as they are, not this tick's leftover writes and ticks.
            space.release();
        }
        // applyChanges already handles its own notifyUpdate/notifyNeighbors for whatever actually
        // changed — returning true here would make SubgridBlockEntity.serverTick redundantly
        // notifyNeighbors(piece) again for just this one piece regardless of whether anything
        // about IT specifically changed.
        return false;
    }

    @Override
    public boolean requiresClientTick() {
        // Checked per-piece inside clientTick below, same reasoning as requiresTick() above —
        // cheap for the vast majority of pieces that aren't mid piston animation.
        return true;
    }

    @Override
    public void clientTick(PlacedPiece piece, Level level, BlockPos subgridPos, SubgridBlockEntity be) {
        if (piece.runtimeBlockEntity instanceof PistonMovingBlockEntity moving) {
            PistonAnimation.clientTick(piece, subgridPos, moving);
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
                // In a fake space of the grid, as the server ticks it: a block entity asking for its own block at its
                // (virtual) position gets it there, not whatever the real level holds there (Create's chute refreshes
                // its state on its first tick and was handed void air). Its level is given back after, for the
                // renderers that read it.
                FakeLevel space = clientTickSpace(be, level);
                Level was = generic.getLevel();
                generic.setLevel(space);
                try {
                    tickClientTyped(entityBlock, space, state, generic.getBlockPos(), generic);
                } finally {
                    generic.setLevel(was);
                }
            }
        }
    }

    /**
     * The client fake space {@code be}'s pieces tick in, one per grid and emptied before each use: what a client tick
     * writes to it is never applied, as the client's copy of a grid only changes by what the server sends.
     */
    private static FakeLevel clientTickSpace(SubgridBlockEntity be, Level level) {
        if (be.clientTickSpace instanceof FakeLevel cached && cached.realLevel() == level) {
            cached.cells().clear();
            return cached;
        }
        FakeLevel space = buildClientFakeSpace(be, level);
        be.clientTickSpace = space;
        return space;
    }

    /** Client-side twin of tickTyped below — same bridging trick, different (client) Level. */
    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> void tickClientTyped(EntityBlock entityBlock, Level level,
                                                                  BlockState state, BlockPos pos, T phantom) {
        var ticker = entityBlock.getTicker(level, state, (BlockEntityType<T>) phantom.getType());
        if (ticker == null) return;
        ticker.tick(level, pos, state, phantom);
    }

    /**
     * Client-side entry point for PhantomDataPayload: loads the server's current phantom data into this
     * client's cached phantom for the piece at cell (building it first if needed). A block entity's update tag
     * (clientUpdate) is loaded the way a chunk loads it on a client; saved data as saved data.
     */
    public static void applyPhantomData(SubgridBlockEntity be, BlockPos cell, CompoundTag nbt, boolean clientUpdate, Level level) {
        PlacedPiece piece = be.getPieceAt(cell.getX(), cell.getY(), cell.getZ());
        if (piece == null || !(piece.definition instanceof VanillaBlockPiece)) return;
        BlockEntity phantom = piece.runtimeBlockEntity instanceof BlockEntity cached ? cached : blockEntityFor(piece, be, level);
        if (phantom == null) return;
        if (clientUpdate) phantom.handleUpdateTag(nbt, level.registryAccess());
        else phantom.loadCustomOnly(nbt, level.registryAccess());
    }

    /**
     * EntityBlock.getTicker's type parameter has to match the BlockEntityType exactly (it's
     * generic, not wildcard-friendly) — phantom is only known as a plain BlockEntity at the call
     * site above, so bridge through a method with its own type variable the way vanilla's own
     * BaseEntityBlock.createTickerHelper does. Safe: phantom.getType() IS phantom's real type,
     * the unchecked cast just tells the compiler what's already true at runtime.
     */
    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> boolean hasServerTicker(EntityBlock entityBlock, ServerLevel level,
                                                                     BlockState state, T phantom) {
        return entityBlock.getTicker(level, state, (BlockEntityType<T>) phantom.getType()) != null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> boolean tickTyped(EntityBlock entityBlock, FakeServerLevel fakeLevel,
                                                              BlockState state, BlockPos pos, T phantom) {
        var ticker = entityBlock.getTicker(fakeLevel, state, (BlockEntityType<T>) phantom.getType());
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
        // The phantom goes along for what its loot table copies off the block entity: a shulker box's contents.
        return Block.getDrops(state, level, be.getBlockPos(),
                piece.runtimeBlockEntity instanceof BlockEntity phantom ? phantom : null, null, tool);
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

        // The piece lives at its own unique position in the global virtual frame, far from the player, so
        // stillValid's distance check (player vs the phantom's stored position) is redirected to the real
        // SubgridBlock by PlayerMixin; PhantomInteraction remembers which grid that is.
        BlockPos realPos = be.getBlockPos();
        PhantomInteraction.remember(player, be.virtualOrigin(), be.getGridSize(), realPos);
        if (PieceClickDispatch.dispatch(be, piece, operatingServerSpace(piece, be, serverLevel), player,
                InteractionHand.MAIN_HAND, hit)) {
            be.setChanged(); // what a listener changed lives in the phantom, saved with the grid
            return InteractionResult.SUCCESS;
        }
        SubgridFakeCellGetter cells = new SubgridFakeCellGetter(be, piece.anchor);
        FakeLevel fakeLevel = new FakeLevel(serverLevel, cells, realPos);
        cells.attachLevel(fakeLevel);

        BlockPos at = cells.toWorld(piece.anchor);
        // The real hit point, scaled into the piece's virtual block: blocks read where on them the click landed.
        BlockHitResult fakeHit = new BlockHitResult(
                VirtualHit.toVirtual(hit.getLocation(), realPos, be.virtualOrigin(), be.getGridSize()),
                hit.getDirection(), at, hit.isInside());
        // Vanilla's order: the block's use with the held item first, then its use without one. A block item gets
        // its turn too: Create's blocks place a held block of their own through their placement helpers (a shaft
        // extends a shaft), or encase themselves with a casing. Whatever such a use writes into the fake space
        // becomes a piece in applyChanges; a plain block's use passes, and SubgridEventHandler places it.
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty()) {
            // First the item's own say before the block's (NeoForge's onItemUseFirst, as ServerPlayerGameMode asks
            // it): a Create cogwheel places itself through its placement helper there, diagonal to a cog of the
            // other size.
            InteractionResult first = held.onItemUseFirst(new UseOnContext(
                    fakeLevel, player, InteractionHand.MAIN_HAND, held, fakeHit));
            if (first != InteractionResult.PASS) {
                GridWriteBack.applyChanges(be, fakeLevel, serverLevel);
                return first;
            }
            ItemInteractionResult itemResult = cells.getBlockState(at).useItemOn(
                    held, fakeLevel, player, InteractionHand.MAIN_HAND, fakeHit);
            if (itemResult != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
                GridWriteBack.applyChanges(be, fakeLevel, serverLevel);
                // Not consumed: the item's own use (a wrench turning the block) gets its turn.
                return itemResult.consumesAction() ? itemResult.result() : InteractionResult.PASS;
            }
        }
        InteractionResult result = cells.getBlockState(at).useWithoutItem(fakeLevel, player, fakeHit);
        // Last, as vanilla: the item's own use on the clicked block. Only for a block item that brings its own (a
        // Create belt connector: first shaft, then second, then the belt between them) — a plain block item's use
        // is placing itself, which SubgridEventHandler does.
        if (result == InteractionResult.PASS && OwnUseOn.of(held)) {
            result = held.useOn(new UseOnContext(
                    fakeLevel, player, InteractionHand.MAIN_HAND, held, fakeHit));
        }

        GridWriteBack.applyChanges(be, fakeLevel, serverLevel);
        return result;
    }

    /** Whether a held block item overrides BlockItem#useOn, i.e. does something on a block other than placing itself. */
    private static final class OwnUseOn {
        private static final ClassValue<Boolean> OVERRIDES = new ClassValue<>() {
            @Override
            protected Boolean computeValue(Class<?> type) {
                try {
                    return type.getMethod("useOn", UseOnContext.class).getDeclaringClass()
                            != BlockItem.class;
                } catch (NoSuchMethodException e) {
                    return false;
                }
            }
        };

        static boolean of(ItemStack stack) {
            return stack.getItem() instanceof BlockItem && OVERRIDES.get(stack.getItem().getClass());
        }
    }

    @Override
    public void onNeighborChanged(PlacedPiece piece, ServerLevel level, BlockPos subgridPos, SubgridBlockEntity be,
                                   PlacedPiece changedNeighbor) {
        BlockState state = stateOf(piece);
        if (state == null) return;

        Direction dir = directionTo(be, piece, changedNeighbor);
        BlockState neighborState = changedNeighbor.definition.renderState(changedNeighbor);

        FakeLevel fakeLevel = buildFakeSpace(be, level);
        BlockPos at = fakeLevel.world(piece.anchor);
        BlockPos fakeNeighborPos = dir != null ? at.relative(dir) : at;

        fakeLevel.cells().getBlockState(at)
                .handleNeighborChanged(fakeLevel, at, neighborState.getBlock(), fakeNeighborPos, false);

        if (dir != null) {
            // handleNeighborChanged drives power-level-style self-mutation (a block reacting to
            // and updating itself via its own setBlock call); it does NOT recompute
            // connection-dependent shape state (which sides a fence/wall/wire visually connects
            // to). That's a separate vanilla hook — real placement already runs it once via
            // getStateForPlacement, but we don't have vanilla's automatic post-placement
            // shape-update cascade, so ongoing neighbor changes need it called explicitly too.
            BlockState current = fakeLevel.cells().getBlockState(at);
            BlockState reshaped = current.updateShape(dir, neighborState, fakeLevel, at, fakeNeighborPos);
            Block.updateOrDestroy(current, reshaped, fakeLevel, at,
                    Block.UPDATE_ALL);
        }

        GridWriteBack.applyChanges(be, fakeLevel, level);
    }

    /**
     * The block across {@code face} of the grid changed: a piston head against that face goes if its piston did. Only
     * a head: a piece resting on a real block (a lever on the ground) doesn't always read that block from here.
     */
    public void reshapeAgainst(PlacedPiece piece, ServerLevel level, SubgridBlockEntity be, Direction face) {
        if (!(stateOf(piece) != null && stateOf(piece).getBlock() instanceof PistonHeadBlock)) return;
        FakeLevel fakeLevel = buildFakeSpace(be, level);
        BlockPos at = fakeLevel.world(piece.anchor), across = at.relative(face);
        fakeLevel.cells().set(at, fakeLevel.cells().getBlockState(at)
                .updateShape(face, fakeLevel.getBlockState(across), fakeLevel, at, across));
        GridWriteBack.applyChanges(be, fakeLevel, level);
    }

    @Override
    public void scheduledTick(PlacedPiece piece, ServerLevel level, BlockPos subgridPos, SubgridBlockEntity be) {
        scheduledTick(piece, level, subgridPos, be, null);
    }

    /**
     * On the grid's one reused space, not a new one: besides being cheaper, a redstone torch keeps its recent toggles
     * per level instance, and on a new level each time it never burned out.
     */
    @Override
    public void scheduledTick(PlacedPiece piece, ServerLevel level, BlockPos subgridPos, SubgridBlockEntity be, @Nullable Object type) {
        BlockState state = stateOf(piece);
        if (state == null) return;
        boolean blockTick = type == null || (type instanceof Block block && state.is(block));
        boolean fluidTick = type == null || (type instanceof Fluid fluid && state.getFluidState().is(fluid));
        if (!blockTick && !fluidTick) return;

        TickSpace space = TickSpace.acquire(be, level, null);
        try {
            FakeServerLevel fakeLevel = space.level;
            runScheduledTick(fakeLevel, fakeLevel.world(piece.anchor), level, blockTick, fluidTick);
            GridWriteBack.applyChanges(be, fakeLevel, level);
        } finally {
            space.release();
        }
    }

    private static void runScheduledTick(FakeServerLevel fakeLevel, BlockPos at, ServerLevel level, boolean blockTick, boolean fluidTick) {
        if (blockTick) fakeLevel.cells().getBlockState(at).tick(fakeLevel, at, level.getRandom());
        // LiquidBlock doesn't override the regular block tick above at all — water/lava spread
        // exclusively through the SEPARATE fluid-tick schedule (LiquidBlock.onPlace/
        // neighborChanged, both already reached, call level.scheduleTick(pos, fluid, delay), now
        // captured by FakeLevel's fluid-tick overloads). drainScheduledTicks doesn't distinguish
        // which kind of tick was requested, so always attempt both here — FluidState.tick is a
        // real no-op for Fluids.EMPTY (every non-fluid BlockState), so this is inert for the vast
        // majority of pieces and only does something for genuine water/lava.
        if (!fluidTick) return;
        fakeLevel.cells().getBlockState(at).getFluidState().tick(fakeLevel, at);
    }

    /**
     * What a chunk and Level#setBlock do when a block arrives, for a piece just added to its grid: its onPlace, then the
     * updates and the shape updates, which reach diagonals (dust climbing a step reshapes the dust below it).
     */
    @Override
    public void afterPlaced(PlacedPiece piece, ServerLevel level, SubgridBlockEntity be) {
        BlockState state = stateOf(piece);
        if (state == null) return;
        BlockState air = Blocks.AIR.defaultBlockState();
        int flags = Block.UPDATE_CLIENTS;
        TickSpace space = TickSpace.acquire(be, level, null);
        try {
            FakeServerLevel fakeLevel = space.level;
            BlockPos at = fakeLevel.world(piece.anchor);
            BlockAccess.onPlace(state, fakeLevel, at, air, false);
            if (fakeLevel.getBlockState(at) == state) { // as in FakeWrites: changed by its own onPlace, nothing is told
                fakeLevel.blockUpdated(at, air.getBlock());
                if (state.hasAnalogOutputSignal()) fakeLevel.updateNeighbourForOutputSignal(at, state.getBlock());
                air.updateIndirectNeighbourShapes(fakeLevel, at, flags, 511);
                state.updateNeighbourShapes(fakeLevel, at, flags, 511);
                state.updateIndirectNeighbourShapes(fakeLevel, at, flags, 511);
            }
            GridWriteBack.applyChanges(be, fakeLevel, level);
        } finally {
            space.release();
        }
        tellOtherDefinitions(piece, level, be);
    }

    /** The updates above stay among vanilla blocks; a piece of another kind, or a cell of a larger piece, is told apart. */
    private static void tellOtherDefinitions(PlacedPiece piece, ServerLevel level, SubgridBlockEntity be) {
        boolean large = piece.footprint.getX() * piece.footprint.getY() * piece.footprint.getZ() > 1;
        for (SubgridBlockEntity.Neighbor neighbor : be.neighborsOf(piece)) {
            if ((large || !(neighbor.piece().definition instanceof VanillaBlockPiece))
                    && neighbor.be().getLevel() instanceof ServerLevel neighborLevel) {
                neighbor.piece().definition.onNeighborChanged(neighbor.piece(), neighborLevel, neighbor.be().getBlockPos(), neighbor.be(), piece);
            }
        }
    }

    /**
     * What a chunk and Level#setBlock do when a block goes, for a piece that left its grid: its onRemove with the cell
     * empty, then the updates, which reach as far as the block makes them (a torch tells what the block above it
     * powers). Everything about it hearing the change runs here in one call. What isn't a VanillaBlockPiece hears the
     * old way.
     */
    @Override
    public void afterRemoved(PlacedPiece piece, ServerLevel level, SubgridBlockEntity be) {
        BlockState state = stateOf(piece);
        if (state == null) return;
        BlockState air = Blocks.AIR.defaultBlockState();
        int flags = Block.UPDATE_CLIENTS;
        TickSpace space = TickSpace.acquire(be, level, null);
        try {
            FakeServerLevel fakeLevel = space.level;
            BlockPos at = fakeLevel.world(piece.anchor);
            // What a block's onRemove reads back from its cell, as a chunk still has it then: a chest spills its contents.
            if (piece.runtimeBlockEntity instanceof BlockEntity phantom) fakeLevel.cells().setBlockEntity(at, phantom);
            BlockAccess.onRemove(state, fakeLevel, at, air, false);
            fakeLevel.blockUpdated(at, state.getBlock());
            if (state.hasAnalogOutputSignal()) fakeLevel.updateNeighbourForOutputSignal(at, state.getBlock());
            state.updateIndirectNeighbourShapes(fakeLevel, at, flags, 511);
            air.updateNeighbourShapes(fakeLevel, at, flags, 511);
            air.updateIndirectNeighbourShapes(fakeLevel, at, flags, 511);
            fakeLevel.gameEvent(GameEvent.BLOCK_DESTROY, at,
                    GameEvent.Context.of(state));
            GridWriteBack.applyChanges(be, fakeLevel, level);
        } finally {
            space.release();
        }
        tellOtherDefinitions(piece, level, be);
        be.markPiecesChanged();
    }

    /** A block event's turn came (GridTickScheduler): it runs like any other call into the grid. */
    @Override
    public void blockEvent(ServerLevel level, SubgridBlockEntity be, GridTickScheduler.BlockEvent event) {
        TickSpace space = TickSpace.acquire(be, level, null);
        try {
            space.level.runBlockEvent(event.pos(), event.block(), event.id(), event.param());
            GridWriteBack.applyChanges(be, space.level, level);
        } finally {
            space.release();
        }
    }

    @Override
    public void randomTick(PlacedPiece piece, ServerLevel level, BlockPos subgridPos, SubgridBlockEntity be) {
        BlockState state = stateOf(piece);
        if (state == null || !state.isRandomlyTicking()) return;

        FakeServerLevel fakeLevel = buildFakeServerSpace(be, level);
        BlockPos at = fakeLevel.world(piece.anchor);

        fakeLevel.cells().getBlockState(at).randomTick(fakeLevel, at, level.getRandom());

        GridWriteBack.applyChanges(be, fakeLevel, level);
    }

    // --- Fake space plumbing, shared with SubgridEventHandler's placement flow ---

    /**
     * A fake space for CLIENT-side hosts of phantom block entities (the Flywheel SubgridVisual). Its light
     * and biome queries are answered at the SubgridBlock's real position, so a hosted visual that lights
     * itself from its block entity's position — which is now a virtual-cube position that can be inside
     * solid ground — still gets the light of the block it visually sits in.
     */
    public static FakeLevel buildClientFakeSpace(SubgridBlockEntity be, Level clientLevel) {
        return plainFakeSpace(be, clientLevel);
    }

    /**
     * Builds a FakeLevel whose cell space mirrors every VanillaBlockPiece sibling in be. Used for
     * interact/neighborChanged/placement — none of which actually need a genuine ServerLevel,
     * only a Level — so they stay on this simpler, proven path rather than the more fragile
     * FakeServerLevel (see buildFakeServerSpace), which is scoped to just the tick methods
     * that are statically typed to require one.
     */
    public static FakeLevel buildFakeSpace(SubgridBlockEntity be, ServerLevel serverLevel) {
        return plainFakeSpace(be, serverLevel);
    }

    /** A FakeLevel over {@code be}'s cells, wrapping {@code real}: buildFakeSpace and buildClientFakeSpace. */
    private static FakeLevel plainFakeSpace(SubgridBlockEntity be, Level real) {
        SubgridFakeCellGetter cells = (SubgridFakeCellGetter) cellsFor(be);
        FakeLevel fakeLevel = new FakeLevel(real, cells, be.getBlockPos());
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
        return new SubgridFakeCellGetter(be, null);
    }

    /**
     * Like blockEntityFor, but for a piece that is only being READ by some other piece's operation
     * (a hopper looking at the chest above it): returns the phantom without re-pointing an
     * already-bound one at the reader's fake level. blockEntityFor's unconditional setLevel gave the
     * chest the hopper-tick level, whose alias maps the chest's real position to the HOPPER's
     * anchor — so the chest's open menu, which validates through phantom.getLevel().getBlockEntity(
     * phantom.getBlockPos()) == phantom, failed and closed on every hopper cooldown (issue #57).
     * An unbound phantom still gets the reader's level so it is usable at all until its own
     * tick/onUse binds it properly.
     */
    static BlockEntity peekBlockEntity(PlacedPiece piece, SubgridBlockEntity be, Level level) {
        if (piece.runtimeBlockEntity instanceof BlockEntity cached && cached.getLevel() != null) return cached;
        return blockEntityFor(piece, be, level);
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
        BlockEntity created = entityBlock.newBlockEntity(be.virtualPosOf(piece.anchor), state);
        if (created == null) return null;
        created.setLevel(level);
        if (piece.extraData.contains("be")) {
            created.loadCustomOnly(piece.extraData.getCompound("be"), level.registryAccess());
        }
        piece.runtimeBlockEntity = created;
        PhantomOwners.register(created, be, piece);
        GridGameEvents.register(created, be);
        return created;
    }

    /**
     * A real block entity is removed with its block (Level#removeBlockEntity -> setRemoved), and some act on it
     * there: Create's kinetic block entities detach from their network, stopping everything they drove at once
     * instead of at the next periodic validation. The phantom gets the same call, bound to a fake space of its
     * grid so whatever it does looks at its own neighbours.
     */
    @Override
    public void onRemoved(PlacedPiece piece, ServerLevel level, SubgridBlockEntity be) {
        if (!(piece.runtimeBlockEntity instanceof BlockEntity phantom) || phantom.isRemoved()) return;
        FakeServerLevel fakeLevel = buildFakeServerSpace(be, level);
        phantom.setLevel(fakeLevel);
        try {
            phantom.setRemoved();
        } catch (RuntimeException e) {
            LOGGER.warn("Removing the block entity of a piece at {} (cell {}) failed", be.getBlockPos(), piece.anchor, e);
        }
    }

    /**
     * The phantom of {@code piece}, bound to a fake space of its own grid — the same binding its own tick
     * gives it. For code outside the grid that hands the phantom to vanilla/mod logic which then walks its
     * neighbours through phantom.getLevel() (Create's rotation propagation entering from a real block): a
     * phantom still bound to the real level would look its virtual neighbours up in the real world.
     */
    public static BlockEntity boundPhantom(PlacedPiece piece, SubgridBlockEntity be, ServerLevel level) {
        if (piece.runtimeBlockEntity instanceof BlockEntity cached && cached.getLevel() instanceof FakeSpace) {
            return cached;
        }
        return blockEntityFor(piece, be, operatingServerSpace(piece, be, level));
    }

    /** A fake server space of {@code be} operated on by {@code piece}: a lookup of its cell binds its phantom to it. */
    static FakeServerLevel operatingServerSpace(PlacedPiece piece, SubgridBlockEntity be, ServerLevel level) {
        SubgridFakeCellGetter cells = new SubgridFakeCellGetter(be, piece.anchor);
        FakeServerLevel fakeLevel = FakeServerLevel.create(level, cells, be.getBlockPos());
        cells.attachLevel(fakeLevel);
        cells.live = false; // only a phantom's binding: nothing applies what is written to it
        return fakeLevel;
    }

    /** Every VanillaBlockPiece's state by anchor: what a caller diffing a fake space's writes against compares with. */
    public static Map<BlockPos, BlockState> snapshotStates(SubgridBlockEntity be) {
        Map<BlockPos, BlockState> map = new HashMap<>();
        for (PlacedPiece p : be.getPieces()) {
            if (p.definition instanceof VanillaBlockPiece) map.put(p.anchor, stateOf(p));
        }
        return map;
    }

    /** Which face of piece the already-placed changedNeighbor touches, or null if it can't be found. */
    static Direction directionTo(SubgridBlockEntity be, PlacedPiece piece, PlacedPiece changedNeighbor) {
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
