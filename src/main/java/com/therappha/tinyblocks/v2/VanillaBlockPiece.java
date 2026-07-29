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
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v2: a single generic PieceDefinition that can hold ANY real vanilla BlockState, instead of
 * one hand-written PieceDefinition per block type (see PieceDefinitions.java for the old way).
 * Stores the actual BlockState in piece.runtimeState and runs interact/neighborChanged/hardness
 * through a fake, in-memory position space (FakeCellGetter/FakeServerLevel) built from this
 * piece's SubgridBlockEntity siblings — real vanilla behavior, zero reimplementation.
 */
public final class VanillaBlockPiece extends PieceDefinition {

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
    public List<ItemStack> drops(PlacedPiece piece) {
        // Real loot-table drops need ServerLevel/LootParams (same wall as scheduled ticks) —
        // deferred. This 1:1 block-to-item fallback covers the vast majority of simple blocks.
        BlockState state = stateOf(piece);
        if (state == null) return List.of();
        Item item = state.getBlock().asItem();
        return item != Items.AIR ? List.of(new ItemStack(item)) : List.of();
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

        FakeServerLevel fakeLevel = buildFakeSpace(be, serverLevel);
        Map<BlockPos, BlockState> before = snapshot(be);

        BlockHitResult fakeHit = new BlockHitResult(Vec3.atCenterOf(piece.anchor), hit.getDirection(), piece.anchor, hit.isInside());
        InteractionResult result = fakeLevel.cells().getBlockState(piece.anchor).useWithoutItem(fakeLevel, player, fakeHit);

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

        FakeServerLevel fakeLevel = buildFakeSpace(be, level);
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

        FakeServerLevel fakeLevel = buildFakeSpace(be, level);
        Map<BlockPos, BlockState> before = snapshot(be);

        fakeLevel.cells().getBlockState(piece.anchor).tick(fakeLevel, piece.anchor, level.getRandom());

        applyChanges(be, fakeLevel, before, level);
    }

    @Override
    public void randomTick(PlacedPiece piece, ServerLevel level, BlockPos subgridPos, SubgridBlockEntity be) {
        BlockState state = stateOf(piece);
        if (state == null || !state.isRandomlyTicking()) return;

        FakeServerLevel fakeLevel = buildFakeSpace(be, level);
        Map<BlockPos, BlockState> before = snapshot(be);

        fakeLevel.cells().getBlockState(piece.anchor).randomTick(fakeLevel, piece.anchor, level.getRandom());

        applyChanges(be, fakeLevel, before, level);
    }

    // --- Fake space plumbing, shared with SubgridEventHandler's placement flow ---

    /** Builds a FakeServerLevel whose cell space mirrors every VanillaBlockPiece sibling in be. */
    public static FakeServerLevel buildFakeSpace(SubgridBlockEntity be, ServerLevel serverLevel) {
        SubgridFakeCellGetter cells = new SubgridFakeCellGetter(be);
        for (PlacedPiece p : be.getPieces()) {
            if (p.definition == INSTANCE) {
                BlockState s = stateOf(p);
                if (s != null) cells.set(p.anchor, s);
            }
        }
        return FakeServerLevel.create(serverLevel, cells, be.getBlockPos());
    }

    /** Cells not explicitly touched this call fall back to the SubgridBlockEntity's real vanilla view. */
    private static final class SubgridFakeCellGetter extends FakeCellGetter {
        private final SubgridBlockEntity be;
        SubgridFakeCellGetter(SubgridBlockEntity be) { this.be = be; }

        @Override
        protected BlockState fallback(BlockPos pos) {
            return be.realBlockStateAt(pos.getX(), pos.getY(), pos.getZ());
        }
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
     * to turn itself off in 4 ticks) into be's own persistent queue — FakeServerLevel captures
     * these instead of touching a real tick list, since scheduledTick(...) below is what
     * actually drains and fires them later.
     */
    private static void applyChanges(SubgridBlockEntity be, FakeServerLevel fakeLevel,
                                      Map<BlockPos, BlockState> before, ServerLevel realLevel) {
        FakeCellGetter cells = fakeLevel.cells();
        boolean anyChanged = false;
        for (PlacedPiece p : be.getPieces()) {
            if (p.definition != INSTANCE) continue;
            BlockState now = cells.getBlockState(p.anchor);
            if (!now.equals(before.get(p.anchor))) {
                p.runtimeState = now;
                be.notifyNeighbors(p);
                anyChanged = true;
            }
        }
        for (FakeServerLevel.ScheduledEntry entry : fakeLevel.scheduledTicks()) {
            be.scheduleTick(entry.pos().getX(), entry.pos().getY(), entry.pos().getZ(),
                    realLevel.getGameTime() + entry.delay());
            anyChanged = true;
        }
        if (anyChanged) be.notifyUpdate();
    }

    /** Which face of piece the already-placed changedNeighbor touches, or null if it can't be found. */
    private static Direction directionTo(SubgridBlockEntity be, PlacedPiece piece, PlacedPiece changedNeighbor) {
        for (Direction dir : Direction.values()) {
            SubgridBlockEntity.Neighbor n = be.neighborFacing(piece, dir);
            if (n != null && n.piece() == changedNeighbor) return dir;
        }
        return null;
    }
}
