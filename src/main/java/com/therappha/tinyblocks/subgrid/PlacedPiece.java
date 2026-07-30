package com.therappha.tinyblocks.subgrid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public class PlacedPiece {

    public final PieceDefinition definition;
    public final BlockPos anchor;
    public final Direction facing;
    public final Vec3i footprint;
    public float currentSpeed;
    /** Persistent data blob owned by the PieceDefinition (serialized by SubgridBlockEntity). */
    public CompoundTag extraData = new CompoundTag();
    /** Transient runtime state owned by the PieceDefinition (not saved directly). */
    public Object runtimeState;
    /**
     * Transient phantom BlockEntity (e.g. a chest's real ChestBlockEntity) owned by the
     * PieceDefinition, same lifecycle as runtimeState — created lazily on first need, not saved
     * directly (the PieceDefinition round-trips whatever data it holds through extraData).
     */
    public Object runtimeBlockEntity;

    public PlacedPiece(PieceDefinition definition, BlockPos anchor, Direction facing) {
        this.definition = definition;
        this.anchor = anchor;
        this.facing = facing;
        this.footprint = rotate(definition.baseFootprint(), facing.getAxis());
        this.currentSpeed = 0f;
    }

    public boolean occupies(int x, int y, int z) {
        return x >= anchor.getX() && x < anchor.getX() + footprint.getX()
            && y >= anchor.getY() && y < anchor.getY() + footprint.getY()
            && z >= anchor.getZ() && z < anchor.getZ() + footprint.getZ();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", definition.id().toString());
        tag.putInt("ax", anchor.getX());
        tag.putInt("ay", anchor.getY());
        tag.putInt("az", anchor.getZ());
        tag.putString("facing", facing.getName());
        tag.putFloat("speed", currentSpeed);
        if (!extraData.isEmpty()) tag.put("extra", extraData.copy());
        return tag;
    }

    public static PlacedPiece load(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.parse(tag.getString("type"));
        PieceDefinition def = PieceDefinition.getOrThrow(id);
        BlockPos anchor = new BlockPos(tag.getInt("ax"), tag.getInt("ay"), tag.getInt("az"));
        Direction facing = Direction.byName(tag.getString("facing"));
        PlacedPiece piece = new PlacedPiece(def, anchor, facing);
        piece.currentSpeed = tag.getFloat("speed");
        if (tag.contains("extra")) piece.extraData = tag.getCompound("extra");
        return piece;
    }

    private static Vec3i rotate(Vec3i base, Direction.Axis axis) {
        return switch (axis) {
            case X -> new Vec3i(base.getZ(), base.getY(), base.getX());
            case Y -> new Vec3i(base.getX(), base.getZ(), base.getY());
            case Z -> base;
        };
    }
}
