package com.therappha.tinyblocks.compat.create.client;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsScreen;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.items.MinimizerItem;
import com.therappha.tinyblocks.network.MinimizerSizePayload;
import com.therappha.tinyblocks.setup.Registration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The Minimizer's grid size, picked on Create's value board: a right click in the air with the Minimizer in the main
 * hand opens it, releasing the button on a size picks it (as a Create value box held down does). The board's
 * values 0..14 are the sizes 2..16.
 */
@EventBusSubscriber(modid = TinyBlocks.MOD_ID, value = Dist.CLIENT)
public final class MinimizerSizeMenu {

    private MinimizerSizeMenu() {}

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) return;
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof MinimizerItem item)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        Minecraft mc = Minecraft.getInstance();
        int max = Registration.MAX_GRID_SIZE - Registration.MIN_GRID_SIZE;
        ValueSettingsBoard board = new ValueSettingsBoard(Component.translatable("tinyblocks.minimizer.size_board"), max, 2,
                List.of(Component.translatable("tinyblocks.minimizer.size_row")),
                new ValueSettingsFormatter(v -> Component.literal("1/" + (v.value() + Registration.MIN_GRID_SIZE))));
        ValueSettings current = new ValueSettings(0, item.gridSize(stack) - Registration.MIN_GRID_SIZE);
        mc.setScreen(new Screen(board, current));
    }

    /** Create's value board, sending the size picked to the Minimizer instead of a block's value. */
    private static final class Screen extends ValueSettingsScreen {
        Screen(ValueSettingsBoard board, ValueSettings current) {
            super(Minecraft.getInstance().player.blockPosition(), board, current, v -> {}, 0);
        }

        @Override
        protected void saveAndClose(double mouseX, double mouseY) {
            ValueSettings picked = getClosestCoordinate((int) mouseX, (int) mouseY);
            PacketDistributor.sendToServer(new MinimizerSizePayload(picked.value() + Registration.MIN_GRID_SIZE));
            onClose();
        }
    }
}
