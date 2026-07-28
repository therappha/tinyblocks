package com.therappha.tinyblocks.subgrid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class GridRay {

    private GridRay() {}

    /** Grid cell targeted by a hit on the given face of a SubgridBlock. */
    public static Vec3i cellAt(BlockPos pos, Vec3 hitLoc, Direction face, int gridSize) {
        int max = gridSize - 1;
        double nudge = 0.5 / gridSize;
        int gx = Mth.clamp((int) (((hitLoc.x - pos.getX()) - face.getStepX() * nudge) * gridSize), 0, max);
        int gy = Mth.clamp((int) (((hitLoc.y - pos.getY()) - face.getStepY() * nudge) * gridSize), 0, max);
        int gz = Mth.clamp((int) (((hitLoc.z - pos.getZ()) - face.getStepZ() * nudge) * gridSize), 0, max);
        return new Vec3i(gx, gy, gz);
    }
}
