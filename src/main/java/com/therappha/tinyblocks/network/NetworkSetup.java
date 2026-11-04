package com.therappha.tinyblocks.network;

import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.client.SubgridMiningInterceptor;
import com.therappha.tinyblocks.client.flywheel.FlywheelCompat;
import com.therappha.tinyblocks.client.flywheel.FlywheelLoaded;
import com.therappha.tinyblocks.items.GridSaveItem;
import com.therappha.tinyblocks.items.MinimizeMe;
import com.therappha.tinyblocks.items.MinimizerBuild;
import com.therappha.tinyblocks.items.MinimizerItem;
import com.therappha.tinyblocks.setup.SubgridEventHandler;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.PistonAnimation;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = TinyBlocks.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class NetworkSetup {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(SubgridPieceAddedPayload.TYPE, SubgridPieceAddedPayload.STREAM_CODEC, NetworkSetup::handlePieceAdded);
        registrar.playToClient(SubgridPieceRemovedPayload.TYPE, SubgridPieceRemovedPayload.STREAM_CODEC, NetworkSetup::handlePieceRemoved);
        registrar.playToClient(PistonAnimationPayload.TYPE, PistonAnimationPayload.STREAM_CODEC, NetworkSetup::handlePistonAnimation);
        registrar.playToClient(PieceBlockEventPayload.TYPE, PieceBlockEventPayload.STREAM_CODEC, NetworkSetup::handlePieceBlockEvent);
        registrar.playToClient(PhantomDataPayload.TYPE, PhantomDataPayload.STREAM_CODEC, NetworkSetup::handlePhantomData);
        registrar.playToServer(SubgridMinePiecePayload.TYPE, SubgridMinePiecePayload.STREAM_CODEC, NetworkSetup::handleMinePiece);
        registrar.playToServer(MinimizerModePayload.TYPE, MinimizerModePayload.STREAM_CODEC, NetworkSetup::handleMinimizerMode);
        registrar.playToServer(MinimizerLiftPayload.TYPE, MinimizerLiftPayload.STREAM_CODEC, NetworkSetup::handleMinimizerLift);
        registrar.playToServer(MinimizerSizePayload.TYPE, MinimizerSizePayload.STREAM_CODEC, NetworkSetup::handleMinimizerSize);
        registrar.playToServer(MinimizerTogglePayload.TYPE, MinimizerTogglePayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> MinimizerItem.toggleOn(ctx.player())));
        registrar.playToServer(GridSaveTurnPayload.TYPE, GridSaveTurnPayload.STREAM_CODEC, NetworkSetup::handleGridSaveTurn);
        registrar.playToServer(MinimizerLockPayload.TYPE, MinimizerLockPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> MinimizerItem.toggleLock(ctx.player())));
        registrar.playToServer(MinimizeMePayload.TYPE, MinimizeMePayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> MinimizeMe.toggle(ctx.player())));
    }

    private static void handleGridSaveTurn(GridSaveTurnPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> GridSaveItem.turnHeld(ctx.player()));
    }

    private static void handleMinimizerSize(MinimizerSizePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.player();
            var minimizer = player.getMainHandItem().getItem() instanceof MinimizerItem
                    ? player.getMainHandItem() : player.getOffhandItem();
            if (!(minimizer.getItem() instanceof MinimizerItem item)) return;
            MinimizerItem.setGridSize(minimizer, payload.size());
            MinimizerBuild.setCorner(minimizer, null); // a corner of the old size
            player.displayClientMessage(Component.translatable("item.tinyblocks.minimizer.sized",
                    item.gridSize(minimizer)), true);
        });
    }

    private static void handleMinimizerLift(MinimizerLiftPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.player();
            var minimizer = player.getOffhandItem();
            if (!(minimizer.getItem() instanceof MinimizerItem)
                    || MinimizerBuild.corner(minimizer) == null) return;
            int lift = MinimizerBuild.addLift(minimizer, Integer.signum(payload.delta()));
            player.displayClientMessage(Component.translatable("tinyblocks.build.lift",
                    (lift > 0 ? "+" : "") + lift), true);
        });
    }

    /** The one mode key acts on what is in hand: a Grid Save held in the main hand first, then a Minimizer in either. */
    private static void handleMinimizerMode(MinimizerModePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.player();
            var main = player.getMainHandItem();
            var off = player.getOffhandItem();
            if (main.getItem() instanceof GridSaveItem) {
                GridSaveItem.switchMode(main, player);
                return;
            }
            if (off.getItem() instanceof GridSaveItem
                    && !(main.getItem() instanceof MinimizerItem)) {
                GridSaveItem.switchMode(off, player);
                return;
            }
            var minimizer = player.getOffhandItem().getItem() instanceof MinimizerItem
                    ? player.getOffhandItem() : player.getMainHandItem();
            if (!(minimizer.getItem() instanceof MinimizerItem)) return;
            var mode = MinimizerBuild.cycle(minimizer);
            player.displayClientMessage(Component.translatable("tinyblocks.build.mode",
                    Component.translatable(mode.translationKey())), true);
        });
    }

    private static void handlePieceAdded(SubgridPieceAddedPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Level level = ctx.player().level();
            if (level.getBlockEntity(payload.pos()) instanceof SubgridBlockEntity subgrid) {
                subgrid.applyRemoteAdd(PlacedPiece.load(payload.pieceNbt()));
            }
        });
    }

    private static void handlePieceRemoved(SubgridPieceRemovedPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Level level = ctx.player().level();
            if (level.getBlockEntity(payload.pos()) instanceof SubgridBlockEntity subgrid) {
                var cell = payload.cell();
                subgrid.removePieceAt(cell.getX(), cell.getY(), cell.getZ());
            }
            // This payload only ever reaches a client (playToClient) — safe to release the mining
            // interceptor's in-flight guard here (see SubgridMiningInterceptor for why it exists).
            SubgridMiningInterceptor.onMineConfirmed();
        });
    }

    private static void handlePistonAnimation(PistonAnimationPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Level level = ctx.player().level();
            if (level.getBlockEntity(payload.pos()) instanceof SubgridBlockEntity subgrid) {
                PistonAnimation.applyClientAnimation(subgrid, payload.cell(), payload.beNbt(), level);
            }
        });
    }

    private static void handlePhantomData(PhantomDataPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Level level = ctx.player().level();
            if (!(level.getBlockEntity(payload.pos()) instanceof SubgridBlockEntity subgrid)) return;
            VanillaBlockPiece.applyPhantomData(subgrid, payload.cell(), payload.nbt(), level);
            if (FlywheelLoaded.isLoaded()) {
                FlywheelCompat.queueUpdate(subgrid);
            }
        });
    }

    private static void handlePieceBlockEvent(PieceBlockEventPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Level level = ctx.player().level();
            if (!(level.getBlockEntity(payload.pos()) instanceof SubgridBlockEntity subgrid)) return;
            PlacedPiece piece = subgrid.getPieceAt(payload.cell().getX(), payload.cell().getY(), payload.cell().getZ());
            if (piece == null) return;
            var blockEntity = VanillaBlockPiece.blockEntityFor(piece, subgrid, level);
            if (blockEntity != null) {
                blockEntity.triggerEvent(payload.eventId(), payload.eventParam());
            }
        });
    }

    private static void handleMinePiece(SubgridMinePiecePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer serverPlayer && serverPlayer.level() instanceof ServerLevel serverLevel) {
                SubgridEventHandler.handleMinePiece(payload, serverPlayer, serverLevel);
            }
        });
    }
}
