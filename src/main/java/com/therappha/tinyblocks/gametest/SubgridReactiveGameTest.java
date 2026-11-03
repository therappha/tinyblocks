package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.List;
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
     * Fire's ticks are random, so a test of them run by the clock passes or fails by chance. Instead each fire is ticked
     * here, {@code times} times in one go, with the level's random seeded: the same draws, so the same outcome, every
     * run. The test server keeps fire from ticking (doFireTick false); it is on only for this call, so no other test's
     * fire runs meanwhile. {@code tick} is one tick of every fire there is; {@code done} stops early.
     */
    private static void burn(GameTestHelper helper, int times, long seed, Runnable tick, java.util.function.BooleanSupplier done) {
        ServerLevel level = helper.getLevel();
        GameRules.BooleanValue rule = level.getGameRules().getRule(GameRules.RULE_DOFIRETICK);
        boolean was = rule.get();
        rule.set(true, level.getServer());
        level.random.setSeed(seed);
        try {
            for (int i = 0; i < times && !done.getAsBoolean(); i++) tick.run();
        } finally {
            rule.set(was, level.getServer());
        }
    }

    /** One scheduled tick of each fire piece in the grid, through the piece, as the grid's tick queue runs it. */
    private static Runnable fireTicks(SubgridBlockEntity be, ServerLevel level) {
        return () -> {
            for (PlacedPiece piece : new java.util.ArrayList<>(be.getPieces())) {
                if (piece.definition.renderState(piece).is(Blocks.FIRE)) piece.definition.scheduledTick(piece, level, be.getBlockPos(), be, Blocks.FIRE);
            }
        };
    }

    @GameTest(template = "empty_platform", timeoutTicks = 40)
    public void fireOnStoneBurnsOut(GameTestHelper helper) {
        SubgridBlockEntity be = grid(helper, 8);
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(3, 0, 3), Blocks.STONE.defaultBlockState());
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(3, 1, 3), Blocks.FIRE.defaultBlockState());
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            // It goes once it has aged past 3, about every third tick: some 12 ticks, far short of 200.
            burn(helper, 200, 1L, fireTicks(be, helper.getLevel()), () -> !has(be, Blocks.FIRE));
            helper.assertTrue(!has(be, Blocks.FIRE), "fire on stone still burns after 200 of its ticks");
        }).thenSucceed();
    }

    @GameTest(template = "empty_platform", timeoutTicks = 40)
    public void fireOnNetherrackBurnsOn(GameTestHelper helper) {
        SubgridBlockEntity be = grid(helper, 8);
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(3, 0, 3), Blocks.NETHERRACK.defaultBlockState());
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(3, 1, 3), Blocks.FIRE.defaultBlockState());
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            // Netherrack burns forever: no draw of the random puts it out, so any seed will do.
            burn(helper, 200, 2L, fireTicks(be, helper.getLevel()), () -> false);
            helper.assertTrue(has(be, Blocks.FIRE), "fire on netherrack went out");
        }).thenSucceed();
    }

    /**
     * Fire gets from the plank it was lit on to another cell, and what it burns goes without a trace. The same fire on
     * real planks beside it, ticked as many times with the same seed, shows that this many ticks is enough to spread.
     */
    @GameTest(template = "empty_platform", timeoutTicks = 40)
    public void fireSpreadsAlongPlanksAsInTheWorld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SubgridBlockEntity be = grid(helper, 8);
        int planks = 0;
        for (BlockPos cell : BlockPos.betweenClosed(1, 0, 3, 6, 0, 4)) {
            SubgridTestSupport.placePiece(be, level, cell.immutable(), Blocks.OAK_PLANKS.defaultBlockState());
            planks++;
        }
        int all = planks;
        BlockPos lit = new BlockPos(1, 1, 3);
        SubgridTestSupport.placePiece(be, level, lit, Blocks.FIRE.defaultBlockState());
        BlockPos real = new BlockPos(0, 2, 5), realLit = real.above();
        List<BlockPos> realArea = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(real, real.offset(5, 0, 1))) {
            helper.setBlock(pos, Blocks.OAK_PLANKS);
            realArea.add(helper.absolutePos(pos)); // a burnt plank can leave fire in its place
        }
        for (BlockPos pos : BlockPos.betweenClosed(real.above(), real.offset(5, 3, 1))) {
            helper.setBlock(pos, Blocks.AIR);
            realArea.add(helper.absolutePos(pos));
        }
        helper.setBlock(realLit, Blocks.FIRE);
        BlockPos realLitAt = helper.absolutePos(realLit);
        java.util.function.BooleanSupplier tinySpread = () -> be.getPieces().stream()
                .anyMatch(p -> p.definition.renderState(p).is(Blocks.FIRE) && !p.anchor.equals(lit));
        java.util.function.BooleanSupplier realSpread = () -> realArea.stream()
                .anyMatch(pos -> !pos.equals(realLitAt) && level.getBlockState(pos).is(Blocks.FIRE));
        int ticks = 400;
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            burn(helper, ticks, 3L, () -> {
                for (BlockPos pos : realArea) {
                    if (level.getBlockState(pos).is(Blocks.FIRE)) level.getBlockState(pos).tick(level, pos, level.random);
                }
            }, realSpread);
            helper.assertTrue(realSpread.getAsBoolean(), "the real fire didn't spread in " + ticks + " ticks either: the test proves nothing");
            boolean[] burnt = new boolean[1];
            burn(helper, ticks, 3L, fireTicks(be, level), () -> burnt[0] = tinySpread.getAsBoolean()
                    && be.getPieces().stream().filter(p -> p.definition.renderState(p).is(Blocks.OAK_PLANKS)).count() < all);
            long left = be.getPieces().stream().filter(p -> p.definition.renderState(p).is(Blocks.OAK_PLANKS)).count();
            helper.assertTrue(tinySpread.getAsBoolean() && left < all, "after " + ticks + " ticks the tiny fire spread: "
                    + tinySpread.getAsBoolean() + ", " + left + " of " + all + " planks left");
            helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(be.getBlockPos()).inflate(3)).isEmpty(),
                    "burnt planks dropped items");
        }).thenSucceed();
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
        // Counted already, so no random tick of the grid's can take them before the log goes.
        PlacedPiece leaf = SubgridTestSupport.placePiece(be, helper.getLevel(), near, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1));
        PlacedPiece next = SubgridTestSupport.placePiece(be, helper.getLevel(), far, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 2));
        helper.startSequence().thenIdle(10).thenExecute(() -> {
            helper.assertTrue(SubgridTestSupport.valueAt(helper, be, near, LeavesBlock.DISTANCE) == 1, "no count");
            randomTicks(be, leaf, helper.getLevel(), 5);
            helper.assertTrue(SubgridTestSupport.stateOf(be, near) != null, "a leaf by a log fell off");
            be.removePieceAt(log.getX(), log.getY(), log.getZ());
        }).thenIdle(20).thenExecute(() -> {
            BlockState left = SubgridTestSupport.stateOf(be, near);
            helper.assertTrue(left == null || left.getValue(LeavesBlock.DISTANCE) == 7, "still counts");
            // The grid's own random ticks may have taken one already: whichever is left goes on its next one.
            if (SubgridTestSupport.stateOf(be, near) != null) randomTicks(be, leaf, helper.getLevel(), 1);
            if (SubgridTestSupport.stateOf(be, far) != null) randomTicks(be, next, helper.getLevel(), 1);
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
