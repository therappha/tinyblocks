package com.therappha.tinyblocks.client;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Dev-only checks that need a real client, run by the clientSelfTest run configuration (system property
 * tinyblocks.selftest=mining, placement or all): it joins the dev world, builds pieces in front of the player and
 * acts through the real client path (MultiPlayerGameMode, so SubgridMiningInterceptor, vanilla's own destroy action
 * and the placement assist all run as in play), logs "[selftest] ... PASS/FAIL" and quits. Inert without the property.
 *
 * mining: two pieces in a row, broken with one click; only the front one may go.
 * valuebox: a tiny creative motor's speed slot: outlined when looked at, a click starts Create's hold-to-edit, and
 * the value the board would send reaches the motor and comes back to the client.
 * placement: Create's placement assist on a tiny shaft; the ghost shown and the block the click places must agree.
 */
@EventBusSubscriber(modid = TinyBlocks.MOD_ID, value = Dist.CLIENT)
public final class ClientSelfTest {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    /** "mining", "placement" or "all". */
    private static final String SELFTEST = System.getProperty("tinyblocks.selftest", "");
    private static final boolean ENABLED = !SELFTEST.isEmpty();
    private static final boolean MINING = SELFTEST.equals("mining") || SELFTEST.equals("all");
    private static final boolean PLACEMENT = SELFTEST.equals("placement") || SELFTEST.equals("all");
    private static final boolean VALUE_BOX = SELFTEST.equals("valuebox") || SELFTEST.equals("all");

    private static final BlockPos FRONT = new BlockPos(4, 4, 7);
    private static final BlockPos BEHIND = new BlockPos(4, 4, 6);

