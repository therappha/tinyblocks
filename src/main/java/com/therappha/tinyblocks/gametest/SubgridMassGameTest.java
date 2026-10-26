package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In a Sable sub-level a SubgridBlock weighs what a Create chassis does, whatever is inside it. */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridMassGameTest {

    @GameTest(template = "empty_platform")
    public void subgridWeighsAChassis(GameTestHelper helper) {
        BlockPos chassisPos = new BlockPos(4, 1, 2);
        helper.setBlock(chassisPos, AllBlocks.LINEAR_CHASSIS.getDefaultState());
        double chassis = PhysicsBlockPropertyHelper.getMass(helper.getLevel(), helper.absolutePos(chassisPos),
                helper.getBlockState(chassisPos));

        BlockPos emptyPos = new BlockPos(2, 1, 2);
        SubgridTestSupport.placeSubgrid(helper, emptyPos);
        double empty = PhysicsBlockPropertyHelper.getMass(helper.getLevel(), helper.absolutePos(emptyPos),
                helper.getBlockState(emptyPos));

        BlockPos fullPos = new BlockPos(2, 1, 4);
        SubgridBlockEntity full = SubgridTestSupport.placeSubgrid(helper, fullPos);
        SubgridTestSupport.placePiece(full, helper.getLevel(), new BlockPos(1, 1, 1), Blocks.STONE.defaultBlockState());
        double withPiece = PhysicsBlockPropertyHelper.getMass(helper.getLevel(), helper.absolutePos(fullPos),
                helper.getBlockState(fullPos));

        helper.assertTrue(chassis > 0, "precondition: a chassis has mass, got " + chassis);
        helper.assertTrue(empty == chassis && withPiece == chassis,
                "subgrid mass empty=" + empty + " with a piece=" + withPiece + ", chassis=" + chassis);
        helper.succeed();
    }
}
