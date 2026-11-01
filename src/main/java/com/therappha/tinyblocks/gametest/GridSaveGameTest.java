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
import net.minecraft.core.Vec3i;
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
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
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
 * R as the server takes it (GridSaveItem#turnHeld, what GridSaveTurnPayload does). And two grids taken together, as
 * a box selected with a sneaking click on one and a click on the other, and placed turned: their cogs still mesh
 * across the edge between them and their links still answer. A box taken without one grid in it reacting to another
 * going, and one placed half turned against a west face, after a refused take and a refused place.
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

    /** A tiny transmitter held on by a redstone block drives a real receiver, stops when saved, and drives it again where placed. */
    @GameTest(template = "empty_platform", timeoutTicks = 200)
    public void aSavedTransmitterTransmitsAgainWherePlaced(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, FROM);
        SubgridTestSupport.placePiece(be, level, LINK.below(), Blocks.STONE.defaultBlockState());
        PlacedPiece link = SubgridTestSupport.placePiece(be, level, LINK,
                AllBlocks.REDSTONE_LINK.getDefaultState().setValue(DirectionalBlock.FACING, Direction.UP));
        SubgridTestSupport.placePiece(be, level, LINK.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        BlockPos receiver = new BlockPos(5, 2, 1);
        // Placed a transmitter too: a link builds its link behaviour on the tick it finds itself one.
        var receiverState = AllBlocks.REDSTONE_LINK.getDefaultState().setValue(DirectionalBlock.FACING, Direction.UP);
        helper.setBlock(receiver, receiverState);
        ServerPlayer player = SubgridTestSupport.serverPlayer(helper, be);
        ItemStack save = new ItemStack(Registration.GRID_SAVE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, save);
        BlockPos floor = helper.absolutePos(TO.below());
        BlockHitResult onFloor = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        // Not a frequency other link tests use: they run alongside, in range.
        ItemStack frequency = new ItemStack(Items.NETHER_STAR);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(linkOf(be, level, link) != null, "the link has no link behaviour yet");
                    helper.assertTrue(realLink(helper, receiver) != null, "the real link has no link behaviour yet");
                })
                .thenExecute(() -> helper.setBlock(receiver, receiverState.setValue(RedstoneLinkBlock.RECEIVER, true)))
                .thenWaitUntil(() -> helper.assertTrue(realLink(helper, receiver).isListening(), "the real link isn't a receiver yet"))
                .thenExecute(() -> {
                    for (LinkBehaviour behaviour : List.of(linkOf(be, level, link), realLink(helper, receiver))) {
                        behaviour.setFrequency(true, frequency);
                        behaviour.setFrequency(false, frequency);
                    }
                })
                .thenWaitUntil(() -> helper.assertBlockProperty(receiver, RedstoneLinkBlock.POWERED, true))
                .thenExecute(() -> player.gameMode.useItemOn(player, level, save, InteractionHand.MAIN_HAND, SubgridTestSupport.topHit(be, LINK)))
                .thenWaitUntil(() -> helper.assertBlockProperty(receiver, RedstoneLinkBlock.POWERED, false))
                .thenExecute(() -> {
                    player.gameMode.useItemOn(player, level, save, InteractionHand.MAIN_HAND, onFloor);
                    helper.assertTrue(!GridSaveItem.isFull(save), "the Grid Save still holds its grid");
                })
                .thenWaitUntil(() -> helper.assertBlockProperty(receiver, RedstoneLinkBlock.POWERED, true))
                .thenSucceed();
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

    /**
     * Two grids side by side, a motor in the west one turning a cog that meshes with one across the edge in the east
     * one, and a link in each: taken as one box and placed a quarter turned (the east grid now south of the west one).
     */
    @GameTest(template = "empty_platform", timeoutTicks = 200)
    public void twoGridsSavedTogetherAndPlacedTurnedStillMesh(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos westAt = new BlockPos(1, 1, 1), eastAt = new BlockPos(2, 1, 1);
        SubgridBlockEntity west = SubgridTestSupport.placeSubgrid(helper, westAt);
        SubgridBlockEntity east = SubgridTestSupport.placeSubgrid(helper, eastAt);
        int last = west.getGridSize() - 1;
        BlockPos westCog = new BlockPos(last, 4, 4), eastCog = new BlockPos(0, 4, 4);
        BlockPos westLink = new BlockPos(2, 2, 2), eastLink = new BlockPos(5, 2, 5);
        BlockState cog = AllBlocks.COGWHEEL.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Y);
        SubgridTestSupport.placePiece(west, level, westCog.below(),
                AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
        SubgridTestSupport.placePiece(west, level, westCog, cog);
        SubgridTestSupport.placePiece(east, level, eastCog, cog);
        PlacedPiece[] links = new PlacedPiece[2];
        SubgridBlockEntity[] grids = {west, east};
        BlockPos[] linkCells = {westLink, eastLink};
        for (int i = 0; i < 2; i++) {
            SubgridTestSupport.placePiece(grids[i], level, linkCells[i].below(), Blocks.STONE.defaultBlockState());
            links[i] = SubgridTestSupport.placePiece(grids[i], level, linkCells[i],
                    AllBlocks.REDSTONE_LINK.getDefaultState().setValue(DirectionalBlock.FACING, Direction.UP));
        }
        ServerPlayer player = SubgridTestSupport.serverPlayer(helper, west);
        // Not a frequency other link tests use: they run alongside, in range.
        Item frequencyItem = Items.SHULKER_SHELL;
        Frequency frequency = Frequency.of(new ItemStack(frequencyItem));
        ItemStack save = new ItemStack(Registration.GRID_SAVE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, save);
        Rotation rotation = Rotation.CLOCKWISE_90;
        // Placed on the floor at toWest: a quarter turn puts what was east of it south of it.
        BlockPos toWest = new BlockPos(3, 2, 2), toEast = toWest.south();
        BlockPos floor = helper.absolutePos(toWest.below());
        BlockHitResult onFloor = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        SubgridBlockEntity[] placed = new SubgridBlockEntity[2];

        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    Player sneaking = helper.makeMockPlayer(GameType.SURVIVAL);
                    sneaking.setShiftKeyDown(true);
                    for (int i = 0; i < 2; i++) SubgridTestSupport.useOnPiece(grids[i], level, links[i], sneaking);
                })
                .thenWaitUntil(() -> {
                    for (int i = 0; i < 2; i++) helper.assertTrue(linkOf(grids[i], level, links[i]) != null, "a link has no link behaviour yet");
                })
                .thenExecute(() -> {
                    for (int i = 0; i < 2; i++) {
                        LinkBehaviour behaviour = linkOf(grids[i], level, links[i]);
                        behaviour.setFrequency(true, new ItemStack(frequencyItem));
                        behaviour.setFrequency(false, new ItemStack(frequencyItem));
                    }
                })
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(spinning(east, level, eastCog), "the east cog doesn't turn before the grids are saved"))
                .thenExecute(() -> {
                    // A sneaking click on one grid sets a corner, a click on the other takes the box.
                    player.setShiftKeyDown(true);
                    player.gameMode.useItemOn(player, level, save, InteractionHand.MAIN_HAND, SubgridTestSupport.topHit(west, westLink));
                    player.setShiftKeyDown(false);
                    helper.assertTrue(GridSaveItem.corner(save, level) != null && !GridSaveItem.isFull(save), "a sneaking click set no corner");
                    player.gameMode.useItemOn(player, level, save, InteractionHand.MAIN_HAND, SubgridTestSupport.topHit(east, eastLink));
                    helper.assertTrue(GridSaveItem.isFull(save) && GridSaveItem.corner(save, level) == null, "the box was not taken");
                    helper.assertTrue(GridSaveItem.entries(GridSaveItem.grid(save)).size() == 2, "the box holds no two grids");
                    helper.assertBlockPresent(Blocks.AIR, westAt);
                    helper.assertBlockPresent(Blocks.AIR, eastAt);
                    helper.assertTrue(GridSaveItem.turnHeld(player), "R found no Grid Save to turn");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    player.gameMode.useItemOn(player, level, save, InteractionHand.MAIN_HAND, onFloor);
                    helper.assertTrue(!GridSaveItem.isFull(save), "the Grid Save still holds its grids");
                    BlockPos[] at = {toWest, toEast};
                    int[] pieces = {4, 3};
                    for (int i = 0; i < 2; i++) {
                        helper.assertTrue(level.getBlockEntity(helper.absolutePos(at[i])) instanceof SubgridBlockEntity,
                                "no grid was placed at " + at[i]);
                        placed[i] = (SubgridBlockEntity) level.getBlockEntity(helper.absolutePos(at[i]));
                        helper.assertTrue(placed[i].getPieces().size() == pieces[i],
                                "the grid at " + at[i] + " holds " + placed[i].getPieces().size() + " pieces, not " + pieces[i]);
                    }
                    helper.assertBlockPresent(Blocks.AIR, westAt);
                    helper.assertBlockPresent(Blocks.AIR, eastAt);
                    helper.assertEntityNotPresent(EntityType.ITEM);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertTrue(spinning(placed[0], level, west.transformCell(westCog, rotation, Mirror.NONE)),
                            "the cog by the motor doesn't turn where the grids were placed");
                    helper.assertTrue(spinning(placed[1], level, east.transformCell(eastCog, rotation, Mirror.NONE)),
                            "the rotation doesn't cross the edge where the grids were placed");
                })
                .thenExecute(() -> press(helper, player, frequency, true))
                .thenIdle(5)
                .thenExecute(() -> {
                    for (int i = 0; i < 2; i++) helper.assertTrue(receiverOn(placed[i], west.transformCell(linkCells[i], rotation, Mirror.NONE)),
                            "a placed link stays off with the button held");
                })
                .thenExecute(() -> press(helper, player, frequency, false))
                .thenIdle(5)
                .thenExecute(() -> {
                    for (int i = 0; i < 2; i++) helper.assertTrue(!receiverOn(placed[i], west.transformCell(linkCells[i], rotation, Mirror.NONE)),
                            "a placed link stays on with the button let go");
                    helper.assertEntityNotPresent(EntityType.ITEM);
                })
                .thenSucceed();
    }

    /**
     * A powered tiny piston in one grid, kept from extending by tiny obsidian across the edge in the next: the two
     * taken together, nothing is left where they were. The piston, saved as it was, must not see the obsidian go
     * first and push out into the emptied block.
     */
    @GameTest(template = "empty_platform", timeoutTicks = 200)
    public void gridsTakenTogetherDoNotReactToOneAnotherGoing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos blockingAt = new BlockPos(1, 1, 1), pistonAt = new BlockPos(2, 1, 1);
        SubgridBlockEntity blocking = SubgridTestSupport.placeSubgrid(helper, blockingAt);
        SubgridBlockEntity pistonGrid = SubgridTestSupport.placeSubgrid(helper, pistonAt);
        int last = blocking.getGridSize() - 1;
        BlockPos piston = new BlockPos(0, 0, 4);
        SubgridTestSupport.placePiece(blocking, level, new BlockPos(last, 0, 4), Blocks.OBSIDIAN.defaultBlockState());
        SubgridTestSupport.placePiece(pistonGrid, level, piston,
                Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.WEST));
        ServerPlayer player = SubgridTestSupport.serverPlayer(helper, blocking);
        ItemStack save = new ItemStack(Registration.GRID_SAVE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, save);

        helper.startSequence()
                .thenExecute(() -> SubgridTestSupport.placePiece(pistonGrid, level, piston.above(), Blocks.REDSTONE_BLOCK.defaultBlockState()))
                .thenIdle(5)
                .thenExecute(() -> {
                    BlockState state = SubgridTestSupport.stateOf(pistonGrid, piston);
                    helper.assertTrue(state != null && !state.getValue(PistonBaseBlock.EXTENDED), "the piston extended into the obsidian");
                })
                .thenExecute(() -> {
                    player.setShiftKeyDown(true);
                    player.gameMode.useItemOn(player, level, save, InteractionHand.MAIN_HAND, SubgridTestSupport.topHit(blocking, BlockPos.ZERO));
                    player.setShiftKeyDown(false);
                    player.gameMode.useItemOn(player, level, save, InteractionHand.MAIN_HAND, SubgridTestSupport.topHit(pistonGrid, BlockPos.ZERO));
                    helper.assertTrue(GridSaveItem.isFull(save) && GridSaveItem.entries(GridSaveItem.grid(save)).size() == 2,
                            "the box was not taken");
                    helper.assertBlockPresent(Blocks.AIR, blockingAt);
                    helper.assertBlockPresent(Blocks.AIR, pistonAt);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.AIR, blockingAt);
                    helper.assertBlockPresent(Blocks.AIR, pistonAt);
                    helper.assertEntityNotPresent(EntityType.ITEM);
                })
                .thenSucceed();
    }

    /**
     * Two grids selected with a box past them (kept only as big as the two), after a take refused for a box too big
     * that keeps the corner; turned half round and put against a west face: refused, still full, while one spot is
     * taken, then placed reaching west from the face, the east grid now west of the other, its pieces turned.
     */
    @GameTest(template = "empty_platform", timeoutTicks = 100)
    public void twoGridsPlacedHalfTurnedAgainstAWestFace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos westAt = new BlockPos(1, 1, 1), eastAt = new BlockPos(2, 1, 1);
        SubgridBlockEntity west = SubgridTestSupport.placeSubgrid(helper, westAt);
        SubgridBlockEntity east = SubgridTestSupport.placeSubgrid(helper, eastAt);
        SubgridTestSupport.placePiece(west, level, BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        SubgridTestSupport.placePiece(east, level, BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        SubgridTestSupport.placePiece(east, level, new BlockPos(1, 0, 0), Blocks.STONE.defaultBlockState());
        ServerPlayer player = SubgridTestSupport.serverPlayer(helper, west);
        ItemStack save = new ItemStack(Registration.GRID_SAVE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, save);
        BlockPos wall = new BlockPos(4, 2, 3), toWest = new BlockPos(3, 2, 3), toEast = new BlockPos(2, 2, 3);
        helper.setBlock(wall, Blocks.STONE);
        BlockHitResult onWall = hitOn(helper, wall, Direction.WEST);

        helper.startSequence()
                .thenExecute(() -> {
                    player.setShiftKeyDown(true);
                    player.gameMode.useItemOn(player, level, save, InteractionHand.MAIN_HAND, SubgridTestSupport.topHit(west, BlockPos.ZERO));
                    player.setShiftKeyDown(false);
                    BlockPos corner = GridSaveItem.corner(save, level);
                    helper.assertTrue(corner != null, "a sneaking click set no corner");
                    player.gameMode.useItemOn(player, level, save, InteractionHand.MAIN_HAND, hitOn(helper, new BlockPos(1, 5, 1), Direction.UP));
                    helper.assertTrue(!GridSaveItem.isFull(save) && corner.equals(GridSaveItem.corner(save, level)),
                            "a box too big was taken, or lost the corner");
                    helper.assertTrue(level.getBlockEntity(helper.absolutePos(westAt)) instanceof SubgridBlockEntity, "a refused take took a grid");
                    player.gameMode.useItemOn(player, level, save, InteractionHand.MAIN_HAND, hitOn(helper, new BlockPos(2, 2, 1), Direction.UP));
                    helper.assertTrue(GridSaveItem.isFull(save), "the box was not taken");
                    helper.assertTrue(GridSaveItem.box(GridSaveItem.grid(save)).equals(new Vec3i(2, 1, 1)),
                            "the box kept is " + GridSaveItem.box(GridSaveItem.grid(save)) + ", not only the two grids");
                    for (int i = 0; i < 2; i++) helper.assertTrue(GridSaveItem.turnHeld(player), "R found no Grid Save to turn");
                    helper.setBlock(toEast, Blocks.STONE);
                    player.gameMode.useItemOn(player, level, save, InteractionHand.MAIN_HAND, onWall);
                    helper.assertTrue(GridSaveItem.isFull(save), "a refused placement emptied the Grid Save");
                    helper.assertBlockPresent(Blocks.AIR, toWest);
                    helper.setBlock(toEast, Blocks.AIR);
                    player.gameMode.useItemOn(player, level, save, InteractionHand.MAIN_HAND, onWall);
                    helper.assertTrue(!GridSaveItem.isFull(save), "the Grid Save still holds its grids");
                    BlockPos[] at = {toWest, toEast};
                    int[] pieces = {1, 2};
                    BlockPos turnedCell = west.transformCell(BlockPos.ZERO, Rotation.CLOCKWISE_180, Mirror.NONE);
                    for (int i = 0; i < 2; i++) {
                        helper.assertTrue(level.getBlockEntity(helper.absolutePos(at[i])) instanceof SubgridBlockEntity be
                                        && be.getPieces().size() == pieces[i] && be.getPieceAt(turnedCell.getX(), turnedCell.getY(), turnedCell.getZ()) != null,
                                "no grid of " + pieces[i] + " turned pieces at " + at[i]);
                    }
                    helper.assertEntityNotPresent(EntityType.ITEM);
                })
                .thenSucceed();
    }

    /** A click on the middle of {@code face} of the block at {@code pos} (relative). */
    private static BlockHitResult hitOn(GameTestHelper helper, BlockPos pos, Direction face) {
        BlockPos at = helper.absolutePos(pos);
        return new BlockHitResult(Vec3.atCenterOf(at).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5)), face, at, false);
    }

    private static LinkBehaviour realLink(GameTestHelper helper, BlockPos pos) {
        return BlockEntityBehaviour.get(helper.getLevel(), helper.absolutePos(pos), LinkBehaviour.TYPE);
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
