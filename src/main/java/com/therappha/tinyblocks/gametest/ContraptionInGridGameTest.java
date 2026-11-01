package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.piston.LinearActuatorBlockEntity;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A contraption can't assemble in a grid: it is refused, and every block it would have taken stays where it is. */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class ContraptionInGridGameTest {

    private static final BlockPos MOTOR = new BlockPos(3, 2, 3), MACHINE = MOTOR.above(), ON = MACHINE.above();

    private static void assembleNextTick(BlockEntity machine, Class<?> declaring) {
        try {
            var field = declaring.getDeclaredField("assembleNextTick");
            field.setAccessible(true);
            field.setBoolean(machine, true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BlockState motor() {
        return AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP);
    }

    private static void checkNothingLost(GameTestHelper helper, SubgridBlockEntity be, int pieces, BlockPos plank) {
        int contraptions = helper.getLevel().getEntitiesOfClass(AbstractContraptionEntity.class,
                new AABB(be.getBlockPos()).inflate(8)).size();
        helper.assertTrue(contraptions == 0, contraptions + " contraptions came out of a grid");
        helper.assertTrue(be.getPieces().size() == pieces, be.getPieces().size() + " pieces left, not " + pieces);
        helper.assertTrue(Blocks.OAK_PLANKS.defaultBlockState().equals(SubgridTestSupport.stateOf(be, plank)), "the plank is gone");
    }

    @GameTest(template = "empty_platform", timeoutTicks = 100)
    public void aBearingInAGridLosesNothing(GameTestHelper helper) {
        var level = helper.getLevel();
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 2, 2));
        SubgridTestSupport.placePiece(be, level, MOTOR, motor());
        PlacedPiece bearing = SubgridTestSupport.placePiece(be, level, MACHINE,
                AllBlocks.MECHANICAL_BEARING.getDefaultState().setValue(BearingBlock.FACING, Direction.UP));
        SubgridTestSupport.placePiece(be, level, ON, Blocks.OAK_PLANKS.defaultBlockState());
        helper.startSequence().thenIdle(5)
                .thenExecute(() -> assembleNextTick(VanillaBlockPiece.boundPhantom(bearing, be, level), MechanicalBearingBlockEntity.class))
                .thenIdle(20).thenExecute(() -> checkNothingLost(helper, be, 3, ON)).thenSucceed();
    }

    @GameTest(template = "empty_platform", timeoutTicks = 100)
    public void aMechanicalPistonInAGridLosesNothing(GameTestHelper helper) {
        var level = helper.getLevel();
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 2, 2));
        // The shaft goes in from the side, and a pole on each end leaves room to move whichever way the motor turns.
        // Stopped, or with no room or no pole, Create gives up by itself and this test would prove nothing.
        SubgridTestSupport.placePiece(be, level, MACHINE.east(),
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.WEST));
        PlacedPiece piston = SubgridTestSupport.placePiece(be, level, MACHINE,
                AllBlocks.MECHANICAL_PISTON.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP)
                        .setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true));
        BlockState pole = AllBlocks.PISTON_EXTENSION_POLE.getDefaultState().setValue(BlockStateProperties.FACING, Direction.UP);
        SubgridTestSupport.placePiece(be, level, MACHINE.below(), pole);
        SubgridTestSupport.placePiece(be, level, ON, pole);
        BlockPos plank = ON.above();
        SubgridTestSupport.placePiece(be, level, plank, Blocks.OAK_PLANKS.defaultBlockState());
        helper.startSequence().thenIdle(5)
                .thenExecute(() -> {
                    BlockEntity machine = VanillaBlockPiece.boundPhantom(piston, be, level);
                    helper.assertTrue(((KineticBlockEntity) machine).getSpeed() != 0, "the piston isn't turning");
                    assembleNextTick(machine, LinearActuatorBlockEntity.class);
                })
                .thenIdle(20).thenExecute(() -> checkNothingLost(helper, be, 5, plank)).thenSucceed();
    }
}
