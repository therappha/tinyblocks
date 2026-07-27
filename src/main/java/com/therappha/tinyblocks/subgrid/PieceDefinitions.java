package com.therappha.tinyblocks.subgrid;

import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public final class PieceDefinitions {

    public static final PieceDefinition STONE_BLOCK = new PieceDefinition(
            ResourceLocation.fromNamespaceAndPath(TinyBlocks.MOD_ID, "stone_block"),
            new Vec3i(1, 1, 1)) {
        @Override
        public BlockState renderState(Direction.Axis axis) {
            return Blocks.STONE.defaultBlockState();
        }

        @Override
        public float destroyTime() { return 1.5f; }

        @Override
        public boolean requiresCorrectTool() { return true; }

        @Override
        public List<ItemStack> drops(PlacedPiece piece) {
            return List.of(new ItemStack(Items.COBBLESTONE));
        }
    };

    public static void init() {}

    private PieceDefinitions() {}
}
