package com.therappha.tinyblocks.items;

import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.subgrid.SubgridBlock;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Carries a whole grid: a click on a SubgridBlock takes it, pieces and all, into the item and leaves air (nothing
 * drops: the pieces are in the item); a click with it full puts the grid back down in front of the clicked face,
 * turned by the quarter turns R gave it (GridSavePreview shows it there first), and empties it.
 *
 * Kept in the stack's custom data under {@link #GRID_KEY}: {@code size} (the grid size, so the SubgridBlock to
 * place), {@code data} (the grid entity's saveCustomOnly) and {@code turns} (0-3 quarter turns clockwise).
 */
public class GridSaveItem extends Item {

    public static final String GRID_KEY = "tinyblocks_grid";

    public GridSaveItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** The grid {@code stack} carries, or null when it is empty. */
    @Nullable
    public static CompoundTag grid(ItemStack stack) {
        CompoundTag all = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return all.contains(GRID_KEY) ? all.getCompound(GRID_KEY) : null;
    }

    public static boolean isFull(ItemStack stack) {
        return grid(stack) != null;
    }

    /** The turn the grid is placed with, as R set it. */
    public static Rotation rotation(ItemStack stack) {
        CompoundTag grid = grid(stack);
        return grid == null ? Rotation.NONE : Rotation.values()[Math.floorMod(grid.getInt("turns"), 4)];
    }

    /** R: a quarter turn more, clockwise. Nothing to turn in an empty one. */
    public static void turn(ItemStack stack) {
        if (!isFull(stack)) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, all -> {
            CompoundTag grid = all.getCompound(GRID_KEY);
            grid.putInt("turns", Math.floorMod(grid.getInt("turns") + 1, 4));
        });
    }

    /** Where a full one puts its grid for {@code hit}: in front of the clicked face. */
    public static BlockPos target(BlockHitResult hit) {
        return hit.getBlockPos().relative(hit.getDirection());
    }

    /**
     * Before the clicked block's own use (and SubgridEventHandler stays out of it): a click on a piece is for the
     * grid, not for the piece. The client only says it was taken; the server does it.
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        boolean full = isFull(stack);
        if (!full && !(level.getBlockState(clicked).getBlock() instanceof SubgridBlock)) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        BlockPos pos = full ? target(new BlockHitResult(context.getClickLocation(), context.getClickedFace(), clicked, false)) : clicked;
        if (player != null && !player.mayUseItemAt(pos, context.getClickedFace(), stack)) return InteractionResult.FAIL;
        boolean done = full ? place(serverLevel, pos, stack) : pickUp(serverLevel, clicked, stack);
        return done ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /** Takes the grid at {@code pos} into the empty {@code stack} and removes its block; false if there is none. */
    public static boolean pickUp(ServerLevel level, BlockPos pos, ItemStack stack) {
        if (isFull(stack) || !(level.getBlockEntity(pos) instanceof SubgridBlockEntity be)) return false;
        CompoundTag grid = new CompoundTag();
        grid.putInt("size", be.getGridSize());
        grid.put("data", be.saveCustomOnly(level.registryAccess()));
        grid.putInt("turns", 0);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, all -> all.put(GRID_KEY, grid));
        // Removed as when broken (each piece leaves its Create networks: SubgridBlockEntity#setRemoved), but with
        // no drops: removeBlock drops nothing, and the pieces are in the item now.
        level.removeBlock(pos, false);
        return true;
    }

    /** Puts the grid {@code stack} carries at {@code pos}, turned, and empties it; false if it can't go there. */
    public static boolean place(ServerLevel level, BlockPos pos, ItemStack stack) {
        CompoundTag grid = grid(stack);
        if (grid == null || !level.getBlockState(pos).canBeReplaced() || !level.isInWorldBounds(pos)) return false;
        SubgridBlock block = Registration.subgridBlock(grid.getInt("size"));
        if (block.gridSize != grid.getInt("size")) return false;
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof SubgridBlockEntity be)) return false;
        be.loadMoved(grid.getCompound("data"), rotation(stack));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, all -> all.remove(GRID_KEY));
        return true;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isFull(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag grid = grid(stack);
        if (grid == null) {
            tooltip.add(Component.translatable("item.tinyblocks.grid_save.tooltip_empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        int pieces = grid.getCompound("data").getList("pieces", net.minecraft.nbt.Tag.TAG_COMPOUND).size();
        tooltip.add(Component.translatable("item.tinyblocks.grid_save.tooltip_full", grid.getInt("size"), pieces)
                .withStyle(ChatFormatting.GRAY));
    }
}
