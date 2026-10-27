package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.items.BuildMode;
import com.therappha.tinyblocks.items.MinimizerBuild;
import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A Minimizer in a shape mode: two corner clicks build the shape between them. */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class MinimizerShapesGameTest {

    /**
     * Corners on top of stone at (0,0,0) and (3,2,3): the cells above them, (0,1,0) and (3,3,3), span a 4x3x4 box,
     * which takes in the stone at (3,2,3) itself.
     */
    private static int build(GameTestHelper helper, BuildMode mode, Player player, SubgridBlockEntity be) {
        ItemStack minimizer = new ItemStack(Registration.MINIMIZER.get());
        while (MinimizerBuild.mode(minimizer) != mode) MinimizerBuild.cycle(minimizer);
        player.setItemInHand(InteractionHand.OFF_HAND, minimizer);
        int before = be.getPieces().size();
        SubgridTestSupport.rightClick(be, player, SubgridTestSupport.topHit(be, new BlockPos(0, 0, 0)));
        helper.assertTrue(MinimizerBuild.corner(player.getOffhandItem()) != null, "the first click set no corner");
        SubgridTestSupport.rightClick(be, player, SubgridTestSupport.topHit(be, new BlockPos(3, 2, 3)));
        helper.assertTrue(MinimizerBuild.corner(player.getOffhandItem()) == null, "the corner was kept after building");
        return be.getPieces().size() - before;
    }

    private static SubgridBlockEntity stoneCorners(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState());
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(3, 2, 3), Blocks.STONE.defaultBlockState());
        return be;
    }

    @GameTest(template = "empty_platform")
    public void fillBuildsTheWholeBoxAndSpendsTheBlocks(GameTestHelper helper) {
        SubgridBlockEntity be = stoneCorners(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 64));

        int placed = build(helper, BuildMode.FILL, player, be);

        helper.assertTrue(placed == 47, "placed " + placed + " pieces, not 48 cells less the stone already in one");
        helper.assertTrue(player.getMainHandItem().getCount() == 64 - 47, "dirt left: " + player.getMainHandItem().getCount());
        helper.assertTrue(SubgridTestSupport.stateOf(be, new BlockPos(1, 2, 1)).is(Blocks.DIRT), "the middle of the box is empty");
        helper.succeed();
    }

    @GameTest(template = "empty_platform")
    public void hollowLeavesTheMiddleEmpty(GameTestHelper helper) {
        SubgridBlockEntity be = stoneCorners(helper);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));

        int placed = build(helper, BuildMode.HOLLOW, player, be);

        // 48 cells less the 2x1x2 inside, less the stone already on the shell.
        helper.assertTrue(placed == 43, "placed " + placed);
        helper.assertTrue(SubgridTestSupport.stateOf(be, new BlockPos(1, 2, 1)) == null, "the middle was filled");
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "creative spent blocks");
        helper.succeed();
    }

    /** A sneaking click drops the first corner. */
    @GameTest(template = "empty_platform")
    public void sneakingDropsTheCorner(GameTestHelper helper) {
        SubgridBlockEntity be = stoneCorners(helper);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
        ItemStack minimizer = new ItemStack(Registration.MINIMIZER.get());
        MinimizerBuild.cycle(minimizer); // FILL
        player.setItemInHand(InteractionHand.OFF_HAND, minimizer);

        SubgridTestSupport.rightClick(be, player, SubgridTestSupport.topHit(be, new BlockPos(0, 0, 0)));
        player.setShiftKeyDown(true);
        SubgridTestSupport.rightClick(be, player, SubgridTestSupport.faceHit(be, new BlockPos(3, 2, 3), Direction.UP));

        helper.assertTrue(MinimizerBuild.corner(player.getOffhandItem()) == null, "the corner is still held");
        helper.assertTrue(be.getPieces().size() == 2, "a sneaking click built something");
        helper.succeed();
    }

    /** Ctrl + scroll's lift raises the second corner: corners at layer 1, lifted by 2, fill layers 1 to 3. */
    @GameTest(template = "empty_platform")
    public void liftRaisesTheSecondCorner(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState());
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(3, 0, 3), Blocks.STONE.defaultBlockState());
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
        ItemStack minimizer = new ItemStack(Registration.MINIMIZER.get());
        MinimizerBuild.cycle(minimizer); // FILL
        player.setItemInHand(InteractionHand.OFF_HAND, minimizer);

        SubgridTestSupport.rightClick(be, player, SubgridTestSupport.topHit(be, new BlockPos(0, 0, 0)));
        MinimizerBuild.addLift(player.getOffhandItem(), 2);
        SubgridTestSupport.rightClick(be, player, SubgridTestSupport.topHit(be, new BlockPos(3, 0, 3)));

        helper.assertTrue(be.getPieces().size() == 2 + 48, "placed " + (be.getPieces().size() - 2) + ", not 4x3x4");
        helper.assertTrue(SubgridTestSupport.stateOf(be, new BlockPos(3, 3, 3)) != null, "nothing at the lifted corner");
        helper.assertTrue(MinimizerBuild.lift(player.getOffhandItem()) == 0, "the lift was kept after building");
        helper.succeed();
    }

    /** One Minimizer for every size: set to 1/5, a block placed against a real one goes into a new 5-grid. */
    @GameTest(template = "empty_platform")
    public void aMinimizerSetTo5MakesA5Grid(GameTestHelper helper) {
        BlockPos floor = new BlockPos(2, 1, 2);
        helper.setBlock(floor, Blocks.STONE);
        helper.setBlock(floor.above(), Blocks.AIR);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
        ItemStack minimizer = new ItemStack(Registration.MINIMIZER.get());
        helper.assertTrue(Registration.MINIMIZER.get().gridSize(minimizer) == 7, "a new Minimizer does not start at 1/7");
        com.therappha.tinyblocks.items.MinimizerItem.setGridSize(minimizer, 5);
        player.setItemInHand(InteractionHand.OFF_HAND, minimizer);

        BlockPos real = helper.absolutePos(floor);
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(real).add(0, 0.5, 0), Direction.UP, real, false);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, real, hit));

        helper.assertTrue(helper.getLevel().getBlockEntity(real.above()) instanceof SubgridBlockEntity grid
                        && grid.getGridSize() == 5 && grid.getPieces().size() == 1
                        && helper.getLevel().getBlockState(real.above()).is(Registration.subgridBlock(5)),
                "no 5-grid with the dirt in it above the stone: " + helper.getLevel().getBlockState(real.above()));
        helper.succeed();
    }

    private static void clickTop(GameTestHelper helper, Player player, BlockPos at) {
        BlockPos real = helper.absolutePos(at);
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(real).add(0, 0.5, 0), Direction.UP, real, false);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, real, hit));
    }

    /** At the normal size (1/1) a shape is built with normal blocks: a 3x3 floor of dirt between two corners. */
    @GameTest(template = "empty_platform")
    public void atTheNormalSizeAShapeIsBuiltWithNormalBlocks(GameTestHelper helper) {
        for (BlockPos at : java.util.List.of(new BlockPos(1, 1, 1), new BlockPos(3, 1, 3))) {
            helper.setBlock(at, Blocks.STONE);
        }
        for (BlockPos at : BlockPos.betweenClosed(new BlockPos(1, 2, 1), new BlockPos(3, 2, 3))) helper.setBlock(at, Blocks.AIR);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
        ItemStack minimizer = new ItemStack(Registration.MINIMIZER.get());
        com.therappha.tinyblocks.items.MinimizerItem.setGridSize(minimizer, 1);
        MinimizerBuild.cycle(minimizer); // FILL
        player.setItemInHand(InteractionHand.OFF_HAND, minimizer);

        clickTop(helper, player, new BlockPos(1, 1, 1));
        clickTop(helper, player, new BlockPos(3, 1, 3));

        long dirt = java.util.stream.StreamSupport.stream(BlockPos.betweenClosed(new BlockPos(1, 2, 1), new BlockPos(3, 2, 3)).spliterator(), false)
                .filter(at -> helper.getBlockState(at).is(Blocks.DIRT)).count();
        helper.assertTrue(dirt == 9, "dirt blocks in the 3x3 floor: " + dirt);
        helper.succeed();
    }

    /** At the normal size, in the Normal mode, the Minimizer leaves a click on a piece to vanilla (a normal block). */
    @GameTest(template = "empty_platform")
    public void atTheNormalSizeAClickOnAPieceIsLeftToVanilla(GameTestHelper helper) {
        SubgridBlockEntity be = stoneCorners(helper);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
        ItemStack minimizer = new ItemStack(Registration.MINIMIZER.get());
        com.therappha.tinyblocks.items.MinimizerItem.setGridSize(minimizer, 1);
        player.setItemInHand(InteractionHand.OFF_HAND, minimizer);

        var event = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND,
                be.getBlockPos(), SubgridTestSupport.topHit(be, new BlockPos(0, 0, 0)));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);

        helper.assertTrue(!event.isCanceled(), "the click on the piece was taken, not left to vanilla");
        helper.assertTrue(be.getPieces().size() == 2, "a piece was placed");
        helper.succeed();
    }

    /** Minimize me: holding a 1/7 Minimizer the player shrinks to 1/7 (size, speed, step), and back on the next press. */
    @GameTest(template = "empty_platform")
    public void minimizeMeShrinksThePlayerToTheMinimizersSizeAndBack(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Registration.MINIMIZER.get())); // starts at 1/7
        double height = player.getBbHeight(), speed = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);

        com.therappha.tinyblocks.items.MinimizeMe.toggle(player);
        player.refreshDimensions();
        helper.assertTrue(Math.abs(player.getScale() - 1 / 7f) < 1e-4, "scale " + player.getScale() + ", not 1/7");
        helper.assertTrue(Math.abs(player.getBbHeight() - height / 7) < 1e-4, "hitbox " + player.getBbHeight() + ", not " + height / 7);
        helper.assertTrue(Math.abs(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) - speed / 7) < 1e-6,
                "speed not a seventh");

        com.therappha.tinyblocks.items.MinimizeMe.toggle(player);
        player.refreshDimensions();
        helper.assertTrue(player.getScale() == 1 && Math.abs(player.getBbHeight() - height) < 1e-4, "not back to full size");
        helper.succeed();
    }
}
