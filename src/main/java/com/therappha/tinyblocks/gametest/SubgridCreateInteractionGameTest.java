package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
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
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Using Create's own tools and slots on a piece. */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridCreateInteractionGameTest {

    @GameTest(template = "empty_platform")
    public void wrenchTurnsATinyShaft(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        BlockPos anchor = new BlockPos(4, 4, 4);
        SubgridTestSupport.placePiece(be, helper.getLevel(), anchor,
                AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        SubgridTestSupport.useItemOn(be, helper.getLevel(), player, AllItems.WRENCH.asStack(), anchor);

        BlockState after = SubgridTestSupport.stateOf(be, anchor);
        helper.assertTrue(after != null && after.getValue(RotatedPillarKineticBlock.AXIS) != Direction.Axis.X,
                "wrench did not turn the shaft: " + after);
        helper.succeed();
    }

    /**
     * Right-clicking a tiny redstone link's top with an item sets one of its two frequencies — Create does that
     * in a RightClickBlock listener, which only sees the piece through PieceClickDispatch.
     */
    @GameTest(template = "empty_platform", timeoutTicks = 100)
    public void clickSetsATinyRedstoneLinkFrequency(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        BlockPos anchor = new BlockPos(4, 1, 4);
        standOn(be, helper, anchor);
        PlacedPiece link = SubgridTestSupport.placePiece(be, helper.getLevel(), anchor,
                AllBlocks.REDSTONE_LINK.getDefaultState().setValue(DirectionalBlock.FACING, Direction.UP));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
        player.setXRot(90); // looking straight down onto the link

        double cell = 1.0 / be.getGridSize();
        BlockPos real = be.getBlockPos();
        // A quarter of the way across the link's top surface (its two slots sit side by side there).
        Vec3 hitPoint = new Vec3(real.getX() + (anchor.getX() + 0.5) * cell, real.getY() + (anchor.getY() + 0.25) * cell,
                real.getZ() + (anchor.getZ() + 0.3) * cell);
        // The link creates its link behaviour when it first ticks.
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    link.definition.onUse(link, helper.getLevel(), real, be, player,
                            new BlockHitResult(hitPoint, Direction.UP, real, false));
                    BlockEntity phantom = SubgridTestSupport.blockEntityOf(be, link, helper.getLevel());
                    LinkBehaviour behaviour = BlockEntityBehaviour.get(phantom, LinkBehaviour.TYPE);
                    helper.assertTrue(behaviour != null, "the tiny link has no link behaviour: " + phantom);
                    var key = behaviour.getNetworkKey();
                    helper.assertTrue(key.getFirst().getStack().is(Items.DIAMOND) != key.getSecond().getStack().is(Items.DIAMOND),
                            "the click did not set exactly one frequency: " + key.getFirst().getStack() + " / " + key.getSecond().getStack());
                })
                .thenSucceed();
    }

    /** A link facing up only stays where the block under it is solid, like a full-size one. */
    private static void standOn(SubgridBlockEntity be, GameTestHelper helper, BlockPos link) {
        SubgridTestSupport.placePiece(be, helper.getLevel(), link.below(),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
    }

    private static BlockState link(boolean receiver) {
        return AllBlocks.REDSTONE_LINK.getDefaultState().setValue(DirectionalBlock.FACING, Direction.UP)
                .setValue(com.simibubi.create.content.redstone.link.RedstoneLinkBlock.RECEIVER, receiver);
    }

    private static boolean powered(BlockState state) {
        return state != null && state.getValue(com.simibubi.create.content.redstone.link.RedstoneLinkBlock.POWERED);
    }

    /**
     * Both frequency slots set to a diamond. A link builds its link behaviour on its first tick only when its mode
     * differs from the transmitter default, so every link here is placed as a transmitter, ticks once, and is then
     * turned into a receiver if needed — what a player does with a sneak-click.
     */
    private static void tune(BlockEntity linkEntity) {
        LinkBehaviour behaviour = BlockEntityBehaviour.get(linkEntity, LinkBehaviour.TYPE);
        if (behaviour == null) throw new IllegalStateException("no link behaviour on " + linkEntity);
        behaviour.setFrequency(true, new ItemStack(Items.DIAMOND));
        behaviour.setFrequency(false, new ItemStack(Items.DIAMOND));
    }

    private static String describe(BlockEntity linkEntity) {
        LinkBehaviour behaviour = BlockEntityBehaviour.get(linkEntity, LinkBehaviour.TYPE);
        if (behaviour == null) return "[no link behaviour]";
        var network = com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER
                .networksIn(linkEntity.getLevel()).get(behaviour.getNetworkKey());
        return "[" + linkEntity.getBlockPos() + " level=" + linkEntity.getLevel().getClass().getSimpleName()
                + " listening=" + behaviour.isListening() + " alive=" + behaviour.isAlive()
                + " transmits=" + behaviour.getTransmittedStrength()
                + " received=" + (linkEntity instanceof com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity l ? l.getReceivedSignal() : -1)
                + " key=" + behaviour.getNetworkKey().getFirst().getStack().getItem() + "/" + behaviour.getNetworkKey().getSecond().getStack().getItem()
                + " behaviour@" + Integer.toHexString(System.identityHashCode(behaviour))
                + " network=" + (network == null ? "none" : network.size() + (network.contains(behaviour) ? " incl. self" : " NOT incl. self")
                        + network.stream().map(o -> o instanceof LinkBehaviour lb ? lb.blockEntity.getBlockPos() + "@" + Integer.toHexString(System.identityHashCode(lb))
                                + (lb.isListening() ? "R" : "T") + lb.getTransmittedStrength() : String.valueOf(o)).toList())
                + " state=" + linkEntity.getBlockState() + "]";
    }

    /** A powered tiny transmitter inside a subgrid powers a real receiver on the same frequency outside. */
    @GameTest(template = "empty_platform", timeoutTicks = 100)
    public void tinyTransmitterPowersARealReceiver(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        standOn(be, helper, new BlockPos(4, 1, 4));
        PlacedPiece transmitter = SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(4, 1, 4), link(false));
        SubgridTestSupport.placePiece(be, helper.getLevel(), new BlockPos(5, 1, 4),
                net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
        BlockPos receiver = new BlockPos(4, 1, 2);
        helper.setBlock(receiver.below(), net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(receiver, link(false));

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.setBlock(receiver, link(true));
                    tune(SubgridTestSupport.blockEntityOf(be, transmitter, helper.getLevel()));
                })
                .thenIdle(3)
                .thenExecute(() -> tune(helper.getBlockEntity(receiver)))
                .thenIdle(20)
                .thenExecute(() -> helper.assertTrue(powered(helper.getBlockState(receiver)),
                        "the real receiver is not powered by the tiny transmitter: "
                                + describe(SubgridTestSupport.blockEntityOf(be, transmitter, helper.getLevel()))
                                + " -> " + describe(helper.getBlockEntity(receiver))
                                + " inRange=" + com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.withinRange(
                                        BlockEntityBehaviour.get(SubgridTestSupport.blockEntityOf(be, transmitter, helper.getLevel()), LinkBehaviour.TYPE),
                                        BlockEntityBehaviour.get(helper.getBlockEntity(receiver), LinkBehaviour.TYPE))
                                + " realPos=" + com.therappha.tinyblocks.v2.PhantomOwners.realPosOf(SubgridTestSupport.blockEntityOf(be, transmitter, helper.getLevel()))))
                .thenSucceed();
    }

    /** A powered real transmitter powers a tiny receiver on the same frequency inside a subgrid. */
    @GameTest(template = "empty_platform", timeoutTicks = 100)
    public void realTransmitterPowersATinyReceiver(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        BlockPos tinyAnchor = new BlockPos(4, 1, 4);
        standOn(be, helper, tinyAnchor);
        PlacedPiece receiver = SubgridTestSupport.placePiece(be, helper.getLevel(), tinyAnchor, link(false));
        BlockPos transmitter = new BlockPos(4, 1, 2);
        helper.setBlock(transmitter.below(), net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(transmitter, link(false));
        helper.setBlock(new BlockPos(5, 1, 2), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    // Into a receiver the way a player does it: a sneaking click with an empty hand.
                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                    player.setShiftKeyDown(true);
                    SubgridTestSupport.useOnPiece(be, helper.getLevel(), receiver, player);
                    tune(helper.getBlockEntity(transmitter));
                })
                .thenIdle(3)
                .thenExecute(() -> tune(SubgridTestSupport.blockEntityOf(be, receiver, helper.getLevel())))
                .thenIdle(20)
                .thenExecute(() -> helper.assertTrue(powered(SubgridTestSupport.stateOf(be, tinyAnchor)),
                        "the tiny receiver is not powered by the real transmitter: " + SubgridTestSupport.stateOf(be, tinyAnchor)
                                + " " + describe(SubgridTestSupport.blockEntityOf(be, receiver, helper.getLevel()))
                                + " -> " + describe(helper.getBlockEntity(transmitter))))
                .thenSucceed();
    }

    /**
     * Create's value settings packet, as the client sends it once a player picked a speed on a tiny creative
     * motor's value box: it names the motor by its phantom's virtual position.
     */
    @GameTest(template = "empty_platform", timeoutTicks = 100)
    public void valueSettingsSetATinyCreativeMotorsSpeed(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        BlockPos anchor = new BlockPos(4, 4, 4);
        PlacedPiece motor = SubgridTestSupport.placePiece(be, helper.getLevel(), anchor,
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalBlock.FACING, Direction.UP));
        net.minecraft.server.level.ServerPlayer player = SubgridTestSupport.serverPlayer(helper, be);

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    var phantom = (com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)
                            SubgridTestSupport.blockEntityOf(be, motor, helper.getLevel());
                    new com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsPacket(be.virtualPosOf(anchor),
                            1, 64, null, null, Direction.UP, false, phantom.generatedSpeed.netId()).handle(player);
                    helper.assertTrue(phantom.generatedSpeed.getValue() == 64,
                            "the tiny motor's speed is " + phantom.generatedSpeed.getValue() + ", not 64");
                })
                .thenSucceed();
    }

    /** A grid on a sub-level sits in its plot, far from the player, and a value box there still reaches its piece. */
    @GameTest(template = "empty_platform", timeoutTicks = 100)
    public void aValueBoxReachesAPieceOnASubLevel(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 2, 2);
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, at);
        BlockPos anchor = new BlockPos(4, 4, 4);
        SubgridTestSupport.placePiece(be, helper.getLevel(), anchor,
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalBlock.FACING, Direction.UP));
        net.minecraft.server.level.ServerPlayer player = SubgridTestSupport.serverPlayer(helper, be);
        BlockPos real = helper.absolutePos(at);
        Object[] subLevel = new Object[1];

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> subLevel[0] = SableSupport.assemble(helper.getLevel(), real, java.util.List.of(real), real, real))
                .thenIdle(5) // Sable places a new sub-level on its next physics tick
                .thenExecute(() -> {
                    var level = helper.getLevel();
                    var moved = (SubgridBlockEntity) level.getBlockEntity(SableSupport.plotCenter(subLevel[0]));
                    var phantom = (com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)
                            SubgridTestSupport.blockEntityOf(moved, moved.getPieces().iterator().next(), level);
                    new com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsPacket(moved.virtualPosOf(anchor),
                            1, 64, null, null, Direction.UP, false, phantom.generatedSpeed.netId()).handle(player);
                    int speed = phantom.generatedSpeed.getValue();
                    SableSupport.remove(level, subLevel[0]);
                    helper.assertTrue(speed == 64, "the tiny motor's speed is " + speed + ", not 64");
                })
                .thenSucceed();
    }

    /** A linked controller's button on a tiny receiver's frequency powers it, as with a full-size receiver. */
    @GameTest(template = "empty_platform", timeoutTicks = 100)
    public void linkedControllerPowersATinyReceiver(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        BlockPos anchor = new BlockPos(4, 1, 4);
        standOn(be, helper, anchor);
        PlacedPiece receiver = SubgridTestSupport.placePiece(be, helper.getLevel(), anchor, link(false));
        net.minecraft.server.level.ServerPlayer player = SubgridTestSupport.serverPlayer(helper, be);

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    Player sneaking = helper.makeMockPlayer(GameType.SURVIVAL);
                    sneaking.setShiftKeyDown(true);
                    SubgridTestSupport.useOnPiece(be, helper.getLevel(), receiver, sneaking);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    // What /tinyblocks frequency does.
                    LinkBehaviour link = BlockEntityBehaviour.get(
                            com.therappha.tinyblocks.v2.VanillaBlockPiece.boundPhantom(receiver, be, helper.getLevel()), LinkBehaviour.TYPE);
                    link.setFrequency(true, new ItemStack(Items.DIAMOND));
                    link.setFrequency(false, new ItemStack(Items.DIAMOND));
                    var diamond = com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency.of(new ItemStack(Items.DIAMOND));
                    com.simibubi.create.content.redstone.link.controller.LinkedControllerServerHandler.receivePressed(
                            helper.getLevel(), player.blockPosition(), player.getUUID(),
                            java.util.List.of(net.createmod.catnip.data.Couple.create(diamond, diamond)), true);
                })
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(powered(SubgridTestSupport.stateOf(be, anchor)),
                        "the tiny receiver is not powered by the controller: " + SubgridTestSupport.stateOf(be, anchor)
                                + " " + describe(SubgridTestSupport.blockEntityOf(be, receiver, helper.getLevel()))))
                .thenSucceed();
    }

    /** Binding a linked controller's button to a tiny link copies the link's frequencies, as the bind packet does. */
    @GameTest(template = "empty_platform", timeoutTicks = 100)
    public void linkedControllerBindsToATinyLink(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        BlockPos anchor = new BlockPos(4, 1, 4);
        standOn(be, helper, anchor);
        PlacedPiece link = SubgridTestSupport.placePiece(be, helper.getLevel(), anchor, link(false));
        net.minecraft.server.level.ServerPlayer player = SubgridTestSupport.serverPlayer(helper, be);
        player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.LINKED_CONTROLLER.asStack());

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    tune(SubgridTestSupport.blockEntityOf(be, link, helper.getLevel()));
                    new com.simibubi.create.content.redstone.link.controller.LinkedControllerBindPacket(0, be.virtualPosOf(anchor))
                            .handle(player);
                    ItemStack bound = com.simibubi.create.content.redstone.link.controller.LinkedControllerItem
                            .getFrequencyItems(player.getMainHandItem()).getStackInSlot(0);
                    helper.assertTrue(bound.is(Items.DIAMOND), "the controller's first button got " + bound + ", not the link's diamond");
                })
                .thenSucceed();
    }

    /** The controller's button lights a tiny lamp next to the tiny receiver it powers. */
    @GameTest(template = "empty_platform", timeoutTicks = 100)
    public void linkedControllerLightsATinyLampThroughATinyReceiver(GameTestHelper helper) {
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, new BlockPos(2, 1, 2));
        BlockPos anchor = new BlockPos(4, 1, 4);
        BlockPos lamp = anchor.east();
        standOn(be, helper, anchor);
        PlacedPiece receiver = SubgridTestSupport.placePiece(be, helper.getLevel(), anchor, link(false));
        SubgridTestSupport.placePiece(be, helper.getLevel(), lamp, net.minecraft.world.level.block.Blocks.REDSTONE_LAMP.defaultBlockState());
        net.minecraft.server.level.ServerPlayer player = SubgridTestSupport.serverPlayer(helper, be);

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    Player sneaking = helper.makeMockPlayer(GameType.SURVIVAL);
                    sneaking.setShiftKeyDown(true);
                    SubgridTestSupport.useOnPiece(be, helper.getLevel(), receiver, sneaking);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    tune(com.therappha.tinyblocks.v2.VanillaBlockPiece.boundPhantom(receiver, be, helper.getLevel()));
                    var diamond = com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency.of(new ItemStack(Items.DIAMOND));
                    com.simibubi.create.content.redstone.link.controller.LinkedControllerServerHandler.receivePressed(
                            helper.getLevel(), player.blockPosition(), player.getUUID(),
                            java.util.List.of(net.createmod.catnip.data.Couple.create(diamond, diamond)), true);
                })
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(
                        SubgridTestSupport.stateOf(be, lamp).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT),
                        "the tiny lamp is dark; receiver " + SubgridTestSupport.stateOf(be, anchor)
                                + " " + describe(SubgridTestSupport.blockEntityOf(be, receiver, helper.getLevel()))))
                .thenSucceed();
    }
}
