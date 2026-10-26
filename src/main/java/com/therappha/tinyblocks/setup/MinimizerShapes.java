package com.therappha.tinyblocks.setup;

import com.therappha.tinyblocks.items.BuildMode;
import com.therappha.tinyblocks.items.MinimizerBuild;
import com.therappha.tinyblocks.items.MinimizerItem;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.BlockAccess;
import com.therappha.tinyblocks.v2.FakeLevel;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.ticks.ScheduledTick;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A Minimizer in a shape mode (BuildMode other than PLACE): the first click marks a corner, the second builds the
 * shape between the two with the block in the main hand — across as many blocks as it spans, making grids of the
 * corner's size where there is air. A sneaking click drops the corner.
 */
public final class MinimizerShapes {

    /** The most cells one shape may have. */
    public static final int MAX_CELLS = 8192;

    private MinimizerShapes() {}

    public static void click(ServerLevel level, Player player, ItemStack minimizer, MinimizerItem minimizerItem,
                             BlockItem blockItem, ItemStack stack, BlockHitResult hit) {
        BuildMode mode = MinimizerBuild.mode(minimizer);
        if (player.isShiftKeyDown()) {
            MinimizerBuild.setCorner(minimizer, null);
            player.displayClientMessage(Component.translatable("tinyblocks.build.corner_cleared"), true);
            return;
        }
        MinimizerBuild.Target target = MinimizerBuild.target(level, hit, minimizer);
        if (target == null) return;
        MinimizerBuild.Corner corner = MinimizerBuild.corner(minimizer);
        if (corner == null || corner.gridSize() != target.gridSize()) {
            MinimizerBuild.setCorner(minimizer, new MinimizerBuild.Corner(target.cell(), target.gridSize(), hit.getDirection().getAxis()));
            player.displayClientMessage(Component.translatable("tinyblocks.build.corner_set",
                    Component.translatable(mode.translationKey())), true);
            return;
        }

        List<BlockPos> cells = mode.cells(corner.cell(), MinimizerBuild.secondCorner(minimizer, target), corner.faceAxis(), MAX_CELLS);
        if (cells.size() > MAX_CELLS) {
            player.displayClientMessage(Component.translatable("tinyblocks.build.too_big", MAX_CELLS), true);
            return;
        }
        BlockState state = blockItem.getBlock().getStateForPlacement(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
        if (state == null) state = blockItem.getBlock().defaultBlockState();

        int size = target.gridSize();
        int placed = 0;
        Set<SubgridBlockEntity> touched = new LinkedHashSet<>();
        for (BlockPos fine : cells) {
            if (!player.isCreative() && stack.isEmpty()) break;
            BlockPos real = MinimizerBuild.realOf(fine, size);
            BlockPos cell = MinimizerBuild.cellOf(fine, size);
            SubgridBlockEntity grid = level.getBlockEntity(real) instanceof SubgridBlockEntity existing ? existing
                    : level.getBlockState(real).isAir() ? SubgridBlockEntity.createAt(level, real, size) : null;
            if (grid == null || grid.getGridSize() != size || grid.getPieceAt(cell.getX(), cell.getY(), cell.getZ()) != null) continue;

            PlacedPiece piece = new PlacedPiece(VanillaBlockPiece.INSTANCE, cell, hit.getDirection());
            piece.runtimeState = state;
            if (!grid.placePiece(piece)) continue;
            // As one placed by hand (SubgridEventHandler): told it was placed, its first ticks scheduled.
            FakeLevel space = VanillaBlockPiece.buildFakeSpace(grid, level);
            BlockAccess.onPlace(state, space, space.world(cell), Blocks.AIR.defaultBlockState(), false);
            VanillaBlockPiece.scheduleCaptured(grid, space, level.getGameTime());
            touched.add(grid);
            placed++;
            if (!player.isCreative()) stack.shrink(1);
        }
        for (SubgridBlockEntity grid : touched) {
            BlockPos pos = grid.getBlockPos();
            grid.notifyUpdate();
            // The same delayed resync a hand placement schedules, for grids created just now.
            level.getBlockTicks().schedule(new ScheduledTick<>(level.getBlockState(pos).getBlock(), pos, level.getGameTime() + 2, 0L));
        }
        MinimizerBuild.setCorner(minimizer, null);
        if (placed > 0) {
            SoundType sound = state.getSoundType();
            level.playSound(null, hit.getBlockPos(), sound.getPlaceSound(), SoundSource.BLOCKS,
                    (sound.getVolume() + 1f) / 2f, sound.getPitch() * 0.8f);
        }
        player.displayClientMessage(Component.translatable("tinyblocks.build.placed", placed), true);
    }
}
