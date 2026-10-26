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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Dev-only checks that need a real client, run by the clientSelfTest run configuration (system property
 * tinyblocks.selftest=mining): it joins the dev world, builds two pieces in a row in front of the player, breaks
 * through the real client path (MultiPlayerGameMode, so SubgridMiningInterceptor and vanilla's own destroy
 * action both run as in play), logs "[selftest] ... PASS/FAIL" and quits. Inert without the property.
 */
@EventBusSubscriber(modid = TinyBlocks.MOD_ID, value = Dist.CLIENT)
public final class ClientSelfTest {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final boolean ENABLED = "mining".equals(System.getProperty("tinyblocks.selftest"));

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
    private static int scenario = 0;
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
                server.execute(() -> setUp(server, s));
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

    /** Two pieces in a row, two blocks north of the player, at eye level on the cell column the player looks along. */
    private static void setUp(MinecraftServer server, Scenario s) {
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = player.serverLevel();
        player.setGameMode(s.mode());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, s.held().copy());
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
        for (BlockPos cell : List.of(FRONT, BEHIND)) {
            PlacedPiece piece = new PlacedPiece(VanillaBlockPiece.INSTANCE, cell, Direction.NORTH);
            piece.runtimeState = s.piece();
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
}
