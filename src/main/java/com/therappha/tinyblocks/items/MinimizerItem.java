package com.therappha.tinyblocks.items;

import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.subgrid.SubgridBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Held in the offhand: makes any BlockItem in the main hand create/place into a subgrid instead
 * of placing normally. Comes in the same four grid sizes as the debug pieces (default 1/8).
 */
public class MinimizerItem extends Item {

    public MinimizerItem(Properties properties) {
        super(properties);
    }

    /** Which SubgridBlock variant this Minimizer creates when auto-creating a new subgrid. */
    public SubgridBlock preferredSubgrid() {
        return Registration.SUBGRID_BLOCK.get();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.tinyblocks.minimizer.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
