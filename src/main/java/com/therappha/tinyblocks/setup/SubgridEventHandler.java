package com.therappha.tinyblocks.setup;

import com.therappha.tinyblocks.TinyBlocks;
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
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Player player = event.getEntity();

        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof BlockItem blockItem) || blockItem.getBlock() instanceof SubgridBlock) return;

        BlockPos pos = event.getPos();
        BlockState clickedState = level.getBlockState(pos);
        boolean clickedIsSubgrid = clickedState.getBlock() instanceof SubgridBlock;
        boolean minimizerActive = player.getOffhandItem().getItem() == Registration.MINIMIZER.get();
        if (!clickedIsSubgrid && !minimizerActive) return;

        BlockHitResult hit = event.getHitVec();
        Direction face = hit.getDirection();

        SubgridBlockEntity be;
        int nx, ny, nz;

        if (clickedIsSubgrid) {
            if (!(level.getBlockEntity(pos) instanceof SubgridBlockEntity existing)) return;
            be = existing;
            Vec3i clickedCell = GridRay.cellAt(pos, hit.getLocation(), face, be.gridSize);
            int max = be.gridSize - 1;
            nx = clickedCell.getX() + face.getStepX();
            ny = clickedCell.getY() + face.getStepY();
            nz = clickedCell.getZ() + face.getStepZ();
            // Only placing additional pieces inside this existing SubgridBlock is in scope
            // here — targeting past the grid edge (a new block, or a neighboring SubgridBlock)
            // is not, even with the Minimizer active.
            if (nx < 0 || nx > max || ny < 0 || ny > max || nz < 0 || nz > max) return;
        } else {
            BlockPos targetPos = pos.relative(face);
            BlockState targetState = level.getBlockState(targetPos);
            if (!targetState.isAir() && !(targetState.getBlock() instanceof SubgridBlock)) return;
            if (targetState.isAir()) {
                level.setBlock(targetPos, Registration.SUBGRID_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
            }
            if (!(level.getBlockEntity(targetPos) instanceof SubgridBlockEntity created)) return;
            be = created;
            int[] cell = TinyPieceItem.computeGridCell(clickedState, pos, face, hit.getLocation(), be.gridSize);
            nx = cell[0]; ny = cell[1]; nz = cell[2];
        }

        if (be.getPieceAt(nx, ny, nz) != null) return;

        event.setCanceled(true);
        BlockPos fakeAnchor = new BlockPos(nx, ny, nz);

        FakeLevel fakeLevel = VanillaBlockPiece.buildFakeSpace(be, level);
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
        }
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
        }
    }
}
