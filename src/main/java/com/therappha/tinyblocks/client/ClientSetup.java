package com.therappha.tinyblocks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.items.TinyPieceItem;
import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlock;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = TinyBlocks.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Registration.SUBGRID_BLOCK_ENTITY.get(), SubgridRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerBlock(SubgridClientExtensions.INSTANCE,
            Registration.SUBGRID_BLOCK_2.get(), Registration.SUBGRID_BLOCK_4.get(),
            Registration.SUBGRID_BLOCK.get(), Registration.SUBGRID_BLOCK_16.get());
    }
}

@EventBusSubscriber(modid = TinyBlocks.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
class SubgridClientHandler {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("tinyblocks")
                .then(Commands.literal("highlight")
                    .executes(ctx -> toggleHighlight(16))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                        .executes(ctx -> toggleHighlight(IntegerArgumentType.getInteger(ctx, "radius")))
                    )
                )
        );
    }

    private static int toggleHighlight(int radius) {
        boolean on = SubgridHighlight.toggle(radius);
        Minecraft.getInstance().player.displayClientMessage(
            Component.literal(on
                ? "[TinyBlocks] Highlight ON (radius " + radius + ")"
                : "[TinyBlocks] Highlight OFF"),
            true
        );
        return 1;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        MiningTracker.tick();
    }

    @SubscribeEvent
    public static void onRenderBlockHighlight(RenderHighlightEvent.Block event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        BlockHitResult hit = event.getTarget();
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (!(state.getBlock() instanceof SubgridBlock)) return;

        event.setCanceled(true);

        BlockEntity be = mc.level.getBlockEntity(pos);
        if (!(be instanceof SubgridBlockEntity subgrid)) return;

        int gs = subgrid.gridSize;
        int max = gs - 1;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource bufferSource = event.getMultiBufferSource();
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());

        Direction face = hit.getDirection();
        Vec3 hitLoc = hit.getLocation();
        float cell = 1f / gs;

        double nudge = 0.5 / gs;
        int gx = Mth.clamp((int)(((hitLoc.x - pos.getX()) - face.getStepX() * nudge) * gs), 0, max);
        int gy = Mth.clamp((int)(((hitLoc.y - pos.getY()) - face.getStepY() * nudge) * gs), 0, max);
        int gz = Mth.clamp((int)(((hitLoc.z - pos.getZ()) - face.getStepZ() * nudge) * gs), 0, max);

        poseStack.pushPose();
        poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);

        short owner = subgrid.ownerAt(gx, gy, gz);
        if (owner >= 0) {
            PlacedPiece p = subgrid.getPieces().get(owner);
            double x1 = p.anchor.getX() * cell;
            double y1 = p.anchor.getY() * cell;
            double z1 = p.anchor.getZ() * cell;
            LevelRenderer.renderLineBox(poseStack, lines,
                x1, y1, z1,
                x1 + p.footprint.getX() * cell,
                y1 + p.footprint.getY() * cell,
                z1 + p.footprint.getZ() * cell,
                1f, 1f, 0f, 1f);
        } else {
            LevelRenderer.renderLineBox(poseStack, lines,
                gx * cell, gy * cell, gz * cell,
                (gx + 1) * cell, (gy + 1) * cell, (gz + 1) * cell,
                0.5f, 0.5f, 0.5f, 0.5f);
        }

        poseStack.popPose();

        if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch(RenderType.lines());
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (SubgridHighlight.isEnabled()) {
            renderHighlightedSubgrids(mc, event);
        }

        if (!(mc.hitResult instanceof BlockHitResult hit)) return;
        if (hit.getType() == HitResult.Type.MISS) return;

        Item held = mc.player.getMainHandItem().getItem();
        if (!(held instanceof TinyPieceItem pieceItem) || !pieceItem.showPreview()) return;

        BlockPos clickedPos = hit.getBlockPos();
        Direction face = hit.getDirection();
        BlockState clickedState = mc.level.getBlockState(clickedPos);
        Vec3 hitLoc = hit.getLocation();

        BlockPos targetPos;
        int gs;
        if (clickedState.getBlock() instanceof SubgridBlock csb) {
            targetPos = clickedPos;
            gs = mc.level.getBlockEntity(clickedPos) instanceof SubgridBlockEntity cbe
                ? cbe.gridSize : csb.gridSize;
        } else {
            targetPos = clickedPos.relative(face);
            BlockState targetState = mc.level.getBlockState(targetPos);
            if (!targetState.isAir() && !(targetState.getBlock() instanceof SubgridBlock)) return;
            gs = targetState.getBlock() instanceof SubgridBlock tsb
                ? (mc.level.getBlockEntity(targetPos) instanceof SubgridBlockEntity tbe ? tbe.gridSize : tsb.gridSize)
                : Registration.SUBGRID_BLOCK.get().gridSize;
        }

        int[] grid = TinyPieceItem.computeGridCell(clickedState, clickedPos, face, hitLoc, gs);
        int gx = grid[0], gy = grid[1], gz = grid[2];

        BlockState targetState = mc.level.getBlockState(targetPos);
        if (targetState.getBlock() instanceof SubgridBlock) {
            BlockEntity be = mc.level.getBlockEntity(targetPos);
            if (be instanceof SubgridBlockEntity sg && sg.ownerAt(gx, gy, gz) != -1) return;
        }

        BlockState ghostState = pieceItem.pieceDefinition().renderState(face.getAxis());
        if (ghostState == null) return;

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        float cell = 1f / gs;

        MultiBufferSource ghostSource = type -> new GhostVertexConsumer(bufferSource.getBuffer(type));

        poseStack.pushPose();
        poseStack.translate(
            targetPos.getX() - camera.x,
            targetPos.getY() - camera.y,
            targetPos.getZ() - camera.z
        );
        poseStack.pushPose();
        poseStack.translate(gx * cell, gy * cell, gz * cell);
        poseStack.scale(cell, cell, cell);
        mc.getBlockRenderer().renderSingleBlock(ghostState, poseStack, ghostSource, 0xF000F0, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        poseStack.popPose();

        bufferSource.endBatch();
    }

    private static void renderHighlightedSubgrids(Minecraft mc, RenderLevelStageEvent event) {
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());

        BlockPos center = mc.player.blockPosition();
        int r = SubgridHighlight.getRadius();

        BlockPos.betweenClosed(
            center.offset(-r, -r, -r),
            center.offset(r, r, r)
        ).forEach(pos -> {
            if (!(mc.level.getBlockState(pos).getBlock() instanceof SubgridBlock)) return;
            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            LevelRenderer.renderLineBox(poseStack, lines, 0, 0, 0, 1, 1, 1, 0f, 1f, 1f, 0.6f);
            poseStack.popPose();
        });

        bufferSource.endBatch(RenderType.lines());
    }
}
