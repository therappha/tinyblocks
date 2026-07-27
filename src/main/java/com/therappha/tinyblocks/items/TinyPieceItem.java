package com.therappha.tinyblocks.items;

import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.subgrid.PieceDefinition;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlock;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public abstract class TinyPieceItem extends Item {

    public TinyPieceItem(Properties properties) {
        super(properties);
    }

    public abstract PieceDefinition pieceDefinition();

    /** Override to true for pieces that benefit from a ghost placement preview. */
    public boolean showPreview() { return false; }

    /** Which SubgridBlock variant to create when placing into air. */
    public SubgridBlock preferredSubgrid() { return Registration.SUBGRID_BLOCK.get(); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        Direction face = context.getClickedFace();
        Player player = context.getPlayer();
        Vec3 hit = context.getClickLocation();

        BlockPos targetPos;
        int gx, gy, gz;

        if (clickedState.getBlock() instanceof SubgridBlock clickedSB) {
            int gs = gridSizeAt(level, clickedPos, clickedSB);
            int max = gs - 1;
            double nudge = 0.5 / gs;
            int hx = (int)(((hit.x - clickedPos.getX()) - face.getStepX() * nudge) * gs);
            int hy = (int)(((hit.y - clickedPos.getY()) - face.getStepY() * nudge) * gs);
            int hz = (int)(((hit.z - clickedPos.getZ()) - face.getStepZ() * nudge) * gs);
            int nx = hx + face.getStepX();
            int ny = hy + face.getStepY();
            int nz = hz + face.getStepZ();

            if (nx >= 0 && nx < gs && ny >= 0 && ny < gs && nz >= 0 && nz < gs) {
                targetPos = clickedPos;
                gx = nx; gy = ny; gz = nz;
            } else {
                targetPos = clickedPos.relative(face);
                BlockState targetState = level.getBlockState(targetPos);
                if (!targetState.isAir() && !(targetState.getBlock() instanceof SubgridBlock)) {
                    return InteractionResult.PASS;
                }
                gx = nx < 0 ? max : nx > max ? 0 : Mth.clamp(nx, 0, max);
                gy = ny < 0 ? max : ny > max ? 0 : Mth.clamp(ny, 0, max);
                gz = nz < 0 ? max : nz > max ? 0 : Mth.clamp(nz, 0, max);
            }
        } else {
            targetPos = clickedPos.relative(face);
            BlockState targetState = level.getBlockState(targetPos);
            if (!targetState.isAir() && !(targetState.getBlock() instanceof SubgridBlock)) {
                return InteractionResult.PASS;
            }
            int gs = targetState.getBlock() instanceof SubgridBlock tsb
                ? gridSizeAt(level, targetPos, tsb)
                : preferredSubgrid().gridSize;
            int[] grid = computeGridCell(clickedState, clickedPos, face, hit, gs);
            gx = grid[0]; gy = grid[1]; gz = grid[2];
        }

        PlacedPiece piece = new PlacedPiece(pieceDefinition(), new BlockPos(gx, gy, gz), face.getAxis());

        if (!level.isClientSide) {
            BlockState targetState = level.getBlockState(targetPos);
            if (targetState.isAir()) {
                level.setBlock(targetPos, preferredSubgrid().defaultBlockState(), Block.UPDATE_ALL);
            }
            BlockEntity be = level.getBlockEntity(targetPos);
            if (be instanceof SubgridBlockEntity subgrid && subgrid.placePiece(piece)) {
                if (player != null && !player.isCreative()) context.getItemInHand().shrink(1);
                level.sendBlockUpdated(targetPos, targetState, level.getBlockState(targetPos), Block.UPDATE_CLIENTS);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static int gridSizeAt(Level level, BlockPos pos, SubgridBlock block) {
        return level.getBlockEntity(pos) instanceof SubgridBlockEntity be ? be.gridSize : block.gridSize;
    }

    public static int[] computeGridCell(BlockState clickedState, BlockPos clickedPos,
                                         Direction face, Vec3 hit, int gs) {
        int max = gs - 1;
        double nudge = 0.5 / gs;

        if (clickedState.getBlock() instanceof SubgridBlock) {
            double hxf = (hit.x - clickedPos.getX()) - face.getStepX() * nudge;
            double hyf = (hit.y - clickedPos.getY()) - face.getStepY() * nudge;
            double hzf = (hit.z - clickedPos.getZ()) - face.getStepZ() * nudge;
            int hx = Mth.clamp((int)(hxf * gs), 0, max);
            int hy = Mth.clamp((int)(hyf * gs), 0, max);
            int hz = Mth.clamp((int)(hzf * gs), 0, max);
            return new int[]{
                Mth.clamp(hx + face.getStepX(), 0, max),
                Mth.clamp(hy + face.getStepY(), 0, max),
                Mth.clamp(hz + face.getStepZ(), 0, max)
            };
        } else {
            BlockPos targetPos = clickedPos.relative(face);
            double hxf = (hit.x - targetPos.getX()) - face.getStepX() * nudge;
            double hyf = (hit.y - targetPos.getY()) - face.getStepY() * nudge;
            double hzf = (hit.z - targetPos.getZ()) - face.getStepZ() * nudge;
            return new int[]{
                Mth.clamp((int)(hxf * gs), 0, max),
                Mth.clamp((int)(hyf * gs), 0, max),
                Mth.clamp((int)(hzf * gs), 0, max)
            };
        }
    }
}
