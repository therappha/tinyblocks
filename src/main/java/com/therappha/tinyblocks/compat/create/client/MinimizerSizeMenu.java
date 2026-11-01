package com.therappha.tinyblocks.compat.create.client;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsScreen;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.client.MinimizerKeys;
import com.therappha.tinyblocks.items.MinimizerItem;
import com.therappha.tinyblocks.network.MinimizerSizePayload;
import com.therappha.tinyblocks.setup.Registration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The Minimizer's grid size, picked on Create's value board: holding the size key with it in either hand opens it,
 * and letting go on a size picks it. Not on sneak: sneaking is too much a part of building. The board's values 0..15 are the sizes 1/1 (normal blocks) to 1/16.
 */
@EventBusSubscriber(modid = TinyBlocks.MOD_ID, value = Dist.CLIENT)
public final class MinimizerSizeMenu {

    private MinimizerSizeMenu() {}

    /** The size key pressed with a Minimizer in either hand opens the board. */
    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (MinimizerKeys.SIZE.consumeClick()) {
            if (mc.player == null || mc.screen != null) return;
            ItemStack stack = mc.player.getMainHandItem().getItem() instanceof MinimizerItem ? mc.player.getMainHandItem() : mc.player.getOffhandItem();
            if (!(stack.getItem() instanceof MinimizerItem item)) return;
            int max = Registration.MAX_GRID_SIZE - MinimizerItem.NORMAL_SIZE;
            ValueSettingsBoard board = new ValueSettingsBoard(Component.translatable("tinyblocks.minimizer.size_board"), max, 1,
                    List.of(Component.translatable("tinyblocks.minimizer.size_row")),
                    new ValueSettingsFormatter(v -> Component.literal("1/" + (v.value() + MinimizerItem.NORMAL_SIZE))));
            ValueSettings current = new ValueSettings(0, item.gridSize(stack) - MinimizerItem.NORMAL_SIZE);
            mc.setScreen(new Screen(board, current));
        }
    }

    /** Create's value board, sending the size picked to the Minimizer instead of a block's value. */
    private static final class Screen extends ValueSettingsScreen {
        Screen(ValueSettingsBoard board, ValueSettings current) {
            super(Minecraft.getInstance().player.blockPosition(), board, current, v -> {}, 0);
        }

        /** Create's board picks when the use key is let go; this one when the size key is. */
        @Override
        public boolean keyReleased(int key, int scanCode, int modifiers) {
            if (!MinimizerKeys.SIZE.matches(key, scanCode)) return super.keyReleased(key, scanCode, modifiers);
            pickUnderCursor();
            return true;
        }

        /** The size key may be bound to a mouse button. */
        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (!MinimizerKeys.SIZE.matchesMouse(button)) return super.mouseReleased(mouseX, mouseY, button);
            pickUnderCursor();
            return true;
        }

        private void pickUnderCursor() {
            var window = Minecraft.getInstance().getWindow();
            var mouse = Minecraft.getInstance().mouseHandler;
            saveAndClose(mouse.xpos() * window.getGuiScaledWidth() / window.getScreenWidth(),
                    mouse.ypos() * window.getGuiScaledHeight() / window.getScreenHeight());
        }

        @Override
        protected void saveAndClose(double mouseX, double mouseY) {
            ValueSettings picked = getClosestCoordinate((int) mouseX, (int) mouseY);
            PacketDistributor.sendToServer(new MinimizerSizePayload(picked.value() + MinimizerItem.NORMAL_SIZE));
            onClose();
        }
    }
}
