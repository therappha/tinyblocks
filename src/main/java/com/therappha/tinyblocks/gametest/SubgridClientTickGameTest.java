package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A piece's block entity ticked as the client ticks it. */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridClientTickGameTest {

    /**
     * A Create chute's first tick reads its own block at its position to refresh its state. A piece's block entity
     * sits at a virtual position, where the real level holds nothing (void air below the world): ticked against the
     * real level, as the client ticked it, that read threw on every tick.
     */
    @GameTest(template = "empty_platform")
    public void aChutesFirstClientTickFindsItsOwnBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        BlockState chute = AllBlocks.CHUTE.getDefaultState();
        PlacedPiece piece = SubgridTestSupport.placePiece(be, level, new BlockPos(4, 4, 4), chute);
        // A fresh block entity, not yet initialized, as the client builds its copy of the piece.
        BlockEntity fresh = ((EntityBlock) chute.getBlock()).newBlockEntity(be.virtualPosOf(piece.anchor), chute);
        fresh.setLevel(level);
        piece.runtimeBlockEntity = fresh;

        VanillaBlockPiece.INSTANCE.clientTick(piece, level, be.getBlockPos(), be);

        helper.assertTrue(fresh.getBlockState().is(chute.getBlock()), "the chute's block entity lost its block: " + fresh.getBlockState());
        helper.assertTrue(fresh.getLevel() == level, "the chute's block entity wasn't given its level back");
        helper.succeed();
    }
}
