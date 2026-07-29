package com.therappha.tinyblocks.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlock;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

@EventBusSubscriber(modid = TinyBlocks.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class TinyBlocksCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("tinyblocks")
                .then(Commands.literal("status").executes(TinyBlocksCommand::executeStatus))
                .then(Commands.literal("help").executes(TinyBlocksCommand::executeHelp))
        );
    }

    private static int executeHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
            "[TinyGears] Commands:\n" +
            "  /tinyblocks status — info about the SubgridBlock you're looking at\n" +
            "  /tinyblocks help   — this message"
        ), false);
        return 1;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("Must be run by a player"));
            return 0;
        }

        HitResult hit = player.pick(5.0, 0f, false);
        if (!(hit instanceof BlockHitResult bhr)) {
            source.sendFailure(Component.literal("Not looking at a block"));
            return 0;
        }

        BlockPos pos = bhr.getBlockPos();
        ServerLevel level = source.getLevel();
        BlockState state = level.getBlockState(pos);

        if (!(state.getBlock() instanceof SubgridBlock)) {
            source.sendFailure(Component.literal("Not looking at a SubgridBlock (looking at: " + pos.toShortString() + ")"));
            return 0;
        }

        if (!(level.getBlockEntity(pos) instanceof SubgridBlockEntity be)) {
            source.sendFailure(Component.literal("No BlockEntity at " + pos.toShortString()));
            return 0;
        }

        List<PlacedPiece> pieces = be.getPieces();
        source.sendSuccess(() -> Component.literal(
            "SubgridBlock at " + pos.toShortString() + " — " + pieces.size() + " piece(s):"
        ), false);

        for (int i = 0; i < pieces.size(); i++) {
            PlacedPiece p = pieces.get(i);
            int fi = i;
            source.sendSuccess(() -> Component.literal(String.format(
                "  [%d] %s  pos=(%d,%d,%d)  facing=%s",
                fi,
                p.definition.id().getPath(),
                p.anchor.getX(), p.anchor.getY(), p.anchor.getZ(),
                p.facing.getName()
            )), false);
        }

        return pieces.size();
    }
}
