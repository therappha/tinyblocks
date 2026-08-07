package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Regression coverage for b834add: SubgridFakeCellGetter#resolve translating the whole aliased
 * neighborhood (not just the exact clicked position) so a door's upper half can find its own
 * lower half at pos.below() — found live as "clicking the top half sometimes had no sound", i.e.
 * the two halves silently falling out of sync. Two separately-placed pieces here (lower/upper),
 * exactly like a real door split across two subgrid cells.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridDoorGameTest {

    private static final String PLATFORM = "empty_platform";

    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public void openingTheLowerHalfOpensTheUpperHalfToo(GameTestHelper helper) {
        BlockPos subgridPos = new BlockPos(2, 1, 2);
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, subgridPos);
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.CREATIVE);

        BlockPos lowerAnchor = new BlockPos(4, 0, 4);
        BlockPos upperAnchor = lowerAnchor.above();

        PlacedPiece lower = SubgridTestSupport.placePiece(be, level, lowerAnchor,
                Blocks.OAK_DOOR.defaultBlockState()
                        .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                        .setValue(DoorBlock.FACING, Direction.NORTH)
                        .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
                        .setValue(DoorBlock.OPEN, false));
        SubgridTestSupport.placePiece(be, level, upperAnchor,
                Blocks.OAK_DOOR.defaultBlockState()
                        .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
                        .setValue(DoorBlock.FACING, Direction.NORTH)
                        .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
                        .setValue(DoorBlock.OPEN, false));

        helper.startSequence()
                .thenExecute(() -> SubgridTestSupport.useOnPiece(be, level, lower, player))
                .thenExecute(() -> {
                    var lowerState = SubgridTestSupport.stateOf(be, lowerAnchor);
                    var upperState = SubgridTestSupport.stateOf(be, upperAnchor);
                    helper.assertTrue(lowerState != null && lowerState.getValue(DoorBlock.OPEN), "lower half never opened");
                    helper.assertTrue(upperState != null && upperState.getValue(DoorBlock.OPEN),
                            "upper half didn't open with the lower half — the two halves fell out of sync");
                })
                .thenSucceed();
    }
}
