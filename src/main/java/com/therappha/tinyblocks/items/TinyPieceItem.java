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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
        int gs;
        int gx, gy, gz;

        if (clickedState.getBlock() instanceof SubgridBlock clickedSB) {
            gs = gridSizeAt(level, clickedPos, clickedSB);
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
                if (targetState.getBlock() instanceof SubgridBlock) {
                    // Same-size gate: matches SubgridBlockEntity.resolve/resolveOrCreate's
                    // gridSize-match check everywhere else in the engine — a mismatched-size
                    // neighbor refuses the cross, it doesn't clamp into whatever range fits.
                    if (gridSizeAt(level, targetPos, (SubgridBlock) targetState.getBlock()) != gs) {
                        return InteractionResult.PASS;
                    }
                } else if (!targetState.isAir()) {
                    return InteractionResult.PASS;
                }
                gx = SubgridBlockEntity.wrap(nx, max);
                gy = SubgridBlockEntity.wrap(ny, max);
                gz = SubgridBlockEntity.wrap(nz, max);
            }
        } else {
            targetPos = clickedPos.relative(face);
            BlockState targetState = level.getBlockState(targetPos);
            if (!targetState.isAir() && !(targetState.getBlock() instanceof SubgridBlock)) {
                return InteractionResult.PASS;
            }
            gs = targetState.getBlock() instanceof SubgridBlock tsb
                ? gridSizeAt(level, targetPos, tsb)
                : preferredSubgrid().gridSize;
            int[] grid = computeGridCell(clickedPos, face, hit, gs);
            gx = grid[0]; gy = grid[1]; gz = grid[2];
        }

        PlacedPiece piece = new PlacedPiece(pieceDefinition(), new BlockPos(gx, gy, gz), face);

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            BlockState beforeState = level.getBlockState(targetPos);
            SubgridBlockEntity subgrid = SubgridBlockEntity.createAt(serverLevel, targetPos, gs);
            if (subgrid != null && subgrid.placePiece(piece)) {
                if (player != null && !player.isCreative()) context.getItemInHand().shrink(1);
                level.sendBlockUpdated(targetPos, beforeState, level.getBlockState(targetPos), Block.UPDATE_CLIENTS);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static int gridSizeAt(Level level, BlockPos pos, SubgridBlock block) {
        return level.getBlockEntity(pos) instanceof SubgridBlockEntity be ? be.gridSize : block.gridSize;
    }

    /**
     * Which cell of a gs-sized grid at clickedPos.relative(face) the ray hit — for placing
     * against a block that ISN'T itself a subgrid (a fresh subgrid about to be created there, or
     * an existing one of a different origin than the block actually clicked). Every caller only
     * ever invokes this when clickedPos's own block is confirmed NOT a SubgridBlock; a click
     * that overflows an existing subgrid's own bounds is resolved separately, via
     * SubgridBlockEntity.resolve/resolveOrCreate's gridSize-match + wrap (see useOn above).
     */
    public static int[] computeGridCell(BlockPos clickedPos, Direction face, Vec3 hit, int gs) {
        int max = gs - 1;
        double nudge = 0.5 / gs;
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
