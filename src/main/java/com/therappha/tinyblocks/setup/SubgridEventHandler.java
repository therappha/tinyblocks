package com.therappha.tinyblocks.setup;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.items.MinimizerItem;
import com.therappha.tinyblocks.items.TinyPieceItem;
import com.therappha.tinyblocks.subgrid.GridRay;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlock;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.FakeLevel;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.ScheduledTick;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.List;

@EventBusSubscriber(modid = TinyBlocks.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class SubgridEventHandler {

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof SubgridBlock)) return;
        event.setCanceled(true);

        ServerLevel level = (ServerLevel) event.getLevel();
        BlockPos pos = event.getPos();
        Player player = event.getPlayer();

        HitResult hit = player.pick(5.0, 0f, false);
        if (!(hit instanceof BlockHitResult bhr) || !bhr.getBlockPos().equals(pos)) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SubgridBlockEntity subgrid)) return;

        Vec3i cell = GridRay.cellAt(pos, bhr.getLocation(), bhr.getDirection(), subgrid.gridSize);
        PlacedPiece removed = subgrid.removePieceAt(cell.getX(), cell.getY(), cell.getZ());
        if (removed == null) return;

        BlockState renderState = removed.definition.renderState(removed);
        boolean correctTool = !removed.definition.requiresCorrectTool()
                || player.hasCorrectToolForDrops(renderState);
        if (correctTool && !player.isCreative()) {
            List<ItemStack> drops = removed.definition.drops(removed);
            double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
            for (ItemStack stack : drops) {
                level.addFreshEntity(new ItemEntity(level, cx, cy, cz, stack));
            }
        }

        if (subgrid.getPieces().isEmpty()) {
            level.removeBlock(pos, false);
        } else {
            // NeoForge sends a block resync packet after cancel which causes the client
            // to recreate the BE as empty. Schedule a tick 2 ticks out so notifyUpdate()
            // fires AFTER the client has already processed that resync.
            level.getBlockTicks().schedule(new ScheduledTick<>(event.getState().getBlock(), pos, level.getGameTime() + 2, 0L));
        }
    }

    /**
     * Phase C debug convenience (v2): holding any real BlockItem and right-clicking an existing
     * SubgridBlock places a real v2.VanillaBlockPiece using vanilla's own getStateForPlacement/
     * canSurvive logic, instead of needing a dedicated debug item per vanilla block. Holding the
     * Minimizer in the offhand additionally lets you right-click any normal block — a new
     * subgrid_block is created there first (mirroring TinyPieceItem.useOn's own else branch),
     * so you don't need an existing subgrid to reference before placing into one.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        Player player = event.getEntity();

        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof BlockItem blockItem) || blockItem.getBlock() instanceof SubgridBlock) return;

        BlockPos pos = event.getPos();
        BlockState clickedState = level.getBlockState(pos);
        boolean clickedIsSubgrid = clickedState.getBlock() instanceof SubgridBlock;
        MinimizerItem minimizer = player.getOffhandItem().getItem() instanceof MinimizerItem mi ? mi : null;
        if (!clickedIsSubgrid && minimizer == null) return;

        BlockHitResult hit = event.getHitVec();
        Direction face = hit.getDirection();

        // Decide whether we're intercepting this click using only checks that resolve
        // identically on both logical sides (read-only, no mutation yet) — needed so we can
        // cancel on the CLIENT too. Cancelling only server-side left the client's own
        // speculative "place the real block" prediction flashing before the correction landed.
        if (clickedIsSubgrid) {
            if (!(level.getBlockEntity(pos) instanceof SubgridBlockEntity be)) return;
            Vec3i clickedCell = GridRay.cellAt(pos, hit.getLocation(), face, be.gridSize);
            // If the exact cell being clicked already holds a piece, let vanilla's normal
            // interaction resolution run instead (e.g. flips a lever), unless sneaking.
            if (!player.isShiftKeyDown()
                    && be.getPieceAt(clickedCell.getX(), clickedCell.getY(), clickedCell.getZ()) != null) {
                return;
            }
            if (isOutOfBounds(clickedCell, face, be.gridSize)) {
                // A piece placed here would fall past this grid's edge — only intercept if the
                // adjacent real-world position is valid to continue the pillar into.
                BlockPos targetPos = pos.relative(face);
                BlockState targetState = level.getBlockState(targetPos);
                if (!targetState.isAir() && !(targetState.getBlock() instanceof SubgridBlock)) return;
            }
        } else {
            BlockPos targetPos = pos.relative(face);
            BlockState targetState = level.getBlockState(targetPos);
            if (!targetState.isAir() && !(targetState.getBlock() instanceof SubgridBlock)) return;
        }

        event.setCanceled(true);
        if (!(level instanceof ServerLevel serverLevel)) return;

        // --- Everything below is server-authoritative; recompute against live state. ---
        SubgridBlockEntity be;
        int nx, ny, nz;

        if (clickedIsSubgrid) {
            if (!(serverLevel.getBlockEntity(pos) instanceof SubgridBlockEntity existing)) return;
            Vec3i clickedCell = GridRay.cellAt(pos, hit.getLocation(), face, existing.gridSize);
            int max = existing.gridSize - 1;
            int cx = clickedCell.getX() + face.getStepX();
            int cy = clickedCell.getY() + face.getStepY();
            int cz = clickedCell.getZ() + face.getStepZ();

            if (cx >= 0 && cx <= max && cy >= 0 && cy <= max && cz >= 0 && cz <= max) {
                be = existing;
                nx = cx; ny = cy; nz = cz;
            } else {
                // Overflowed past this grid's edge — continue into the adjacent real-world
                // position, same size as the grid being left (needed for cross-grid propagation
                // to work at all — see SubgridBlockEntity.crossGridNeighborAt's gridSize check)
                // and same wrap convention it already uses for that boundary.
                BlockPos targetPos = pos.relative(face);
                BlockState targetState = serverLevel.getBlockState(targetPos);
                if (targetState.isAir()) {
                    serverLevel.setBlock(targetPos, subgridBlockFor(existing.gridSize).defaultBlockState(), Block.UPDATE_ALL);
                }
                if (!(serverLevel.getBlockEntity(targetPos) instanceof SubgridBlockEntity adjacent)) return;
                if (adjacent.gridSize != existing.gridSize) return;
                be = adjacent;
                int wmax = adjacent.gridSize - 1;
                nx = wrap(cx, wmax); ny = wrap(cy, wmax); nz = wrap(cz, wmax);
            }
        } else {
            BlockPos targetPos = pos.relative(face);
            BlockState targetState = serverLevel.getBlockState(targetPos);
            if (targetState.isAir()) {
                serverLevel.setBlock(targetPos, minimizer.preferredSubgrid().defaultBlockState(), Block.UPDATE_ALL);
            }
            if (!(serverLevel.getBlockEntity(targetPos) instanceof SubgridBlockEntity created)) return;
            be = created;
            int[] cell = TinyPieceItem.computeGridCell(clickedState, pos, face, hit.getLocation(), be.gridSize);
            nx = cell[0]; ny = cell[1]; nz = cell[2];
        }

        if (be.getPieceAt(nx, ny, nz) != null) return;

        BlockPos fakeAnchor = new BlockPos(nx, ny, nz);
        FakeLevel fakeLevel = VanillaBlockPiece.buildFakeSpace(be, serverLevel);
        BlockHitResult fakeHit = new BlockHitResult(Vec3.atCenterOf(fakeAnchor), face, fakeAnchor, hit.isInside());
        BlockPlaceContext placeCtx = new BlockPlaceContext(
                new UseOnContext(fakeLevel, player, event.getHand(), stack, fakeHit));

        BlockState state = blockItem.getBlock().getStateForPlacement(placeCtx);
        if (state == null || !state.canSurvive(fakeLevel, fakeAnchor)) return;

        PlacedPiece piece = new PlacedPiece(VanillaBlockPiece.INSTANCE, fakeAnchor, face);
        piece.runtimeState = state;
        if (be.placePiece(piece)) {
            state.getBlock().setPlacedBy(fakeLevel, fakeAnchor, state, player, stack);
            if (!player.isCreative()) stack.shrink(1);
            // Same NeoForge post-cancel resync race onBlockBreak/onLeftClickBlock already work
            // around: an immediate sendBlockUpdated wasn't enough on a freshly-created
            // SubgridBlock, so also schedule a delayed re-notify to land after that resync.
            BlockPos subgridPos = be.getBlockPos();
            serverLevel.sendBlockUpdated(subgridPos, serverLevel.getBlockState(subgridPos), serverLevel.getBlockState(subgridPos), Block.UPDATE_CLIENTS);
            serverLevel.getBlockTicks().schedule(new ScheduledTick<>(serverLevel.getBlockState(subgridPos).getBlock(), subgridPos, serverLevel.getGameTime() + 2, 0L));
        }
    }

    private static boolean isOutOfBounds(Vec3i clickedCell, Direction face, int gridSize) {
        int max = gridSize - 1;
        int cx = clickedCell.getX() + face.getStepX();
        int cy = clickedCell.getY() + face.getStepY();
        int cz = clickedCell.getZ() + face.getStepZ();
        return cx < 0 || cx > max || cy < 0 || cy > max || cz < 0 || cz > max;
    }

    private static int wrap(int v, int max) {
        if (v < 0) return max;
        if (v > max) return 0;
        return v;
    }

    private static SubgridBlock subgridBlockFor(int gridSize) {
        return switch (gridSize) {
            case 2 -> Registration.SUBGRID_BLOCK_2.get();
            case 4 -> Registration.SUBGRID_BLOCK_4.get();
            case 16 -> Registration.SUBGRID_BLOCK_16.get();
            default -> Registration.SUBGRID_BLOCK.get();
        };
    }

    /**
     * Phase C debug convenience (v2): holding a real BlockItem and left-clicking a piece inside
     * a SubgridBlock removes it instantly — skips destroyTime()/mining-progress entirely, for
     * fast placement/removal iteration while testing. The real hardness path (mining without a
     * block item in hand) is unaffected.
     */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        if (!(level.getBlockState(pos).getBlock() instanceof SubgridBlock)) return;

        Player player = event.getEntity();
        if (!(player.getMainHandItem().getItem() instanceof BlockItem)) return;
        if (!(level.getBlockEntity(pos) instanceof SubgridBlockEntity be)) return;

        HitResult hit = player.pick(5.0, 0f, false);
        if (!(hit instanceof BlockHitResult bhr) || !bhr.getBlockPos().equals(pos)) return;

        Vec3i cell = GridRay.cellAt(pos, bhr.getLocation(), bhr.getDirection(), be.gridSize);
        PlacedPiece removed = be.removePieceAt(cell.getX(), cell.getY(), cell.getZ());
        if (removed == null) return;

        event.setCanceled(true);
        if (!player.isCreative()) {
            List<ItemStack> drops = removed.definition.drops(removed);
            double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
            for (ItemStack drop : drops) {
                level.addFreshEntity(new ItemEntity(level, cx, cy, cz, drop));
            }
        }
        if (be.getPieces().isEmpty()) {
            level.removeBlock(pos, false);
        } else {
            // Same NeoForge post-cancel resync race onBlockBreak already works around: schedule
            // a tick 2 ticks out so notifyUpdate() fires AFTER the client has processed that
            // resync, instead of being stomped by it.
            level.getBlockTicks().schedule(new ScheduledTick<>(level.getBlockState(pos).getBlock(), pos, level.getGameTime() + 2, 0L));
        }
    }
}
