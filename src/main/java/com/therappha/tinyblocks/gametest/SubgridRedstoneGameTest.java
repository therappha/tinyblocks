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
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Regression coverage for the generic onUse/onNeighborChanged propagation between two pieces
 * inside the same subgrid — a lever piece toggled by a real (simulated) right-click powering a
 * lamp piece next to it. Complements SubgridPistonGameTest (which powers its piston with a plain
 * redstone block, deliberately avoiding lever attachment semantics) by exercising the actual
 * player-interaction entry point, VanillaBlockPiece#onUse, directly.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridRedstoneGameTest {

    private static final String PLATFORM = "empty_platform";

    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public void leverPieceTogglesAnAdjacentLampPiece(GameTestHelper helper) {
        BlockPos subgridPos = new BlockPos(2, 1, 2);
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, subgridPos);
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.CREATIVE);

        BlockPos lampAnchor = new BlockPos(4, 0, 4);
        BlockPos leverAnchor = lampAnchor.relative(Direction.WEST);

        SubgridTestSupport.placePiece(be, level, lampAnchor, Blocks.REDSTONE_LAMP.defaultBlockState());
        PlacedPiece lever = SubgridTestSupport.placePiece(be, level, leverAnchor,
                Blocks.LEVER.defaultBlockState()
                        .setValue(LeverBlock.FACE, AttachFace.FLOOR)
                        .setValue(LeverBlock.FACING, Direction.EAST));

        helper.startSequence()
                .thenExecute(() -> {
                    var lampBefore = SubgridTestSupport.stateOf(be, lampAnchor);
                    helper.assertTrue(lampBefore != null && !lampBefore.getValue(RedstoneLampBlock.LIT), "lamp should start unlit");
                })
                .thenExecute(() -> SubgridTestSupport.useOnPiece(be, level, lever, player))
                .thenIdle(5)
                .thenExecute(() -> {
                    var lampAfter = SubgridTestSupport.stateOf(be, lampAnchor);
                    helper.assertTrue(lampAfter != null && lampAfter.getValue(RedstoneLampBlock.LIT),
                            "lamp piece never lit up after the adjacent lever piece was toggled");
                })
                .thenSucceed();
    }
}
