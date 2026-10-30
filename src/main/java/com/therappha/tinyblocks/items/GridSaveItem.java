package com.therappha.tinyblocks.items;

import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.subgrid.SubgridBlock;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Carries a whole grid: a click on a SubgridBlock takes it, pieces and all, into the item and leaves air (nothing
 * drops: the pieces are in the item); a click with it full puts the grid back down in front of the clicked face,
 * turned by the quarter turns R gave it (GridSavePreview shows it there first), and empties it. Or several grids
 * together, as Create's Super Glue selects: a sneaking click sets a corner ({@link #CORNER_KEY}), the next click the
 * other one, and every SubgridBlock in that box is taken, each where it was against the others.
 *
 * Kept in the stack's custom data under {@link #GRID_KEY}: {@code size} (the grid size, so the SubgridBlock to
 * place), {@code data} (the grid entity's saveCustomOnly) and {@code turns} (0-3 quarter turns clockwise); or, for
 * a box, {@code turns}, {@code box} (its size) and {@code grids}, each a {@code size} and {@code data} with its
 * {@code offset} from the box's lowest corner, unturned. {@link #entries} reads both.
 */
public class GridSaveItem extends Item {

    public static final String GRID_KEY = "tinyblocks_grid";

    /** The first corner of a box being selected, set by a sneaking click on an empty one: x, y, z. */
    public static final String CORNER_KEY = "tinyblocks_corner";

    /** The most grids a box takes, and the most blocks it spans on each axis. */
    public static final int MAX_GRIDS = 4, MAX_SPAN = 4;

    /** One grid a full one carries: where it goes in the box (unturned), its size and its saved data. */
    public record Entry(BlockPos offset, int size, CompoundTag data) {}

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

    /** The grids {@code grid} (as {@link #grid} reads it) holds: one at no offset for a single grid. */
    public static List<Entry> entries(CompoundTag grid) {
        List<Entry> entries = new ArrayList<>();
        if (!grid.contains("grids")) {
            entries.add(new Entry(BlockPos.ZERO, grid.getInt("size"), grid.getCompound("data")));
            return entries;
        }
        for (Tag tag : grid.getList("grids", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) tag;
            int[] offset = entry.getIntArray("offset");
            entries.add(new Entry(offset.length == 3 ? new BlockPos(offset[0], offset[1], offset[2]) : BlockPos.ZERO,
                    entry.getInt("size"), entry.getCompound("data")));
        }
        return entries;
    }

    /** The size of the box {@code grid} fills, unturned: a single block for a single grid. */
    public static Vec3i box(CompoundTag grid) {
        int[] box = grid.getIntArray("box");
        return box.length == 3 ? new Vec3i(box[0], box[1], box[2]) : new Vec3i(1, 1, 1);
    }

    /** The box turned: a quarter turn swaps its x and z. */
    public static Vec3i turnedBox(Vec3i box, Rotation rotation) {
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90
                ? new Vec3i(box.getZ(), box.getY(), box.getX()) : box;
    }

    /** Where a grid at {@code offset} in {@code box} is once the box is turned, as SubgridBlockEntity#transformCell turns a cell. */
    public static BlockPos turnedOffset(BlockPos offset, Vec3i box, Rotation rotation) {
        int x = offset.getX(), y = offset.getY(), z = offset.getZ();
        return switch (rotation) {
            case NONE -> offset;
            case CLOCKWISE_90 -> new BlockPos(box.getZ() - 1 - z, y, x);
            case CLOCKWISE_180 -> new BlockPos(box.getX() - 1 - x, y, box.getZ() - 1 - z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(z, y, box.getX() - 1 - x);
        };
    }

    /**
     * The lowest corner of a {@code turnedBox} placed at {@code inFront} (target), against {@code face}: a box put on
     * a west, north or under face reaches away from it, not into the block clicked.
     */
    public static BlockPos origin(BlockPos inFront, Direction face, Vec3i turnedBox) {
        if (face.getAxisDirection() == Direction.AxisDirection.POSITIVE) return inFront;
        return inFront.relative(face, turnedBox.get(face.getAxis()) - 1);
    }

    /** The corner a sneaking click set, or null. */
    @Nullable
    public static BlockPos corner(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(CORNER_KEY)) return null;
        int[] corner = data.getUnsafe().getIntArray(CORNER_KEY);
        return corner.length == 3 ? new BlockPos(corner[0], corner[1], corner[2]) : null;
    }

    private static void setCorner(ItemStack stack, @Nullable BlockPos corner) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, all -> {
            if (corner == null) all.remove(CORNER_KEY);
            else all.putIntArray(CORNER_KEY, new int[]{corner.getX(), corner.getY(), corner.getZ()});
        });
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
     * grid, not for the piece, and a click selecting a box is for the box. The client only says it was taken; the
     * server does it.
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        Player player = context.getPlayer();
        boolean full = isFull(stack);
        boolean selecting = !full && (corner(stack) != null || player != null && player.isSecondaryUseActive());
        if (!full && !selecting && !(level.getBlockState(clicked).getBlock() instanceof SubgridBlock)) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;
        boolean done;
        if (full) done = place(serverLevel, clicked.relative(face), face, stack, player);
        else if (player != null && player.isSecondaryUseActive()) done = selectCorner(clicked, stack, player);
        else if (selecting) done = pickUpBox(serverLevel, corner(stack), clicked, stack, player);
        else done = pickUp(serverLevel, clicked, stack, player);
        return done ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /** A sneaking click on a block: the first corner of a box there, or, on the corner already set, none. */
    private static boolean selectCorner(BlockPos clicked, ItemStack stack, Player player) {
        boolean again = clicked.equals(corner(stack));
        setCorner(stack, again ? null : clicked);
        tell(player, again ? "item.tinyblocks.grid_save.corner_cleared" : "item.tinyblocks.grid_save.corner_set");
        return true;
    }

    /** A sneaking click on nothing lets go of the corner set, as with Create's Super Glue. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() || corner(stack) == null) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            setCorner(stack, null);
            tell(player, "item.tinyblocks.grid_save.corner_cleared");
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Put away, it forgets the corner it was selecting from. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || selected || corner(stack) == null) return;
        if (entity instanceof LivingEntity living && living.getOffhandItem() == stack) return;
        setCorner(stack, null);
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
     * Takes every grid in the box between {@code corner} and {@code other} into the empty {@code stack}, each with
     * where it was in the box, and removes their blocks; all or none, refused (the corner kept) for any grid pickUp
     * would refuse, for none or more than {@link #MAX_GRIDS}, for a box over {@link #MAX_SPAN} a side or all of them
     * together over {@link #MAX_GRID_BYTES}. What is not a grid is neither taken nor asked about.
     */
    public static boolean pickUpBox(ServerLevel level, BlockPos corner, BlockPos other, ItemStack stack, @Nullable Player player) {
        if (isFull(stack)) return false;
        BlockPos min = BlockPos.min(corner, other), max = BlockPos.max(corner, other);
        Vec3i box = max.subtract(min).offset(1, 1, 1);
        if (box.getX() > MAX_SPAN || box.getY() > MAX_SPAN || box.getZ() > MAX_SPAN) {
            tell(player, "item.tinyblocks.grid_save.box_too_big");
            return false;
        }
        List<SubgridBlockEntity> grids = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockEntity(pos) instanceof SubgridBlockEntity be) grids.add(be);
        }
        if (grids.isEmpty() || grids.size() > MAX_GRIDS) {
            tell(player, grids.isEmpty() ? "item.tinyblocks.grid_save.no_grids" : "item.tinyblocks.grid_save.too_many");
            return false;
        }
        // Every one asked before any is taken: one refusing leaves them all.
        for (SubgridBlockEntity be : grids) {
            BlockPos pos = be.getBlockPos();
            if (player instanceof ServerPlayer serverPlayer
                    && player.blockActionRestricted(level, pos, serverPlayer.gameMode.getGameModeForPlayer())) return false;
            if (be.hasActiveCaptures() || be.pendingDropCount() > 0) {
                tell(player, "item.tinyblocks.grid_save.moving");
                return false;
            }
            if (player != null && !mayBreak(level, pos, player)) return false;
        }
        // Every one saved before any is removed: a grid removed changes what its neighbours hold (their speed, their
        // links), and those are saved as they were together.
        ListTag entries = new ListTag();
        for (SubgridBlockEntity be : grids) {
            BlockPos offset = be.getBlockPos().subtract(min);
            CompoundTag entry = new CompoundTag();
            entry.putIntArray("offset", new int[]{offset.getX(), offset.getY(), offset.getZ()});
            entry.putInt("size", be.getGridSize());
            entry.put("data", be.saveCustomOnly(level.registryAccess()));
            entries.add(entry);
        }
        CompoundTag grid = new CompoundTag();
        grid.putInt("turns", 0);
        grid.putIntArray("box", new int[]{box.getX(), box.getY(), box.getZ()});
        grid.put("grids", entries);
        if (grid.sizeInBytes() > MAX_GRID_BYTES) {
            tell(player, "item.tinyblocks.grid_save.too_big");
            return false;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, all -> {
            all.put(GRID_KEY, grid);
            all.remove(CORNER_KEY);
        });
        // As pickUp removes one.
        for (SubgridBlockEntity be : grids) level.removeBlock(be.getBlockPos(), false);
        return true;
    }

    /**
     * Puts the grids {@code stack} carries at {@code pos} (in front of {@code face} of the block clicked), turned, and
     * empties it; false, still full, if any of them can't go there: not replaceable, out of the world, someone in the
     * way (the whole cell: what a grid fills isn't known before it is there), not {@code player}'s to use or a
     * protection cancelling its place event. All or none.
     */
    public static boolean place(ServerLevel level, BlockPos pos, Direction face, ItemStack stack, @Nullable Player player) {
        CompoundTag grid = grid(stack);
        if (grid == null) return false;
        Rotation rotation = rotation(stack);
        Vec3i box = box(grid);
        BlockPos origin = origin(pos, face, turnedBox(box, rotation));
        List<Entry> entries = entries(grid);
        List<BlockPos> targets = new ArrayList<>();
        for (Entry entry : entries) {
            BlockPos target = origin.offset(turnedOffset(entry.offset(), box, rotation));
            if (!level.getBlockState(target).canBeReplaced() || !level.isInWorldBounds(target)) return false;
            if (player != null && !player.mayUseItemAt(target, face, stack)) return false;
            if (!level.isUnobstructed(null, Shapes.block().move(target.getX(), target.getY(), target.getZ()))) return false;
            if (Registration.subgridBlock(entry.size()).gridSize != entry.size()) return false;
            targets.add(target);
        }
        List<BlockSnapshot> before = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            before.add(BlockSnapshot.create(level.dimension(), level, targets.get(i)));
            level.setBlock(targets.get(i), Registration.subgridBlock(entries.get(i).size()).defaultBlockState(), Block.UPDATE_ALL);
        }
        // As BlockItem asks, with the blocks there (a player's place event reads them from the level); the grids are
        // still empty, so taking them back leaves nothing behind.
        boolean cancelled = before.size() == 1
                ? EventHooks.onBlockPlace(player, before.get(0), face)
                : EventHooks.onMultiBlockPlace(player, before, face);
        if (cancelled) {
            for (BlockSnapshot snapshot : before) snapshot.restore(Block.UPDATE_ALL);
            return false;
        }
        List<SubgridBlockEntity> placed = new ArrayList<>();
        for (BlockPos target : targets) {
            if (level.getBlockEntity(target) instanceof SubgridBlockEntity be) placed.add(be);
        }
        if (placed.size() != entries.size()) return false;
        // Every grid loaded and turned before any looks across (as grids moved together by Sable): a cog against the
        // next grid finds that grid's cog already there, turned.
        for (int i = 0; i < placed.size(); i++) placed.get(i).loadCustomOnly(entries.get(i).data(), level.registryAccess());
        for (SubgridBlockEntity be : placed) be.turnMovedQuietly(rotation, Mirror.NONE);
        for (SubgridBlockEntity be : placed) be.lookAroundMoved();
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
        List<Entry> entries = entries(data.getUnsafe().getCompound(GRID_KEY));
        int pieces = 0;
        for (Entry entry : entries) pieces += entry.data().getList("pieces", Tag.TAG_COMPOUND).size();
        MutableComponent text = entries.size() == 1
                ? Component.translatable("item.tinyblocks.grid_save.tooltip_full", entries.get(0).size(), pieces)
                : Component.translatable("item.tinyblocks.grid_save.tooltip_full_box", entries.size(), pieces);
        tooltip.add(text.withStyle(ChatFormatting.GRAY));
    }
}
