package com.therappha.tinyblocks.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * EXPERIMENT / dev convenience: {@code /tinyblocks assemble} starts the Simulated physics assembler the player
 * is looking at, without its lever drag gesture (the client consumes right clicks on it, so a server-side
 * right-click hook never fires). Calls the block entity's public {@code assembleOrDisassemble()} by
 * reflection: nothing else here is compiled against Simulated, and a missing class just means no match.
 */
@EventBusSubscriber(modid = TinyBlocks.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class DevAssemblerCommand {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ASSEMBLER_BLOCK_ENTITY = "PhysicsAssemblerBlockEntity";

    private DevAssemblerCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tinyblocks")
                .then(Commands.literal("assemble").executes(DevAssemblerCommand::assemble)));
    }

    private static final int SEARCH_RADIUS = 8;

    /** The assembler under the crosshair if there is one, otherwise the closest one within reach of the player. */
    private static BlockEntity findAssembler(ServerPlayer player) {
        if (player.pick(SEARCH_RADIUS, 1.0f, false) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && isAssembler(player.level().getBlockEntity(hit.getBlockPos()))) {
            return player.level().getBlockEntity(hit.getBlockPos());
        }
        BlockPos center = player.blockPosition();
        BlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS),
                center.offset(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS))) {
            BlockEntity candidate = player.level().getBlockEntity(pos);
            if (!isAssembler(candidate)) continue;
            double distance = pos.distSqr(center);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isAssembler(BlockEntity be) {
        return be != null && be.getClass().getSimpleName().equals(ASSEMBLER_BLOCK_ENTITY);
    }

    private static int assemble(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("[TinyBlocks] run this as a player, looking at a physics assembler"));
            return 0;
        }
        BlockEntity be = findAssembler(player);
        if (be == null) {
            source.sendFailure(Component.literal("[TinyBlocks] no physics assembler within " + SEARCH_RADIUS + " blocks of you"));
            return 0;
        }
        try {
            Method assemble = be.getClass().getMethod("assembleOrDisassemble");
            assemble.invoke(be);
            source.sendSuccess(() -> Component.literal("[TinyBlocks] assembleOrDisassemble() called"), false);
            return 1;
        } catch (ReflectiveOperationException e) {
            LOGGER.error("physics assembler command failed", e);
            source.sendFailure(Component.literal("[TinyBlocks] assembler call failed: " + e));
            return 0;
        }
    }
}