    private record Scenario(String name, GameType mode, BlockState piece, int holdTicks, ItemStack held) {}

    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario("creative click", GameType.CREATIVE, Blocks.STONE.defaultBlockState(), 0, ItemStack.EMPTY),
            new Scenario("creative click holding a block", GameType.CREATIVE, Blocks.STONE.defaultBlockState(), 0,
                    new ItemStack(Items.DIRT)),
            new Scenario("survival click on instantly breakable pieces", GameType.SURVIVAL,
                    Blocks.SLIME_BLOCK.defaultBlockState(), 3, ItemStack.EMPTY),
            new Scenario("survival click on instantly breakable pieces holding a block", GameType.SURVIVAL,
                    Blocks.SLIME_BLOCK.defaultBlockState(), 3, new ItemStack(Items.DIRT)),
            new Scenario("survival click on stone holding a block", GameType.SURVIVAL,
                    Blocks.STONE.defaultBlockState(), 3, new ItemStack(Items.DIRT)));
    private static boolean finished = false;

    private static final List<String> RESULTS = new ArrayList<>();
    private static final AtomicReference<String> CHECKED = new AtomicReference<>();
    private static int scenario = MINING ? 0 : Integer.MAX_VALUE;
    private static boolean placementDone = !PLACEMENT;
    private static boolean valueBoxDone = !VALUE_BOX;
    private static int phase = 0;
    private static int wait = 100; // let the world finish loading
    private static volatile BlockPos grid; // written on the server thread, read on the client's

    private ClientSelfTest() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || mc.gameMode == null || server == null) return;
        if (wait-- > 0) return;

        if (finished) return;
        if (scenario >= SCENARIOS.size() && !placementDone) {
            placementStep(mc, server);
            return;
        }
        if (scenario >= SCENARIOS.size() && !valueBoxDone) {
            valueBoxStep(mc, server);
            return;
        }
        if (scenario >= SCENARIOS.size()) {
            finished = true;
            RESULTS.forEach(r -> LOGGER.info("[selftest] {}", r));
            LOGGER.info("[selftest] done: {}", RESULTS.stream().allMatch(r -> r.startsWith("PASS")) ? "ALL PASS" : "FAILURES");
            mc.stop();
            return;
        }
        Scenario s = SCENARIOS.get(scenario);
        switch (phase) {
            case 0 -> { // build the scene on the server
                server.execute(() -> setUp(server, s.mode(), s.held(), s.piece(), List.of(FRONT, BEHIND)));
                phase = 1;
                wait = 20;
            }
            case 1 -> { // aim and click, through the client's own game mode
                lookNorth(mc);
                mc.gameMode.startDestroyBlock(grid, Direction.SOUTH);
                phase = s.holdTicks() > 0 ? 2 : 3;
                wait = s.holdTicks() > 0 ? 0 : 20;
                holdLeft = s.holdTicks();
            }
            case 2 -> { // the button held a few ticks, as a click is
                lookNorth(mc);
                mc.gameMode.continueDestroyBlock(grid, Direction.SOUTH);
                if (--holdLeft <= 0) {
                    mc.gameMode.stopDestroyBlock();
                    phase = 3;
                    wait = 20;
                }
            }
            case 3 -> {
                CHECKED.set(null);
                server.execute(() -> CHECKED.set(check(server)));
                phase = 4;
                wait = 5;
            }
            case 4 -> {
                String outcome = CHECKED.get();
                if (outcome == null) return;
                RESULTS.add(outcome + " — " + s.name());
                scenario++;
                phase = 0;
            }
            default -> { }
        }
    }

    private static int holdLeft;

    private static void lookNorth(Minecraft mc) {
        mc.player.setYRot(180f);
        mc.player.setXRot(0f);
        mc.player.setYHeadRot(180f);
        mc.player.yRotO = 180f;
        mc.player.xRotO = 0f;
        mc.player.yHeadRotO = 180f;
    }

    /** Pieces in an 8-grid two blocks north of the player, at eye level; FRONT is on the cell column the player looks along. */
    private static void setUp(MinecraftServer server, GameType mode, ItemStack held, BlockState pieceState, List<BlockPos> cells) {
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = player.serverLevel();
        player.setGameMode(mode);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held.copy());
        BlockPos feet = player.blockPosition();
        player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 180f, 0f);
        for (int dz = 1; dz <= 3; dz++) {
            for (int dy = 0; dy <= 2; dy++) {
                level.setBlockAndUpdate(feet.north(dz).above(dy), Blocks.AIR.defaultBlockState());
            }
        }
        grid = feet.north(2).above();
        SubgridBlockEntity be = SubgridBlockEntity.createAt(level, grid, 8);
        if (be == null) return;
        for (BlockPos cell : cells) {
            PlacedPiece piece = new PlacedPiece(VanillaBlockPiece.INSTANCE, cell, Direction.NORTH);
            piece.runtimeState = pieceState;
            be.placePiece(piece);
        }
        be.notifyUpdate();
    }

    private static String check(MinecraftServer server) {
        ServerLevel level = server.getPlayerList().getPlayers().get(0).serverLevel();
        if (!(level.getBlockEntity(grid) instanceof SubgridBlockEntity be)) {
            return "FAIL: the whole SubgridBlock is gone";
        }
        boolean front = be.getPieceAt(FRONT.getX(), FRONT.getY(), FRONT.getZ()) != null;
        boolean behind = be.getPieceAt(BEHIND.getX(), BEHIND.getY(), BEHIND.getZ()) != null;
        if (!front && behind) return "PASS: one piece broken, the one behind it kept";
        return "FAIL: front piece " + (front ? "kept" : "broken") + ", piece behind " + (behind ? "kept" : "broken");
    }

    // --- Create placement assist: the ghost shows where the click puts the block ---

    /** Not a constant: this class loads before Create's blocks are registered. */
    private static BlockState shaftX() {
        return com.simibubi.create.AllBlocks.SHAFT.getDefaultState()
                .setValue(com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock.AXIS, net.minecraft.core.Direction.Axis.X);
    }
    private static int placementPhase = 0;
    private static com.therappha.tinyblocks.compat.create.client.SubgridPlacementPreview.Ghost seenGhost;

    /**
     * A lone east-west tiny shaft in front of the player, aimed at on its south side near its east end while holding
     * a shaft: Create's helper puts the new one on the east end of the line. The ghost has to show up there, and the
     * real click has to put exactly that block there.
     */
    private static void placementStep(Minecraft mc, MinecraftServer server) {
        switch (placementPhase) {
            case 0 -> {
                server.execute(() -> setUp(server, GameType.SURVIVAL,
                        com.simibubi.create.AllBlocks.SHAFT.asStack(4), shaftX(), List.of(FRONT)));
                placementPhase = 1;
                wait = 20;
            }
            case 1 -> {
                // The shaft's south side (it is 4/16 of a cell thick, centred), 0.9 of the way east along it.
                Vec3 target = Vec3.atLowerCornerOf(grid).add(new Vec3(FRONT.getX() + 0.9, FRONT.getY() + 0.5,
                        FRONT.getZ() + 10 / 16.0).scale(1 / 8.0));
                lookAt(mc, target);
                placementPhase = 2;
                wait = 5; // the crosshair target and the placement assist refresh
            }
            case 2 -> {
                seenGhost = com.therappha.tinyblocks.compat.create.client.SubgridPlacementPreview.current();
                if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit) {
                    mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                }
                placementPhase = 3;
                wait = 20;
            }
            case 3 -> {
                CHECKED.set(null);
                var ghost = seenGhost;
                server.execute(() -> CHECKED.set(checkPlacement(server, ghost)));
                placementPhase = 4;
                wait = 5;
            }
            case 4 -> {
                String outcome = CHECKED.get();
                if (outcome == null) return;
                RESULTS.add(outcome + " — placement ghost of a shaft extending a tiny shaft line");
                placementDone = true;
            }
            default -> { }
        }
    }

    private static void lookAt(Minecraft mc, Vec3 target) {
        Vec3 d = target.subtract(mc.player.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.setYHeadRot(yaw);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
        mc.player.yHeadRotO = yaw;
    }

    private static String checkPlacement(MinecraftServer server,
                                         com.therappha.tinyblocks.compat.create.client.SubgridPlacementPreview.Ghost ghost) {
        if (ghost == null) return "FAIL: no ghost was shown";
        ServerLevel level = server.getPlayerList().getPlayers().get(0).serverLevel();
        if (!(level.getBlockEntity(grid) instanceof SubgridBlockEntity be)) return "FAIL: the SubgridBlock is gone";
        Vec3 local = ghost.corner().subtract(Vec3.atLowerCornerOf(grid)).scale(8);
        BlockPos cell = new BlockPos((int) Math.round(local.x), (int) Math.round(local.y), (int) Math.round(local.z));
        BlockPos expected = FRONT.east();
        if (!cell.equals(expected)) return "FAIL: the ghost was at cell " + cell + ", not at the end of the line " + expected;
        PlacedPiece placed = be.getPieceAt(cell.getX(), cell.getY(), cell.getZ());
        if (placed == null) return "FAIL: the ghost was at " + cell + " but the click put nothing there; pieces "
                + be.getPieces().stream().map(p -> p.anchor + "=" + p.runtimeState).toList();
        if (!ghost.state().equals(placed.runtimeState)) {
            return "FAIL: the ghost showed " + ghost.state() + " but the click placed " + placed.runtimeState;
        }
        return "PASS: the ghost showed " + ghost.state() + " at " + cell + " and the click placed it there";
    }

    // --- Create value boxes: a tiny creative motor's speed ---

    private static int valueBoxPhase = 0;
    private static volatile int speedNetId;
    private static final List<String> VALUE_BOX_PROBLEMS = new ArrayList<>();

    private static void valueBoxStep(Minecraft mc, MinecraftServer server) {
        switch (valueBoxPhase) {
            case 0 -> {
                server.execute(() -> {
                    setUp(server, GameType.CREATIVE, ItemStack.EMPTY, com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState()
                            .setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, Direction.UP), List.of(FRONT));
                    ServerLevel level = server.getPlayerList().getPlayers().get(0).serverLevel();
                    if (level.getBlockEntity(grid) instanceof SubgridBlockEntity be
                            && VanillaBlockPiece.boundPhantom(be.getPieceAt(FRONT.getX(), FRONT.getY(), FRONT.getZ()), be, level)
                            instanceof com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity motor) {
                        speedNetId = motor.generatedSpeed.netId();
                    }
                });
                valueBoxPhase = 1;
                wait = 20;
            }
            case 1 -> {
                // The speed slot on the motor's south side (MotorValueBox: 8, 8, 12.5 px, 1 px down for a motor facing up).
                lookAt(mc, Vec3.atLowerCornerOf(grid).add(new Vec3(FRONT.getX() + 0.5, FRONT.getY() + 7 / 16.0,
                        FRONT.getZ() + 12.5 / 16).scale(1 / 8.0)));
                valueBoxPhase = 2;
                wait = 5;
            }
            case 2 -> {
                if (com.therappha.tinyblocks.compat.create.client.VirtualView.outliner().getOutlines().isEmpty()) {
                    VALUE_BOX_PROBLEMS.add("no value box outline while looking at the slot");
                }
                if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit) {
                    mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                }
                var handler = com.simibubi.create.CreateClient.VALUE_SETTINGS_HANDLER;
                BlockPos expected = new BlockPos(grid.getX() * 8, grid.getY() * 8, grid.getZ() * 8).offset(FRONT);
                if (handler.interactHeldTicks == -1 || !expected.equals(handler.interactHeldPos)) {
                    VALUE_BOX_PROBLEMS.add("the click did not start editing the motor's value (held "
                            + handler.interactHeldTicks + " at " + handler.interactHeldPos + ", motor at " + expected + ")");
                }
                // What the value board sends once a speed is picked: row 1 is clockwise, 100 rpm.
                net.createmod.catnip.platform.CatnipServices.NETWORK.sendToServer(
                        new com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsPacket(expected, 1, 100,
                                null, null, Direction.UP, false, speedNetId));
                valueBoxPhase = 3;
                wait = 20;
            }
            case 3 -> {
                CHECKED.set(null);
                server.execute(() -> {
                    ServerLevel level = server.getPlayerList().getPlayers().get(0).serverLevel();
                    int speed = level.getBlockEntity(grid) instanceof SubgridBlockEntity be
                            && be.getPieceAt(FRONT.getX(), FRONT.getY(), FRONT.getZ()).runtimeBlockEntity
                            instanceof com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity motor
                            ? motor.generatedSpeed.getValue() : Integer.MIN_VALUE;
                    CHECKED.set(String.valueOf(speed));
                });
                valueBoxPhase = 4;
                wait = 5;
            }
            case 4 -> {
                String serverSpeed = CHECKED.get();
                if (serverSpeed == null) return;
                if (!"100".equals(serverSpeed)) VALUE_BOX_PROBLEMS.add("the server motor's speed is " + serverSpeed + ", not 100");
                int clientSpeed = mc.level.getBlockEntity(grid) instanceof SubgridBlockEntity be
                        && be.getPieceAt(FRONT.getX(), FRONT.getY(), FRONT.getZ()) != null
                        && be.getPieceAt(FRONT.getX(), FRONT.getY(), FRONT.getZ()).runtimeBlockEntity
                        instanceof com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity motor
                        ? motor.generatedSpeed.getValue() : Integer.MIN_VALUE;
                if (clientSpeed != 100) VALUE_BOX_PROBLEMS.add("the client still shows speed " + clientSpeed);
                RESULTS.add((VALUE_BOX_PROBLEMS.isEmpty() ? "PASS: outlined, click started editing, speed 100 on both sides"
                        : "FAIL: " + String.join("; ", VALUE_BOX_PROBLEMS)) + " — tiny creative motor value box");
                valueBoxDone = true;
            }
            default -> { }
        }
    }
}
