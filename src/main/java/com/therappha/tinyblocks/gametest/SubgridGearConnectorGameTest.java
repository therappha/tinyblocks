package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.compat.create.ConnectorPlacement;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In an even grid the shaft connector is a gear (ShaftConnectorPiece#meshCells): it takes a real shaft outside and
 * meshes inside with small tiny cogwheels on its sides — not with a large one on a diagonal, which doesn't reach it,
 * nor with a shaft.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridGearConnectorGameTest {

    private static final String PLATFORM = "empty_platform";

    private static BlockState alongX(com.tterrag.registrate.util.entry.BlockEntry<?> block) {
        return block.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X);
    }

    /** An 8-grid with a gear connector on its east face, and a real motor against that face turning it. */
    private static PlacedPiece[] gearDrivenByARealMotor(GameTestHelper helper, SubgridBlockEntity[] grid) {
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
        SubgridBlockEntity be = SubgridBlockEntity.createAt(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)), 8);
        if (be == null || !ConnectorPlacement.place(be, helper.getLevel(), Direction.EAST)) throw new IllegalStateException("no gear");
        helper.setBlock(new BlockPos(3, 2, 2), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.WEST));
        grid[0] = be;
        return new PlacedPiece[] {be.getPieceAt(7, 3, 3)};
    }

    private static float speed(GameTestHelper helper, SubgridBlockEntity be, PlacedPiece piece) {
        BlockEntity phantom = SubgridTestSupport.blockEntityOf(be, piece, helper.getLevel());
        return phantom instanceof KineticBlockEntity k ? k.getSpeed() : Float.NaN;
    }

    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public void aSmallCogwheelOnTheGearsSideTurnsTwiceAsFastTheOtherWay(GameTestHelper helper) {
        SubgridBlockEntity[] grid = new SubgridBlockEntity[1];
        PlacedPiece gear = gearDrivenByARealMotor(helper, grid)[0];
        PlacedPiece cog = SubgridTestSupport.placePiece(grid[0], helper.getLevel(), new BlockPos(7, 2, 3), alongX(AllBlocks.COGWHEEL));

        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> {
                    float g = speed(helper, grid[0], gear), c = speed(helper, grid[0], cog);
                    helper.assertTrue(g != 0 && c == -2 * g, "gear " + g + ", small cogwheel on its side " + c + " (not -2x)");
                })
                .thenSucceed();
    }

    /** A large cogwheel on the gear's diagonal sits too far off to touch it: it stays still. */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public void aLargeCogwheelOnTheGearsDiagonalDoesNotTurn(GameTestHelper helper) {
        SubgridBlockEntity[] grid = new SubgridBlockEntity[1];
        PlacedPiece gear = gearDrivenByARealMotor(helper, grid)[0];
        PlacedPiece cog = SubgridTestSupport.placePiece(grid[0], helper.getLevel(), new BlockPos(7, 2, 2), alongX(AllBlocks.LARGE_COGWHEEL));

        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> {
                    helper.assertTrue(speed(helper, grid[0], gear) != 0, "the gear itself is still");
                    helper.assertTrue(speed(helper, grid[0], cog) == 0, "a large cogwheel on the diagonal turns: " + speed(helper, grid[0], cog));
                })
                .thenSucceed();
    }

    /** A tiny shaft right behind the gear is not driven: the gear only meshes with cogwheels. */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public void theGearTakesNoShaft(GameTestHelper helper) {
        SubgridBlockEntity[] grid = new SubgridBlockEntity[1];
        PlacedPiece gear = gearDrivenByARealMotor(helper, grid)[0];
        PlacedPiece shaft = SubgridTestSupport.placePiece(grid[0], helper.getLevel(), new BlockPos(6, 3, 3), alongX(AllBlocks.SHAFT));

        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> {
                    helper.assertTrue(speed(helper, grid[0], gear) != 0, "the gear itself is still");
                    helper.assertTrue(speed(helper, grid[0], shaft) == 0, "a shaft against the gear turns: " + speed(helper, grid[0], shaft));
                })
                .thenSucceed();
    }

    /** The other way: a tiny motor turns a small cogwheel on the gear's side, the gear, and the real shaft outside. */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public void aTinyMotorTurnsTheRealShaftThroughTheGear(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
        SubgridBlockEntity be = SubgridBlockEntity.createAt(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)), 8);
        if (be == null || !ConnectorPlacement.place(be, helper.getLevel(), Direction.EAST)) throw new IllegalStateException("no gear");
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(7, 2, 3), alongX(AllBlocks.COGWHEEL));
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(6, 2, 3),
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(3, 2, 2), alongX(AllBlocks.SHAFT));

        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> helper.assertTrue(helper.getBlockEntity(new BlockPos(3, 2, 2)) instanceof KineticBlockEntity k
                        && k.getSpeed() != 0, "the real shaft outside is still"))
                .thenSucceed();
    }

    /**
     * A small cogwheel clicked onto the gear goes onto a side of it, on its axis — Create itself would refuse a small
     * cogwheel next to a large one — and a shaft clicked onto it goes next to the clicked face as usual, not onto a side.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public void aSmallCogwheelClickedOntoTheGearGoesOntoItsSide(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
        SubgridBlockEntity be = SubgridBlockEntity.createAt(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)), 8);
        if (be == null || !ConnectorPlacement.place(be, helper.getLevel(), Direction.EAST)) throw new IllegalStateException("no gear");
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, AllBlocks.COGWHEEL.asStack());

        SubgridTestSupport.rightClick(be, player, SubgridTestSupport.faceHit(be, new BlockPos(7, 4, 4), Direction.EAST));

        java.util.List<BlockPos> sides = com.therappha.tinyblocks.compat.create.ShaftConnectorPiece.meshCells(Direction.EAST, 8);
        long placed = sides.stream().map(c -> SubgridTestSupport.stateOf(be, c))
                .filter(st -> st != null && st.is(AllBlocks.COGWHEEL.get()) && st.getValue(RotatedPillarKineticBlock.AXIS) == Direction.Axis.X)
                .count();
        helper.assertTrue(placed == 1, "cogwheels on the gear's sides: " + placed + "; pieces "
                + be.getPieces().stream().map(p -> p.anchor + "=" + p.runtimeState).toList());

        int before = be.getPieces().size();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, AllBlocks.SHAFT.asStack());
        SubgridTestSupport.rightClick(be, player, SubgridTestSupport.faceHit(be, new BlockPos(7, 3, 3), Direction.WEST));
        helper.assertTrue(SubgridTestSupport.stateOf(be, new BlockPos(6, 3, 3)) != null && be.getPieces().size() == before + 1,
                "the shaft did not go next to the clicked face of the gear");
        helper.succeed();
    }
}
