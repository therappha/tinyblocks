package com.therappha.tinyblocks.setup;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlock;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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

        Vec3 hitLoc = bhr.getLocation();
        Direction face = bhr.getDirection();
        int gs = subgrid.gridSize;
        int max = gs - 1;
        double nudge = 0.5 / gs;
        int gx = Mth.clamp((int)(((hitLoc.x - pos.getX()) - face.getStepX() * nudge) * gs), 0, max);
        int gy = Mth.clamp((int)(((hitLoc.y - pos.getY()) - face.getStepY() * nudge) * gs), 0, max);
        int gz = Mth.clamp((int)(((hitLoc.z - pos.getZ()) - face.getStepZ() * nudge) * gs), 0, max);

        PlacedPiece removed = subgrid.removePieceAt(gx, gy, gz);
        if (removed == null) return;

        BlockState renderState = removed.definition.renderState(removed.axis);
        boolean correctTool = !removed.definition.requiresCorrectTool()
                || player.hasCorrectToolForDrops(renderState);
        if (correctTool) {
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
