package com.therappha.tinyblocks.items;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * What a Minimizer builds between two corner cells. Cells are "fine" positions: a SubgridBlock's real position times
 * the grid size plus the cell, one grid of cells over the whole world, so a shape can run across several blocks.
 */
public enum BuildMode {
    /** One piece per click, as without modes. */
    PLACE,
    /** Every cell of the box. */
    FILL,
    /** The box's six faces. */
    HOLLOW,
    /** The box's four sides, no floor or ceiling. */
    WALLS,
    /** One cell thick, in the plane of the face the first corner was clicked on. */
    PLANE,
    /** Not a shape: a click breaks the whole grid under the crosshair, outlined red (MinimizerShapes#destroy). */
    DESTROY;

    public BuildMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public String translationKey() {
        return "tinyblocks.build_mode." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * The cells of this shape between {@code a} and {@code b}, at most {@code limit} + 1 of them (more means too
     * big). {@code faceAxis} is the axis of the face the first corner was clicked on, used by PLANE.
     */
    public List<BlockPos> cells(BlockPos a, BlockPos b, Direction.Axis faceAxis, int limit) {
        int minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY()), maxY = Math.max(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ());
        if (this == PLANE) {
            // Flattened onto the first corner's layer along the clicked face's axis.
            switch (faceAxis) {
                case X -> { minX = a.getX(); maxX = a.getX(); }
                case Y -> { minY = a.getY(); maxY = a.getY(); }
                case Z -> { minZ = a.getZ(); maxZ = a.getZ(); }
            }
        }
        List<BlockPos> out = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean sideX = x == minX || x == maxX, sideY = y == minY || y == maxY, sideZ = z == minZ || z == maxZ;
                    boolean in = switch (this) {
                        case PLACE, FILL, PLANE, DESTROY -> true;
                        case HOLLOW -> sideX || sideY || sideZ;
                        case WALLS -> sideX || sideZ;
                    };
                    if (!in) continue;
                    out.add(new BlockPos(x, y, z));
                    if (out.size() > limit) return out;
                }
            }
        }
        return out;
    }

    /** The corners of the box {@link #cells} fills, for a preview outline: {min, max} inclusive. */
    public BlockPos[] bounds(BlockPos a, BlockPos b, Direction.Axis faceAxis) {
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        if (this == PLANE) {
            min = switch (faceAxis) {
                case X -> new BlockPos(a.getX(), min.getY(), min.getZ());
                case Y -> new BlockPos(min.getX(), a.getY(), min.getZ());
                case Z -> new BlockPos(min.getX(), min.getY(), a.getZ());
            };
            max = switch (faceAxis) {
                case X -> new BlockPos(a.getX(), max.getY(), max.getZ());
                case Y -> new BlockPos(max.getX(), a.getY(), max.getZ());
                case Z -> new BlockPos(max.getX(), max.getY(), a.getZ());
            };
        }
        return new BlockPos[] {min, max};
    }
}
