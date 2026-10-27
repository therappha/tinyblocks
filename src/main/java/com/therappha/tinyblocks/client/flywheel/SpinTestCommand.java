package com.therappha.tinyblocks.client.flywheel;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * EXPERIMENT (feat/flywheel-create-experiment) — dev-only test aid, NOT for release.
 * <pre>/tinyblocks spin &lt;speed&gt;</pre> forces a client-side rotation speed on every Create kinetic
 * block entity in the subgrid being looked at, so the Flywheel visuals can be checked spinning without
 * a working kinetic network behind them.
 */
@EventBusSubscriber(modid = TinyBlocks.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class SpinTestCommand {

    private SpinTestCommand() {}

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        if (!ModList.get().isLoaded("create") || !FlywheelLoaded.isLoaded()) return;
        event.getDispatcher().register(Commands.literal("tinyblocks")
                .then(Commands.literal("spin")
                        .then(Commands.argument("speed", FloatArgumentType.floatArg(-256f, 256f))
                                .executes(ctx -> spin(FloatArgumentType.getFloat(ctx, "speed"))))));
    }

    private static int spin(float speed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit)
                || !(mc.level.getBlockEntity(hit.getBlockPos()) instanceof SubgridBlockEntity be)) {
            mc.player.displayClientMessage(Component.literal("[TinyBlocks] look at a subgrid block"), true);
            return 0;
        }
        int count = 0;
        for (PlacedPiece piece : be.getPieces()) {
            if (piece.runtimeBlockEntity instanceof KineticBlockEntity kinetic) {
                kinetic.setSpeed(speed);
                count++;
            }
        }
        VisualizationHelper.queueUpdate(be);
        mc.player.displayClientMessage(Component.literal("[TinyBlocks] spin " + speed + " on " + count + " kinetic piece(s)"), true);
        return count;
    }
}
