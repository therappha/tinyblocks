package com.therappha.tinyblocks.items;

import com.therappha.tinyblocks.subgrid.GridRay;
import com.therappha.tinyblocks.subgrid.SubgridBlock;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * A Minimizer's build mode and the first corner it is holding, kept on the Minimizer stack itself (custom data, so
 * the client has it too, for the preview). Cells are fine positions: see {@link BuildMode}.
 */
public final class MinimizerBuild {

    private static final String KEY = "tinyblocks_build";

    /** The first corner of a shape: its fine cell, the grid size it was picked in, and the axis of the clicked face. */
    public record Corner(BlockPos cell, int gridSize, Direction.Axis faceAxis) {}

    /** Where a click places: the fine cell a piece would go in, and that grid's size. */
    public record Target(BlockPos cell, int gridSize) {}

    private MinimizerBuild() {}

    public static BuildMode mode(ItemStack minimizer) {
        int ordinal = tag(minimizer).getInt("mode");
        BuildMode[] modes = BuildMode.values();
        return ordinal >= 0 && ordinal < modes.length ? modes[ordinal] : BuildMode.PLACE;
    }

    /** Switches to the next mode; any corner held is dropped. */
    public static BuildMode cycle(ItemStack minimizer) {
        BuildMode next = mode(minimizer).next();
        CustomData.update(DataComponents.CUSTOM_DATA, minimizer, all -> {
            CompoundTag tag = all.getCompound(KEY);
            tag.putInt("mode", next.ordinal());
            tag.remove("corner");
            all.put(KEY, tag);
        });
        return next;
    }

    @Nullable
    public static Corner corner(ItemStack minimizer) {
        CompoundTag tag = tag(minimizer);
        if (!tag.contains("corner")) return null;
        CompoundTag c = tag.getCompound("corner");
        return new Corner(new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z")), c.getInt("size"),
                Direction.Axis.values()[Math.floorMod(c.getInt("axis"), 3)]);
    }

    public static void setCorner(ItemStack minimizer, @Nullable Corner corner) {
        CustomData.update(DataComponents.CUSTOM_DATA, minimizer, all -> {
            CompoundTag tag = all.getCompound(KEY);
            if (corner == null) {
                tag.remove("corner");
            } else {
                CompoundTag c = new CompoundTag();
                c.putInt("x", corner.cell().getX());
                c.putInt("y", corner.cell().getY());
                c.putInt("z", corner.cell().getZ());
                c.putInt("size", corner.gridSize());
                c.putInt("axis", corner.faceAxis().ordinal());
                tag.put("corner", c);
            }
            all.put(KEY, tag);
        });
    }

    private static CompoundTag tag(ItemStack minimizer) {
        return minimizer.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(KEY);
    }

    /**
     * The fine cell a block placed by this click goes in — next to the clicked piece across the clicked face, or, on
     * a real block, in a new grid of the Minimizer's size against it — or null where nothing would be placed.
     */
    @Nullable
    public static Target target(Level level, BlockHitResult hit, MinimizerItem minimizer) {
        BlockPos pos = hit.getBlockPos();
        Direction face = hit.getDirection();
        BlockState clicked = level.getBlockState(pos);
        if (clicked.getBlock() instanceof SubgridBlock block) {
            int size = level.getBlockEntity(pos) instanceof SubgridBlockEntity be ? be.getGridSize() : block.gridSize;
            Vec3i cell = GridRay.cellAt(pos, hit.getLocation(), face, size);
            return new Target(fine(pos, size).offset(cell).relative(face), size);
        }
        BlockPos into = pos.relative(face);
        BlockState there = level.getBlockState(into);
        int size;
        if (there.getBlock() instanceof SubgridBlock block) {
            size = level.getBlockEntity(into) instanceof SubgridBlockEntity be ? be.getGridSize() : block.gridSize;
        } else if (there.isAir()) {
            size = minimizer.preferredSubgrid().gridSize;
        } else {
            return null;
        }
        int[] cell = TinyPieceItem.computeGridCell(pos, face, hit.getLocation(), size);
        return new Target(fine(into, size).offset(cell[0], cell[1], cell[2]), size);
    }

    /** The fine cell of cell (0,0,0) of a grid of {@code size} at real position {@code real}. */
    public static BlockPos fine(BlockPos real, int size) {
        return new BlockPos(real.getX() * size, real.getY() * size, real.getZ() * size);
    }

    /** The real block a fine cell is in. */
    public static BlockPos realOf(BlockPos fine, int size) {
        return new BlockPos(Math.floorDiv(fine.getX(), size), Math.floorDiv(fine.getY(), size), Math.floorDiv(fine.getZ(), size));
    }

    /** The cell inside its real block a fine cell is. */
    public static BlockPos cellOf(BlockPos fine, int size) {
        return new BlockPos(Math.floorMod(fine.getX(), size), Math.floorMod(fine.getY(), size), Math.floorMod(fine.getZ(), size));
    }
}
