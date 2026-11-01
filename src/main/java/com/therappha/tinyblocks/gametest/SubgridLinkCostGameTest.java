package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A tiny link answers as fast as a full-size one, and costs a tick little. */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridLinkCostGameTest {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static BlockState link(boolean receiver) {
        return AllBlocks.REDSTONE_LINK.getDefaultState().setValue(DirectionalBlock.FACING, Direction.UP)
                .setValue(RedstoneLinkBlock.RECEIVER, receiver);
    }

    private static void tune(net.minecraft.world.level.block.entity.BlockEntity linkEntity) {
        LinkBehaviour behaviour = BlockEntityBehaviour.get(linkEntity, LinkBehaviour.TYPE);
        behaviour.setFrequency(true, new ItemStack(Items.DIAMOND));
        behaviour.setFrequency(false, new ItemStack(Items.DIAMOND));
    }

    /** A tiny lever flips a tiny transmitter; a tiny receiver in the same grid turns on within a few ticks. */
    @GameTest(template = "empty_platform", timeoutTicks = 200)
    public void aTinyLinkAnswersQuicklyAndCheaply(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        var level = helper.getLevel();
        for (int x = 1; x <= 6; x++) {
            SubgridTestSupport.placePiece(be, level, new BlockPos(x, 0, 4), Blocks.STONE.defaultBlockState());
        }
        PlacedPiece transmitter = SubgridTestSupport.placePiece(be, level, new BlockPos(2, 1, 4), link(false));
        PlacedPiece lever = SubgridTestSupport.placePiece(be, level, new BlockPos(1, 1, 4), Blocks.LEVER.defaultBlockState()
                .setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, Direction.EAST));
        PlacedPiece receiver = SubgridTestSupport.placePiece(be, level, new BlockPos(5, 1, 4), link(false));
        BlockPos receiverAt = receiver.anchor;
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        long[] flippedAt = {-1};
        long[] answeredAt = {-1};
        long[] tickNanos = {0, 0};

        helper.onEachTick(() -> {
            if (flippedAt[0] >= 0 && answeredAt[0] < 0
                    && SubgridTestSupport.stateOf(be, receiverAt).getValue(RedstoneLinkBlock.POWERED)) {
                answeredAt[0] = helper.getTick();
            }
        });
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    Player sneaking = helper.makeMockPlayer(GameType.SURVIVAL);
                    sneaking.setShiftKeyDown(true);
                    SubgridTestSupport.useOnPiece(be, level, receiver, sneaking);
                    tune(SubgridTestSupport.blockEntityOf(be, transmitter, level));
                })
                .thenIdle(3)
                .thenExecute(() -> tune(SubgridTestSupport.blockEntityOf(be, receiver, level)))
                .thenIdle(5)
                .thenExecute(() -> {
                    SubgridTestSupport.useOnPiece(be, level, lever, player);
                    flippedAt[0] = helper.getTick();
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    // Time a stretch of the grid's own ticking with both links live.
                    long start = System.nanoTime();
                    for (int i = 0; i < 100; i++) {
                        be.serverTick(level);
                    }
                    tickNanos[0] = (System.nanoTime() - start) / 100;
                })
                .thenExecute(() -> {
                    long ticks = answeredAt[0] < 0 ? -1 : answeredAt[0] - flippedAt[0];
                    LOGGER.info("[link-cost] receiver answered after {} ticks; one grid tick takes {} us",
                            ticks, tickNanos[0] / 1000);
                    helper.assertTrue(ticks >= 0 && ticks <= 4, "the tiny receiver answered after " + ticks + " ticks");
                    helper.assertTrue(tickNanos[0] < 2_000_000, "one grid tick takes " + tickNanos[0] / 1000 + " us");
                })
                .thenSucceed();
    }
}
