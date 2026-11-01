package com.therappha.tinyblocks.items;

import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
 * normally. Its grid size (1/2 to 1/16, or 1/1 for normal blocks) is kept on the stack and picked on Create's value
 * board, opened by a right click in the air with it in the main hand (client MinimizerSizeMenu); the older
 * fixed-size Minimizers start at theirs.
 */
public class MinimizerItem extends Item {

    private static final String SIZE_KEY = "tinyblocks_size";
    private static final String LOCKED_KEY = "tinyblocks_locked";

    /** The normal size, 1/1: placing normal blocks (in a shape mode, building with them). No SubgridBlock has it. */
    public static final int NORMAL_SIZE = 1;

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
        return size >= NORMAL_SIZE && size <= Registration.MAX_GRID_SIZE ? size : preferredSubgrid().gridSize;
    }

    /** Which SubgridBlock {@code stack} creates when auto-creating a new subgrid. */
    public SubgridBlock preferredSubgrid(ItemStack stack) {
        return Registration.subgridBlock(gridSize(stack));
    }

    public static void setGridSize(ItemStack stack, int size) {
        int clamped = Mth.clamp(size, NORMAL_SIZE, Registration.MAX_GRID_SIZE);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(SIZE_KEY, clamped));
    }

    /** Whether {@code stack} is locked: while held, only grids of its size can be broken, shown by its glint. */
    public static boolean isLocked(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(LOCKED_KEY);
    }

    public static void setLocked(ItemStack stack, boolean locked) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(LOCKED_KEY, locked));
    }

    /**
     * Whether {@code player} may break what is at {@code pos}: anything, unless they hold a locked Minimizer (the main
     * hand's first). Then only a grid of its size; at the normal size 1/1, only what isn't a grid.
     */
    public static boolean mayBreak(Player player, BlockGetter level, BlockPos pos) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof MinimizerItem && isLocked(held))) held = player.getOffhandItem();
        if (!(held.getItem() instanceof MinimizerItem minimizer && isLocked(held))) return true;
        int size = minimizer.gridSize(held);
        return level.getBlockEntity(pos) instanceof SubgridBlockEntity be ? be.getGridSize() == size : size == NORMAL_SIZE;
    }

    /** A right click in the air in the off hand locks or unlocks it; in the main hand it picks the size (MinimizerSizeMenu). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.OFF_HAND) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide()) {
            boolean locked = !isLocked(stack);
            setLocked(stack, locked);
            player.displayClientMessage(Component.translatable(locked ? "tinyblocks.minimizer.locked" : "tinyblocks.minimizer.unlocked"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isLocked(stack) || super.isFoil(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.tinyblocks.minimizer.sized", gridSize(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.tinyblocks.minimizer.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.tinyblocks.minimizer.tooltip_size").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.tinyblocks.minimizer.tooltip_lock").withStyle(ChatFormatting.GRAY));
    }
}
