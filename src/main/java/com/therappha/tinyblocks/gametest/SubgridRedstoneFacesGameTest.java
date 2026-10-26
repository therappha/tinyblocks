package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Redstone through a SubgridBlock's faces: the pieces against a face give the block on that side the strongest
 * signal among them, and feel what that block gives them — a real one, or, cell by cell where they overlap, the
 * pieces of a SubgridBlock of another size.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridRedstoneFacesGameTest {

    private static final String PLATFORM = "empty_platform";

    private static SubgridBlockEntity grid(GameTestHelper helper, BlockPos at, int size) {
        helper.setBlock(at, Blocks.AIR);
        SubgridBlockEntity be = SubgridBlockEntity.createAt(helper.getLevel(), helper.absolutePos(at), size);
        if (be == null) throw new IllegalStateException("no " + size + "-grid at " + at);
        return be;
    }

    /** A lever lying on the floor of its cell: on the grid's bottom layer, attached to the block below the grid. */
    private static BlockState floorLever(boolean on) {
        return Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.POWERED, on);
    }

    private static boolean lit(BlockState state) {
        return state != null && state.getValue(RedstoneLampBlock.LIT);
    }

    private static void flip(GameTestHelper helper, SubgridBlockEntity be, PlacedPiece lever) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        SubgridTestSupport.useOnPiece(be, helper.getLevel(), lever, player);
    }

    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public void aTinyLeverPowersTheBlockBelowTheGrid(GameTestHelper helper) {
        BlockPos lamp = new BlockPos(2, 1, 2);
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
        SubgridBlockEntity be = grid(helper, lamp.above(), 7);
        PlacedPiece lever = SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(3, 0, 3), floorLever(true));

        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(lit(helper.getBlockState(lamp)), "the lamp below the grid is dark"))
                .thenExecute(() -> flip(helper, be, lever))
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(!lit(helper.getBlockState(lamp)), "the lamp stayed lit with the lever off"))
                .thenSucceed();
    }

    /** One lever on, one off, on the same face: the face is on; with the first turned off too, it is off. */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public void aFaceGivesTheStrongestOfItsPieces(GameTestHelper helper) {
        BlockPos lamp = new BlockPos(2, 1, 2);
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
        SubgridBlockEntity be = grid(helper, lamp.above(), 7);
        PlacedPiece on = SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(1, 0, 1), floorLever(true));
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(5, 0, 5), floorLever(false));

        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(lit(helper.getBlockState(lamp)), "one lever on did not power the face"))
                .thenExecute(() -> flip(helper, be, on))
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(!lit(helper.getBlockState(lamp)), "both levers off, the face is still on"))
                .thenSucceed();
    }

    /** A real redstone block under the grid lights a tiny lamp on the bottom layer, not one in the middle. */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public void aRealBlockPowersOnlyThePiecesAgainstItsFace(GameTestHelper helper) {
        BlockPos below = new BlockPos(2, 1, 2);
        helper.setBlock(below, Blocks.STONE);
        SubgridBlockEntity be = grid(helper, below.above(), 7);
        BlockPos bottom = new BlockPos(3, 0, 3), middle = new BlockPos(3, 3, 3);
        SubgridTestSupport.placePiece(be, helper.getLevel(), bottom, Blocks.REDSTONE_LAMP.defaultBlockState());
        SubgridTestSupport.placePiece(be, helper.getLevel(), middle, Blocks.REDSTONE_LAMP.defaultBlockState());
        helper.setBlock(below, Blocks.REDSTONE_BLOCK);

        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(lit(SubgridTestSupport.stateOf(be, bottom)), "the lamp against the redstone block is dark");
                    helper.assertTrue(!lit(SubgridTestSupport.stateOf(be, middle)), "the lamp in the middle of the grid lit up");
                })
                .thenSucceed();
    }

    /** A real lever on a stone block next to the grid powers the stone, and the stone the tiny lamp against it. */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public void aStronglyPoweredRealBlockPowersThePiecesAgainstIt(GameTestHelper helper) {
        SubgridBlockEntity be = grid(helper, new BlockPos(2, 2, 2), 7);
        BlockPos lamp = new BlockPos(6, 3, 3);
        SubgridTestSupport.placePiece(be, helper.getLevel(), lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.STONE);
        BlockPos leverPos = new BlockPos(4, 2, 2);
        helper.setBlock(leverPos, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL)
                .setValue(LeverBlock.FACING, Direction.EAST));

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> ((LeverBlock) Blocks.LEVER).pull(helper.getBlockState(leverPos), helper.getLevel(),
                        helper.absolutePos(leverPos), null))
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(lit(SubgridTestSupport.stateOf(be, lamp)),
                        "the tiny lamp against the powered stone is dark"))
                .thenSucceed();
    }

    /** Two grids of the same size side by side: a tiny redstone block lights the tiny lamp across. */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public void redstoneCrossesBetweenGridsOfTheSameSize(GameTestHelper helper) {
        SubgridBlockEntity a = grid(helper, new BlockPos(2, 2, 2), 7);
        SubgridBlockEntity b = grid(helper, new BlockPos(3, 2, 2), 7);
        BlockPos lamp = new BlockPos(0, 3, 3);
        SubgridTestSupport.placePiece(b, helper.getLevel(), lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
        SubgridTestSupport.placePiece(a, helper.getLevel(), new BlockPos(6, 3, 3), Blocks.REDSTONE_BLOCK.defaultBlockState());

        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(lit(SubgridTestSupport.stateOf(b, lamp)), "the lamp across is dark"))
                .thenSucceed();
    }

    /**
     * A 1/7 grid next to a 1/9 one: a tiny redstone block in a corner cell of the 1/7 face lights the 1/9 lamp whose
     * cell it overlaps, not one in the middle of the face.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public void redstoneCrossesBetweenSizesWhereTheCellsOverlap(GameTestHelper helper) {
        SubgridBlockEntity a = grid(helper, new BlockPos(2, 2, 2), 7);
        SubgridBlockEntity b = grid(helper, new BlockPos(3, 2, 2), 9);
        BlockPos corner = new BlockPos(0, 0, 0), middle = new BlockPos(0, 4, 4);
        SubgridTestSupport.placePiece(b, helper.getLevel(), corner, Blocks.REDSTONE_LAMP.defaultBlockState());
        SubgridTestSupport.placePiece(b, helper.getLevel(), middle, Blocks.REDSTONE_LAMP.defaultBlockState());
        SubgridTestSupport.placePiece(a, helper.getLevel(), new BlockPos(6, 0, 0), Blocks.REDSTONE_BLOCK.defaultBlockState());

        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(lit(SubgridTestSupport.stateOf(b, corner)), "the overlapping corner lamp is dark");
                    helper.assertTrue(!lit(SubgridTestSupport.stateOf(b, middle)), "the lamp in the middle of the face lit up");
                })
                .thenSucceed();
    }
}
