package com.therappha.tinyblocks.v2;

import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Cells not explicitly touched this call fall back to the SubgridBlockEntity's real vanilla view.
 * Vanilla code addresses it in the grid's virtual world frame (SubgridBlockEntity#virtualOrigin);
 * internal bookkeeping (cell map keys, touchedCells) stays in LOCAL cell coordinates.
 */
final class SubgridFakeCellGetter extends FakeCellGetter {
    final SubgridBlockEntity be;
    /** World-frame position of local cell (0,0,0). */
    private final BlockPos origin;
    /** The local cell whose own tick/onUse this space was built for, or null when nothing is being operated. */
    @javax.annotation.Nullable private BlockPos operatedCell;
    /**
     * Set right after the FakeLevel/FakeServerLevel wrapping this cell space is constructed —
     * can't be known at this object's own construction time, since it's built first and
     * handed to the FakeLevel constructor. Only needed for getBlockEntity's phantom
     * BlockEntity.setLevel call below.
     */
    private Level attachedLevel;

    SubgridFakeCellGetter(SubgridBlockEntity be, @javax.annotation.Nullable BlockPos operatedCell) {
        this.be = be;
        this.origin = be.virtualOrigin();
        this.operatedCell = operatedCell;
    }

    void attachLevel(Level level) {
        this.attachedLevel = level;
    }

    /** Empties the space of the last use's writes and points it at {@code operatedCell} (TickSpace). */
    void reuseFor(BlockPos operatedCell) {
        clear();
        this.operatedCell = operatedCell;
    }

    @Override
    public BlockPos toWorld(BlockPos local) {
        return local.offset(origin);
    }

    @Override
    public BlockPos toLocal(BlockPos world) {
        return world.subtract(origin);
    }

    private BlockPos resolve(BlockPos pos) {
        return toLocal(pos);
    }

    @Override
    public BlockPos resolveLocal(BlockPos pos) {
        return toLocal(pos);
    }

    @Override
    public Vec3 toReal(Vec3 virtual) {
        return VirtualHit.toReal(virtual, be.getBlockPos(), origin, be.getGridSize());
    }

    /**
     * A piece against a face asking the signal from the block across it: the real world's answer there (a
     * powered solid block's strong power included, which the cells can't see), or, across a SubgridBlock of
     * another size, the strongest of its pieces whose cells overlap the asking one's. A grid of the same size
     * runs on cell by cell (null). {@code direction} points from the asking cell to {@code world}.
     */
    @Override
    public Integer outsideSignal(BlockPos world, Direction direction, boolean strong) {
        BlockPos local = toLocal(world);
        if (be.inBounds(local.getX(), local.getY(), local.getZ())) return null;
        BlockPos asking = local.relative(direction.getOpposite());
        Level real = be.getLevel();
        if (real == null || !be.inBounds(asking.getX(), asking.getY(), asking.getZ())) return null;
        BlockPos across = be.getBlockPos().relative(direction);
        if (real.getBlockEntity(across) instanceof SubgridBlockEntity other) {
            if (other.getGridSize() == be.getGridSize()) return null;
            int[] uv = SubgridBlockEntity.acrossFace(direction.getAxis(), asking.getX(), asking.getY(), asking.getZ());
            return other.overlapSignal(direction.getOpposite(), be.getGridSize(), uv[0], uv[1], strong);
        }
        return strong ? real.getDirectSignal(across, direction) : real.getSignal(across, direction);
    }

    @Override
    public void gameEvent(net.minecraft.core.Holder<net.minecraft.world.level.gameevent.GameEvent> event, Vec3 pos,
                          net.minecraft.world.level.gameevent.GameEvent.Context context) {
        GridGameEvents.post(be, event, pos, context);
    }

    /** False once the call this space was made for has been applied. */
    boolean live = true;

    @Override
    public boolean afterItsCall(java.util.function.Consumer<Level> work) {
        if (live || !(be.getLevel() instanceof ServerLevel level)) return false;
        TickSpace space = TickSpace.acquire(be, level, null);
        try {
            work.accept(space.level);
            VanillaBlockPiece.applyChanges(be, space.level, level);
        } finally {
            space.release();
        }
        return true;
    }

    @Override
    public boolean outsideGrids(BlockPos world) {
        BlockPos local = toLocal(world);
        return !be.inBounds(local.getX(), local.getY(), local.getZ())
                && !(be.resolve(local.getX(), local.getY(), local.getZ()) instanceof SubgridBlockEntity.CellRef.InAdjacentGrid);
    }

