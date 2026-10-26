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

/** Shift+G (rebindable) switches a held Minimizer's build mode. */
public final class MinimizerKeys {

    public static final KeyMapping NEXT_MODE = new KeyMapping("key.tinyblocks.minimizer_mode",
            KeyConflictContext.IN_GAME, KeyModifier.SHIFT, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G,
            "key.categories.tinyblocks");

    private MinimizerKeys() {}

    @EventBusSubscriber(modid = TinyBlocks.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(NEXT_MODE);
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
    }
}
