package com.therappha.tinyblocks.compat.create;

import com.mojang.brigadier.context.CommandContext;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.GridRay;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * EXPERIMENT dev convenience until clicking a tiny redstone link's slots works: /tinyblocks frequency blue|red
 * sets the looked-at tiny link's first (blue) or second (red) frequency to the item in the main hand.
 */
@EventBusSubscriber(modid = TinyBlocks.MOD_ID)
public final class LinkFrequencyCommand {

    private LinkFrequencyCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tinyblocks")
                .then(Commands.literal("frequency")
                        .then(Commands.literal("blue").executes(ctx -> set(ctx, true)))
                        .then(Commands.literal("red").executes(ctx -> set(ctx, false)))));
    }

    private static int set(CommandContext<CommandSourceStack> ctx, boolean first) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("[TinyBlocks] run this as a player, looking at a tiny redstone link"));
            return 0;
        }
        LinkBehaviour link = lookedAtLink(player);
        if (link == null) {
            source.sendFailure(Component.literal("[TinyBlocks] no tiny redstone link under the crosshair"));
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        link.setFrequency(first, held);
        if (link.blockEntity instanceof net.minecraft.world.level.block.entity.BlockEntity phantom) phantom.setChanged();
        source.sendSuccess(() -> Component.literal("[TinyBlocks] " + (first ? "blue" : "red") + " frequency set to "
                + (held.isEmpty() ? "nothing" : held.getHoverName().getString())), false);
        return 1;
    }

    private static LinkBehaviour lookedAtLink(ServerPlayer player) {
        if (!(player.pick(player.blockInteractionRange(), 1.0f, false) instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK
                || !(player.serverLevel().getBlockEntity(hit.getBlockPos()) instanceof SubgridBlockEntity grid)) {
            return null;
        }
        Vec3i cell = GridRay.cellAt(hit.getBlockPos(), hit.getLocation(), hit.getDirection(), grid.getGridSize());
        PlacedPiece piece = grid.getPieceAt(cell.getX(), cell.getY(), cell.getZ());
        if (piece == null || !(piece.definition instanceof VanillaBlockPiece)) return null;
        grid.setChanged(); // the frequency lives in the phantom, saved with the grid
        return BlockEntityBehaviour.get(VanillaBlockPiece.boundPhantom(piece, grid, player.serverLevel()), LinkBehaviour.TYPE);
    }
}
