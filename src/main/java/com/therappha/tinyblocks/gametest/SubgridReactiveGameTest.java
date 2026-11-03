package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** What is too random to compare two runs of: fire, melting, leaves falling off, and what TNT leaves behind. */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridReactiveGameTest {

    private static final BlockPos GRID = new BlockPos(2, 2, 2);

    private static SubgridBlockEntity grid(GameTestHelper helper, int size) {
        helper.setBlock(GRID, Blocks.AIR);
        return SubgridBlockEntity.createAt(helper.getLevel(), helper.absolutePos(GRID), size);
    }

    private static boolean has(SubgridBlockEntity be, net.minecraft.world.level.block.Block block) {
        return be.getPieces().stream().anyMatch(p -> p.definition.renderState(p).is(block));
    }

    private static void randomTicks(SubgridBlockEntity be, PlacedPiece piece, ServerLevel level, int times) {
        for (int i = 0; i < times; i++) piece.definition.randomTick(piece, level, be.getBlockPos(), be);
    }

    /**
     * The test server keeps fire from ticking (doFireTick false). Turned on while a fire test runs, and back off when
     * the last one running is done: tests run side by side.
     */
    private static int fireTests = 0;

    private static GameTestSequence withFireTicking(GameTestHelper helper, Runnable until) {
        GameRules.BooleanValue rule = helper.getLevel().getGameRules().getRule(GameRules.RULE_DOFIRETICK);
        if (fireTests++ == 0) rule.set(true, helper.getLevel().getServer());
        return helper.startSequence().thenWaitUntil(until).thenExecute(() -> {
            if (--fireTests == 0) rule.set(false, helper.getLevel().getServer());
        });
    }

    @GameTest(template = "empty_platform", timeoutTicks = 1200)
    public void fireOnStoneBurnsOut(GameTestHelper helper) {
        SubgridBlockEntity be = grid(helper, 8);
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(3, 0, 3), Blocks.STONE.defaultBlockState());
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(3, 1, 3), Blocks.FIRE.defaultBlockState());
        withFireTicking(helper, () -> helper.assertTrue(!has(be, Blocks.FIRE), "still burning")).thenSucceed();
    }

    @GameTest(template = "empty_platform", timeoutTicks = 3000)
    public void fireOnNetherrackBurnsOn(GameTestHelper helper) {
        SubgridBlockEntity be = grid(helper, 8);
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(3, 0, 3), Blocks.NETHERRACK.defaultBlockState());
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(3, 1, 3), Blocks.FIRE.defaultBlockState());
        int[] ticks = {0};
        // Long enough for fire on stone to have gone out many times over, and aged all the way.
        withFireTicking(helper, () -> {
            helper.assertTrue(has(be, Blocks.FIRE), "fire on netherrack went out");
            helper.assertTrue(++ticks[0] > 1000, "not yet");
        }).thenSucceed();
    }

    /**
     * Fire gets from the plank it was lit on to another cell, and what it burns goes without a trace. How soon is up to
     * chance, so the same fire is lit on real planks beside it: once the real one has spread, the tiny one has had
     * long enough when it hasn't done so 3000 ticks later.
     */
    @GameTest(template = "empty_platform", timeoutTicks = 12000)
    public void fireSpreadsAlongPlanksAsInTheWorld(GameTestHelper helper) {
        SubgridBlockEntity be = grid(helper, 8);
        int planks = 0;
        for (BlockPos cell : BlockPos.betweenClosed(1, 0, 3, 6, 0, 4)) {
            SubgridTestSupport.placePiece(be, helper.getLevel(), cell.immutable(), Blocks.OAK_PLANKS.defaultBlockState());
            planks++;
        }
        int all = planks;
        BlockPos lit = new BlockPos(1, 1, 3);
        SubgridTestSupport.placePiece(be, helper.getLevel(), lit, Blocks.FIRE.defaultBlockState());
        BlockPos real = new BlockPos(0, 2, 5), realLit = real.above();
        for (BlockPos pos : BlockPos.betweenClosed(real, real.offset(5, 0, 1))) helper.setBlock(pos, Blocks.OAK_PLANKS);
        for (BlockPos pos : BlockPos.betweenClosed(real.above(), real.offset(5, 3, 1))) helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(realLit, Blocks.FIRE);
        boolean[] spread = new boolean[1];
        int[] tick = {0}, realSpreadAt = {-1};
        withFireTicking(helper, () -> {
            tick[0]++;
            spread[0] |= be.getPieces().stream().anyMatch(p -> p.definition.renderState(p).is(Blocks.FIRE) && !p.anchor.equals(lit));
            if (realSpreadAt[0] < 0) {
                for (BlockPos pos : BlockPos.betweenClosed(real.above(), real.offset(5, 3, 1))) {
                    if (!pos.equals(realLit) && helper.getBlockState(pos).is(Blocks.FIRE)) realSpreadAt[0] = tick[0];
                }
            }
            long left = be.getPieces().stream().filter(p -> p.definition.renderState(p).is(Blocks.OAK_PLANKS)).count();
            if (realSpreadAt[0] >= 0 && tick[0] > realSpreadAt[0] + 3000 && !spread[0]) {
                throw new net.minecraft.gametest.framework.GameTestAssertException("the real fire spread at tick " + realSpreadAt[0]
                        + ", the tiny one hasn't by " + tick[0] + " (" + left + " of " + all + " planks left)");
            }
            helper.assertTrue(spread[0] && left < all, "not yet");
        }).thenExecute(() -> helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new AABB(be.getBlockPos()).inflate(3)).isEmpty(), "burnt planks dropped items")).thenSucceed();
    }

    @GameTest(template = "empty_platform", timeoutTicks = 40)
    public void iceMeltsByALightAndHoldsInTheDark(GameTestHelper helper) {
        helper.setBlock(GRID.above(), Blocks.STONE);
        helper.setBlock(GRID.east(), Blocks.STONE);
        helper.setBlock(GRID.west(), Blocks.STONE);
        helper.setBlock(GRID.north(), Blocks.STONE);
        helper.setBlock(GRID.south(), Blocks.STONE);
        SubgridBlockEntity be = grid(helper, 8);
        BlockPos at = new BlockPos(3, 1, 3);
        PlacedPiece ice = SubgridTestSupport.placePiece(be, helper.getLevel(), at, Blocks.ICE.defaultBlockState());
        SubgridTestSupport.placePiece(be, helper.getLevel(), at.below(), Blocks.STONE.defaultBlockState());
        helper.startSequence().thenIdle(3).thenExecute(() -> {
            randomTicks(be, ice, helper.getLevel(), 5);
            helper.assertTrue(SubgridTestSupport.pieceIs(be, at, Blocks.ICE), "ice melted with no light on it");
            helper.setBlock(GRID.east(), Blocks.GLOWSTONE);
        }).thenIdle(5).thenExecute(() -> {
            randomTicks(be, ice, helper.getLevel(), 5);
            BlockState there = SubgridTestSupport.stateOf(be, at);
            helper.assertTrue(there != null && there.is(Blocks.WATER), "ice in the light of a glowstone is " + there);
        }).thenSucceed();
    }

    /** With no log near it, a leaf piece's count runs out and it falls off at a random tick; with a log near, it stays. */
    @GameTest(template = "empty_platform", timeoutTicks = 100)
    public void leavesWithNoLogFallOff(GameTestHelper helper) {
        SubgridBlockEntity be = grid(helper, 16);
        BlockPos log = new BlockPos(4, 4, 4), near = log.east(), far = near.east();
        SubgridTestSupport.placePiece(be, helper.getLevel(), log, Blocks.OAK_LOG.defaultBlockState());
        PlacedPiece leaf = SubgridTestSupport.placePiece(be, helper.getLevel(), near, Blocks.OAK_LEAVES.defaultBlockState());
        PlacedPiece next = SubgridTestSupport.placePiece(be, helper.getLevel(), far, Blocks.OAK_LEAVES.defaultBlockState());
        helper.startSequence().thenIdle(10).thenExecute(() -> {
            helper.assertTrue(SubgridTestSupport.valueAt(helper, be, near, LeavesBlock.DISTANCE) == 1, "no count");
            randomTicks(be, leaf, helper.getLevel(), 5);
            helper.assertTrue(SubgridTestSupport.stateOf(be, near) != null, "a leaf by a log fell off");
            be.removePieceAt(log.getX(), log.getY(), log.getZ());
        }).thenIdle(20).thenExecute(() -> {
            helper.assertTrue(SubgridTestSupport.valueAt(helper, be, near, LeavesBlock.DISTANCE) == 7, "still counts");
            randomTicks(be, leaf, helper.getLevel(), 1);
            randomTicks(be, next, helper.getLevel(), 1);
            helper.assertTrue(be.getPieces().isEmpty(), "leaves left with no log: " + be.getPieces().size());
        }).thenSucceed();
    }

    /** Lit by redstone a TNT piece goes, and there is a primed TNT for it, as a real one has. */
    @GameTest(template = "empty_platform", timeoutTicks = 40)
    public void tntLitByRedstoneGoesOff(GameTestHelper helper) {
        SubgridBlockEntity be = grid(helper, 8);
        BlockPos at = new BlockPos(3, 1, 3);
        SubgridTestSupport.placePiece(be, helper.getLevel(), at.below(), Blocks.STONE.defaultBlockState());
        SubgridTestSupport.placePiece(be, helper.getLevel(), at, Blocks.TNT.defaultBlockState());
        helper.startSequence().thenIdle(3).thenExecute(() -> SubgridTestSupport.placePiece(be, helper.getLevel(), at.east(), Blocks.REDSTONE_BLOCK.defaultBlockState()))
                .thenIdle(3).thenExecute(() -> {
                    helper.assertTrue(SubgridTestSupport.stateOf(be, at) == null, "the TNT piece is still there");
                    AABB around = new AABB(be.getBlockPos()).inflate(3);
                    helper.assertTrue(!helper.getLevel().getEntitiesOfClass(PrimedTnt.class, around).isEmpty(), "the TNT went and no primed TNT came of it");
                    helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, around).isEmpty(), "lit TNT left a TNT item too");
                }).thenSucceed();
    }
}
