package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Regression coverage for SubgridEventHandler#runItemInteraction: any item's own useOn (not just
 * BlockItem placement) must reach a piece sitting inside a subgrid — a hoe tilling dirt, bone
 * meal growing a crop. Both go through the exact same generic mechanism, so together they also
 * guard against a fix that only special-cased one specific item ever regressing back to that.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridToolGameTest {

    private static final String PLATFORM = "empty_platform";

    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public void hoeTillsDirtIntoFarmland(GameTestHelper helper) {
        BlockPos subgridPos = new BlockPos(2, 1, 2);
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, subgridPos);
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.CREATIVE);

        BlockPos dirtAnchor = new BlockPos(4, 0, 4);
        SubgridTestSupport.placePiece(be, level, dirtAnchor, Blocks.DIRT.defaultBlockState());

        helper.startSequence()
                .thenExecute(() -> SubgridTestSupport.useItemOn(be, level, player, new ItemStack(Items.WOODEN_HOE), dirtAnchor))
                .thenExecute(() -> {
                    var state = SubgridTestSupport.stateOf(be, dirtAnchor);
                    helper.assertTrue(state != null && state.is(Blocks.FARMLAND), "dirt piece was never tilled into farmland");
                })
                .thenSucceed();
    }

    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public void boneMealGrowsAWheatCrop(GameTestHelper helper) {
        BlockPos subgridPos = new BlockPos(2, 1, 2);
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, subgridPos);
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.CREATIVE);

        BlockPos farmlandAnchor = new BlockPos(4, 0, 4);
        BlockPos cropAnchor = farmlandAnchor.above();
        SubgridTestSupport.placePiece(be, level, farmlandAnchor, Blocks.FARMLAND.defaultBlockState());
        SubgridTestSupport.placePiece(be, level, cropAnchor, Blocks.WHEAT.defaultBlockState());

        helper.startSequence()
                .thenExecute(() -> {
                    // BoneMealItem's own growth roll is randomized (not every application succeeds)
                    // — retrying a bounded number of times tests the real, non-deterministic code
                    // path instead of assuming a fixed success rate, while keeping the test itself
                    // deterministic (P(50 independent rolls all failing) is negligible for any
                    // sane vanilla success chance).
                    for (int attempt = 0; attempt < 50; attempt++) {
                        SubgridTestSupport.useItemOn(be, level, player, new ItemStack(Items.BONE_MEAL), cropAnchor);
                        var state = SubgridTestSupport.stateOf(be, cropAnchor);
                        if (state != null && state.getValue(CropBlock.AGE) > 0) return;
                    }
                    helper.fail("wheat crop never grew after 50 bone meal attempts", cropAnchor);
                })
                .thenSucceed();
    }
}
