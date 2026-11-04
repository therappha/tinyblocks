package com.therappha.tinyblocks.subgrid;

import com.mojang.logging.LogUtils;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity.CellRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/** Where a local cell of a grid lands: in it, in a neighbour grid of the same size, or in the real world. */
final class GridResolver {

    private static final Logger LOGGER = LogUtils.getLogger();

    private GridResolver() {}

    /**
     * Read-only resolution for a direction the caller already knows (e.g. a per-face neighbor
     * loop that computed dir before checking bounds) — (x,y,z) is assumed out-of-bounds already.
     */
    static CellRef resolve(SubgridBlockEntity from, Direction dir, int x, int y, int z) {
        Level level = from.getLevel();
        if (!(level instanceof ServerLevel)) return logResolve(from, dir, x, y, z, new CellRef.OutOfScope());
        BlockPos targetPos = from.getBlockPos().relative(dir);
        CellRef ref;
        if (level.getBlockEntity(targetPos) instanceof SubgridBlockEntity other && other.gridSize == from.gridSize) {
            int max = from.gridSize - 1;
            ref = new CellRef.InAdjacentGrid(other, SubgridBlockEntity.wrap(x, max), SubgridBlockEntity.wrap(y, max), SubgridBlockEntity.wrap(z, max));
        } else {
            ref = new CellRef.RealWorld(targetPos);
        }
        return logResolve(from, dir, x, y, z, ref);
    }

    /** Bounds a pathological chain — comfortably covers a corner overflowing all 3 axes at once. */
    private static final int MAX_CHAIN_HOPS = 4;

