package com.therappha.tinyblocks.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.diff.RealSpaceView;
import com.therappha.tinyblocks.diff.SpaceDiff;
import com.therappha.tinyblocks.diff.SpaceView;
import com.therappha.tinyblocks.diff.TinySpaceView;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The comparison from the client's side, for what only a client has. {@code /tinyblocksclient diff} takes the same
 * arguments as {@code /tinyblocks diff} and compares the pieces this client holds with the integrated server's (a piece
 * that never reached it, or is stale) and with the real blocks as this client has them. Only block states: a client has
 * no inventories or comparator outputs to compare.
 */
@EventBusSubscriber(modid = TinyBlocks.MOD_ID, value = Dist.CLIENT)
public final class ClientDiffCommand {

    private ClientDiffCommand() {}

    private static final int LINES = 10;

    /** The pieces as this client has them, cells laid out as in TinySpaceView with no shift. */
    private record ClientTinyView(Level level, BlockPos origin, int gridSize) implements SpaceView {
        private PlacedPiece piece(BlockPos cell) {
            BlockPos at = origin.offset(Math.floorDiv(cell.getX(), gridSize), Math.floorDiv(cell.getY(), gridSize), Math.floorDiv(cell.getZ(), gridSize));
            if (!(level.getBlockEntity(at) instanceof SubgridBlockEntity grid) || grid.getGridSize() != gridSize) return null;
            return grid.getPieceAt(Math.floorMod(cell.getX(), gridSize), Math.floorMod(cell.getY(), gridSize), Math.floorMod(cell.getZ(), gridSize));
        }

        @Override
        public BlockState state(BlockPos cell) {
            PlacedPiece piece = piece(cell);
            return piece != null ? piece.definition.renderState(piece) : Blocks.AIR.defaultBlockState();
        }

        @Override
        public BlockState drawn(BlockPos cell) {
            return state(cell);
        }

        @Override
        public BlockEntity blockEntity(BlockPos cell) {
            return null;
        }

        @Override
        public int analogSignal(BlockPos cell) {
            return 0;
        }
    }

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tinyblocksclient").then(Commands.literal("diff")
                .then(Commands.argument("real", BlockPosArgument.blockPos())
                .then(Commands.argument("grid", BlockPosArgument.blockPos())
                .then(Commands.argument("gridSize", IntegerArgumentType.integer(2, 16))
                .then(Commands.argument("width", IntegerArgumentType.integer(1, 64))
                .then(Commands.argument("height", IntegerArgumentType.integer(1, 64))
                .then(Commands.argument("depth", IntegerArgumentType.integer(1, 64))
                        .executes(ClientDiffCommand::run)))))))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) return 0;
        BlockPos real = ctx.getArgument("real", Coordinates.class).getBlockPos(source);
        BlockPos grid = ctx.getArgument("grid", Coordinates.class).getBlockPos(source);
        int gridSize = IntegerArgumentType.getInteger(ctx, "gridSize");
        List<BlockPos> cells = SpaceDiff.box(IntegerArgumentType.getInteger(ctx, "width"),
                IntegerArgumentType.getInteger(ctx, "height"), IntegerArgumentType.getInteger(ctx, "depth"));

        Map<BlockPos, Map<String, String>> client = SpaceDiff.snapshot(new ClientTinyView(level, grid, gridSize), cells);
        int found = report(source, "cliente contra blocos reais", "blocos", "cliente",
                SpaceDiff.compare(SpaceDiff.snapshot(new RealSpaceView(level, real), cells), client, SpaceDiff.STATE));

        var server = minecraft.getSingleplayerServer();
        if (server == null) {
            source.sendSuccess(() -> Component.literal("[diff] sem servidor integrado: cliente contra servidor não comparado"), false);
            return found;
        }
        try {
            ServerLevel serverLevel = server.getLevel(level.dimension());
            Map<BlockPos, Map<String, String>> onServer = server.submit(() -> SpaceDiff.snapshot(
                    new TinySpaceView(serverLevel, grid, gridSize, BlockPos.ZERO), cells)).get(5, TimeUnit.SECONDS);
            found += report(source, "servidor contra cliente", "servidor", "cliente", SpaceDiff.compare(onServer, client, SpaceDiff.STATE));
        } catch (Exception e) {
            source.sendFailure(Component.literal("[diff] não consegui ler o servidor: " + e));
        }
        return found;
    }

    private static int report(CommandSourceStack source, String title, String left, String right, List<SpaceDiff.Difference> differences) {
        source.sendSuccess(() -> Component.literal("[diff] " + title + ": " + differences.size() + " diferenças"), false);
        differences.stream().limit(LINES).forEach(d -> source.sendSuccess(() -> Component.literal(SpaceDiff.describe(d, left, right)), false));
        return differences.size();
    }
}
