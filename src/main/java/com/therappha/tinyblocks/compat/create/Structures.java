package com.therappha.tinyblocks.compat.create;

import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Which of Create's multiblocks (a fluid tank, an item vault) a block is part of, told the same way in the world and in
 * a grid: its controller relative to itself, and the structure's size. Positions are relative so that a grid's virtual
 * frame and the world's compare alike.
 */
public final class Structures {

    private Structures() {}

    /** "controller +0,-1,+0 of 2x2x3" (width x width x length along its axis), or "no controller". */
    public static String of(BlockEntity be, IMultiBlockEntityContainer part) {
        BlockPos controller = part.getController();
        if (controller == null) return "no controller";
        BlockPos offset = controller.subtract(be.getBlockPos());
        IMultiBlockEntityContainer head = controllerOf(be, part);
        String size = head == null ? "controller missing"
                : head.getWidth() + "x" + head.getWidth() + "x" + head.getHeight() + " along " + head.getMainConnectionAxis();
        return "controller " + signed(offset.getX()) + "," + signed(offset.getY()) + "," + signed(offset.getZ()) + " of " + size;
    }

    /**
     * Every cell of the structure {@code be} is part of, walked as Create walks it (ConnectivityHandler), and whether
     * the block there names the same controller: the lines a player reads to see whether what looks like one tank is
     * one. Looked up through {@code be}'s own level, so a phantom bound to its grid finds the parts in the grids next
     * to it as Create itself does.
     */
    public static List<String> report(BlockEntity be) {
        List<String> lines = new ArrayList<>();
        if (!(be instanceof IMultiBlockEntityContainer part)) {
            lines.add("not part of a Create multiblock");
            return lines;
        }
        lines.add("this block: " + of(be, part));
        IMultiBlockEntityContainer head = controllerOf(be, part);
        if (head == null || !(head instanceof BlockEntity headBe)) return lines;
        BlockPos origin = headBe.getBlockPos();
        Direction.Axis axis = head.getMainConnectionAxis();
        int total = 0, agreeing = 0;
        for (int along = 0; along < head.getHeight(); along++) {
            for (int a = 0; a < head.getWidth(); a++) {
                for (int b = 0; b < head.getWidth(); b++) {
                    BlockPos pos = switch (axis) {
                        case X -> origin.offset(along, a, b);
                        case Y -> origin.offset(a, along, b);
                        case Z -> origin.offset(a, b, along);
                    };
                    total++;
                    BlockEntity there = be.getLevel().getBlockEntity(pos);
                    BlockPos rel = pos.subtract(origin);
                    String at = signed(rel.getX()) + "," + signed(rel.getY()) + "," + signed(rel.getZ());
                    if (there instanceof IMultiBlockEntityContainer other && origin.equals(other.getController())) {
                        agreeing++;
                    } else if (there instanceof IMultiBlockEntityContainer other) {
                        lines.add("  part " + at + " from the controller names another: " + of(there, other));
                    } else {
                        lines.add("  part " + at + " from the controller is missing (" + (there == null ? "nothing there" : there.getType()) + ")");
                    }
                }
            }
        }
        lines.add(agreeing == total ? "one structure: all " + total + " parts name the same controller"
                : agreeing + " of " + total + " parts name this controller");
        return lines;
    }

    @Nullable
    private static IMultiBlockEntityContainer controllerOf(BlockEntity be, IMultiBlockEntityContainer part) {
        if (part.isController()) return part;
        Level level = be.getLevel();
        BlockPos controller = part.getController();
        if (level == null || controller == null) return null;
        return level.getBlockEntity(controller) instanceof IMultiBlockEntityContainer head ? head : null;
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : Integer.toString(value);
    }
}
