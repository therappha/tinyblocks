package com.therappha.tinyblocks.items;

import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.subgrid.SubgridBlock;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;

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

    /**
     * The most a saved grid may weigh (Tag#sizeInBytes). An item stack reaches clients through COMPOUND_TAG, which
     * reads at most 2MB of NBT before it drops the connection (a grid's own block entity has no such limit), and a
     * whole inventory goes in one packet: kept well under.
     */
    public static final int MAX_GRID_BYTES = 1 << 20;

    /** Set while GridSaveItem asks the protections about a take: SubgridEventHandler#onBlockBreak keeps out of it. */
    private static boolean askingToBreak;

    public GridSaveItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static boolean isAskingToBreak() {
        return askingToBreak;
    }

    /** The custom data of a full {@code stack}, unchanged as long as its grid is (a key to cache by), or null. */
    @Nullable
    public static CustomData data(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(GRID_KEY) ? data : null;
    }

    /** A copy of the grid {@code stack} carries, or null when it is empty. */
    @Nullable
    public static CompoundTag grid(ItemStack stack) {
        CustomData data = data(stack);
        return data == null ? null : data.getUnsafe().getCompound(GRID_KEY).copy();
    }

    public static boolean isFull(ItemStack stack) {
        return data(stack) != null;
    }

    /** The turn the grid is placed with, as R set it. */
    public static Rotation rotation(ItemStack stack) {
        CustomData data = data(stack);
        return data == null ? Rotation.NONE : Rotation.values()[Math.floorMod(data.getUnsafe().getCompound(GRID_KEY).getInt("turns"), 4)];
    }

    /**
     * The full Grid Save a click of {@code player}'s would place, or null: in the main hand, or in the off hand while
     * the main one holds nothing that would act first (nothing, or a Grid Save, empty, that passes off the grids).
     */
    @Nullable
    public static ItemStack heldFull(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof GridSaveItem && isFull(main)) return main;
        ItemStack off = player.getOffhandItem();
        boolean mainPasses = main.isEmpty() || main.getItem() instanceof GridSaveItem;
        return mainPasses && off.getItem() instanceof GridSaveItem && isFull(off) ? off : null;
    }

    /** R (GridSaveTurnPayload): the Grid Save {@code player} would place with turns a quarter more; false if none. */
    public static boolean turnHeld(Player player) {
        ItemStack stack = heldFull(player);
        if (stack == null) return false;
        turn(stack);
        return true;
    }

    /** A quarter turn more, clockwise. Nothing to turn in an empty one. */
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
        Direction face = context.getClickedFace();
        boolean full = isFull(stack);
        if (!full && !(level.getBlockState(clicked).getBlock() instanceof SubgridBlock)) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        boolean done = full
                ? place(serverLevel, clicked.relative(face), face, stack, player)
                : pickUp(serverLevel, clicked, stack, player);
        return done ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /**
     * Takes the grid at {@code pos} into the empty {@code stack} and removes its block; false if there is none, if
     * {@code player} may not break it (adventure mode, or a protection cancelling its BreakEvent), while a piston in it
     * is still moving something (what it carries, and what it drops after, is in no saved data), or if the grid is
     * too heavy for an item ({@link #MAX_GRID_BYTES}). {@code player} is null for a machine with none.
     */
    public static boolean pickUp(ServerLevel level, BlockPos pos, ItemStack stack, @Nullable Player player) {
        if (isFull(stack) || !(level.getBlockEntity(pos) instanceof SubgridBlockEntity be)) return false;
        if (player instanceof ServerPlayer serverPlayer
                && player.blockActionRestricted(level, pos, serverPlayer.gameMode.getGameModeForPlayer())) return false;
        if (be.hasActiveCaptures() || be.pendingDropCount() > 0) {
            tell(player, "item.tinyblocks.grid_save.moving");
            return false;
        }
        if (player != null && !mayBreak(level, pos, player)) return false;
        CompoundTag grid = new CompoundTag();
        grid.putInt("size", be.getGridSize());
        grid.put("data", be.saveCustomOnly(level.registryAccess()));
        grid.putInt("turns", 0);
        if (grid.sizeInBytes() > MAX_GRID_BYTES) {
            tell(player, "item.tinyblocks.grid_save.too_big");
            return false;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, all -> all.put(GRID_KEY, grid));
        // Removed as when broken (each piece leaves its Create networks: SubgridBlockEntity#setRemoved), but with
        // no drops: removeBlock drops nothing, and the pieces are in the item now.
        level.removeBlock(pos, false);
        return true;
    }

    /** Asks the protections (claim mods and such) whether {@code player} may break the grid, as a break would. */
    private static boolean mayBreak(ServerLevel level, BlockPos pos, Player player) {
        askingToBreak = true;
        try {
            return !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player)).isCanceled();
        } finally {
            askingToBreak = false;
        }
    }

    /**
     * Puts the grid {@code stack} carries at {@code pos} (in front of {@code face} of the block clicked), turned, and
     * empties it; false, still full, if it can't go there: not replaceable, out of the world, someone in the way (the
     * whole cell: what the grid fills isn't known before it is there), not {@code player}'s to use or a protection
     * cancelling its place event.
     */
    public static boolean place(ServerLevel level, BlockPos pos, Direction face, ItemStack stack, @Nullable Player player) {
        CompoundTag grid = grid(stack);
        if (grid == null || !level.getBlockState(pos).canBeReplaced() || !level.isInWorldBounds(pos)) return false;
        if (player != null && !player.mayUseItemAt(pos, face, stack)) return false;
        if (!level.isUnobstructed(null, Shapes.block().move(pos.getX(), pos.getY(), pos.getZ()))) return false;
        SubgridBlock block = Registration.subgridBlock(grid.getInt("size"));
        if (block.gridSize != grid.getInt("size")) return false;
        BlockSnapshot before = BlockSnapshot.create(level.dimension(), level, pos);
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
        // As BlockItem asks, with the block there (a player's place event reads it from the level); the grid is still
        // empty, so taking it back leaves nothing behind.
        if (EventHooks.onBlockPlace(player, before, face)) {
            before.restore(Block.UPDATE_ALL);
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof SubgridBlockEntity be)) return false;
        be.loadMoved(grid.getCompound("data"), rotation(stack));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, all -> all.remove(GRID_KEY));
        return true;
    }

    private static void tell(@Nullable Player player, String key) {
        if (player != null) player.displayClientMessage(Component.translatable(key), true);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isFull(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CustomData data = data(stack);
        if (data == null) {
            tooltip.add(Component.translatable("item.tinyblocks.grid_save.tooltip_empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        CompoundTag grid = data.getUnsafe().getCompound(GRID_KEY);
        int pieces = grid.getCompound("data").getList("pieces", net.minecraft.nbt.Tag.TAG_COMPOUND).size();
        tooltip.add(Component.translatable("item.tinyblocks.grid_save.tooltip_full", grid.getInt("size"), pieces)
                .withStyle(ChatFormatting.GRAY));
    }
}
