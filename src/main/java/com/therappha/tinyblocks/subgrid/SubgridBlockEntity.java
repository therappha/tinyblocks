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
     * Returns the placed piece AND which SubgridBlockEntity actually owns it (rather than a bare
     * boolean or just the piece) — a caller that also captured a real BlockEntity for this same
     * write (e.g. a piston's moving-block animation state) needs both: something to attach the
     * capture to, AND the correct owner to run capture bookkeeping against when the write lands in
     * a DIFFERENT grid than the one that made this call (see VanillaBlockPiece.applyChanges'
     * syncBlockEntity — issue #46's cross-grid sticky-piston-duplication fix depends on this being
     * the real owner, not always the calling grid).
     */
    public record PlacementResult(SubgridBlockEntity owner, PlacedPiece piece) {}

    @Nullable
    public PlacementResult placePieceCrossBoundary(PieceDefinition definition, BlockPos local, Direction facing,
                                                     Object runtimeState, ServerLevel realLevel) {
        CellRef ref = resolveOrCreate(local.getX(), local.getY(), local.getZ(), realLevel);
        return switch (ref) {
            case CellRef.Local l -> {
                PlacedPiece p = placeOrUpdatePiece(definition, new BlockPos(l.x(), l.y(), l.z()), facing, runtimeState);
                yield p != null ? new PlacementResult(this, p) : null;
            }
            case CellRef.InAdjacentGrid a -> {
                PlacedPiece p = a.grid().placeOrUpdatePiece(
                        definition, new BlockPos(a.x(), a.y(), a.z()), facing, runtimeState);
                yield p != null ? new PlacementResult(a.grid(), p) : null;
            }
            case CellRef.Created c -> {
                PlacedPiece p = c.grid().placeOrUpdatePiece(
                        definition, new BlockPos(c.x(), c.y(), c.z()), facing, runtimeState);
                yield p != null ? new PlacementResult(c.grid(), p) : null;
            }
            case CellRef.RealWorld ignored -> null;
            case CellRef.OutOfScope ignored -> null;
        };
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
            Object was = existing.runtimeState;
            existing.runtimeState = runtimeState;
            // Its block entity follows, as for a change made in this grid's own fake space.
            if (was instanceof BlockState wasState && runtimeState instanceof BlockState nowState) {
                com.therappha.tinyblocks.v2.VanillaBlockPiece.followStateChange(this, existing, wasState, nowState);
            }
            notifyNeighbors(existing);
            notifyUpdate();
            return existing;
        }
        PlacedPiece piece = new PlacedPiece(definition, local, facing);
        piece.runtimeState = runtimeState;
        return placePiece(piece) ? piece : null;
    }

    private static SubgridBlock subgridBlockFor(int gridSize) {
        return com.therappha.tinyblocks.setup.Registration.subgridBlock(gridSize);
    }

    /**
     * If targetPos is real-world air, creates a SubgridBlockEntity of the given gridSize there.
     * Returns whatever SubgridBlockEntity now occupies targetPos — freshly created, or whatever
     * was already there (of ANY gridSize; this doesn't gate on a match, unlike resolveOrCreate,
     * since a caller placing directly at a clicked block has no "source grid" to match sizes
     * against) — or null if targetPos is occupied by something that isn't a SubgridBlockEntity.
     */
    public static SubgridBlockEntity createAt(ServerLevel realLevel, BlockPos targetPos, int gridSize) {
        if (realLevel.getBlockState(targetPos).isAir()) {
            realLevel.setBlock(targetPos, subgridBlockFor(gridSize).defaultBlockState(), Block.UPDATE_ALL);
        }
        return realLevel.getBlockEntity(targetPos) instanceof SubgridBlockEntity sub ? sub : null;
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
            removed.definition.onRemoved(removed, serverLevel, this);
        }
        notifyNeighbors(removed);
        return removed;
    }

    /**
     * Set when a fake level's sendBlockUpdated fires (a phantom BlockEntity asked to sync, e.g. Create's
     * SmartBlockEntity#sendData); flushed once per tick in serverTick as PhantomDataPayloads.
     */
    private boolean phantomDataDirty = false;

    public void markPhantomDataDirty() {
        phantomDataDirty = true;
    }

    /** How many times phantom data was pushed to clients; lets GameTests observe the sync without a client. */
    private int phantomDataFlushes = 0;

    public int phantomDataFlushes() {
        return phantomDataFlushes;
    }

    /** How often (ticks) phantom data is compared against what clients last received. */
    private static final int PHANTOM_CHANGE_SCAN_INTERVAL = 20;

    /**
     * Pushes phantom block entity data to clients. Always when something marked it dirty (everything is
     * resent), and otherwise every few ticks for just the phantoms whose saved data changed since the last
     * push: block entities change on their own after the event that touched them (a kinetic block dropping
     * to speed 0 a while after its motor was removed) and nothing reliably calls back for that (Create's
     * sendData goes through ServerChunkCache#blockChanged, which the fake levels cannot intercept).
     * Containers are left out of the periodic scan: serializing a full inventory every pass is wasteful and
     * their menus already sync themselves.
     */
    private void flushPhantomData(ServerLevel level) {
        boolean all = phantomDataDirty;
        if (!all && level.getGameTime() % PHANTOM_CHANGE_SCAN_INTERVAL != 0) return;
        phantomDataDirty = false;
        boolean sentAny = false;
        for (PlacedPiece piece : pieces) {
            if (!(piece.runtimeBlockEntity instanceof BlockEntity phantom)) continue;
            // A piston's moving-block BE has its own dedicated animation payload (see PistonAnimationPayload).
            if (phantom instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity) continue;
            if (!all && phantom instanceof net.minecraft.world.Container) continue;
            var tag = phantom.saveCustomOnly(level.registryAccess());
            int hash = tag.hashCode();
            if (!all && hash == piece.lastSyncedPhantomHash) continue;
            piece.lastSyncedPhantomHash = hash;
            sentAny = true;
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingChunk(level,
                    new net.minecraft.world.level.ChunkPos(worldPosition),
                    new com.therappha.tinyblocks.network.PhantomDataPayload(worldPosition, new BlockPos(piece.anchor), tag));
        }
        if (sentAny) phantomDataFlushes++;
    }

    private boolean chunkUnloaded = false;

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        chunkUnloaded = true;
    }

    /**
     * The SubgridBlock itself was removed: every piece goes with it, so each gets the same onRemoved a single
     * broken piece gets (a Create shaft inside lets the network around it know at once). Not on a chunk
     * unload, which only stops the pieces running.
     */
    @Override
    public void setRemoved() {
        super.setRemoved();
        if (chunkUnloaded || !(level instanceof ServerLevel serverLevel)) return;
        for (PlacedPiece piece : List.copyOf(pieces)) {
            piece.definition.onRemoved(piece, serverLevel, this);
        }
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
        flushPhantomData(level);
        updateRedstoneOutputs(level);
        removeIfLeftEmpty(level);
    }

    /** How long a grid stays empty before it goes: past any operation still bringing a piece in (a piston's move). */
    static final int EMPTY_GRACE_TICKS = 20;
    private int emptyTicks = 0;

    /**
     * A grid whose last piece went — however it went: mined, pushed out by a piston, taken apart by Create, fallen —
     * or that was made and never got one, removes its SubgridBlock once nothing is pending in it any more.
     */
    private void removeIfLeftEmpty(ServerLevel level) {
        if (!pieces.isEmpty() || !pendingDrops.isEmpty() || !scheduledTicks.isEmpty() || hasActiveCaptures()) {
            emptyTicks = 0;
            return;
        }
        if (++emptyTicks >= EMPTY_GRACE_TICKS) level.removeBlock(worldPosition, false);
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
     * Where a local coordinate (possibly out of this grid's bounds) actually resolves to — the
     * single generic answer every boundary-crossing query in this class produces (issue #42).
     * Read-only resolution (resolve) never returns Created; only resolveOrCreate can. Deliberately
     * doesn't fetch BlockState/BlockEntity itself, so it stays reusable for state reads,
     * piece/neighbor reads, and writes without baking in a return type — callers do their own
     * type-specific read against whatever this resolves to.
     */
    public sealed interface CellRef {
        /** (x,y,z) is inside this grid, unchanged. */
        record Local(int x, int y, int z) implements CellRef {}
        /** (x,y,z) overflowed into a same-gridSize neighboring SubgridBlockEntity, wrapped coords. */
        record InAdjacentGrid(SubgridBlockEntity grid, int x, int y, int z) implements CellRef {}
        /** Like InAdjacentGrid, but the neighbor didn't exist yet — resolveOrCreate just made it. */
        record Created(SubgridBlockEntity grid, int x, int y, int z) implements CellRef {}
        /** Overflowed into real-world space beyond this grid — no same-size subgrid there. */
        record RealWorld(BlockPos pos) implements CellRef {}
        /** Overflowed 2+ axes at once (diagonal/corner), or no ServerLevel to resolve against. */
        record OutOfScope() implements CellRef {}
    }

    /**
     * Read-only resolution for a direction the caller already knows (e.g. a per-face neighbor
     * loop that computed dir before checking bounds) — (x,y,z) is assumed out-of-bounds already.
     */
    public CellRef resolve(Direction dir, int x, int y, int z) {
        if (!(level instanceof ServerLevel)) return logResolve(dir, x, y, z, new CellRef.OutOfScope());
        BlockPos targetPos = worldPosition.relative(dir);
        CellRef ref;
        if (level.getBlockEntity(targetPos) instanceof SubgridBlockEntity other && other.gridSize == gridSize) {
            int max = gridSize - 1;
            ref = new CellRef.InAdjacentGrid(other, wrap(x, max), wrap(y, max), wrap(z, max));
        } else {
            ref = new CellRef.RealWorld(targetPos);
        }
        return logResolve(dir, x, y, z, ref);
    }

    /** Bounds a pathological chain — comfortably covers a corner overflowing all 3 axes at once. */
    private static final int MAX_CHAIN_HOPS = 4;

    /**
     * Read-only resolution deriving the direction from single-axis overflow, CHAINING through
     * multiple same-gridSize neighbor grids when more than one axis overflows at once (issue
     * #43). A corner query — one step past a side edge AND one step past the floor — needs two
     * hops (into the side neighbor, then down from THAT grid's own frame) to find the real
     * answer; the old single-hop-only version reported every such compound query as bare air
     * regardless of what was actually there, which is what drew fluid spread toward every
     * air-adjacent edge (confirmed live via the [xgrid-fluid] trace: local=(8,-1,4) on an 8-cell
     * grid hit OutOfScope 194 times in one short GameTest run). In-bounds -> Local without
     * touching level at all. Each hop fully resolves whatever magnitude of overflow that axis has
     * (via wrap's floorMod, not a fixed ±1 collapse — a piston chain-pushing a second block past
     * the first needs exactly this, see wrap's own doc) before moving to the next axis; capped at
     * MAX_CHAIN_HOPS grids crossed, not at overflow magnitude. This is read-only — it never calls
     * createAt, so a diagonal query now genuinely attempts to resolve through existing grids
     * instead of short-circuiting to OutOfScope, but still can't conjure one into existence just
     * because something asked. resolveOrCreate (see its own doc) chains the same way but creates
     * along the way — the write-side counterpart, where creating a subgrid is already its point.
     */
    public CellRef resolve(int x, int y, int z) {
        if (!outOfBounds(x, y, z)) return new CellRef.Local(x, y, z);
        if (!(level instanceof ServerLevel serverLevel)) return logResolve(null, x, y, z, new CellRef.OutOfScope());

        SubgridBlockEntity grid = this;
        int lx = x, ly = y, lz = z;
        Direction firstHop = null;

        for (int hop = 0; hop < MAX_CHAIN_HOPS && grid.outOfBounds(lx, ly, lz); hop++) {
            Direction dir = grid.singleStepOverflowAxis(lx, ly, lz);
            if (dir == null) return logResolve(firstHop, x, y, z, new CellRef.OutOfScope());
            if (firstHop == null) firstHop = dir;

            BlockPos targetPos = grid.worldPosition.relative(dir);
            if (!(serverLevel.getBlockEntity(targetPos) instanceof SubgridBlockEntity next) || next.gridSize != gridSize) {
                BlockPos real = foldRemainingIntoRealWorld(targetPos, dir, lx, ly, lz, gridSize);
                return logResolve(firstHop, x, y, z, new CellRef.RealWorld(real));
            }

            // Peel exactly ONE grid-width per hop, not the fully-reduced final coordinate — a
            // magnitude spanning 2+ grids (e.g. 16 on an 8-cell grid) needs an equal number of
            // hops, one real subgrid-existence check per grid crossed, not a single floorMod
            // jump straight to the final answer while only ever advancing past one neighbor.
            lx = dir.getAxis() == Direction.Axis.X ? peelOneGridWidth(lx, gridSize) : lx;
            ly = dir.getAxis() == Direction.Axis.Y ? peelOneGridWidth(ly, gridSize) : ly;
            lz = dir.getAxis() == Direction.Axis.Z ? peelOneGridWidth(lz, gridSize) : lz;
            grid = next;
        }

        if (grid.outOfBounds(lx, ly, lz)) return logResolve(firstHop, x, y, z, new CellRef.OutOfScope());
        CellRef result = grid == this ? new CellRef.Local(lx, ly, lz) : new CellRef.InAdjacentGrid(grid, lx, ly, lz);
        return logResolve(firstHop, x, y, z, result);
    }

    /** One hop's worth of wrap — subtracts/adds exactly one gridSize, may still be out of bounds. */
    private static int peelOneGridWidth(int v, int gridSize) {
        return v < 0 ? v + gridSize : v - gridSize;
    }

    /**
     * First axis (x/y/z, checked in that order) that's out of bounds, regardless of how far —
     * magnitude only matters for how far foldRemainingIntoRealWorld/wrap step, not for whether an
     * axis counts as overflowing at all. Package-visible for direct unit testing.
     */
    @Nullable
    Direction singleStepOverflowAxis(int x, int y, int z) {
        if (x < 0) return Direction.WEST;
        if (x >= gridSize) return Direction.EAST;
        if (y < 0) return Direction.DOWN;
        if (y >= gridSize) return Direction.UP;
        if (z < 0) return Direction.NORTH;
        if (z >= gridSize) return Direction.SOUTH;
        return null;
    }

    /**
     * Once a hop finds real (non-subgrid) world space instead of a matching neighbor, any OTHER
     * axis still out of bounds folds directly into further relative() steps from base — real-world
     * granularity has no "local coordinate" concept beyond this point, so remaining overflow just
     * means that many more real blocks over in that direction. floorDiv, not a fixed ±1: a magnitude
     * of exactly one grid-width was the only case ever confirmed live, but a piston chain-pushing a
     * SECOND block past the first (issue #34's live follow-up) reads a position a full grid-width
     * further still — collapsing that to the same single step as the first would silently alias two
     * distinct real positions onto one, exactly the bug this generalizes away.
     */
    static BlockPos foldRemainingIntoRealWorld(BlockPos base, Direction resolvedAxis, int x, int y, int z, int gridSize) {
        BlockPos pos = base;
        if (resolvedAxis.getAxis() != Direction.Axis.X) {
            pos = pos.relative(Direction.EAST, Math.floorDiv(x, gridSize));
        }
        if (resolvedAxis.getAxis() != Direction.Axis.Y) {
            pos = pos.relative(Direction.UP, Math.floorDiv(y, gridSize));
        }
        if (resolvedAxis.getAxis() != Direction.Axis.Z) {
            pos = pos.relative(Direction.SOUTH, Math.floorDiv(z, gridSize));
        }
        return pos;
    }

    /**
     * Like resolve(), but chains through creating a same-gridSize SubgridBlockEntity at any hop
     * whose real-world target is air, instead of only ever reading — the write-side counterpart
     * used whenever something spreading/moving/growing needs a writable cell across the boundary.
     * A write landing a full 2+ real-world hops away (a 3rd subgrid, not just the immediate
     * neighbor — issue #46's live follow-up: a piston chain-push spanning that far couldn't
     * resolve at all) creates every intermediate grid along the way that doesn't exist yet, the
     * same way a single hop already created one. Reports Created if ANY hop in the chain needed
     * creating, InAdjacentGrid only if every hop crossed was already there.
     */
    public CellRef resolveOrCreate(int x, int y, int z, ServerLevel realLevel) {
        if (!outOfBounds(x, y, z)) return new CellRef.Local(x, y, z);

        SubgridBlockEntity grid = this;
        int lx = x, ly = y, lz = z;
        Direction firstHop = null;
        boolean anyCreated = false;

        for (int hop = 0; hop < MAX_CHAIN_HOPS && grid.outOfBounds(lx, ly, lz); hop++) {
            Direction dir = grid.singleStepOverflowAxis(lx, ly, lz);
            if (dir == null) return logResolve(firstHop, x, y, z, new CellRef.OutOfScope());
            if (firstHop == null) firstHop = dir;

            BlockPos targetPos = grid.worldPosition.relative(dir);
            boolean wasAir = realLevel.getBlockState(targetPos).isAir();
            SubgridBlockEntity next = createAt(realLevel, targetPos, gridSize);
            if (next == null || next.gridSize != gridSize) {
                BlockPos real = foldRemainingIntoRealWorld(targetPos, dir, lx, ly, lz, gridSize);
                return logResolve(firstHop, x, y, z, new CellRef.RealWorld(real));
            }
            if (wasAir) anyCreated = true;

            lx = dir.getAxis() == Direction.Axis.X ? peelOneGridWidth(lx, gridSize) : lx;
            ly = dir.getAxis() == Direction.Axis.Y ? peelOneGridWidth(ly, gridSize) : ly;
            lz = dir.getAxis() == Direction.Axis.Z ? peelOneGridWidth(lz, gridSize) : lz;
            grid = next;
        }

        if (grid.outOfBounds(lx, ly, lz)) return logResolve(firstHop, x, y, z, new CellRef.OutOfScope());
        CellRef result = anyCreated
                ? new CellRef.Created(grid, lx, ly, lz)
                : new CellRef.InAdjacentGrid(grid, lx, ly, lz);
        return logResolve(firstHop, x, y, z, result);
    }

    /**
     * DEBUG, not INFO — unlike [piston-anim]/[mining-diag] (gated on rare, specific events), this
     * fires on every out-of-bounds neighbor/signal read near a boundary, which vanilla probes
     * constantly (e.g. every weak-power scan touching an edge cell) regardless of whether a real
     * boundary crossing is happening. At INFO this produced 1.4M lines in one play session (86% of
     * the whole log) and caused real lag from the logging volume alone — see issue #42. Guarding on
     * isDebugEnabled() also skips describeCellRef's string-building work when disabled, not just
     * the write. Still permanent, still findable — NeoForge's own debug.log captures DEBUG output
     * separately from latest.log — just no longer paid for on every tick by default.
     */
    /**
     * Diagnostic-only (issue #43): set true around FluidState.tick calls (VanillaBlockPiece
     * .scheduledTick) so logResolve can trace which boundary reads happen mid fluid-spread at
     * INFO — the regular [xgrid] DEBUG line turned out not to actually be captured anywhere by
     * this project's default logging config (checked: debug.log has zero [xgrid] entries even
     * with LOGGER.isDebugEnabled() guarding it), so a targeted, narrowly-scoped INFO trace is the
     * only way to get real visibility without reintroducing the volume problem #42's downgrade
     * fixed. Safe at INFO specifically because fluid ticks are throttled/infrequent, unlike the
     * constant hasSignal/canSurvive traffic every resolve() call sees generally. Single-threaded
     * server tick — no concurrency concerns with this being static.
     */
    private static boolean tracingFluid = false;

    public static void setTracingFluid(boolean value) {
        tracingFluid = value;
    }

    private CellRef logResolve(@Nullable Direction dir, int x, int y, int z, CellRef ref) {
        if (tracingFluid) {
            LOGGER.debug("[xgrid-fluid] resolve dir={} from={} local=({},{},{}) -> {} (issue #43)",
                    dir, worldPosition, x, y, z, describeCellRef(ref));
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[xgrid] resolve dir={} from={} local=({},{},{}) -> {}",
                    dir, worldPosition, x, y, z, describeCellRef(ref));
        }
        return ref;
    }

    private static String describeCellRef(CellRef ref) {
        return switch (ref) {
            case CellRef.Local l -> "Local(%d,%d,%d)".formatted(l.x(), l.y(), l.z());
            case CellRef.InAdjacentGrid a -> "InAdjacentGrid(pos=%s,cell=%d,%d,%d)"
                    .formatted(a.grid().getBlockPos(), a.x(), a.y(), a.z());
            case CellRef.Created c -> "Created(pos=%s,cell=%d,%d,%d)"
                    .formatted(c.grid().getBlockPos(), c.x(), c.y(), c.z());
            case CellRef.RealWorld r -> "RealWorld(%s)".formatted(r.pos());
            case CellRef.OutOfScope ignored -> "OutOfScope";
        };
    }

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
        if (!(resolve(dir, nx, ny, nz) instanceof CellRef.InAdjacentGrid a)) return null;
        PlacedPiece piece = a.grid().getPieceAt(a.x(), a.y(), a.z());
        return piece != null ? new Neighbor(a.grid(), piece) : null;
    }

    /**
     * Wraps v into [0,max]. Math.floorMod, not a fixed boundary-collapse: two DIFFERENT overflowed
     * values one grid-width apart (e.g. 8 and 9, gridSize 8) must land on DIFFERENT wrapped cells
     * (0 and 1) in the neighbor grid, not silently collapse onto the same one — found live via a
     * piston chain-pushing a second block past the first, which needs exactly this (issue #34's
     * live follow-up). floorMod agrees with a naive "collapse to boundary" implementation for
     * every magnitude-1 case (the only one ever exercised before), so this is a strict superset,
     * not a behavior change for anything already working.
     */
    public static int wrap(int v, int max) {
        return Math.floorMod(v, max + 1);
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
        return switch (resolve(x, y, z)) {
            case CellRef.Local l -> {
                PlacedPiece piece = getPieceAt(l.x(), l.y(), l.z());
                yield piece != null ? piece.definition.renderState(piece) : Blocks.AIR.defaultBlockState();
            }
            case CellRef.InAdjacentGrid a -> {
                PlacedPiece piece = a.grid().getPieceAt(a.x(), a.y(), a.z());
                yield piece != null ? piece.definition.renderState(piece) : Blocks.AIR.defaultBlockState();
            }
            case CellRef.RealWorld r -> level.getBlockState(r.pos());
            case CellRef.Created ignored -> Blocks.AIR.defaultBlockState(); // resolve() never creates
            case CellRef.OutOfScope ignored -> Blocks.AIR.defaultBlockState();
        };
    }

    public void notifyUpdate() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            markNeighbouringPhantomsDirty();
        }
    }

    /**
     * A change in this grid can alter the phantom block entities of the grids next to it without anything
     * telling THEM (Create's rotation propagating across the edge sets the neighbour's speed, but only this
     * grid is re-synced); queue a phantom data push for each adjacent same-size grid. Skipped when this grid
     * holds no phantom block entity of its own, so plain block grids (water, redstone) cause no extra traffic.
     */
    private void markNeighbouringPhantomsDirty() {
        boolean anyPhantom = false;
        for (PlacedPiece piece : pieces) {
            if (piece.runtimeBlockEntity != null) { anyPhantom = true; break; }
        }
        if (!anyPhantom) return;
        for (Direction dir : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(dir)) instanceof SubgridBlockEntity other && other.gridSize == gridSize) {
                other.markPhantomDataDirty();
            }
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
        addToGridNoShape(p);
        rebuildShape();
    }

    /** Like addToGrid but leaves the merged shape stale — callers adding many pieces rebuild once. */
    private void addToGridNoShape(PlacedPiece p) {
        short idx = (short) pieces.size();
        pieces.add(p);
        for (int x = p.anchor.getX(); x < p.anchor.getX() + p.footprint.getX(); x++)
            for (int y = p.anchor.getY(); y < p.anchor.getY() + p.footprint.getY(); y++)
                for (int z = p.anchor.getZ(); z < p.anchor.getZ() + p.footprint.getZ(); z++)
                    cellOwner[indexOf(x, y, z)] = idx;
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
    /** Test hook: how many times the merged shape has been rebuilt. */
    int shapeRebuildCount = 0;

    private void rebuildShape() {
        shapeRebuildCount++;
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

    // --- Redstone through the faces ---

    /**
     * Each face gives the block on that side the strongest signal among the pieces against it, weak and strong
     * alike (SubgridBlock#getSignal), and, for a SubgridBlock of another size on that side, cell by cell
     * (overlapSignal). Per face, indexed by the cell's two coordinates across it (faceIndex).
     */
    private int[][] weakCells = new int[6][0], strongCells = new int[6][0];
    private final int[] weakFace = new int[6], strongFace = new int[6];
    private int signalKey;
    private boolean signalsBuilt = false;

    /** Changes when a piece is added, removed or changes state: states are interned, so their identities tell. */
    private int pieceKey() {
        int key = gridSize;
        for (PlacedPiece p : pieces) {
            key = 31 * key + p.anchor.hashCode();
            key = 31 * key + System.identityHashCode(p.runtimeState);
        }
        return key;
    }

    /** Index of the cell (x,y,z) on a face along {@code axis}: its two other coordinates, in x, y, z order. */
    private int faceIndex(Direction.Axis axis, int x, int y, int z) {
        return switch (axis) {
            case X -> y * gridSize + z;
            case Y -> x * gridSize + z;
            case Z -> x * gridSize + y;
        };
    }

    private static boolean onFace(Direction face, int x, int y, int z, int size) {
        int coordinate = face.getAxis().choose(x, y, z);
        return coordinate == (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? size - 1 : 0);
    }

    private void refreshSignals() {
        int key = pieceKey();
        if (signalsBuilt && key == signalKey) return;
        signalsBuilt = true;
        signalKey = key;
        weakCells = new int[6][gridSize * gridSize];
        strongCells = new int[6][gridSize * gridSize];
        Arrays.fill(weakFace, 0);
        Arrays.fill(strongFace, 0);
        if (!(level instanceof ServerLevel server)) return;
        net.minecraft.world.level.Level space = null;
        for (PlacedPiece p : pieces) {
            if (!(p.runtimeState instanceof BlockState state) || !state.isSignalSource()) continue;
            for (int x = p.anchor.getX(); x < p.anchor.getX() + p.footprint.getX(); x++)
                for (int y = p.anchor.getY(); y < p.anchor.getY() + p.footprint.getY(); y++)
                    for (int z = p.anchor.getZ(); z < p.anchor.getZ() + p.footprint.getZ(); z++)
                        for (Direction face : Direction.values()) {
                            if (!onFace(face, x, y, z, gridSize)) continue;
                            if (space == null) space = com.therappha.tinyblocks.v2.VanillaBlockPiece.buildFakeSpace(this, server);
                            // Asked as the block across that face would: the direction points from it into the grid.
                            BlockPos at = virtualPosOf(new BlockPos(x, y, z));
                            Direction towards = face.getOpposite();
                            int weak = state.getSignal(space, at, towards), strong = state.getDirectSignal(space, at, towards);
                            int f = face.ordinal(), i = faceIndex(face.getAxis(), x, y, z);
                            weakCells[f][i] = Math.max(weakCells[f][i], weak);
                            strongCells[f][i] = Math.max(strongCells[f][i], strong);
                            weakFace[f] = Math.max(weakFace[f], weak);
                            strongFace[f] = Math.max(strongFace[f], strong);
                        }
        }
    }

    /** The signal this grid gives the block across {@code face}: the strongest of the pieces against it. */
    public int faceSignal(Direction face, boolean strong) {
        refreshSignals();
        return (strong ? strongFace : weakFace)[face.ordinal()];
    }

    /**
     * The signal this grid gives, across {@code face}, the cell (u, v) of a SubgridBlock of {@code otherSize} there:
     * the strongest of the pieces whose squares on the shared face overlap that cell's.
     */
    public int overlapSignal(Direction face, int otherSize, int u, int v, boolean strong) {
        refreshSignals();
        int[] cells = (strong ? strongCells : weakCells)[face.ordinal()];
        int best = 0;
        for (int i = u * gridSize / otherSize; i < gridSize && i * otherSize < (u + 1) * gridSize; i++) {
            if ((i + 1) * otherSize <= u * gridSize) continue;
            for (int j = v * gridSize / otherSize; j < gridSize && j * otherSize < (v + 1) * gridSize; j++) {
                if ((j + 1) * otherSize <= v * gridSize) continue;
                best = Math.max(best, cells[i * gridSize + j]);
            }
        }
        return best;
    }

    /** The two coordinates across {@code axis} of the cell (x,y,z), as faceIndex orders them. */
    public static int[] acrossFace(Direction.Axis axis, int x, int y, int z) {
        return switch (axis) {
            case X -> new int[] {y, z};
            case Y -> new int[] {x, z};
            case Z -> new int[] {x, y};
        };
    }

    /** Once a tick: when what the faces give changed, the blocks around are told, as a lever tells them. */
    private void updateRedstoneOutputs(ServerLevel level) {
        if (signalsBuilt && pieceKey() == signalKey) return;
        int[][] weakBefore = weakCells, strongBefore = strongCells;
        int[] strongFaceBefore = strongFace.clone();
        refreshSignals();
        if (Arrays.deepEquals(weakBefore, weakCells) && Arrays.deepEquals(strongBefore, strongCells)) return;
        Block block = getBlockState().getBlock();
        level.updateNeighborsAt(worldPosition, block);
        for (Direction face : Direction.values()) {
            if (strongFaceBefore[face.ordinal()] != strongFace[face.ordinal()]) {
                level.updateNeighborsAt(worldPosition.relative(face), block); // what that block now powers in turn
            }
        }
    }

    /** The block across {@code face} changed: the pieces against that face look again (a signal there, for one). */
    public void notifyFace(Direction face) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        for (PlacedPiece piece : new ArrayList<>(pieces)) {
            boolean against = false;
            for (int x = piece.anchor.getX(); x < piece.anchor.getX() + piece.footprint.getX() && !against; x++)
                for (int y = piece.anchor.getY(); y < piece.anchor.getY() + piece.footprint.getY() && !against; y++)
                    for (int z = piece.anchor.getZ(); z < piece.anchor.getZ() + piece.footprint.getZ() && !against; z++)
                        against = onFace(face, x, y, z, gridSize);
            if (!against || !pieces.contains(piece)) continue;
            try {
                piece.definition.onNeighborChanged(piece, serverLevel, worldPosition, this, piece);
            } catch (Exception e) {
                logTickFailure("onNeighborChanged (real neighbour)", piece, e);
            }
        }
    }

    private VoxelShape outlineShape = Shapes.empty();
    private int outlineKey;
    private boolean outlineBuilt = false;

    /**
     * What a crosshair or a click hits: each piece's own shape, shrunk into its cell — a redstone link is its thin
     * plate, so a click lands on the plate where its frequency slots are, not on the face of a whole cell. (Collision
     * stays whole cells: getCachedShape.) Rebuilt only when a piece or its state changed; states are interned, so
     * their identities say whether one did.
     */
    public VoxelShape getOutlineShape() {
        int key = gridSize;
        for (PlacedPiece p : pieces) {
            key = 31 * key + p.anchor.hashCode();
            key = 31 * key + System.identityHashCode(p.runtimeState);
        }
        if (!outlineBuilt || key != outlineKey) {
            outlineShape = buildOutlineShape();
            outlineKey = key;
            outlineBuilt = true;
        }
        return outlineShape;
    }

    /** The shape of {@code piece} in its own block space (0..1), or null where it fills its cells whole. */
    @javax.annotation.Nullable
    public static VoxelShape pieceShape(PlacedPiece piece) {
        if (!(piece.runtimeState instanceof net.minecraft.world.level.block.state.BlockState state)) return null;
        if (piece.footprint.getX() != 1 || piece.footprint.getY() != 1 || piece.footprint.getZ() != 1) return null;
        VoxelShape shape;
        try {
            shape = state.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        } catch (RuntimeException e) {
            return null; // a shape that needs a real world around it
        }
        // An empty shape (light, structure void) would leave the piece impossible to aim at, so to break.
        if (shape.isEmpty() || net.minecraft.world.level.block.Block.isShapeFullBlock(shape)) return null;
        return shape;
    }

    private VoxelShape buildOutlineShape() {
        var whole = new net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape(gridSize, gridSize, gridSize);
        boolean anyWhole = false;
        VoxelShape partial = Shapes.empty();
        double cell = 1.0 / gridSize;
        for (PlacedPiece p : pieces) {
            VoxelShape shape = pieceShape(p);
            if (shape == null) {
                for (int x = 0; x < p.footprint.getX(); x++)
                    for (int y = 0; y < p.footprint.getY(); y++)
                        for (int z = 0; z < p.footprint.getZ(); z++) {
                            int cx = p.anchor.getX() + x, cy = p.anchor.getY() + y, cz = p.anchor.getZ() + z;
                            if (!outOfBounds(cx, cy, cz)) {
                                whole.fill(cx, cy, cz);
                                anyWhole = true;
                            }
                        }
                continue;
            }
            for (net.minecraft.world.phys.AABB box : shape.toAabbs()) {
                partial = Shapes.joinUnoptimized(partial, Shapes.create(
                        (p.anchor.getX() + box.minX) * cell, (p.anchor.getY() + box.minY) * cell, (p.anchor.getZ() + box.minZ) * cell,
                        (p.anchor.getX() + box.maxX) * cell, (p.anchor.getY() + box.maxY) * cell, (p.anchor.getZ() + box.maxZ) * cell),
                        net.minecraft.world.phys.shapes.BooleanOp.OR);
            }
        }
        VoxelShape out = anyWhole ? ShapeAccess.wrap(whole) : Shapes.empty();
        return partial.isEmpty() ? out : Shapes.joinUnoptimized(out, partial, net.minecraft.world.phys.shapes.BooleanOp.OR).optimize();
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

    /**
     * Virtual-frame position of local cell (0,0,0). The frame is global: every SubgridBlock sits at
     * {@code worldPosition * gridSize}, so the cells of two neighbouring grids are adjacent in it exactly
     * like the blocks are in the world. A lookup that steps across a grid edge therefore lands on the
     * neighbour's own phantom block entity at a position consistent with the one asked for, which is what
     * lookups that compare block entity positions (Create's rotation propagation) need.
     */
    public BlockPos virtualOrigin() {
        return new BlockPos(
                wrapHorizontal(worldPosition.getX()) * gridSize,
                worldPosition.getY() * gridSize,
                wrapHorizontal(worldPosition.getZ()) * gridSize);
    }

    /**
     * Horizontal virtual coordinates are world coordinates times the grid size, which would leave the world
     * border (+-30M) for any grid far from the origin and make vanilla reject every cell there. Folding the world
     * position into a 1M-wide window first keeps them inside (1M * 16 = 16M); the only cost is that two grids
     * facing each other across a window seam (every 1M blocks) are no longer adjacent in the frame.
     */
    public static final int HORIZONTAL_WINDOW = 1 << 20;

    private static int wrapHorizontal(int coordinate) {
        return Math.floorMod(coordinate + HORIZONTAL_WINDOW / 2, HORIZONTAL_WINDOW) - HORIZONTAL_WINDOW / 2;
    }

    /** World-frame position a phantom block entity for the piece at {@code cell} lives at. */
    public BlockPos virtualPosOf(net.minecraft.core.Vec3i cell) {
        return virtualOrigin().offset(cell);
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
            addToGridNoShape(piece);
            piece.definition.onLoaded(piece, registries);
        }
        // Once, not per piece: rebuildShape is O(gridSize^3), so rebuilding after every add made
        // loading N pieces cost N x gridSize^3 (issue #50).
        rebuildShape();

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
