package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** For the beta, a SubgridBlock weighs next to nothing on a Sable sub-level, whatever is inside it. */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridMassGameTest {

    /** Next to nothing, empty or not (PhysicsBlockPropertyHelperMixin), but some: a sub-level of grids must keep a mass. */
    @GameTest(template = "empty_platform")
    public void aSubgridWeighsNextToNothing(GameTestHelper helper) {
        BlockPos chassisPos = new BlockPos(4, 1, 2);
        helper.setBlock(chassisPos, com.simibubi.create.AllBlocks.LINEAR_CHASSIS.getDefaultState());
        double chassis = PhysicsBlockPropertyHelper.getMass(helper.getLevel(), helper.absolutePos(chassisPos), helper.getBlockState(chassisPos));
        BlockPos emptyPos = new BlockPos(2, 1, 2);
        SubgridTestSupport.placeSubgrid(helper, emptyPos);
        BlockPos fullPos = new BlockPos(2, 1, 4);
        SubgridBlockEntity full = SubgridTestSupport.placeSubgrid(helper, fullPos);
        SubgridTestSupport.placePiece(full, helper.getLevel(), new BlockPos(1, 1, 1), Blocks.STONE.defaultBlockState());

        for (BlockPos pos : new BlockPos[] {emptyPos, fullPos}) {
            BlockPos abs = helper.absolutePos(pos);
            var state = helper.getBlockState(pos);
            double mass = PhysicsBlockPropertyHelper.getMass(helper.getLevel(), abs, state);
            helper.assertTrue(mass > 0 && mass < chassis / 10, "subgrid mass " + mass + ", chassis " + chassis);
            helper.assertTrue(dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState.isSolid(helper.getLevel(), abs, state), "a subgrid no longer collides");
        }
        helper.succeed();
    }
}
