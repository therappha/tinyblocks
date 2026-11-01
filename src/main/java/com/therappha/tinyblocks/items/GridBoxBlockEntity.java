package com.therappha.tinyblocks.items;

import com.simibubi.create.AllSoundEvents;
import com.therappha.tinyblocks.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * One block of a box. The first, at its lowest corner, holds the full Grid Save, where it was clicked, and how big the box
 * is; the others only say where that first one is, so a click or a break on any of them reaches it.
 */
public class GridBoxBlockEntity extends BlockEntity {

    private static final int PARTICLES = 10;
    private ItemStack held = ItemStack.EMPTY;
    private BlockPos clicked = BlockPos.ZERO;
    private Direction face = Direction.UP;
    private Vec3i size = new Vec3i(1, 1, 1);
    @Nullable private BlockPos main;

    public GridBoxBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.GRID_BOX_ENTITY.get(), pos, state);
    }

    public ItemStack held() {
        return held;
    }

    public Vec3i size() {
        return size;
    }

    /** Whether this block draws the box. */
    public boolean isMain() {
        return main == null;
    }

    /** The block holding this box; this one, if its first block can't be found. */
    GridBoxBlockEntity main() {
        return main != null && level != null && level.getBlockEntity(main) instanceof GridBoxBlockEntity first ? first : this;
    }

    void hold(ItemStack stack, BlockPos clicked, Direction face, Vec3i size) {
        this.held = stack;
        this.clicked = clicked;
        this.face = face;
        this.size = size;
        changed();
    }

    void partOf(BlockPos main) {
        this.main = main;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private List<BlockPos> cells() {
        List<BlockPos> cells = new ArrayList<>();
        BlockPos.betweenClosed(worldPosition, worldPosition.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))
                .forEach(cell -> cells.add(cell.immutable()));
        return cells;
    }

    /** Takes every block of the box out, without any of them giving the Grid Save back. */
    private ItemStack clear(ServerLevel level) {
        ItemStack stack = held;
        held = ItemStack.EMPTY;
        for (BlockPos cell : cells()) {
            if (level.getBlockState(cell).is(Registration.GRID_BOX.get())) level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        return stack;
    }

    /** One of its blocks went: the whole box goes, and the Grid Save comes back, once. */
    void broken(ServerLevel level) {
        if (held.isEmpty()) return;
        ItemStack stack = held;
        Block.popResource(level, worldPosition, stack.copy());
        clear(level);
    }

    /**
     * The box goes and the grid takes its place, with the sound and the bits of cardboard of a Create package opening.
     * Refused, by a player's protection for one, the box stays, and says so.
     */
    void unbox(ServerLevel level, Player player) {
        BlockPos at = clicked;
        Direction from = face;
        Vec3i boxSize = size;
        BlockPos corner = worldPosition;
        BlockState state = getBlockState();
        List<BlockPos> cells = cells();
        ItemStack stack = clear(level);
        if (GridSaveItem.place(level, at, from, stack, player)) {
            AllSoundEvents.PACKAGE_POP.playOnServer(level, corner);
            net.minecraft.world.phys.Vec3 centre = new AABB(corner).expandTowards(boxSize.getX() - 1, boxSize.getY() - 1, boxSize.getZ() - 1).getCenter();
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Registration.GRID_SAVE.get())),
                    centre.x, centre.y, centre.z, PARTICLES, 0.25 * boxSize.getX(), 0.25 * boxSize.getY(), 0.25 * boxSize.getZ(), 0.1);
            return;
        }
        for (BlockPos cell : cells) level.setBlock(cell, state, Block.UPDATE_ALL);
        if (level.getBlockEntity(corner) instanceof GridBoxBlockEntity box) box.hold(stack, at, from, boxSize);
        for (BlockPos cell : cells) {
            if (!cell.equals(corner) && level.getBlockEntity(cell) instanceof GridBoxBlockEntity part) part.partOf(corner);
        }
        player.displayClientMessage(Component.translatable("tinyblocks.grid_box.blocked"), true);
    }

    public AABB bounds() {
        return new AABB(worldPosition).expandTowards(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!held.isEmpty()) tag.put("held", held.save(registries));
        saveShared(tag);
    }

    /** All a client needs: where the box is and how big, not the grids inside. */
    private void saveShared(CompoundTag tag) {
        tag.put("clicked", NbtUtils.writeBlockPos(clicked));
        tag.putString("face", face.getName());
        tag.putIntArray("size", new int[] {size.getX(), size.getY(), size.getZ()});
        if (main != null) tag.put("main", NbtUtils.writeBlockPos(main));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        held = tag.contains("held") ? ItemStack.parseOptional(registries, tag.getCompound("held")) : ItemStack.EMPTY;
        clicked = NbtUtils.readBlockPos(tag, "clicked").orElse(worldPosition);
        Direction read = Direction.byName(tag.getString("face"));
        face = read != null ? read : Direction.UP;
        int[] box = tag.getIntArray("size");
        size = box.length == 3 ? new Vec3i(box[0], box[1], box[2]) : new Vec3i(1, 1, 1);
        main = NbtUtils.readBlockPos(tag, "main").orElse(null);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveShared(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
