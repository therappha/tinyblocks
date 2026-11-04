package com.therappha.tinyblocks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.items.BuildMode;
import com.therappha.tinyblocks.items.MinimizerBuild;
import com.therappha.tinyblocks.items.MinimizerItem;
import com.therappha.tinyblocks.subgrid.SubgridBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
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
        if (!MinimizerItem.isOn(minimizer) || !(minimizer.getItem() instanceof MinimizerItem item)) return;
        BuildMode mode = MinimizerBuild.mode(minimizer);
        if (mode == BuildMode.DESTROY) {
            outlineDoomed(event, mc);
            return;
        }
        if (!(mc.player.getMainHandItem().getItem() instanceof BlockItem)) return;
        if (mode == BuildMode.PLACE) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) return;
        MinimizerBuild.Target target = MinimizerBuild.target(mc.level, hit, minimizer);
        if (target == null) return;

        int size = target.gridSize();
        MinimizerBuild.Corner corner = MinimizerBuild.corner(minimizer);
        BlockPos min = target.cell(), max = target.cell();
        if (corner != null && MinimizerBuild.sameShape(mc.level, corner, target)) {
            BlockPos[] bounds = mode.bounds(corner.cell(), MinimizerBuild.secondCorner(minimizer, target), corner.faceAxis());
            min = bounds[0];
            max = bounds[1];
        }

        Vec3 camera = SubLevelView.enter(event, hit.getBlockPos());
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
        SubLevelView.exit(event);
    }

    /** Destroy mode: the grid a click would break, in red, the whole block as it all goes. Nothing on one it keeps. */
    private static void outlineDoomed(RenderLevelStageEvent event, Minecraft mc) {
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) return;
        BlockPos pos = hit.getBlockPos();
        if (!(mc.level.getBlockState(pos).getBlock() instanceof SubgridBlock)) return;
        if (!MinimizerItem.mayBreak(mc.player, mc.level, pos)) return;
        Vec3 camera = SubLevelView.enter(event, pos);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        LevelRenderer.renderLineBox(event.getPoseStack(), buffers.getBuffer(RenderType.lines()),
                new AABB(pos).move(camera.scale(-1)).inflate(1 / 512d), 1f, 0.15f, 0.15f, 1f);
        buffers.endBatch(RenderType.lines());
        SubLevelView.exit(event);
    }
}
