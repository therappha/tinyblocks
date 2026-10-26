package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.therappha.tinyblocks.TinyBlocks;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A Create block clicked with a block in hand inside a subgrid does what it does full size. */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridCreatePlacementGameTest {

    private static final BlockState SHAFT_X =
            AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X);

    /** On top of {@code cell}, near its east edge: where a player aims to extend an east-west shaft eastwards. */
    private static BlockHitResult topNearEastEdge(SubgridBlockEntity be, BlockPos cell) {
        double size = be.getGridSize();
        Vec3 point = new Vec3(cell.getX() + 0.9, cell.getY() + 1, cell.getZ() + 0.5).scale(1 / size)
                .add(Vec3.atLowerCornerOf(be.getBlockPos()));
        return new BlockHitResult(point, Direction.UP, be.getBlockPos(), false);
    }

    private static Player holding(GameTestHelper helper, ItemStack stack) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }

    /** Create's shaft placement helper: a shaft clicked on its side goes on the end of the shaft line, not on top. */
    @GameTest(template = "empty_platform")
    public void aShaftClickedOnItsSideExtendsTheLine(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        BlockPos clicked = new BlockPos(3, 4, 4);
        SubgridTestSupport.placePiece(be, helper.getLevel(), clicked, SHAFT_X);
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(4, 4, 4), SHAFT_X);
        Player player = holding(helper, AllBlocks.SHAFT.asStack(4));

        SubgridTestSupport.rightClick(be, player, topNearEastEdge(be, clicked));

        BlockState end = SubgridTestSupport.stateOf(be, new BlockPos(5, 4, 4));
        helper.assertTrue(end != null && end.is(AllBlocks.SHAFT.get())
                        && end.getValue(RotatedPillarKineticBlock.AXIS) == Direction.Axis.X,
                "no east-west shaft at the end of the line: " + end);
        helper.assertTrue(SubgridTestSupport.stateOf(be, clicked.above()) == null,
                "the shaft went on top instead: " + SubgridTestSupport.stateOf(be, clicked.above()));
        helper.assertTrue(player.getMainHandItem().getCount() == 3, "the shaft was not taken from the hand");
        helper.succeed();
    }

    /** The end of the line is past the grid's east edge: the shaft goes into a new grid in the block next to it. */
    @GameTest(template = "empty_platform")
    public void aShaftLineExtendsIntoTheNextBlock(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.AIR);
        int edge = be.getGridSize() - 1;
        BlockPos clicked = new BlockPos(edge, 4, 4);
        SubgridTestSupport.placePiece(be, helper.getLevel(), clicked, SHAFT_X);
        Player player = holding(helper, AllBlocks.SHAFT.asStack());

        SubgridTestSupport.rightClick(be, player, topNearEastEdge(be, clicked));

        helper.assertTrue(helper.getLevel().getBlockEntity(be.getBlockPos().east()) instanceof SubgridBlockEntity next
                        && SubgridTestSupport.stateOf(next, new BlockPos(0, 4, 4)) != null
                        && SubgridTestSupport.stateOf(next, new BlockPos(0, 4, 4)).is(AllBlocks.SHAFT.get()),
                "no shaft in the first cell of the grid east of it; east block "
                        + helper.getLevel().getBlockState(be.getBlockPos().east()) + ", pieces here "
                        + be.getPieces().stream().map(p -> p.anchor + "=" + p.runtimeState).toList()
                        + ", hand " + player.getMainHandItem());
        helper.succeed();
    }

    /** Andesite casing on a tiny shaft encases it, as on a full-size one. */
    @GameTest(template = "empty_platform")
    public void casingEncasesATinyShaft(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        BlockPos shaft = new BlockPos(4, 4, 4);
        SubgridTestSupport.placePiece(be, helper.getLevel(), shaft, SHAFT_X);
        Player player = holding(helper, AllBlocks.ANDESITE_CASING.asStack());

        SubgridTestSupport.rightClick(be, player, SubgridTestSupport.topHit(be, shaft));

        BlockState after = SubgridTestSupport.stateOf(be, shaft);
        helper.assertTrue(after != null && after.is(AllBlocks.ANDESITE_ENCASED_SHAFT.get()),
                "the shaft was not encased: " + after);
        helper.assertTrue(SubgridTestSupport.stateOf(be, shaft.above()) == null,
                "the casing was placed as a block on top instead");
        var phantom = SubgridTestSupport.blockEntityOf(be, be.getPieceAt(shaft.getX(), shaft.getY(), shaft.getZ()), helper.getLevel());
        helper.assertTrue(phantom != null && phantom.getType() == com.simibubi.create.AllBlockEntityTypes.ENCASED_SHAFT.get()
                        && phantom.getBlockState().equals(after),
                "the encased shaft kept the shaft's block entity: " + phantom + " " + (phantom == null ? "" : phantom.getBlockState()));
        helper.succeed();
    }

    /** A block with no use of its own still gets the clicked block placed against it. */
    @GameTest(template = "empty_platform")
    public void aPlainBlockStillGoesOnTheClickedFace(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        BlockPos stone = new BlockPos(4, 4, 4);
        SubgridTestSupport.placePiece(be, helper.getLevel(), stone, Blocks.STONE.defaultBlockState());
        Player player = holding(helper, new ItemStack(Items.DIRT));

        SubgridTestSupport.rightClick(be, player, SubgridTestSupport.topHit(be, stone));

        BlockState above = SubgridTestSupport.stateOf(be, stone.above());
        helper.assertTrue(above != null && above.is(Blocks.DIRT), "no dirt on top of the stone: " + above);
        helper.succeed();
    }

    /** The same click by a real (server) player, which other mods' place listeners treat differently from a mock one. */
    @GameTest(template = "empty_platform")
    public void aServerPlayersShaftExtendsTheLine(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        BlockPos clicked = new BlockPos(3, 4, 4);
        SubgridTestSupport.placePiece(be, helper.getLevel(), clicked, SHAFT_X);
        net.minecraft.server.level.ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel());
        player.moveTo(Vec3.atCenterOf(be.getBlockPos()).add(0, 0, 2));
        player.setItemInHand(InteractionHand.MAIN_HAND, AllBlocks.SHAFT.asStack(4));

        SubgridTestSupport.rightClick(be, player, topNearEastEdge(be, clicked));

        BlockState end = SubgridTestSupport.stateOf(be, clicked.east());
        helper.assertTrue(end != null && end.is(AllBlocks.SHAFT.get()),
                "no shaft at the end of the line: " + end + "; on top: " + SubgridTestSupport.stateOf(be, clicked.above()));
        helper.succeed();
    }

    /**
     * A cogwheel clicked onto a large cogwheel goes diagonal to it, where the two mesh — Create's cogwheel item
     * does that in its onItemUseFirst, before the clicked block's own use.
     */
    @GameTest(template = "empty_platform")
    public void aCogwheelOnALargeCogwheelGoesWhereTheyMesh(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        BlockPos large = new BlockPos(4, 4, 4);
        SubgridTestSupport.placePiece(be, helper.getLevel(), large,
                AllBlocks.LARGE_COGWHEEL.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Y));
        Player player = holding(helper, AllBlocks.COGWHEEL.asStack(4));

        SubgridTestSupport.rightClick(be, player, topNearEastEdge(be, large));

        helper.assertTrue(SubgridTestSupport.stateOf(be, large.above()) == null,
                "the cogwheel went straight on top: " + SubgridTestSupport.stateOf(be, large.above()));
        var placed = be.getPieces().stream().filter(p -> !p.anchor.equals(large)).map(p -> p.anchor + "=" + p.runtimeState).toList();
        helper.assertTrue(placed.size() == 1 && placed.get(0).contains("create:cogwheel"),
                "expected one cogwheel placed diagonally, got " + placed);
        helper.succeed();
    }
}
