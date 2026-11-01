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
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The Minimizer's grid size, picked on Create's value board: sneaking and scrolling with it in either hand opens it,
 * and letting go of sneak on a size picks it. The board's values 0..15 are the sizes 1/1 (normal blocks) to 1/16.
 */
@EventBusSubscriber(modid = TinyBlocks.MOD_ID, value = Dist.CLIENT)
public final class MinimizerSizeMenu {

    private MinimizerSizeMenu() {}

    /** Sneaking with a Minimizer in either hand, the wheel opens the board instead of changing the hotbar slot. */
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !mc.options.keyShift.isDown() || event.getScrollDeltaY() == 0) return;
        ItemStack stack = mc.player.getMainHandItem().getItem() instanceof MinimizerItem ? mc.player.getMainHandItem() : mc.player.getOffhandItem();
        if (!(stack.getItem() instanceof MinimizerItem item)) return;
        event.setCanceled(true);
        int max = Registration.MAX_GRID_SIZE - MinimizerItem.NORMAL_SIZE;
        ValueSettingsBoard board = new ValueSettingsBoard(Component.translatable("tinyblocks.minimizer.size_board"), max, 1,
                List.of(Component.translatable("tinyblocks.minimizer.size_row")),
                new ValueSettingsFormatter(v -> Component.literal("1/" + (v.value() + MinimizerItem.NORMAL_SIZE))));
        ValueSettings current = new ValueSettings(0, item.gridSize(stack) - MinimizerItem.NORMAL_SIZE);
        mc.setScreen(new Screen(board, current));
    }

    /** Create's value board, sending the size picked to the Minimizer instead of a block's value. */
    private static final class Screen extends ValueSettingsScreen {
        Screen(ValueSettingsBoard board, ValueSettings current) {
            super(Minecraft.getInstance().player.blockPosition(), board, current, v -> {}, 0);
        }

        /** Create's board picks when the use key is let go; this one is held by sneak instead. */
        @Override
        public boolean keyReleased(int key, int scanCode, int modifiers) {
            if (!Minecraft.getInstance().options.keyShift.matches(key, scanCode)) return super.keyReleased(key, scanCode, modifiers);
            var window = Minecraft.getInstance().getWindow();
            var mouse = Minecraft.getInstance().mouseHandler;
            saveAndClose(mouse.xpos() * window.getGuiScaledWidth() / window.getScreenWidth(),
                    mouse.ypos() * window.getGuiScaledHeight() / window.getScreenHeight());
            return true;
        }

        @Override
        protected void saveAndClose(double mouseX, double mouseY) {
            ValueSettings picked = getClosestCoordinate((int) mouseX, (int) mouseY);
            PacketDistributor.sendToServer(new MinimizerSizePayload(picked.value() + MinimizerItem.NORMAL_SIZE));
            onClose();
        }
    }
}
