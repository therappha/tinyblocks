package com.therappha.tinyblocks.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.items.MinimizerItem;
import com.therappha.tinyblocks.network.MinimizerModePayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Shift+G (rebindable) switches a held Minimizer's build mode; with a shape's first corner held, Ctrl + scroll
 * raises or lowers the second corner (as Simulated's honey glue grows its box), instead of changing hotbar slot.
 */
public final class MinimizerKeys {

    public static final KeyMapping NEXT_MODE = new KeyMapping("key.tinyblocks.minimizer_mode",
            KeyConflictContext.IN_GAME, KeyModifier.SHIFT, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G,
            "key.categories.tinyblocks");

    /** X (rebindable): shrink to the held Minimizer's size, or back to full size (MinimizeMe). */
    public static final KeyMapping MINIMIZE_ME = new KeyMapping("key.tinyblocks.minimize_me",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.tinyblocks");

    /** K (rebindable): locks the Minimizer in hand (MinimizerItem#mayBreak). A key: a click in the air is the held item's. */
    public static final KeyMapping LOCK = new KeyMapping("key.tinyblocks.minimizer_lock",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.tinyblocks");

    /** J (rebindable): turns the grid looked at to the held Minimizer's size (GridResize). */
    public static final KeyMapping RESIZE = new KeyMapping("key.tinyblocks.minimizer_resize",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.tinyblocks");

    private MinimizerKeys() {}

    @EventBusSubscriber(modid = TinyBlocks.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(NEXT_MODE);
            event.register(MINIMIZE_ME);
            event.register(LOCK);
            event.register(RESIZE);
        }
    }

    @EventBusSubscriber(modid = TinyBlocks.MOD_ID, value = Dist.CLIENT)
    public static final class Ticking {
        private Ticking() {}

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            while (NEXT_MODE.consumeClick()) {
                if (mc.player == null) return;
                if (mc.player.getOffhandItem().getItem() instanceof MinimizerItem
                        || mc.player.getMainHandItem().getItem() instanceof MinimizerItem) {
                    PacketDistributor.sendToServer(MinimizerModePayload.INSTANCE);
                }
            }
        }

        @SubscribeEvent
        public static void onResize(ClientTickEvent.Post event) {
            while (RESIZE.consumeClick()) {
                if (Minecraft.getInstance().player != null) PacketDistributor.sendToServer(com.therappha.tinyblocks.network.MinimizerResizePayload.INSTANCE);
            }
        }

        /** Sneaking with a Minimizer in either hand, the wheel changes its size, not the hotbar slot. */
        @SubscribeEvent
        public static void onSizeScroll(net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null || !mc.options.keyShift.isDown() || event.getScrollDeltaY() == 0) return;
            if (!(mc.player.getMainHandItem().getItem() instanceof MinimizerItem || mc.player.getOffhandItem().getItem() instanceof MinimizerItem)) return;
            event.setCanceled(true);
            PacketDistributor.sendToServer(new com.therappha.tinyblocks.network.MinimizerSizeStepPayload(event.getScrollDeltaY() > 0 ? 1 : -1));
        }

        @SubscribeEvent
        public static void onLock(ClientTickEvent.Post event) {
            while (LOCK.consumeClick()) {
                if (Minecraft.getInstance().player != null) PacketDistributor.sendToServer(com.therappha.tinyblocks.network.MinimizerLockPayload.INSTANCE);
            }
        }

        @SubscribeEvent
        public static void onMinimizeMe(ClientTickEvent.Post event) {
            while (MINIMIZE_ME.consumeClick()) {
                if (Minecraft.getInstance().player != null) PacketDistributor.sendToServer(com.therappha.tinyblocks.network.MinimizeMePayload.INSTANCE);
            }
        }

        @SubscribeEvent
        public static void onScroll(net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null || !net.minecraft.client.gui.screens.Screen.hasControlDown()) return;
            var minimizer = mc.player.getOffhandItem();
            if (!(minimizer.getItem() instanceof MinimizerItem)
                    || com.therappha.tinyblocks.items.MinimizerBuild.corner(minimizer) == null
                    || event.getScrollDeltaY() == 0) return;
            event.setCanceled(true);
            PacketDistributor.sendToServer(new com.therappha.tinyblocks.network.MinimizerLiftPayload(event.getScrollDeltaY() > 0 ? 1 : -1));
        }
    }
}
