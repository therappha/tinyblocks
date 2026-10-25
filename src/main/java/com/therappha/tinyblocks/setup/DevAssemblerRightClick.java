package com.therappha.tinyblocks.setup;

import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.lang.reflect.Method;

/**
 * EXPERIMENT / dev convenience: the Simulated physics assembler is normally started by dragging its lever
 * with the mouse. An empty-handed right click on it calls its public {@code assembleOrDisassemble()} instead,
 * so assembly can be tested without that gesture. Found by reflection on purpose: nothing else in this mod
 * is compiled against Simulated, and a missing class just means the handler never matches.
 */
@EventBusSubscriber(modid = TinyBlocks.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class DevAssemblerRightClick {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String ASSEMBLER_BLOCK_ENTITY = "PhysicsAssemblerBlockEntity";

    private DevAssemblerRightClick() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!event.getEntity().getMainHandItem().isEmpty()) return;
        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (be == null || !be.getClass().getSimpleName().equals(ASSEMBLER_BLOCK_ENTITY)) return;
        try {
            Method assemble = be.getClass().getMethod("assembleOrDisassemble");
            assemble.invoke(be);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("physics assembler right click failed", e);
        }
    }
}
