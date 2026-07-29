package com.therappha.tinyblocks.setup;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.GridRay;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlock;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.ticks.ScheduledTick;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
}
