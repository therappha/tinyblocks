package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Growth is random, so these say what must come of it rather than comparing runs. Bone meal goes in the way a player's
 * click does: the right click event SubgridEventHandler listens to.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridGrowthGameTest {

    private static final BlockPos GRID = new BlockPos(2, 2, 2);
    private static final BlockState FARMLAND = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7);

    private static SubgridBlockEntity grid(GameTestHelper helper, int size) {
        helper.setBlock(GRID, Blocks.AIR);
        return SubgridBlockEntity.createAt(helper.getLevel(), helper.absolutePos(GRID), size);
    }

    private static ServerPlayer holding(GameTestHelper helper, SubgridBlockEntity be, ItemStack stack) {
        ServerPlayer player = SubgridTestSupport.serverPlayer(helper, be);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }

    private static void click(SubgridBlockEntity be, ServerPlayer player, BlockPos cell) {
        SubgridTestSupport.rightClick(be, player, SubgridTestSupport.faceHit(be, cell, Direction.UP));
    }

    private static PlacedPiece wheat(SubgridBlockEntity be, ServerLevel level, BlockPos at, int age) {
        SubgridTestSupport.placePiece(be, level, at.below(), FARMLAND);
        return SubgridTestSupport.placePiece(be, level, at, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, age));
    }

    @GameTest(template = "empty_platform", timeoutTicks = 40)
    public void boneMealGrowsACropAndIsUsedUp(GameTestHelper helper) {
        SubgridBlockEntity be = grid(helper, 8);
        BlockPos at = new BlockPos(3, 1, 3);
        wheat(be, helper.getLevel(), at, 0);
        ServerPlayer player = holding(helper, be, new ItemStack(Items.BONE_MEAL, 3));
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            click(be, player, at);
            helper.assertTrue(SubgridTestSupport.valueAt(helper, be, at, CropBlock.AGE) > 0, "the crop didn't grow");
            helper.assertTrue(player.getMainHandItem().getCount() == 2, player.getMainHandItem().getCount() + " bone meal left, not 2");
        }).thenSucceed();
    }

    @GameTest(template = "empty_platform", timeoutTicks = 40)
    public void boneMealOnARipeCropIsNotUsedUp(GameTestHelper helper) {
        SubgridBlockEntity be = grid(helper, 8);
        BlockPos at = new BlockPos(3, 1, 3);
        wheat(be, helper.getLevel(), at, 7);
        ServerPlayer player = holding(helper, be, new ItemStack(Items.BONE_MEAL, 3));
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            click(be, player, at);
            helper.assertTrue(player.getMainHandItem().getCount() == 3, "bone meal went on a crop with nothing left to grow");
        }).thenSucceed();
    }

    /** Grass grows on the cells above it in a patch: all of it placed through the world generator's own features. */
    @GameTest(template = "empty_platform", timeoutTicks = 60)
    public void boneMealOnGrassGrowsPlantsAndIsUsedUp(GameTestHelper helper) {
        SubgridBlockEntity be = grid(helper, 16);
        for (BlockPos cell : BlockPos.betweenClosed(0, 0, 0, 15, 0, 15)) {
            SubgridTestSupport.placePiece(be, helper.getLevel(), cell.immutable(), Blocks.GRASS_BLOCK.defaultBlockState());
        }
        BlockPos at = new BlockPos(7, 0, 7);
        ServerPlayer player = holding(helper, be, new ItemStack(Items.BONE_MEAL, 8));
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            click(be, player, at);
            helper.assertTrue(player.getMainHandItem().getCount() == 7, player.getMainHandItem().getCount() + " bone meal left, not 7");
            helper.assertTrue(be.getPieces().size() > 256, "bone meal on grass grew nothing");
        }).thenSucceed();
    }

    /** A sapling takes a few goes, and then is a tree, of logs and leaves, in pieces. */
    @GameTest(template = "empty_platform", timeoutTicks = 60)
    public void boneMealGrowsASaplingIntoATree(GameTestHelper helper) {
        SubgridBlockEntity be = grid(helper, 16);
        for (BlockPos cell : BlockPos.betweenClosed(4, 0, 4, 11, 0, 11)) {
            SubgridTestSupport.placePiece(be, helper.getLevel(), cell.immutable(), Blocks.DIRT.defaultBlockState());
        }
        BlockPos at = new BlockPos(7, 1, 7);
        SubgridTestSupport.placePiece(be, helper.getLevel(), at, Blocks.OAK_SAPLING.defaultBlockState());
        ServerPlayer player = holding(helper, be, new ItemStack(Items.BONE_MEAL, 64));
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            for (int i = 0; i < 40 && SubgridTestSupport.pieceIs(be, at, Blocks.OAK_SAPLING); i++) click(be, player, at);
            BlockState there = SubgridTestSupport.stateOf(be, at);
            helper.assertTrue(there != null && there.is(Blocks.OAK_LOG), "forty goes of bone meal left " + there);
            helper.assertTrue(player.getMainHandItem().getCount() < 64, "no bone meal was used");
            long leaves = be.getPieces().stream().filter(p -> p.definition.renderState(p).is(Blocks.OAK_LEAVES)).count();
            helper.assertTrue(leaves > 10, "the tree has " + leaves + " leaves");
        }).thenSucceed();
    }

    /** A crop's own random tick, run many times: it grows the way it does in the world, and stops when ripe. */
    @GameTest(template = "empty_platform", timeoutTicks = 40)
    public void randomTicksGrowACropToRipe(GameTestHelper helper) {
        SubgridBlockEntity be = grid(helper, 8);
        BlockPos at = new BlockPos(3, 1, 3);
        PlacedPiece wheat = wheat(be, helper.getLevel(), at, 0);
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            for (int i = 0; i < 2000; i++) wheat.definition.randomTick(wheat, helper.getLevel(), be.getBlockPos(), be);
            helper.assertTrue(SubgridTestSupport.valueAt(helper, be, at, CropBlock.AGE) == 7, "not ripe after 2000 random ticks");
        }).thenSucceed();
    }

    /** The grid's own sampler reaches a piece: in a grid of size 2 a piece gets a roll about every three ticks. */
    @GameTest(template = "empty_platform", timeoutTicks = 3000)
    public void aGridSamplesItsOwnRandomTicks(GameTestHelper helper) {
        SubgridBlockEntity be = grid(helper, 2);
        BlockPos at = new BlockPos(0, 1, 0);
        wheat(be, helper.getLevel(), at, 0);
        helper.succeedWhen(() -> helper.assertTrue(SubgridTestSupport.valueAt(helper, be, at, CropBlock.AGE) > 0, "not grown yet"));
    }

    @GameTest(template = "empty_platform", timeoutTicks = 3000)
    public void sugarCaneAndCactusGrowUpOnRandomTicks(GameTestHelper helper) {
        SubgridBlockEntity be = grid(helper, 16);
        ServerLevel level = helper.getLevel();
        SubgridTestSupport.placePiece(be, level, new BlockPos(3, 0, 3), Blocks.SAND.defaultBlockState());
        PlacedPiece cactus = SubgridTestSupport.placePiece(be, level, new BlockPos(3, 1, 3), Blocks.CACTUS.defaultBlockState());
        SubgridTestSupport.placePiece(be, level, new BlockPos(8, 0, 8), Blocks.SAND.defaultBlockState());
        SubgridTestSupport.placePiece(be, level, new BlockPos(9, 0, 8), Blocks.WATER.defaultBlockState());
        PlacedPiece cane = SubgridTestSupport.placePiece(be, level, new BlockPos(8, 1, 8), Blocks.SUGAR_CANE.defaultBlockState());
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            for (int i = 0; i < 40; i++) {
                cactus.definition.randomTick(cactus, level, be.getBlockPos(), be);
                cane.definition.randomTick(cane, level, be.getBlockPos(), be);
            }
            helper.assertTrue(SubgridTestSupport.pieceIs(be, new BlockPos(3, 2, 3), Blocks.CACTUS), "the cactus didn't grow");
            helper.assertTrue(SubgridTestSupport.pieceIs(be, new BlockPos(8, 2, 8), Blocks.SUGAR_CANE), "the cane didn't grow");
        }).thenSucceed();
    }

    /**
     * Random tick speed 0 stops growth in the world; issue #51: a grid has its own sampler that doesn't read the rule.
     * The rule is the whole server's, so it is 0 only while this grid's own ticks are run here, 1200 of them in one go.
     */
    @GameTest(template = "empty_platform", timeoutTicks = 40, required = false)
    public void randomTickSpeedZeroStopsAGridToo(GameTestHelper helper) {
        SubgridBlockEntity be = grid(helper, 2);
        BlockPos at = new BlockPos(0, 1, 0);
        wheat(be, helper.getLevel(), at, 0);
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            GameRules.IntegerValue rule = helper.getLevel().getGameRules().getRule(GameRules.RULE_RANDOMTICKING);
            int speed = rule.get();
            rule.set(0, helper.getLevel().getServer());
            try {
                for (int i = 0; i < 1200; i++) be.serverTick(helper.getLevel());
            } finally {
                rule.set(speed, helper.getLevel().getServer());
            }
            helper.assertTrue(SubgridTestSupport.valueAt(helper, be, at, CropBlock.AGE) == 0, "it grew with random tick speed 0");
        }).thenSucceed();
    }
}
