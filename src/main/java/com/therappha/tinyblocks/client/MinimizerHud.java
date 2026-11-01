package com.therappha.tinyblocks.client;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.items.MinimizerBuild;
import com.therappha.tinyblocks.items.MinimizerItem;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Under the crosshair for a moment, when a Minimizer comes to hand or changes: the size it places, its build mode, and
 * whether it is locked or switched off. An indication, not something left on the screen.
 */
@EventBusSubscriber(modid = TinyBlocks.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class MinimizerHud {

    private static final int BELOW_CROSSHAIR = 10, COLOUR = 0xE8B2FF, OFF_COLOUR = 0xFF5555, MIN_ALPHA = 8;
    private static final FadingNotice NOTICE = new FadingNotice();

    private MinimizerHud() {}

    @SubscribeEvent
    public static void onRegister(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "minimizer"), MinimizerHud::draw);
    }

    private static void draw(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Component text = mc.player == null ? null : text(mc);
        int alpha = NOTICE.alpha(text == null ? null : text.getString() + colour(mc), Util.getMillis());
        if (alpha < MIN_ALPHA) return; // a font takes an alpha near 0 as fully opaque
        RenderSystem.enableBlend();
        graphics.drawCenteredString(mc.font, text, graphics.guiWidth() / 2, graphics.guiHeight() / 2 + BELOW_CROSSHAIR,
                colour(mc) | alpha << 24);
        RenderSystem.disableBlend();
    }

    private static ItemStack held(Minecraft mc) {
        return mc.player.getMainHandItem().getItem() instanceof MinimizerItem ? mc.player.getMainHandItem() : mc.player.getOffhandItem();
    }

    private static int colour(Minecraft mc) {
        return mc.player != null && !MinimizerItem.isOn(held(mc)) ? OFF_COLOUR : COLOUR;
    }

    /** What to say about the Minimizer in hand; null without one. */
    private static Component text(Minecraft mc) {
        ItemStack held = held(mc);
        if (!(held.getItem() instanceof MinimizerItem minimizer)) return null;
        if (!MinimizerItem.isOn(held)) return Component.translatable("tinyblocks.hud.minimizer_off");
        Component mode = Component.translatable(MinimizerBuild.mode(held).translationKey());
        String key = MinimizerItem.isLocked(held) ? "tinyblocks.hud.minimizer_locked" : "tinyblocks.hud.minimizer";
        return Component.translatable(key, minimizer.gridSize(held), mode);
    }
}
