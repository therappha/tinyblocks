package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A random tick that asks for light (grass/mycelium spreading, crops) must not throw: mods that replace
 * the light storage (Sable) hook the default light queries, which a fake level has no data for.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridRandomTickGameTest {

    private static final String PLATFORM = "empty_platform";

    @GameTest(template = PLATFORM, timeoutTicks = 50)
    public void grassRandomTickReadsLightWithoutThrowing(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();
        PlacedPiece grass = SubgridTestSupport.placePiece(be, level, new BlockPos(3, 0, 3), Blocks.GRASS_BLOCK.defaultBlockState());
        try {
            VanillaBlockPiece.INSTANCE.randomTick(grass, level, be.getBlockPos(), be);
        } catch (RuntimeException e) {
            helper.fail("random tick on a grass piece threw " + e);
            return;
        }
        helper.succeed();
    }
}
