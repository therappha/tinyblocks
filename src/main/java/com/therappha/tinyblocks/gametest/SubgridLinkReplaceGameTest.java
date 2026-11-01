package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerServerHandler;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** A tiny receiver broken and placed again answers a linked controller again, without reopening the world. */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridLinkReplaceGameTest {

    private static final BlockPos RECEIVER = new BlockPos(4, 1, 4);
    // Not the diamond the other link tests use: they run alongside, in range.
    private static final Frequency FREQUENCY = Frequency.of(new ItemStack(Items.ECHO_SHARD));

    @GameTest(template = "empty_platform", timeoutTicks = 300)
    public void aTinyReceiverPlacedAgainAnswersTheController(GameTestHelper helper) {
        SubgridBlockEntity[] grid = {SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2))};
        ServerLevel level = helper.getLevel();
        SubgridTestSupport.placePiece(grid[0], level, RECEIVER.below(), Blocks.STONE.defaultBlockState());
        ServerPlayer player = SubgridTestSupport.serverPlayer(helper, grid[0]);
        PlacedPiece[] receiver = new PlacedPiece[1];

        GameTestSequence sequence = helper.startSequence();
        placeReceiver(helper, sequence, grid, receiver);
        expectAnswer(helper, sequence, grid, player, "the first receiver");
        sequence.thenExecute(() -> grid[0].removePieceAt(RECEIVER.getX(), RECEIVER.getY(), RECEIVER.getZ()))
                .thenIdle(2);
        placeReceiver(helper, sequence, grid, receiver);
        expectAnswer(helper, sequence, grid, player, "the receiver placed again");
        sequence.thenExecute(() -> {
            // Leave the world and join again.
            BlockPos pos = grid[0].getBlockPos();
            CompoundTag saved = grid[0].saveWithFullMetadata(level.registryAccess());
            grid[0].onChunkUnloaded();
            level.removeBlockEntity(pos);
            level.setBlockEntity(BlockEntity.loadStatic(pos, level.getBlockState(pos), saved, level.registryAccess()));
            grid[0] = (SubgridBlockEntity) level.getBlockEntity(pos);
        }).thenIdle(5);
        expectAnswer(helper, sequence, grid, player, "the receiver after reopening the world");
        sequence.thenSucceed();
    }

    /** A tiny transmitter broken while on lets a real receiver go, as a real transmitter does. */
    @GameTest(template = "empty_platform", timeoutTicks = 100)
    public void aBrokenTinyTransmitterLetsItsReceiverGo(GameTestHelper helper) {
        SubgridBlockEntity grid = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();
        SubgridTestSupport.placePiece(grid, level, RECEIVER.below(), Blocks.STONE.defaultBlockState());
        PlacedPiece link = SubgridTestSupport.placePiece(grid, level, RECEIVER,
                AllBlocks.REDSTONE_LINK.getDefaultState().setValue(DirectionalBlock.FACING, Direction.UP));
        SubgridTestSupport.placePiece(grid, level, RECEIVER.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        BlockPos real = new BlockPos(5, 2, 1);
        var realState = AllBlocks.REDSTONE_LINK.getDefaultState().setValue(DirectionalBlock.FACING, Direction.UP);
        helper.setBlock(real, realState);
        // Not a frequency other link tests use: they run alongside, in range.
        ItemStack frequency = new ItemStack(Items.GHAST_TEAR);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(VanillaBlockPiece.boundPhantom(link, grid, level) != null
                            && BlockEntityBehaviour.get(VanillaBlockPiece.boundPhantom(link, grid, level), LinkBehaviour.TYPE) != null,
                            "the tiny link has no link behaviour yet");
                    helper.assertTrue(BlockEntityBehaviour.get(level, helper.absolutePos(real), LinkBehaviour.TYPE) != null,
                            "the real link has no link behaviour yet");
                })
                .thenExecute(() -> helper.setBlock(real, realState.setValue(RedstoneLinkBlock.RECEIVER, true)))
                .thenWaitUntil(() -> helper.assertTrue(BlockEntityBehaviour.get(level, helper.absolutePos(real), LinkBehaviour.TYPE).isListening(),
                        "the real link isn't a receiver yet"))
                .thenExecute(() -> {
                    for (LinkBehaviour behaviour : List.of(BlockEntityBehaviour.get(VanillaBlockPiece.boundPhantom(link, grid, level), LinkBehaviour.TYPE),
                            BlockEntityBehaviour.get(level, helper.absolutePos(real), LinkBehaviour.TYPE))) {
                        behaviour.setFrequency(true, frequency);
                        behaviour.setFrequency(false, frequency);
                    }
                })
                .thenWaitUntil(() -> helper.assertBlockProperty(real, RedstoneLinkBlock.POWERED, true))
                .thenExecute(() -> grid.removePieceAt(RECEIVER.getX(), RECEIVER.getY(), RECEIVER.getZ()))
                .thenWaitUntil(() -> helper.assertBlockProperty(real, RedstoneLinkBlock.POWERED, false))
                .thenSucceed();
    }

    /** A wrench switches a link as a sneaking click does: the placed link hears straight away, not after reopening the world. */
    @GameTest(template = "empty_platform", timeoutTicks = 100)
    public void aWrenchMakesATinyLinkAReceiver(GameTestHelper helper) {
        SubgridBlockEntity grid = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();
        SubgridTestSupport.placePiece(grid, level, RECEIVER.below(), Blocks.STONE.defaultBlockState());
        PlacedPiece link = SubgridTestSupport.placePiece(grid, level, RECEIVER,
                AllBlocks.REDSTONE_LINK.getDefaultState().setValue(DirectionalBlock.FACING, Direction.UP));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> SubgridTestSupport.rightClick(grid, player, SubgridTestSupport.topHit(grid, RECEIVER)))
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(SubgridTestSupport.stateOf(grid, RECEIVER).getValue(RedstoneLinkBlock.RECEIVER),
                            "the wrench doesn't switch the link");
                    LinkBehaviour behaviour = BlockEntityBehaviour.get(
                            VanillaBlockPiece.boundPhantom(link, grid, level), LinkBehaviour.TYPE);
                    helper.assertTrue(behaviour.isListening(), "the link switched by the wrench still transmits");
                })
                .thenSucceed();
    }

    /** Placed as a transmitter, made a receiver with a sneaking click, then tuned to the controller's button. */
    private static void placeReceiver(GameTestHelper helper, GameTestSequence sequence, SubgridBlockEntity[] grid,
                                      PlacedPiece[] receiver) {
        ServerLevel level = helper.getLevel();
        sequence.thenExecute(() -> receiver[0] = SubgridTestSupport.placePiece(grid[0], level, RECEIVER,
                        AllBlocks.REDSTONE_LINK.getDefaultState().setValue(DirectionalBlock.FACING, Direction.UP)))
                .thenIdle(3)
                .thenExecute(() -> {
                    Player sneaking = helper.makeMockPlayer(GameType.SURVIVAL);
                    sneaking.setShiftKeyDown(true);
                    SubgridTestSupport.useOnPiece(grid[0], level, receiver[0], sneaking);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    LinkBehaviour link = BlockEntityBehaviour.get(
                            VanillaBlockPiece.boundPhantom(receiver[0], grid[0], level), LinkBehaviour.TYPE);
                    link.setFrequency(true, new ItemStack(Items.ECHO_SHARD));
                    link.setFrequency(false, new ItemStack(Items.ECHO_SHARD));
                });
    }

    /** The controller's button held: the receiver turns on. Let go: it turns off. */
    private static void expectAnswer(GameTestHelper helper, GameTestSequence sequence, SubgridBlockEntity[] grid,
                                     ServerPlayer player, String which) {
        sequence.thenExecute(() -> press(helper, player, true))
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(receiverOn(grid[0]), which + " stays off with the button held: " + network(helper, grid[0])))
                .thenExecute(() -> press(helper, player, false))
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(!receiverOn(grid[0]), which + " stays on with the button let go: " + network(helper, grid[0])));
    }

    private static void press(GameTestHelper helper, ServerPlayer player, boolean pressed) {
        LinkedControllerServerHandler.receivePressed(helper.getLevel(), player.blockPosition(), player.getUUID(),
                List.of(Couple.create(FREQUENCY, FREQUENCY)), pressed);
    }

    private static String network(GameTestHelper helper, SubgridBlockEntity be) {
        StringBuilder out = new StringBuilder();
        var members = com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.networksIn(helper.getLevel())
                .get(Couple.create(FREQUENCY, FREQUENCY));
        if (members == null) return "no network";
        for (var member : members) {
            out.append(member.getClass().getSimpleName()).append(" alive=").append(member.isAlive())
                    .append(" listening=").append(member.isListening()).append(" at=").append(member.getLocation());
            if (member instanceof LinkBehaviour lb) {
                out.append(" newPosition=").append(lb.newPosition).append(" be=")
                        .append(System.identityHashCode(lb.blockEntity)).append(" removed=").append(lb.blockEntity.isRemoved())
                        .append(" received=").append(((com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity) lb.blockEntity).getReceivedSignal());
            }
            out.append("; ");
        }
        PlacedPiece piece = be.getPieceAt(RECEIVER.getX(), RECEIVER.getY(), RECEIVER.getZ());
        out.append("piece be=").append(piece == null ? "none" : System.identityHashCode(piece.runtimeBlockEntity));
        return out.toString();
    }

    private static boolean receiverOn(SubgridBlockEntity be) {
        return SubgridTestSupport.stateOf(be, RECEIVER).getValue(RedstoneLinkBlock.POWERED);
    }
}
