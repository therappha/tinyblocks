package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A SubgridBlock collides where its pieces are, asked either way: with an entity's collision context (vanilla
 * movement), or without one — what Sable's collision of entities against a sub-level asks, which a block state
 * answers from its cache when its shape is not dynamic.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridCollisionGameTest {

    @GameTest(template = "empty_platform")
    public void aSubgridCollidesWhereItsPiecesAreWithoutAContext(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState());
        BlockPos pos = be.getBlockPos();
        BlockState state = helper.getLevel().getBlockState(pos);

        helper.assertTrue(!state.getCollisionShape(helper.getLevel(), pos,
                        net.minecraft.world.phys.shapes.CollisionContext.empty()).isEmpty(), "no collision even with a context");
        helper.assertTrue(!state.getCollisionShape(helper.getLevel(), pos).isEmpty(),
                "no collision without a context (Sable's entity collision): the cached empty shape");
        helper.succeed();
    }

    /** How many cells of collision the SubgridBlock has, asked as Sable asks (no context). */
    private static long collidingCells(GameTestHelper helper, SubgridBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        double volume = 0;
        for (net.minecraft.world.phys.AABB box : helper.getLevel().getBlockState(pos).getCollisionShape(helper.getLevel(), pos).toAabbs()) {
            volume += box.getXsize() * box.getYsize() * box.getZsize();
        }
        int size = be.getGridSize();
        return Math.round(volume * size * size * size);
    }

    /** The collision follows the pieces: 5 pieces collide as 5 cells, 10 as 10, and 7 after taking 3 away. */
    @GameTest(template = "empty_platform")
    public void theCollisionIsRebuiltWhenPiecesAreAddedOrRemoved(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        for (int x = 0; x < 5; x++) SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(x, 0, 0), Blocks.STONE.defaultBlockState());
        helper.assertTrue(collidingCells(helper, be) == 5, "5 pieces collide as " + collidingCells(helper, be) + " cells");

        for (int x = 0; x < 5; x++) SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(x, 1, 0), Blocks.STONE.defaultBlockState());
        helper.assertTrue(collidingCells(helper, be) == 10, "10 pieces collide as " + collidingCells(helper, be) + " cells");

        for (int x = 0; x < 3; x++) be.removePieceAt(x, 1, 0);
        helper.assertTrue(collidingCells(helper, be) == 7, "7 pieces collide as " + collidingCells(helper, be) + " cells");
        helper.succeed();
    }
}
