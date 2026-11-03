package com.therappha.tinyblocks.subgrid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/** A grid's collision (occupancy) and outline shapes. */
final class GridShapes {

    private GridShapes() {}

    /**
     * The collision shape: every occupied cell whole, built from the occupancy in a single pass instead of unioning one
     * VoxelShape per piece via Shapes.or — that scaled with piece count (and grew more expensive per union as the
     * accumulated shape got more complex), so dense subgrids paid an increasingly large cost on every single
     * place/break. This is bounded by gridSize^3 regardless of piece count (see issue #27).
     */
    static VoxelShape occupancy(SubgridBlockEntity grid) {
        int gridSize = grid.gridSize;
        var discrete = new net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape(gridSize, gridSize, gridSize);
        boolean any = false;
        for (int y = 0; y < gridSize; y++)
            for (int z = 0; z < gridSize; z++)
                for (int x = 0; x < gridSize; x++)
                    if (grid.ownerAt(x, y, z) != SubgridBlockEntity.EMPTY) {
                        discrete.fill(x, y, z);
                        any = true;
                    }
        return any ? ShapeAccess.wrap(discrete) : Shapes.empty();
    }

    /** The shape of {@code piece} in its own block space (0..1), or null where it fills its cells whole. */
    @javax.annotation.Nullable
    static VoxelShape pieceShape(PlacedPiece piece) {
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

    /** What a crosshair or a click hits: each piece's own shape, shrunk into its cell (SubgridBlockEntity#getOutlineShape). */
    static VoxelShape outline(SubgridBlockEntity grid) {
        int gridSize = grid.gridSize;
        var whole = new net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape(gridSize, gridSize, gridSize);
        boolean anyWhole = false;
        List<VoxelShape> parts = new ArrayList<>();
        double cell = 1.0 / gridSize;
        for (PlacedPiece p : grid.getPieces()) {
            VoxelShape shape = pieceShape(p);
            if (shape == null) {
                for (int x = 0; x < p.footprint.getX(); x++)
                    for (int y = 0; y < p.footprint.getY(); y++)
                        for (int z = 0; z < p.footprint.getZ(); z++) {
                            int cx = p.anchor.getX() + x, cy = p.anchor.getY() + y, cz = p.anchor.getZ() + z;
                            if (!grid.outOfBounds(cx, cy, cz)) {
                                whole.fill(cx, cy, cz);
                                anyWhole = true;
                            }
                        }
                continue;
            }
            for (net.minecraft.world.phys.AABB box : shape.toAabbs()) {
                parts.add(Shapes.create(
                        (p.anchor.getX() + box.minX) * cell, (p.anchor.getY() + box.minY) * cell, (p.anchor.getZ() + box.minZ) * cell,
                        (p.anchor.getX() + box.maxX) * cell, (p.anchor.getY() + box.maxY) * cell, (p.anchor.getZ() + box.maxZ) * cell));
            }
        }
        VoxelShape partial = joinAll(parts);
        VoxelShape out = anyWhole ? ShapeAccess.wrap(whole) : Shapes.empty();
        return partial.isEmpty() ? out : Shapes.joinUnoptimized(out, partial, net.minecraft.world.phys.shapes.BooleanOp.OR).optimize();
    }

    /**
     * The union of {@code parts}, joined pairwise up a balanced tree (issue #64): joining them one by one into a
     * growing shape cost O(parts x final shape) — 220 ms for a 16-grid layer of 256 cogwheels, on every change.
     */
    private static VoxelShape joinAll(List<VoxelShape> parts) {
        if (parts.isEmpty()) return Shapes.empty();
        List<VoxelShape> level = parts;
        while (level.size() > 1) {
            List<VoxelShape> next = new ArrayList<>((level.size() + 1) / 2);
            for (int i = 0; i < level.size(); i += 2) {
                next.add(i + 1 < level.size()
                        ? Shapes.joinUnoptimized(level.get(i), level.get(i + 1), net.minecraft.world.phys.shapes.BooleanOp.OR)
                        : level.get(i));
            }
            level = next;
        }
        return level.get(0);
    }
}
