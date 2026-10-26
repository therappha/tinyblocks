package com.therappha.tinyblocks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.items.BuildMode;
import com.therappha.tinyblocks.items.MinimizerBuild;
import com.therappha.tinyblocks.items.MinimizerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * A Minimizer in a shape mode shows what the next click covers: the cell a first corner would be, or, with a corner
 * held, the box of the shape from it to the cell under the crosshair (MinimizerShapes builds the same one).
 */
@EventBusSubscriber(modid = TinyBlocks.MOD_ID, value = Dist.CLIENT)
public final class MinimizerPreview {

    private MinimizerPreview() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ItemStack minimizer = mc.player.getOffhandItem();
        if (!(minimizer.getItem() instanceof MinimizerItem item) || !(mc.player.getMainHandItem().getItem() instanceof BlockItem)) return;
        BuildMode mode = MinimizerBuild.mode(minimizer);
        if (mode == BuildMode.PLACE) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) return;
        MinimizerBuild.Target target = MinimizerBuild.target(mc.level, hit, item);
        if (target == null) return;

        int size = target.gridSize();
        MinimizerBuild.Corner corner = MinimizerBuild.corner(minimizer);
        BlockPos min = target.cell(), max = target.cell();
        if (corner != null && corner.gridSize() == size) {
            BlockPos[] bounds = mode.bounds(corner.cell(), MinimizerBuild.secondCorner(minimizer, target), corner.faceAxis());
            min = bounds[0];
            max = bounds[1];
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        double cell = 1.0 / size;
        poseStack.pushPose();
        // To the box's corner in doubles first: world-sized coordinates lose precision as floats.
        poseStack.translate(min.getX() * cell - camera.x, min.getY() * cell - camera.y, min.getZ() * cell - camera.z);
        LevelRenderer.renderLineBox(poseStack, lines, 0, 0, 0,
                (max.getX() + 1 - min.getX()) * cell, (max.getY() + 1 - min.getY()) * cell, (max.getZ() + 1 - min.getZ()) * cell,
                0.3f, 0.9f, 1f, 1f);
        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
    }
}