    /**
     * Read-only resolution deriving the direction from single-axis overflow, CHAINING through
     * multiple same-from.gridSize neighbor grids when more than one axis overflows at once (issue
     * #43). A corner query — one step past a side edge AND one step past the floor — needs two
     * hops (into the side neighbor, then down from THAT grid's own frame) to find the real
     * answer; the old single-hop-only version reported every such compound query as bare air
     * regardless of what was actually there, which is what drew fluid spread toward every
     * air-adjacent edge (confirmed live in a trace: local=(8,-1,4) on an 8-cell
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
    static CellRef resolve(SubgridBlockEntity from, int x, int y, int z) {
        if (!from.outOfBounds(x, y, z)) return new CellRef.Local(x, y, z);
        if (!(from.getLevel() instanceof ServerLevel serverLevel)) return logResolve(from, null, x, y, z, new CellRef.OutOfScope());
        CellRef direct = directlyInGrid(from, serverLevel, x, y, z);
        if (direct != null) return logResolve(from, null, x, y, z, direct);

        SubgridBlockEntity grid = from;
        int lx = x, ly = y, lz = z;
        Direction firstHop = null;

        for (int hop = 0; hop < MAX_CHAIN_HOPS && grid.outOfBounds(lx, ly, lz); hop++) {
            Direction dir = singleStepOverflowAxis(grid, lx, ly, lz);
            if (dir == null) return logResolve(from, firstHop, x, y, z, new CellRef.OutOfScope());
            if (firstHop == null) firstHop = dir;

            BlockPos targetPos = grid.getBlockPos().relative(dir);
            if (!(serverLevel.getBlockEntity(targetPos) instanceof SubgridBlockEntity next) || next.gridSize != from.gridSize) {
                BlockPos real = foldRemainingIntoRealWorld(targetPos, dir, lx, ly, lz, from.gridSize);
                return logResolve(from, firstHop, x, y, z, new CellRef.RealWorld(real));
            }

            // Peel exactly ONE grid-width per hop, not the fully-reduced final coordinate — a
            // magnitude spanning 2+ grids (e.g. 16 on an 8-cell grid) needs an equal number of
            // hops, one real subgrid-existence check per grid crossed, not a single floorMod
            // jump straight to the final answer while only ever advancing past one neighbor.
            lx = dir.getAxis() == Direction.Axis.X ? peelOneGridWidth(lx, from.gridSize) : lx;
            ly = dir.getAxis() == Direction.Axis.Y ? peelOneGridWidth(ly, from.gridSize) : ly;
            lz = dir.getAxis() == Direction.Axis.Z ? peelOneGridWidth(lz, from.gridSize) : lz;
            grid = next;
        }

        if (grid.outOfBounds(lx, ly, lz)) return logResolve(from, firstHop, x, y, z, new CellRef.OutOfScope());
        CellRef result = grid == from ? new CellRef.Local(lx, ly, lz) : new CellRef.InAdjacentGrid(grid, lx, ly, lz);
        return logResolve(from, firstHop, x, y, z, result);
    }

    /**
     * The cell where a grid of this size holds it, whatever stands between here and there. Walking there axis by axis
     * gives up at a gap: a cell diagonal to this grid, past a corner with no grid on the way, came out as the real block
     * there, which is that very grid's SubgridBlock answering with its whole face. Null when no such grid is there.
     */
    @Nullable
    private static CellRef directlyInGrid(SubgridBlockEntity from, ServerLevel level, int x, int y, int z) {
        BlockPos target = from.getBlockPos().offset(Math.floorDiv(x, from.gridSize), Math.floorDiv(y, from.gridSize), Math.floorDiv(z, from.gridSize));
        if (!(level.getBlockEntity(target) instanceof SubgridBlockEntity grid) || grid.gridSize != from.gridSize) return null;
        return new CellRef.InAdjacentGrid(grid, Math.floorMod(x, from.gridSize), Math.floorMod(y, from.gridSize), Math.floorMod(z, from.gridSize));
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
    static Direction singleStepOverflowAxis(SubgridBlockEntity from, int x, int y, int z) {
        if (x < 0) return Direction.WEST;
        if (x >= from.gridSize) return Direction.EAST;
        if (y < 0) return Direction.DOWN;
        if (y >= from.gridSize) return Direction.UP;
        if (z < 0) return Direction.NORTH;
        if (z >= from.gridSize) return Direction.SOUTH;
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
     * Like resolve(), but chains through creating a same-from.gridSize SubgridBlockEntity at any hop
     * whose real-world target is air, instead of only ever reading — the write-side counterpart
     * used whenever something spreading/moving/growing needs a writable cell across the boundary.
     * A write landing a full 2+ real-world hops away (a 3rd subgrid, not just the immediate
     * neighbor — issue #46's live follow-up: a piston chain-push spanning that far couldn't
     * resolve at all) creates every intermediate grid along the way that doesn't exist yet, the
     * same way a single hop already created one. Reports Created if ANY hop in the chain needed
     * creating, InAdjacentGrid only if every hop crossed was already there.
     */
    static CellRef resolveOrCreate(SubgridBlockEntity from, int x, int y, int z, ServerLevel realLevel) {
        if (!from.outOfBounds(x, y, z)) return new CellRef.Local(x, y, z);
        CellRef direct = directlyInGrid(from, realLevel, x, y, z);
        if (direct != null) return logResolve(from, null, x, y, z, direct);

        SubgridBlockEntity grid = from;
        int lx = x, ly = y, lz = z;
        Direction firstHop = null;
        boolean anyCreated = false;

        for (int hop = 0; hop < MAX_CHAIN_HOPS && grid.outOfBounds(lx, ly, lz); hop++) {
            Direction dir = singleStepOverflowAxis(grid, lx, ly, lz);
            if (dir == null) return logResolve(from, firstHop, x, y, z, new CellRef.OutOfScope());
            if (firstHop == null) firstHop = dir;

            BlockPos targetPos = grid.getBlockPos().relative(dir);
            boolean wasAir = realLevel.getBlockState(targetPos).isAir();
            SubgridBlockEntity next = SubgridBlockEntity.createAt(realLevel, targetPos, from.gridSize);
            if (next == null || next.gridSize != from.gridSize) {
                BlockPos real = foldRemainingIntoRealWorld(targetPos, dir, lx, ly, lz, from.gridSize);
                return logResolve(from, firstHop, x, y, z, new CellRef.RealWorld(real));
            }
            if (wasAir) anyCreated = true;

            lx = dir.getAxis() == Direction.Axis.X ? peelOneGridWidth(lx, from.gridSize) : lx;
            ly = dir.getAxis() == Direction.Axis.Y ? peelOneGridWidth(ly, from.gridSize) : ly;
            lz = dir.getAxis() == Direction.Axis.Z ? peelOneGridWidth(lz, from.gridSize) : lz;
            grid = next;
        }

        if (grid.outOfBounds(lx, ly, lz)) return logResolve(from, firstHop, x, y, z, new CellRef.OutOfScope());
        CellRef result = anyCreated
                ? new CellRef.Created(grid, lx, ly, lz)
                : new CellRef.InAdjacentGrid(grid, lx, ly, lz);
        return logResolve(from, firstHop, x, y, z, result);
    }

    /**
     * DEBUG, not INFO — unlike a trace gated on rare, specific events, this
     * fires on every out-of-bounds neighbor/signal read near a boundary, which vanilla probes
     * constantly (e.g. every weak-power scan touching an edge cell) regardless of whether a real
     * boundary crossing is happening. At INFO this produced 1.4M lines in one play session (86% of
     * the whole log) and caused real lag from the logging volume alone — see issue #42. Guarding on
     * isDebugEnabled() also skips describeCellRef's string-building work when disabled, not just
     * the write. Still permanent, still findable — NeoForge's own debug.log captures DEBUG output
     * separately from latest.log — just no longer paid for on every tick by default.
     */
    private static CellRef logResolve(SubgridBlockEntity from, @Nullable Direction dir, int x, int y, int z, CellRef ref) {
        // The description is only built when it is logged: formatting it on every resolve weighed in a profile.
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[xgrid] resolve dir={} from={} local=({},{},{}) -> {}",
                    dir, from.getBlockPos(), x, y, z, describeCellRef(ref));
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
}
