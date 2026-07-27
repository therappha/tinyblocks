package com.therappha.tinyblocks.client;

import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlock;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Arrays;

@OnlyIn(Dist.CLIENT)
public final class MiningTracker {

    private static BlockPos currentPos = null;
    private static int[] currentCell = null;
    private static float progress = 0f;

    private MiningTracker() {}

    /** Returns crack stage 0-9, or -1 if this block is not being mined. */
    public static int getStage(BlockPos pos) {
        if (currentCell == null || !pos.equals(currentPos) || progress <= 0f) return -1;
        return Math.min(9, (int) (progress * 10));
    }

    /** Returns {gx, gy, gz} of the cell being mined, or null. */
    public static int[] getCell() { return currentCell; }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { reset(); return; }
        if (!(mc.hitResult instanceof BlockHitResult bhr)) { reset(); return; }

        BlockPos pos = bhr.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (!(state.getBlock() instanceof SubgridBlock)) { reset(); return; }
        if (!(mc.level.getBlockEntity(pos) instanceof SubgridBlockEntity be)) { reset(); return; }

        Vec3 hitLoc = bhr.getLocation();
        Direction face = bhr.getDirection();
        int gs = be.gridSize;
        int max = gs - 1;
        double nudge = 0.5 / gs;
        int gx = Mth.clamp((int)(((hitLoc.x - pos.getX()) - face.getStepX() * nudge) * gs), 0, max);
        int gy = Mth.clamp((int)(((hitLoc.y - pos.getY()) - face.getStepY() * nudge) * gs), 0, max);
        int gz = Mth.clamp((int)(((hitLoc.z - pos.getZ()) - face.getStepZ() * nudge) * gs), 0, max);
        int[] cell = {gx, gy, gz};

        // Reset progress if target changed
        if (!pos.equals(currentPos) || !Arrays.equals(cell, currentCell)) {
            currentPos = pos.immutable();
            currentCell = cell;
            progress = 0f;
        }

        PlacedPiece piece = be.getPieceAt(gx, gy, gz);
        if (piece == null) { progress = 0f; return; }

        float speed = destroySpeed(mc.player, piece, pos);
        if (mc.options.keyAttack.isDown()) {
            progress = Math.min(1f, progress + speed);
        } else {
            progress = Math.max(0f, progress - speed * 2f);
        }
    }

    private static float destroySpeed(Player player, PlacedPiece piece, BlockPos pos) {
        float hardness = piece.definition.destroyTime();
        if (hardness <= 0f) return 0f;
        BlockState renderState = piece.definition.renderState(piece.axis);
        float digSpeed = player.getDigSpeed(renderState, pos);
        boolean correct = !piece.definition.requiresCorrectTool() || player.hasCorrectToolForDrops(renderState);
        return digSpeed / hardness / (correct ? 30f : 100f);
    }

    private static void reset() {
        currentPos = null;
        currentCell = null;
        progress = 0f;
    }
}
