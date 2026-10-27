package com.therappha.tinyblocks.v2;

import com.therappha.tinyblocks.subgrid.GridRay;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A hit on a SubgridBlock, seen from inside its grid: the targeted cell, and the same hit in the grid's virtual
 * world frame (SubgridBlockEntity#virtualOrigin), where every cell is a full block. Code written for full-size
 * blocks — a block's use, a mod's click listener, Create's placement helpers and value boxes — reads the hit point
 * relative to the block it hit, so it has to be handed this one, not the centre of the cell or the real hit.
 *
 * @param cell local cell the hit lands in
 * @param hit  the hit in the virtual frame: the cell's virtual position, the same face, the hit point scaled up
 */
public record VirtualHit(SubgridBlockEntity grid, BlockPos cell, BlockHitResult hit) {

    public static VirtualHit of(SubgridBlockEntity grid, BlockHitResult realHit) {
        Vec3i cell = GridRay.cellAt(grid.getBlockPos(), realHit.getLocation(), realHit.getDirection(), grid.getGridSize());
        BlockPos virtualPos = grid.virtualPosOf(cell);
        Vec3 location = toVirtual(realHit.getLocation(), grid.getBlockPos(), grid.virtualOrigin(), grid.getGridSize());
        return new VirtualHit(grid, new BlockPos(cell),
                new BlockHitResult(location, realHit.getDirection(), virtualPos, realHit.isInside()));
    }

    /** The point {@code real} (in the real level, near the SubgridBlock at {@code realPos}) in the virtual frame. */
    public static Vec3 toVirtual(Vec3 real, BlockPos realPos, BlockPos virtualOrigin, int gridSize) {
        return Vec3.atLowerCornerOf(virtualOrigin).add(real.subtract(Vec3.atLowerCornerOf(realPos)).scale(gridSize));
    }

    /** Inverse of {@link #toVirtual}. */
    public static Vec3 toReal(Vec3 virtual, BlockPos realPos, BlockPos virtualOrigin, int gridSize) {
        return Vec3.atLowerCornerOf(realPos).add(virtual.subtract(Vec3.atLowerCornerOf(virtualOrigin)).scale(1.0 / gridSize));
    }

    /** Where the corner of the virtual block {@code virtualPos} sits in the real level; may lie past this grid. */
    public Vec3 realCornerOf(BlockPos virtualPos) {
        return toReal(Vec3.atLowerCornerOf(virtualPos), grid.getBlockPos(), grid.virtualOrigin(), grid.getGridSize());
    }

    /** {@code virtualPos} as a cell of this grid; outside 0..size-1 when it lies in a neighbouring block. */
    public BlockPos localOf(BlockPos virtualPos) {
        return virtualPos.subtract(grid.virtualOrigin());
    }
}
