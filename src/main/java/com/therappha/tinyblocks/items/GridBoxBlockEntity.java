package com.therappha.tinyblocks.items;

import com.simibubi.create.AllSoundEvents;
import com.therappha.tinyblocks.setup.Registration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The full Grid Save in a box, with the face it was put against, which decides where the grid lands. */
public class GridBoxBlockEntity extends BlockEntity {

    private static final int PARTICLES = 10;
    private ItemStack held = ItemStack.EMPTY;
    private Direction face = Direction.UP;

    public GridBoxBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.GRID_BOX_ENTITY.get(), pos, state);
    }

    public ItemStack held() {
        return held;
    }

    void hold(ItemStack stack, Direction face) {
        this.held = stack;
        this.face = face;
        setChanged();
    }

    /**
     * The box goes and the grid takes its place, with the sound and the bits of cardboard of a Create package opening.
     * The grid may no longer fit, something having been built there since: the box then stays closed, and says so.
     */
    void unbox(ServerLevel level, Player player) {
        ItemStack stack = held;
        Direction from = face;
        BlockPos pos = worldPosition;
        held = ItemStack.EMPTY; // so the box going away doesn't give it back
        level.removeBlock(pos, false);
        if (GridSaveItem.place(level, pos, from, stack, player)) {
            AllSoundEvents.PACKAGE_POP.playOnServer(level, pos);
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Registration.GRID_SAVE.get())),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, PARTICLES, 0.25, 0.25, 0.25, 0.1);
            return;
        }
        level.setBlock(pos, getBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof GridBoxBlockEntity box) box.hold(stack, from);
        player.displayClientMessage(Component.translatable("tinyblocks.grid_box.blocked"), true);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!held.isEmpty()) tag.put("held", held.save(registries));
        tag.putString("face", face.getName());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        held = tag.contains("held") ? ItemStack.parseOptional(registries, tag.getCompound("held")) : ItemStack.EMPTY;
        Direction read = Direction.byName(tag.getString("face"));
        face = read != null ? read : Direction.UP;
    }
}
