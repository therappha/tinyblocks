package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.FakeSpace;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A grid's ticking pieces share one fake space, and their phantoms stay bound to it between ticks (issue #64): what
 * code outside the grid reads through them must be the pieces as they are, not a tick's leftovers. And what is
 * cached from the pieces (the outline) follows a piece changing state alone.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridTickSpaceGameTest {

    private static final String PLATFORM = "empty_platform";

    /** A furnace lights in its tick (a write to its cell), then goes: read through a hopper bound to the same space. */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public void aReadThroughABoundPhantomSeesNoLeftoverOfAnotherPiecesTick(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();
        BlockPos hopperAnchor = new BlockPos(1, 0, 1);
        BlockPos furnaceAnchor = new BlockPos(5, 0, 5);
        PlacedPiece hopper = SubgridTestSupport.placePiece(be, level, hopperAnchor,
                Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        PlacedPiece furnace = SubgridTestSupport.placePiece(be, level, furnaceAnchor, Blocks.FURNACE.defaultBlockState());

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    BlockEntity hopperPhantom = VanillaBlockPiece.boundPhantom(hopper, be, level);
                    helper.assertTrue(hopperPhantom.getLevel() instanceof FakeSpace, "the hopper isn't bound to a fake space");

                    BlockEntity furnacePhantom = SubgridTestSupport.blockEntityOf(be, furnace, level);
                    helper.assertTrue(furnacePhantom instanceof Container, "furnace piece has no Container");
                    ((Container) furnacePhantom).setItem(0, new ItemStack(Items.RAW_IRON));
                    ((Container) furnacePhantom).setItem(1, new ItemStack(Items.COAL));
                    furnace.definition.tick(furnace, level, be.getBlockPos(), be);
                    BlockState lit = SubgridTestSupport.stateOf(be, furnaceAnchor);
                    helper.assertTrue(lit != null && lit.getValue(AbstractFurnaceBlock.LIT), "the furnace didn't light: " + lit);

                    be.removePieceAt(furnaceAnchor.getX(), furnaceAnchor.getY(), furnaceAnchor.getZ());
                    BlockState seen = hopperPhantom.getLevel().getBlockState(be.virtualPosOf(furnaceAnchor));
                    helper.assertTrue(seen.isAir(), "a read through the hopper still sees the gone furnace: " + seen);
                })
                .thenSucceed();
    }

    /** A trapdoor opened by a lever beside it changes only its state; the grid's outline must follow. */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public void theOutlineFollowsAPieceChangingStateAlone(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        BlockPos trapdoorAnchor = new BlockPos(4, 0, 4);
        SubgridTestSupport.placePiece(be, level, trapdoorAnchor, Blocks.OAK_TRAPDOOR.defaultBlockState());
        PlacedPiece lever = SubgridTestSupport.placePiece(be, level, trapdoorAnchor.relative(Direction.WEST),
                Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, Direction.EAST));
        var outlineBefore = new Object() { java.util.List<net.minecraft.world.phys.AABB> boxes; };

        helper.startSequence()
                .thenExecute(() -> {
                    outlineBefore.boxes = be.getOutlineShape().toAabbs();
                    SubgridTestSupport.useOnPiece(be, level, lever, player);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    BlockState trapdoor = SubgridTestSupport.stateOf(be, trapdoorAnchor);
                    helper.assertTrue(trapdoor != null && trapdoor.getValue(TrapDoorBlock.OPEN), "the lever didn't open the trapdoor");
                    helper.assertTrue(!be.getOutlineShape().toAabbs().equals(outlineBefore.boxes),
                            "the outline still has the closed trapdoor");
                })
                .thenSucceed();
    }
}
