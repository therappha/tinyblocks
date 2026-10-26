package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.network.SubgridMinePiecePayload;
import com.therappha.tinyblocks.setup.SubgridEventHandler;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * One click breaks one piece. A real player's client mines a piece itself and asks the server to remove that one
 * cell (SubgridMiningInterceptor -> SubgridMinePiecePayload), but vanilla still sends its own "destroy the block"
 * action, which the server turns into a BreakEvent. Re-tracing the crosshair there, after the first piece is
 * already gone, landed on the piece behind it: two pieces in a row went at once.
 */
@GameTestHolder(TinyBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public class SubgridMiningGameTest {

    @GameTest(template = "empty_platform")
    public void oneCreativeClickBreaksOnlyTheFrontPiece(GameTestHelper helper) {
        BlockPos gridPos = new BlockPos(2, 1, 2);
        SubgridBlockEntity be = SubgridTestSupport.placeSubgrid(helper, gridPos);
        int size = be.getGridSize();
        BlockPos front = new BlockPos(4, 4, size - 1);
        BlockPos behind = new BlockPos(4, 4, size - 2);
        SubgridTestSupport.placePiece(be, helper.getLevel(), front, Blocks.STONE.defaultBlockState());
        SubgridTestSupport.placePiece(be, helper.getLevel(), behind, Blocks.STONE.defaultBlockState());

        // Standing south of the grid, eyes level with both pieces, looking north straight through them (the
        // template has a block there, which would be all the crosshair sees).
        helper.setBlock(gridPos.south(), Blocks.AIR);
        // A real (non-fake) player, never added to the world: joining would have mods talk to a client that isn't there.
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "miner"),
                net.minecraft.server.level.ClientInformation.createDefault());
        BlockPos real = be.getBlockPos();
        Vec3 eye = new Vec3(real.getX() + (front.getX() + 0.5) / size, real.getY() + (front.getY() + 0.5) / size, real.getZ() + 1.3);
        player.moveTo(eye.x, eye.y - player.getEyeHeight(), eye.z, 180f, 0f);
        // A living entity looks where its head points, not its body.
        player.setYHeadRot(180f);
        player.yHeadRotO = 180f;

        SubgridEventHandler.handleMinePiece(new SubgridMinePiecePayload(real, front), player, helper.getLevel());
        // What vanilla's own "destroy the block" action becomes on the server (ServerPlayerGameMode#destroyBlock,
        // posted directly: the rest of that method talks to the player's connection, which this one hasn't got).
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(
                helper.getLevel(), real, helper.getLevel().getBlockState(real), player));

        helper.assertTrue(be.getPieceAt(front.getX(), front.getY(), front.getZ()) == null, "the front piece was not mined (crosshair on " + player.pick(5.0, 0f, false).getLocation() + ")");
        helper.assertTrue(be.getPieceAt(behind.getX(), behind.getY(), behind.getZ()) != null,
                "one click also broke the piece behind the one mined");
        helper.succeed();
    }
}
