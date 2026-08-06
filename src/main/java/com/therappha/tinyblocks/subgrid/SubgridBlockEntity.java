package com.therappha.tinyblocks.subgrid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SubgridBlockEntity extends BlockEntity {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final short EMPTY = -1;
    private static final int RANDOM_TICK_SPEED = 3;

    public final int gridSize;
    private final short[] cellOwner;
    private final List<PlacedPiece> pieces = new ArrayList<>();
    private VoxelShape cachedShape = Shapes.empty();

    /** v2 Tier 3: a piece's own scheduleTick request (e.g. a redstone lamp's delayed turn-off). */
    public record ScheduledPieceTick(int x, int y, int z, long targetGameTime) {}
    private final List<ScheduledPieceTick> scheduledTicks = new ArrayList<>();

    /**
     * A piece that went to air without an immediately-visible replacement elsewhere this same
     * call (see VanillaBlockPiece#applyChanges) — queued instead of dropped right away, since a
     * piston's own move sequence can vacate a cell in one call (onNeighborChanged) and only write
     * the block's arrival at its new cell several ticks later, in a separate call (that cell's own
     * captured BE finalizing via tick()). Held until either something cancels it (the delayed
     * arrival showed up, see cancelPendingDrop) or activeCaptures genuinely settles back to empty
     * — NOT a fixed tick count: a fixed guess is either too short (misses a slower animation) or
     * needlessly delays every genuine destruction by the same margin regardless. safetyDeadlineTick
     * is pure insurance against a leaked capture (a piece whose captureEnded() never fires, e.g. an
     * exception mid-finalize) permanently starving this drop — not the primary gate.
     */
    record PendingDrop(BlockState state, net.minecraft.world.phys.Vec3 dropPos,
                        List<net.minecraft.world.item.ItemStack> drops, long safetyDeadlineTick) {}
    private final List<PendingDrop> pendingDrops = new ArrayList<>();
    private static final long PENDING_DROP_SAFETY_TICKS = 200;

    /**
     * Pieces currently holding a vanilla-captured BE (see VanillaBlockPiece#syncBlockEntity) —
     * i.e. an operation vanilla itself constructed with real parameters and hasn't finished with
     * yet (today, only a piston's PistonMovingBlockEntity does this; nothing here is piston-
     * specific, any future block using the same setBlockEntity/removeBlockEntity capture pattern
     * is covered the same way). Identity-based: tracks the PlacedPiece instances themselves, not
     * their state, so it can't be confused by two pieces sharing a BlockState.
     */
    private final Set<PlacedPiece> activeCaptures = Collections.newSetFromMap(new IdentityHashMap<>());

    public void captureStarted(PlacedPiece piece) {
        activeCaptures.add(piece);
    }

    public void captureEnded(PlacedPiece piece) {
        activeCaptures.remove(piece);
    }

    public boolean hasActiveCaptures() {
        return !activeCaptures.isEmpty();
    }

    public int pendingDropCount() {
        return pendingDrops.size();
    }

    public void deferDrop(BlockState state, net.minecraft.world.phys.Vec3 dropPos,
                           List<net.minecraft.world.item.ItemStack> drops, long currentGameTime) {
        if (drops.isEmpty()) return;
        pendingDrops.add(new PendingDrop(state, dropPos, drops, currentGameTime + PENDING_DROP_SAFETY_TICKS));
    }

    /**
     * Cancels the oldest pending drop matching state — called whenever ANY piece (new or
     * existing) ends up holding this same state, on the assumption it's the delayed arrival of
     * whatever just vacated elsewhere rather than a coincidence. Same imprecision the immediate
     * same-call movedElsewhere check in applyChanges already accepted (state equality only, no
     * piece identity) — just widened across time instead of a single call.
     */
    public void cancelPendingDrop(BlockState state) {
        for (int i = 0; i < pendingDrops.size(); i++) {
            if (pendingDrops.get(i).state().equals(state)) {
                pendingDrops.remove(i);
                return;
            }
        }
    }

    /**
     * Decides which pending drops are ready to actually fire at currentGameTime and removes them
     * from the queue — split out from tickPendingDrops so the decision (does activeCaptures/the
     * safety deadline say "fire now") is unit-testable on its own, without a real ServerLevel to
     * spawn the resulting ItemEntity into.
     */
    List<PendingDrop> drainReadyDrops(long currentGameTime) {
        if (pendingDrops.isEmpty()) return List.of();
        boolean stillAnimating = hasActiveCaptures();
        List<PendingDrop> ready = new ArrayList<>();
        pendingDrops.removeIf(pd -> {
            if (stillAnimating && pd.safetyDeadlineTick() > currentGameTime) return false;
            ready.add(pd);
            return true;
        });
        return ready;
    }

    private void tickPendingDrops(ServerLevel level) {
        for (PendingDrop pd : drainReadyDrops(level.getGameTime())) {
            for (net.minecraft.world.item.ItemStack drop : pd.drops()) {
                level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                        level, pd.dropPos().x, pd.dropPos().y, pd.dropPos().z, drop));
            }
        }
    }

    /**
     * Queues piece.definition.scheduledTick(...) to fire once level.getGameTime() reaches
     * targetGameTime. Silently drops requests outside this grid's bounds: vanilla code doesn't
     * only ever call Level.scheduleTick(pos, ...) with pos == its own local position — e.g. a
     * chest's ContainerOpenersCounter schedules a recheck at the chest's own BlockPos, which for
     * a phantom per-piece BlockEntity (see VanillaBlockPiece.blockEntityFor) is deliberately the
     * SubgridBlockEntity's REAL world position, not a local fake-grid coordinate, so it can't
     * possibly index cellOwner. Without this guard that real-world position (large integers) flows
     * straight through FakeLevel.scheduleTick's capture into drainScheduledTicks' getPieceAt call
     * and throws ArrayIndexOutOfBoundsException the next server tick.
     */
    public void scheduleTick(int x, int y, int z, long targetGameTime) {
        if (outOfBounds(x, y, z)) return;
        scheduledTicks.add(new ScheduledPieceTick(x, y, z, targetGameTime));
    }

    public SubgridBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.gridSize = state.getBlock() instanceof SubgridBlock sb ? sb.gridSize : 8;
        this.cellOwner = new short[gridSize * gridSize * gridSize];
        Arrays.fill(cellOwner, EMPTY);
    }

    public VoxelShape getCachedShape() { return cachedShape; }
    public int getGridSize() { return gridSize; }
    public List<PlacedPiece> getPieces() { return pieces; }

    public short ownerAt(int x, int y, int z) {
        return cellOwner[indexOf(x, y, z)];
    }

    // --- Public API ---

    public boolean placePiece(PlacedPiece piece) {
        if (!canFit(piece)) return false;
        addToGrid(piece);
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingChunk(serverLevel,
                    new net.minecraft.world.level.ChunkPos(worldPosition),
                    new com.therappha.tinyblocks.network.SubgridPieceAddedPayload(worldPosition, piece.save()));
        }
        notifyNeighbors(piece);
        // The piece also needs to react to the neighbors it was just placed next to —
        // notifyNeighbors() only informs the pieces around it, not itself.
        if (level instanceof ServerLevel serverLevel) {
            try {
                piece.definition.onNeighborChanged(piece, serverLevel, worldPosition, this, piece);
            } catch (Exception e) {
                logTickFailure("onNeighborChanged (self)", piece, e);
            }
        }
        return true;
    }

    /**
     * Client-side only: applies a piece addition received via SubgridPieceAddedPayload. Mirrors
     * exactly the local-state half of placePiece (grid + shape) without re-sending a packet.
     */
    public void applyRemoteAdd(PlacedPiece piece) {
        addToGrid(piece);
    }

    /**
     * Like placePiece, but if local falls outside this grid's bounds on exactly one axis,
     * finds (or creates) a same-gridSize SubgridBlockEntity in that real-world direction and
     * places it there instead — wrapping the overflowed coordinate into the adjacent grid's
     * local space. The same mechanism SubgridEventHandler's own click-to-place flow already
     * uses when a player targets a cell past a grid's edge, generalized here so anything
     * spreading/falling/growing across a subgrid's boundary (fluids, falling blocks, tree
     * growth) continues into a new subgrid instead of the write being silently dropped. Diagonal
     * overflow (2+ axes at once) is out of scope, same as realBlockStateAt above — returns null.
     * Returns the placed piece (rather than a boolean) so a caller that also captured a real
     * BlockEntity for this same write (e.g. a piston's moving-block animation state) has something
     * to attach it to — see VanillaBlockPiece.applyChanges' syncBlockEntity.
     */
    @Nullable
    public PlacedPiece placePieceCrossBoundary(PieceDefinition definition, BlockPos local, Direction facing,
                                                Object runtimeState, ServerLevel realLevel) {
        if (!outOfBounds(local.getX(), local.getY(), local.getZ())) {
            return placeOrUpdatePiece(definition, local, facing, runtimeState);
        }

        Direction dir = overflowDirection(local.getX(), local.getY(), local.getZ());
        if (dir == null) return null;

        BlockPos targetPos = worldPosition.relative(dir);
        BlockState targetState = realLevel.getBlockState(targetPos);
        if (targetState.isAir()) {
            realLevel.setBlock(targetPos, subgridBlockFor(gridSize).defaultBlockState(), Block.UPDATE_ALL);
        }
        if (!(realLevel.getBlockEntity(targetPos) instanceof SubgridBlockEntity adjacent) || adjacent.gridSize != gridSize) {
            return null;
        }

        int max = gridSize - 1;
        BlockPos wrapped = new BlockPos(wrap(local.getX(), max), wrap(local.getY(), max), wrap(local.getZ(), max));
        return adjacent.placeOrUpdatePiece(definition, wrapped, facing, runtimeState);
    }

    /**
     * Places a new piece at local, or — if that cell is already held by a same-definition piece
     * (e.g. flowing water re-asserting its level against water that already spread there on a
     * previous tick) — updates that piece's runtimeState in place instead of canFit refusing the
     * write outright. A cell held by a DIFFERENT definition genuinely blocks the write, same as
     * placePiece. Only used by placePieceCrossBoundary's vanilla-tick-driven spread/flow callers —
     * player-initiated placement must keep going through plain placePiece, which never silently
     * overwrites an existing piece. Returns the placed/updated piece (rather than a boolean) so a
     * caller that also captured a real BlockEntity for this same write (e.g. a piston's moving-
     * block animation state) has something to attach it to.
     */
    @Nullable
    private PlacedPiece placeOrUpdatePiece(PieceDefinition definition, BlockPos local, Direction facing, Object runtimeState) {
        PlacedPiece existing = getPieceAt(local.getX(), local.getY(), local.getZ());
        if (existing != null) {
            if (existing.definition != definition) return null;
            if (runtimeState.equals(existing.runtimeState)) return existing;
            existing.runtimeState = runtimeState;
            notifyNeighbors(existing);
            notifyUpdate();
            return existing;
        }
        PlacedPiece piece = new PlacedPiece(definition, local, facing);
        piece.runtimeState = runtimeState;
        return placePiece(piece) ? piece : null;
    }

    private static SubgridBlock subgridBlockFor(int gridSize) {
        return switch (gridSize) {
            case 2 -> com.therappha.tinyblocks.setup.Registration.SUBGRID_BLOCK_2.get();
            case 4 -> com.therappha.tinyblocks.setup.Registration.SUBGRID_BLOCK_4.get();
            case 16 -> com.therappha.tinyblocks.setup.Registration.SUBGRID_BLOCK_16.get();
            default -> com.therappha.tinyblocks.setup.Registration.SUBGRID_BLOCK.get();
        };
    }

    @Nullable
    public PlacedPiece getPieceAt(int x, int y, int z) {
        if (outOfBounds(x, y, z)) return null;
        short idx = cellOwner[indexOf(x, y, z)];
        if (idx == EMPTY) return null;
        return pieces.get(idx);
    }

    @Nullable
    public PlacedPiece removePieceAt(int x, int y, int z) {
        if (outOfBounds(x, y, z)) return null;
        short idx = cellOwner[indexOf(x, y, z)];
        if (idx == EMPTY) return null;
        PlacedPiece removed = pieces.get(idx);
        rebuildAfterRemove(idx);
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingChunk(serverLevel,
                    new net.minecraft.world.level.ChunkPos(worldPosition),
                    new com.therappha.tinyblocks.network.SubgridPieceRemovedPayload(worldPosition, new BlockPos(x, y, z)));
        }
        notifyNeighbors(removed);
        return removed;
    }

    public void serverTick(ServerLevel level) {
        boolean dirty = false;
        // A snapshot, not the live list: piece.definition.tick() can itself add/remove pieces (e.g.
        // a piston's moving-block BE finalizing calls neighborChanged, which can cascade into
        // another piece being placed or removed via applyChanges) — mutating `pieces` while this
        // loop's own iterator is still walking it throws ConcurrentModificationException. Found
        // live once the piston finalize path actually started completing (see FakeServerLevel's
        // invalidateCapabilities fix) instead of crashing before ever reaching this cascade.
        for (PlacedPiece piece : new ArrayList<>(pieces)) {
            if (!piece.definition.requiresTick()) continue;
            try {
                if (piece.definition.tick(piece, level, worldPosition, this)) {
                    dirty = true;
                    notifyNeighbors(piece);
                }
            } catch (Exception e) {
                logTickFailure("tick", piece, e);
            }
        }
        if (dirty) notifyUpdate();

        drainScheduledTicks(level);
        sampleRandomTicks(level);
        tickPendingDrops(level);
    }

    /**
     * A piece's real vanilla logic running against a fake position space (see VanillaBlockPiece)
     * is fundamentally exploratory — some code paths (tree/structure feature placement, per-
     * BlockEntity tickers) touch a large surface of ServerLevel-only state our reflection-
     * allocated FakeServerLevel doesn't fully replicate, and the only way to find every gap is to
     * actually hit it. Letting one piece's exception crash the whole server tick (and take every
     * OTHER subgrid down with it) is a far worse failure mode than that one piece silently
     * failing to act this tick — so every dispatch point here is guarded. Logged, not swallowed
     * silently, so a real bug is still visible in the log rather than just mysteriously not
     * working.
     */
    private static void logTickFailure(String phase, PlacedPiece piece, Exception e) {
        LOGGER.error("v2 piece {} at {} threw during {}", piece.definition.id(), piece.anchor, phase, e);
    }

    /** Fires piece.definition.scheduledTick(...) for every request whose target tick has arrived. */
    private void drainScheduledTicks(ServerLevel level) {
        if (scheduledTicks.isEmpty()) return;
        long now = level.getGameTime();
        List<ScheduledPieceTick> due = new ArrayList<>();
        scheduledTicks.removeIf(st -> {
            if (st.targetGameTime() > now) return false;
            due.add(st);
            return true;
        });
        for (ScheduledPieceTick st : due) {
            PlacedPiece piece = getPieceAt(st.x(), st.y(), st.z());
            if (piece == null) continue;
            try {
                piece.definition.scheduledTick(piece, level, worldPosition, this);
            } catch (Exception e) {
                logTickFailure("scheduledTick", piece, e);
            }
        }
    }

    /**
     * Mirrors vanilla's random tick speed (default 3): each of the RANDOM_TICK_SPEED "rolls" per
     * tick independently picks ONE random position across the whole chunk section (4096 blocks)
     * and only fires if something's actually there — meaning any single block has roughly a
     * 3-in-4096 chance of a random tick on a given game tick, not a near-certainty. The previous
     * version guaranteed up to 3 firings every tick regardless of how many pieces existed, so a
     * subgrid with only 1-2 pieces got its farmland (say) randomTick'd nearly every tick instead
     * of roughly once every ~20 minutes — e.g. tilling dirt and having it dry back out instantly.
     * Reusing gridSize^3 as the "chunk section volume" analog keeps the same per-cell odds.
     */
    private void sampleRandomTicks(ServerLevel level) {
        if (pieces.isEmpty()) return;
        RandomSource random = level.getRandom();
        int volume = gridSize * gridSize * gridSize;
        for (int i = 0; i < RANDOM_TICK_SPEED; i++) {
            int cell = random.nextInt(volume);
            if (cell >= pieces.size()) continue;
            PlacedPiece piece = pieces.get(cell);
            try {
                piece.definition.randomTick(piece, level, worldPosition, this);
            } catch (Exception e) {
                logTickFailure("randomTick", piece, e);
            }
        }
    }

    public void clientTick(Level level) {
        for (PlacedPiece piece : pieces) {
            if (piece.definition.requiresClientTick()) {
                try {
                    piece.definition.clientTick(piece, level, worldPosition, this);
                } catch (Exception e) {
                    logTickFailure("clientTick", piece, e);
                }
            }
        }
    }

    /** A piece and the SubgridBlockEntity that hosts it — a neighbor may live in another block. */
    public record Neighbor(SubgridBlockEntity be, PlacedPiece piece) {}

    /**
     * Calls onNeighborChanged on every piece adjacent to changed's footprint, including pieces
     * hosted by a neighboring SubgridBlock across the grid boundary (same grid size only).
     */
    public void notifyNeighbors(PlacedPiece changed) {
        if (!(level instanceof ServerLevel)) return;
        for (Neighbor neighbor : neighborsOf(changed)) {
            if (neighbor.be().level instanceof ServerLevel neighborLevel) {
                try {
                    neighbor.piece().definition.onNeighborChanged(
                            neighbor.piece(), neighborLevel, neighbor.be().getBlockPos(), neighbor.be(), changed);
                } catch (Exception e) {
                    logTickFailure("onNeighborChanged", neighbor.piece(), e);
                }
            }
        }
    }

    /**
     * Pieces currently touching piece's footprint face-to-face. Cells on the edge of this grid
     * also check the neighboring real-world block: if it hosts a SubgridBlockEntity with the
     * same gridSize, the mirrored cell on its near face counts as touching too. Grids of
     * different sizes never propagate into each other — their cells don't line up.
     */
    public Set<Neighbor> neighborsOf(PlacedPiece piece) {
        Set<Neighbor> result = new LinkedHashSet<>();
        for (int x = piece.anchor.getX(); x < piece.anchor.getX() + piece.footprint.getX(); x++)
            for (int y = piece.anchor.getY(); y < piece.anchor.getY() + piece.footprint.getY(); y++)
                for (int z = piece.anchor.getZ(); z < piece.anchor.getZ() + piece.footprint.getZ(); z++)
                    for (Direction dir : Direction.values()) {
                        if (piece.occupies(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ())) continue;
                        Neighbor neighbor = neighborAt(x, y, z, dir);
                        if (neighbor != null) result.add(neighbor);
                    }
        return result;
    }

    /**
     * The single piece touching cell (piece.anchor + dir), if any. Only meaningful for
     * single-cell (1x1x1 footprint) pieces — larger footprints should use neighborsOf instead.
     */
    @Nullable
    public Neighbor neighborFacing(PlacedPiece piece, Direction dir) {
        return neighborAt(piece.anchor.getX(), piece.anchor.getY(), piece.anchor.getZ(), dir);
    }

    @Nullable
    private Neighbor neighborAt(int x, int y, int z, Direction dir) {
        int nx = x + dir.getStepX(), ny = y + dir.getStepY(), nz = z + dir.getStepZ();
        if (outOfBounds(nx, ny, nz)) return crossGridNeighborAt(dir, nx, ny, nz);
        short idx = cellOwner[indexOf(nx, ny, nz)];
        return idx != EMPTY ? new Neighbor(this, pieces.get(idx)) : null;
    }

    @Nullable
    private Neighbor crossGridNeighborAt(Direction dir, int nx, int ny, int nz) {
        if (!(level instanceof ServerLevel)) return null;
        if (!(level.getBlockEntity(worldPosition.relative(dir)) instanceof SubgridBlockEntity other)) return null;
        if (other.gridSize != gridSize) return null;
        int max = gridSize - 1;
        PlacedPiece piece = other.getPieceAt(wrap(nx, max), wrap(ny, max), wrap(nz, max));
        return piece != null ? new Neighbor(other, piece) : null;
    }

    private static int wrap(int v, int max) {
        if (v < 0) return max;
        if (v > max) return 0;
        return v;
    }

    /**
     * Real vanilla view of cell (x,y,z), for v2 pieces running real BlockState logic against
     * this grid. In-bounds returns the occupying piece's render state (or air if empty).
     * Out-of-bounds on exactly one axis resolves to whatever is actually there in the real
     * world: a same-gridSize neighboring SubgridBlockEntity's mirrored near-face cell (same
     * trick notifyNeighbors/crossGridNeighborAt use for redstone propagation), or — if there's
     * no such neighbor — the literal real-world block beyond this one. A piece on the bottom
     * face of a grid sitting on real dirt should see real dirt, not fabricated air, when it
     * checks canSurvive(). Diagonal/corner overflow (two or more axes out of bounds at once,
     * rare for the face-adjacent queries vanilla actually makes) falls back to air.
     */
    public BlockState realBlockStateAt(int x, int y, int z) {
        if (!outOfBounds(x, y, z)) {
            PlacedPiece piece = getPieceAt(x, y, z);
            return piece != null ? piece.definition.renderState(piece) : Blocks.AIR.defaultBlockState();
        }
        if (!(level instanceof ServerLevel)) return Blocks.AIR.defaultBlockState();

        Direction dir = overflowDirection(x, y, z);
        if (dir == null) return Blocks.AIR.defaultBlockState();

        int max = gridSize - 1;
        if (level.getBlockEntity(worldPosition.relative(dir)) instanceof SubgridBlockEntity other
                && other.gridSize == gridSize) {
            PlacedPiece piece = other.getPieceAt(wrap(x, max), wrap(y, max), wrap(z, max));
            return piece != null ? piece.definition.renderState(piece) : Blocks.AIR.defaultBlockState();
        }
        return level.getBlockState(worldPosition.relative(dir));
    }

    /** The single axis (x/y/z) that (x,y,z) overflows, or null if zero or multiple axes do. */
    @Nullable
    private Direction overflowDirection(int x, int y, int z) {
        int max = gridSize - 1;
        boolean overX = x < 0 || x > max;
        boolean overY = y < 0 || y > max;
        boolean overZ = z < 0 || z > max;
        if ((overX ? 1 : 0) + (overY ? 1 : 0) + (overZ ? 1 : 0) != 1) return null;
        if (overX) return x < 0 ? Direction.WEST : Direction.EAST;
        if (overY) return y < 0 ? Direction.DOWN : Direction.UP;
        return z < 0 ? Direction.NORTH : Direction.SOUTH;
    }

    public void notifyUpdate() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // --- Grid helpers ---

    private boolean canFit(PlacedPiece p) {
        for (int x = p.anchor.getX(); x < p.anchor.getX() + p.footprint.getX(); x++)
            for (int y = p.anchor.getY(); y < p.anchor.getY() + p.footprint.getY(); y++)
                for (int z = p.anchor.getZ(); z < p.anchor.getZ() + p.footprint.getZ(); z++) {
                    if (outOfBounds(x, y, z)) return false;
                    if (cellOwner[indexOf(x, y, z)] != EMPTY) return false;
                }
        return true;
    }

    private void addToGrid(PlacedPiece p) {
        short idx = (short) pieces.size();
        pieces.add(p);
        for (int x = p.anchor.getX(); x < p.anchor.getX() + p.footprint.getX(); x++)
            for (int y = p.anchor.getY(); y < p.anchor.getY() + p.footprint.getY(); y++)
                for (int z = p.anchor.getZ(); z < p.anchor.getZ() + p.footprint.getZ(); z++)
                    cellOwner[indexOf(x, y, z)] = idx;
        rebuildShape();
    }

    private void rebuildAfterRemove(short removedIdx) {
        pieces.remove(removedIdx);
        Arrays.fill(cellOwner, EMPTY);
        for (int i = 0; i < pieces.size(); i++) {
            PlacedPiece p = pieces.get(i);
            for (int x = p.anchor.getX(); x < p.anchor.getX() + p.footprint.getX(); x++)
                for (int y = p.anchor.getY(); y < p.anchor.getY() + p.footprint.getY(); y++)
                    for (int z = p.anchor.getZ(); z < p.anchor.getZ() + p.footprint.getZ(); z++)
                        cellOwner[indexOf(x, y, z)] = (short) i;
        }
        rebuildShape();
    }

    /**
     * Builds the merged shape directly from cellOwner's occupancy in a single pass, instead of
     * unioning one VoxelShape per piece via Shapes.or — that scaled with piece count (and grew
     * more expensive per union as the accumulated shape got more complex), so dense subgrids
     * paid an increasingly large cost on every single place/break. This is bounded by
     * gridSize^3 regardless of piece count (see issue #27).
     */
    private void rebuildShape() {
        var discrete = new net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape(gridSize, gridSize, gridSize);
        boolean any = false;
        for (int y = 0; y < gridSize; y++)
            for (int z = 0; z < gridSize; z++)
                for (int x = 0; x < gridSize; x++)
                    if (cellOwner[indexOf(x, y, z)] != EMPTY) {
                        discrete.fill(x, y, z);
                        any = true;
                    }
        cachedShape = any ? ShapeAccess.wrap(discrete) : Shapes.empty();
    }

    public int indexOf(int x, int y, int z) {
        return y * gridSize * gridSize + z * gridSize + x;
    }

    /**
     * Real-world position of the CENTER of a local grid cell (fractional, e.g. block (5,10,3)
     * gridSize 8 anchor (2,0,0) -> (5.3125, 10.0625, 3.0625)). The inverse of GridRay.cellAt.
     * Use this anywhere a piece needs to act as if it were a real block at its own precise spot
     * in the world — particle/sound origin, dropped-item spawn point, distance checks against
     * the player — instead of collapsing every piece in the subgrid down to one shared point
     * (the whole SubgridBlock's own BlockPos, which is what be.getBlockPos() alone gives you).
     */
    public net.minecraft.world.phys.Vec3 realPositionOf(BlockPos local) {
        double cell = 1.0 / gridSize;
        return new net.minecraft.world.phys.Vec3(
                worldPosition.getX() + (local.getX() + 0.5) * cell,
                worldPosition.getY() + (local.getY() + 0.5) * cell,
                worldPosition.getZ() + (local.getZ() + 0.5) * cell
        );
    }

    private boolean outOfBounds(int x, int y, int z) {
        return x < 0 || x >= gridSize || y < 0 || y >= gridSize || z < 0 || z >= gridSize;
    }

    public boolean inBounds(int x, int y, int z) {
        return !outOfBounds(x, y, z);
    }

    // --- NBT ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (PlacedPiece p : pieces)
            p.definition.onSaving(p, registries);
        ListTag list = new ListTag();
        for (PlacedPiece p : pieces)
            list.add(p.save());
        tag.put("pieces", list);

        if (!scheduledTicks.isEmpty()) {
            ListTag tickList = new ListTag();
            for (ScheduledPieceTick st : scheduledTicks) {
                CompoundTag t = new CompoundTag();
                t.putInt("x", st.x());
                t.putInt("y", st.y());
                t.putInt("z", st.z());
                t.putLong("time", st.targetGameTime());
                tickList.add(t);
            }
            tag.put("scheduledTicks", tickList);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        pieces.clear();
        Arrays.fill(cellOwner, EMPTY);
        ListTag list = tag.getList("pieces", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            PlacedPiece piece = PlacedPiece.load(list.getCompound(i));
            addToGrid(piece);
            piece.definition.onLoaded(piece, registries);
        }

        scheduledTicks.clear();
        if (tag.contains("scheduledTicks")) {
            ListTag tickList = tag.getList("scheduledTicks", Tag.TAG_COMPOUND);
            for (int i = 0; i < tickList.size(); i++) {
                CompoundTag t = tickList.getCompound(i);
                scheduledTicks.add(new ScheduledPieceTick(t.getInt("x"), t.getInt("y"), t.getInt("z"), t.getLong("time")));
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
