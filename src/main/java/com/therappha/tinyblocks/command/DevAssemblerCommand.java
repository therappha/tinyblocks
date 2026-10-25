package com.therappha.tinyblocks.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
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

    private static int assemble(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("[TinyBlocks] run this as a player, looking at a physics assembler"));
            return 0;
        }
        if (!(player.pick(8.0, 1.0f, false) instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("[TinyBlocks] look at a physics assembler"));
            return 0;
        }
        BlockEntity be = player.level().getBlockEntity(hit.getBlockPos());
        if (be == null || !be.getClass().getSimpleName().equals(ASSEMBLER_BLOCK_ENTITY)) {
            source.sendFailure(Component.literal("[TinyBlocks] that is not a physics assembler"));
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
