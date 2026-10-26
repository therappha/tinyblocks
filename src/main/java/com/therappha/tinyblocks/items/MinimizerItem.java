package com.therappha.tinyblocks.items;

import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.subgrid.SubgridBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * Held in the offhand: makes any BlockItem in the main hand create/place into a subgrid instead of placing
 * normally. Its grid size (1/2 to 1/16) is kept on the stack and picked on Create's value board, opened by a right
 * click in the air with it in the main hand (client MinimizerSizeMenu); the older fixed-size Minimizers start at
 * theirs.
 */
public class MinimizerItem extends Item {

    private static final String SIZE_KEY = "tinyblocks_size";

    public MinimizerItem(Properties properties) {
        super(properties);
    }

    /** The grid size this Minimizer starts at, before one is picked. */
    public static final int DEFAULT_GRID_SIZE = 7;

    /**
     * The grid this Minimizer starts at: 1/7 — odd, so it has a middle cell, where a tiny shaft lines up with a
     * full-size one (the shaft connector's socket), and still a handy cell size.
     */
    public SubgridBlock preferredSubgrid() {
        return Registration.subgridBlock(DEFAULT_GRID_SIZE);
    }

    /** The grid size {@code stack} makes and places into. */
    public int gridSize(ItemStack stack) {
        int size = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(SIZE_KEY);
        return size >= Registration.MIN_GRID_SIZE && size <= Registration.MAX_GRID_SIZE ? size : preferredSubgrid().gridSize;
    }

    /** Which SubgridBlock {@code stack} creates when auto-creating a new subgrid. */
    public SubgridBlock preferredSubgrid(ItemStack stack) {
        return Registration.subgridBlock(gridSize(stack));
    }

    public static void setGridSize(ItemStack stack, int size) {
        int clamped = Mth.clamp(size, Registration.MIN_GRID_SIZE, Registration.MAX_GRID_SIZE);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(SIZE_KEY, clamped));
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.tinyblocks.minimizer.sized", gridSize(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.tinyblocks.minimizer.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.tinyblocks.minimizer.tooltip_size").withStyle(ChatFormatting.GRAY));
    }
}
