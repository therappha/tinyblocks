package com.therappha.tinyblocks.compat.create;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A belt doesn't exist in part: Create breaks all of it when a segment goes (BeltBlock#onRemove). Taking some grids of a
 * belt running across several cuts it, so it breaks there, as a segment broken in the world would break it, before
 * anything is saved. A segment put down with nothing to go on to (a grid copied, or saved before this rule) breaks too.
 */
public final class BeltCuts {

    private BeltCuts() {}

    /** Before {@code leaving} go: every belt running out of them into a grid that stays is broken. */
    public static void cutLeaving(ServerLevel level, Collection<SubgridBlockEntity> leaving) {
        for (SubgridBlockEntity grid : leaving) {
            breakWhere(level, grid, next -> {
                if (next == null || !(grid.resolve(next.getX(), next.getY(), next.getZ()) instanceof SubgridBlockEntity.CellRef.InAdjacentGrid a)) return false;
                return !leaving.contains(a.grid()) && isBelt(grid.realBlockStateAt(next.getX(), next.getY(), next.getZ()));
            });
        }
    }

    /** After {@code placed} are down: every belt with a segment leading to nothing is broken. */
    public static void breakLooseEnds(ServerLevel level, Collection<SubgridBlockEntity> placed) {
        for (SubgridBlockEntity grid : placed) {
            breakWhere(level, grid, next -> next != null && !isBelt(grid.realBlockStateAt(next.getX(), next.getY(), next.getZ())));
        }
    }

    private interface Check {
        boolean breaks(@Nullable BlockPos nextCell);
    }

    private static void breakWhere(ServerLevel level, SubgridBlockEntity grid, Check check) {
        for (PlacedPiece piece : new ArrayList<>(grid.getPieces())) {
            if (!(piece.runtimeState instanceof BlockState state) || !isBelt(state)) continue;
            BlockPos at = piece.anchor;
            if (grid.getPieceAt(at.getX(), at.getY(), at.getZ()) != piece) continue; // gone with an earlier segment
            if (check.breaks(nextCell(grid, state, at, true)) || check.breaks(nextCell(grid, state, at, false))) breakBelt(level, grid, at);
        }
    }

    @Nullable
    private static BlockPos nextCell(SubgridBlockEntity grid, BlockState state, BlockPos cell, boolean forward) {
        BlockPos next = BeltBlock.nextSegmentPosition(state, grid.virtualPosOf(cell), forward);
        return next == null ? null : next.subtract(grid.virtualOrigin());
    }

    /** Removing a segment runs Create's own onRemove, which takes the rest of the belt, across grids too. */
    private static void breakBelt(ServerLevel level, SubgridBlockEntity grid, BlockPos cell) {
        Vec3 at = grid.realPositionOf(cell);
        PlacedPiece removed = grid.removePieceAt(cell.getX(), cell.getY(), cell.getZ());
        if (removed == null) return;
        List<ItemStack> drops = removed.definition.drops(removed, level, grid.getBlockPos(), grid, ItemStack.EMPTY);
        for (ItemStack drop : drops) level.addFreshEntity(new ItemEntity(level, at.x, at.y, at.z, drop));
    }

    private static boolean isBelt(BlockState state) {
        return AllBlocks.BELT.has(state);
    }
}
