package com.therappha.tinyblocks.compat.create.client;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsInputHandler;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.v2.FakeSpace;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * A right click on a piece's value box starts Create's hold-to-edit on the client (ValueSettingsInputHandler), as
 * on a full-size block: the click is handed to Create's listener again, seen through VirtualView. Taking it here
 * keeps it from SubgridEventHandler, which would treat it as a click to place or use; the server still gets the
 * click and runs the piece's use as usual.
 */
@EventBusSubscriber(modid = TinyBlocks.MOD_ID, value = Dist.CLIENT)
public final class VirtualClick {

    private VirtualClick() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() || event.getLevel() instanceof FakeSpace) return;
        VirtualView.begin();
        try {
            if (!VirtualView.active() || !(Minecraft.getInstance().hitResult instanceof BlockHitResult hit)) return;
            PlayerInteractEvent.RightClickBlock inside =
                    new PlayerInteractEvent.RightClickBlock(event.getEntity(), event.getHand(), hit.getBlockPos(), hit);
            ValueSettingsInputHandler.onBlockActivated(inside);
            if (inside.isCanceled()) {
                event.setCanceled(true);
                event.setCancellationResult(inside.getCancellationResult());
            }
        } finally {
            VirtualView.end();
        }
    }
}
