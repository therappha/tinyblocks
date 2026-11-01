package com.therappha.tinyblocks.subgrid;

import com.therappha.tinyblocks.items.MinimizerItem;
import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a grid into one of another size, looking exactly the same: 1/8 to 1/16 makes each cell eight, and back merges
 * them. So the sizes must divide one another, every piece must be a full plain block (a stair repeated eight times isn't
 * that stair any more, and a chest can't be split), and going smaller needs each group of cells whole and of one block.
 * Anything else is refused, naming what stands in the way, and the grid is left as it was.
 */
public final class GridResize {

    private GridResize() {}

    /** {@code key} is the message for the player, {@code args} what goes in it. */
    public record Result(boolean done, String key, Object... args) {
        Component message() {
            return Component.translatable("tinyblocks.resize." + key, args);
        }
    }

    private static Result refused(String key, Object... args) {
        return new Result(false, key, args);
    }

    /** The resize key: the grid under the crosshair, to the size of the Minimizer in hand. */
    public static void resizeLookedAt(Player player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        ItemStack held = player.getMainHandItem().getItem() instanceof MinimizerItem ? player.getMainHandItem() : player.getOffhandItem();
        HitResult hit = player.pick(5.0, 0f, false);
        if (!(held.getItem() instanceof MinimizerItem minimizer) || !(hit instanceof BlockHitResult block)
                || !(level.getBlockEntity(block.getBlockPos()) instanceof SubgridBlockEntity be)
                || !com.therappha.tinyblocks.items.MinimizerItem.mayBreak(player, level, block.getBlockPos())) {
            player.displayClientMessage(Component.translatable("tinyblocks.resize.no_grid"), true);
            return;
        }
        player.displayClientMessage(resize(level, be, minimizer.gridSize(held)).message(), true);
    }

    public static Result resize(ServerLevel level, SubgridBlockEntity be, int size) {
        int from = be.getGridSize();
        if (size == from) return refused("same", size);
        if (size < Registration.MIN_GRID_SIZE || size > Registration.MAX_GRID_SIZE
                || (size % from != 0 && from % size != 0)) return refused("not_a_multiple", from, size);
        if (be.hasActiveCaptures() || be.pendingDropCount() > 0) return refused("moving");

        Map<BlockPos, BlockState> cells = new HashMap<>();
        Map<BlockPos, Direction> facings = new HashMap<>();
        for (PlacedPiece piece : be.getPieces()) {
            if (!plain(piece)) return refused("not_plain", name(piece));
            cells.put(piece.anchor, (BlockState) piece.runtimeState);
            facings.put(piece.anchor, piece.facing);
        }

        List<PlacedPiece> pieces = new ArrayList<>();
        if (size > from) {
            int k = size / from;
            cells.forEach((cell, state) -> {
                for (BlockPos at : BlockPos.betweenClosed(cell.multiply(k), cell.multiply(k).offset(k - 1, k - 1, k - 1))) {
                    pieces.add(piece(at.immutable(), state, facings.get(cell)));
                }
            });
        } else {
            int k = from / size;
            Map<BlockPos, List<BlockPos>> groups = new HashMap<>();
            cells.keySet().forEach(cell -> groups.computeIfAbsent(new BlockPos(cell.getX() / k, cell.getY() / k, cell.getZ() / k),
                    g -> new ArrayList<>()).add(cell));
            for (Map.Entry<BlockPos, List<BlockPos>> group : groups.entrySet()) {
                BlockState state = cells.get(group.getValue().get(0));
                boolean whole = group.getValue().size() == k * k * k
                        && group.getValue().stream().allMatch(cell -> cells.get(cell) == state);
                if (!whole) return refused("not_uniform", group.getKey().toShortString());
                pieces.add(piece(group.getKey(), state, facings.get(group.getValue().get(0))));
            }
        }

        BlockPos pos = be.getBlockPos();
        be.emptyQuietly(); // nothing left for the SubgridBlock going away to drop
        level.setBlock(pos, Registration.subgridBlock(size).defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof SubgridBlockEntity grid)) throw new IllegalStateException("no grid at " + pos);
        grid.addQuietly(pieces);
        grid.lookAroundMoved();
        grid.notifyUpdate();
        return new Result(true, "done", size);
    }

    private static boolean plain(PlacedPiece piece) {
        return piece.definition == VanillaBlockPiece.INSTANCE && piece.runtimeState instanceof BlockState state
                && !state.hasBlockEntity()
                && Block.isShapeFullBlock(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
    }

    private static String name(PlacedPiece piece) {
        return piece.runtimeState instanceof BlockState state ? BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath() : piece.definition.id().getPath();
    }

    private static PlacedPiece piece(BlockPos cell, BlockState state, Direction facing) {
        PlacedPiece piece = new PlacedPiece(VanillaBlockPiece.INSTANCE, cell, facing);
        piece.runtimeState = state;
        return piece;
    }
}
