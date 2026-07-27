package com.therappha.tinyblocks.client.screen;

import com.therappha.tinyblocks.menu.TinyFurnaceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class TinyFurnaceScreen extends AbstractContainerScreen<TinyFurnaceMenu> {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/gui/container/furnace.png");

    public TinyFurnaceScreen(TinyFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // Flame (burn progress, grows upward)
        if (menu.isLit()) {
            int flame = menu.getBurnProgress();
            graphics.blit(TEXTURE, leftPos + 56, topPos + 36 + 12 - flame, 176, 12 - flame, 14, flame + 1);
        }

        // Arrow (cook progress, grows right)
        int arrow = menu.getCookProgress();
        graphics.blit(TEXTURE, leftPos + 79, topPos + 34, 176, 14, arrow + 1, 16);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
