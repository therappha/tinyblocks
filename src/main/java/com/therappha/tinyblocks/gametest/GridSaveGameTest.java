package com.therappha.tinyblocks.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerServerHandler;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.items.GridSaveItem;
import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * A grid taken into a Grid Save and placed somewhere else, as a player's clicks do (ServerPlayerGameMode#useItemOn):
 * nothing drops, its old place is air and left behind in no kinetic or link network, a click where someone stands
 * places nothing and keeps the grid, and at the new place its pieces are there, its cogwheel turns with its motor
 * and its link answers a linked controller, without reopening the world. Once as it was, once turned a quarter by
 * R as the server takes it (GridSaveItem#turnHeld, what GridSaveTurnPayload does).
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class GridSaveGameTest {

    private static final BlockPos FROM = new BlockPos(1, 1, 1), TO = new BlockPos(3, 2, 3);
    private static final BlockPos MOTOR = new BlockPos(2, 3, 4), COG = new BlockPos(3, 3, 4);
    private static final BlockPos LINK = new BlockPos(5, 1, 5);
    private static final int PIECES = 4;

    @GameTest(template = "empty_platform", timeoutTicks = 200)
    public void aGridSavedAndPlacedElsewhereKeepsWorking(GameTestHelper helper) {
        // Not a frequency other link tests use: they run alongside, in range.
        moved(helper, 0, Items.NAUTILUS_SHELL);
    }

    @GameTest(template = "empty_platform", timeoutTicks = 200)
    public void aGridSavedAndPlacedTurnedKeepsWorking(GameTestHelper helper) {
        moved(helper, 1, Items.HEART_OF_THE_SEA);
    }

    private static void moved(GameTestHelper helper, int turns, Item frequencyItem) {
        ServerLevel level = helper.getLevel();
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, FROM);
        SubgridTestSupport.placePiece(be, level, MOTOR,
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        SubgridTestSupport.placePiece(be, level, COG,
                AllBlocks.COGWHEEL.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));
        SubgridTestSupport.placePiece(be, level, LINK.below(), Blocks.STONE.defaultBlockState());
        PlacedPiece link = SubgridTestSupport.placePiece(be, level, LINK,
                AllBlocks.REDSTONE_LINK.getDefaultState().setValue(DirectionalBlock.FACING, Direction.UP));
        ServerPlayer player = SubgridTestSupport.serverPlayer(helper, be);
        Frequency frequency = Frequency.of(new ItemStack(frequencyItem));
        ItemStack save = new ItemStack(Registration.GRID_SAVE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, save);
        Rotation rotation = Rotation.values()[turns];
        BlockPos cog = be.transformCell(COG, rotation, Mirror.NONE), turnedLink = be.transformCell(LINK, rotation, Mirror.NONE);
        SubgridBlockEntity[] placed = new SubgridBlockEntity[1];
        KineticBlockEntity[] oldCog = new KineticBlockEntity[1];
        LinkBehaviour[] oldLink = new LinkBehaviour[1];
        BlockPos floor = helper.absolutePos(TO.below());
        BlockHitResult onFloor = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);

        helper.startSequence()
                // A transmitter made a receiver with a sneaking click, then tuned, as in SubgridTransformGameTest.
                .thenIdle(3)
                .thenExecute(() -> {
                    Player sneaking = helper.makeMockPlayer(GameType.SURVIVAL);
                    sneaking.setShiftKeyDown(true);
                    SubgridTestSupport.useOnPiece(be, level, link, sneaking);
                })
                .thenWaitUntil(() -> helper.assertTrue(linkOf(be, level, link) != null, "the link has no link behaviour yet"))
                .thenExecute(() -> {
                    LinkBehaviour behaviour = linkOf(be, level, link);
                    behaviour.setFrequency(true, new ItemStack(frequencyItem));
                    behaviour.setFrequency(false, new ItemStack(frequencyItem));
                })
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(spinning(be, level, COG), "the cog doesn't turn before the grid is saved"))
                .thenExecute(() -> {
                    oldCog[0] = (KineticBlockEntity) SubgridTestSupport.blockEntityOf(be, be.getPieceAt(COG.getX(), COG.getY(), COG.getZ()), level);
                    oldLink[0] = linkOf(be, level, link);
                    player.gameMode.useItemOn(player, level, save, InteractionHand.MAIN_HAND, SubgridTestSupport.topHit(be, MOTOR));
                    helper.assertTrue(GridSaveItem.isFull(save), "the Grid Save took nothing");
                    helper.assertBlockPresent(Blocks.AIR, FROM);
                    for (int i = 0; i < turns; i++) helper.assertTrue(GridSaveItem.turnHeld(player), "R found no Grid Save to turn");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(!SubgridTransformGameTest.inANetwork(level, oldCog[0]),
                            "the taken grid's cog is still in a kinetic network");
                    var members = Create.REDSTONE_LINK_NETWORK_HANDLER.networksIn(level).get(Couple.create(frequency, frequency));
                    helper.assertTrue(members == null || !members.contains(oldLink[0]),
                            "the taken grid's link is still in its link network");
                    // Someone standing where it would go: nothing placed, the grid still in the item.
                    ArmorStand inTheWay = helper.spawn(EntityType.ARMOR_STAND, TO);
                    helper.assertTrue(!player.gameMode.useItemOn(player, level, save, InteractionHand.MAIN_HAND, onFloor).consumesAction(),
                            "a grid was placed onto someone");
                    helper.assertTrue(GridSaveItem.isFull(save), "a refused placement emptied the Grid Save");
                    helper.assertBlockPresent(Blocks.AIR, TO);
                    inTheWay.discard();
                })
                .thenExecute(() -> {
                    player.gameMode.useItemOn(player, level, save, InteractionHand.MAIN_HAND, onFloor);
                    helper.assertTrue(!GridSaveItem.isFull(save), "the Grid Save still holds its grid");
                    helper.assertTrue(level.getBlockEntity(helper.absolutePos(TO)) instanceof SubgridBlockEntity, "no grid was placed");
                    placed[0] = (SubgridBlockEntity) level.getBlockEntity(helper.absolutePos(TO));
                    helper.assertTrue(placed[0].getPieces().size() == PIECES,
                            "the placed grid holds " + placed[0].getPieces().size() + " pieces, not " + PIECES);
                    helper.assertBlockPresent(Blocks.AIR, FROM);
                    helper.assertEntityNotPresent(EntityType.ITEM);
                })
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(spinning(placed[0], level, cog), "the cog doesn't turn where the grid was placed"))
                .thenExecute(() -> press(helper, player, frequency, true))
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(receiverOn(placed[0], turnedLink), "the link stays off with the button held"))
                .thenExecute(() -> press(helper, player, frequency, false))
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(!receiverOn(placed[0], turnedLink), "the link stays on with the button let go");
                    helper.assertEntityNotPresent(EntityType.ITEM);
                })
                .thenSucceed();
    }

    private static LinkBehaviour linkOf(SubgridBlockEntity be, ServerLevel level, PlacedPiece link) {
        return BlockEntityBehaviour.get(VanillaBlockPiece.boundPhantom(link, be, level), LinkBehaviour.TYPE);
    }

    private static boolean spinning(SubgridBlockEntity be, ServerLevel level, BlockPos cell) {
        PlacedPiece piece = be.getPieceAt(cell.getX(), cell.getY(), cell.getZ());
        return piece != null && SubgridTestSupport.blockEntityOf(be, piece, level) instanceof KineticBlockEntity k && k.getSpeed() != 0;
    }

    private static void press(GameTestHelper helper, ServerPlayer player, Frequency frequency, boolean pressed) {
        LinkedControllerServerHandler.receivePressed(helper.getLevel(), player.blockPosition(), player.getUUID(),
                List.of(Couple.create(frequency, frequency)), pressed);
    }

    private static boolean receiverOn(SubgridBlockEntity be, BlockPos cell) {
        var state = SubgridTestSupport.stateOf(be, cell);
        return state != null && state.getValue(RedstoneLinkBlock.POWERED);
    }
}