    /** A cell past the edge belongs to the next grid: its tick goes in that grid's queue. */
    @Override
    public boolean scheduleTick(BlockPos world, Object type, int delay, int priority) {
        if (be.getLevel() == null) return false;
        long target = com.therappha.tinyblocks.subgrid.GridTickScheduler.gameTime(be.getLevel()) + delay;
        BlockPos local = toLocal(world);
        if (be.inBounds(local.getX(), local.getY(), local.getZ())) {
            be.scheduleTick(local.getX(), local.getY(), local.getZ(), target, priority, type);
        } else if (be.resolve(local.getX(), local.getY(), local.getZ()) instanceof SubgridBlockEntity.CellRef.InAdjacentGrid a) {
            a.grid().scheduleTick(a.x(), a.y(), a.z(), target, priority, type);
        }
        return true;
    }

    @Override
    public boolean tickPending(BlockPos world, Object type) {
        return askQueue(world, (grid, x, y, z) -> grid.tickPending(x, y, z, type));
    }

    @Override
    public boolean tickDue(BlockPos world, Object type) {
        return askQueue(world, (grid, x, y, z) -> grid.tickDue(x, y, z, type));
    }

    private interface QueueQuestion {
        boolean ask(SubgridBlockEntity grid, int x, int y, int z);
    }

    private boolean askQueue(BlockPos world, QueueQuestion question) {
        BlockPos local = toLocal(world);
        if (be.inBounds(local.getX(), local.getY(), local.getZ())) return question.ask(be, local.getX(), local.getY(), local.getZ());
        return be.resolve(local.getX(), local.getY(), local.getZ()) instanceof SubgridBlockEntity.CellRef.InAdjacentGrid a
                && question.ask(a.grid(), a.x(), a.y(), a.z());
    }

    @Override
    public boolean blockEvent(BlockPos world, net.minecraft.world.level.block.Block block, int id, int param) {
        if (!(be.getLevel() instanceof ServerLevel level)) return false;
        com.therappha.tinyblocks.subgrid.GridTickScheduler.blockEvent(level,
                new com.therappha.tinyblocks.subgrid.GridTickScheduler.BlockEvent(be, world.immutable(), block, id, param));
        return true;
    }

    @Override
    public void blockUpdatedAt(BlockPos world) {
        BlockPos local = toLocal(world);
        if (be.resolve(local.getX(), local.getY(), local.getZ()) instanceof SubgridBlockEntity.CellRef.InAdjacentGrid a
                && a.grid() != be) {
            a.grid().markPhantomDataDirty();
        }
    }

    @Override
    public void set(BlockPos pos, BlockState state) {
        super.set(resolve(pos), state);
    }

    @Override
    protected BlockState originalAt(BlockPos local) {
        if (!be.inBounds(local.getX(), local.getY(), local.getZ())) return null;
        PlacedPiece p = be.getPieceAt(local.getX(), local.getY(), local.getZ());
        return p != null && p.definition instanceof VanillaBlockPiece && p.anchor.equals(local) ? VanillaBlockPiece.stateOf(p) : null;
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
            // A grid whose chunk unloaded holds none: as for a real block entity left from before a reload, the
            // position belongs to the one loaded in its place. Create drops a network member whose position no
            // longer answers with it; a stale phantom that still did was counted next to its successor.
            if (be.isChunkUnloaded()) return null;
            PlacedPiece piece = be.getPieceAt(resolved.getX(), resolved.getY(), resolved.getZ());
            if (piece == null || !(piece.definition instanceof VanillaBlockPiece) || attachedLevel == null) return null;
            // Only the piece actually being operated on (its own tick/onUse — aliasTo is its
            // anchor) may bind its phantom to this fake level; a sibling merely READING it must
            // not re-point it (issue #57, see peekBlockEntity).
            boolean own = operatedCell != null && resolved.equals(operatedCell);
            return own ? VanillaBlockPiece.blockEntityFor(piece, be, attachedLevel) : VanillaBlockPiece.peekBlockEntity(piece, be, attachedLevel);
        }
        // Out-of-bounds: resolved is still a grid-LOCAL coordinate (e.g. (4,-1,4)), NOT a
        // real-world position — it has to go through be.resolve(...) exactly like fallback()'s
        // realBlockStateAt does, or the real level gets asked about a tiny integer coordinate
        // and always answers null (issue #55: a hopper at the bottom of a subgrid saw "a
        // hopper/chest is there" via getBlockState but no BlockEntity to put items into).
        // A piece scanning past the edge (a hopper's extraction/insertion target, a funnel) needs
        // whatever genuinely sits there: a sibling grid's piece, or a real BlockEntity.
        return switch (be.resolve(resolved.getX(), resolved.getY(), resolved.getZ())) {
            case SubgridBlockEntity.CellRef.InAdjacentGrid a -> {
                PlacedPiece other = a.grid().getPieceAt(a.x(), a.y(), a.z());
                if (other == null || !(other.definition instanceof VanillaBlockPiece) || attachedLevel == null) yield null;
                yield VanillaBlockPiece.peekBlockEntity(other, a.grid(), attachedLevel);
            }
            case SubgridBlockEntity.CellRef.RealWorld r -> {
                Level real = be.getLevel();
                yield real != null ? real.getBlockEntity(r.pos()) : null;
            }
            default -> null;
        };
    }
}
