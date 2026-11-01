package com.therappha.tinyblocks.client;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.items.MinimizerBuild;
import com.therappha.tinyblocks.items.MinimizerItem;
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

/** Under the crosshair, while a Minimizer is in either hand: the size it places, its build mode, and whether it is locked. */
@EventBusSubscriber(modid = TinyBlocks.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class MinimizerHud {

    private static final int BELOW_CROSSHAIR = 10, COLOUR = 0xFFE8B2FF;

    private MinimizerHud() {}

    @SubscribeEvent
    public static void onRegister(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "minimizer"), MinimizerHud::draw);
    }

    private static void draw(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack held = mc.player.getMainHandItem().getItem() instanceof MinimizerItem ? mc.player.getMainHandItem() : mc.player.getOffhandItem();
        if (!(held.getItem() instanceof MinimizerItem minimizer)) return;
        Component mode = Component.translatable(MinimizerBuild.mode(held).translationKey());
        String key = MinimizerItem.isLocked(held) ? "tinyblocks.hud.minimizer_locked" : "tinyblocks.hud.minimizer";
        graphics.drawCenteredString(mc.font, Component.translatable(key, minimizer.gridSize(held), mode),
                graphics.guiWidth() / 2, graphics.guiHeight() / 2 + BELOW_CROSSHAIR, COLOUR);
    }
}
