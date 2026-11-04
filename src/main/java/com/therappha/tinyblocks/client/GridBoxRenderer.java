package com.therappha.tinyblocks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.items.GridBoxBlockEntity;
import com.therappha.tinyblocks.setup.Registration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Create's package, 12 by 12 by 12 pixels from (2, 0, 2) in its block, stretched over all the blocks of a box. */
public final class GridBoxRenderer implements BlockEntityRenderer<GridBoxBlockEntity> {

    private static final float MODEL_SIZE = 12 / 16f, MODEL_INSET = 2 / 16f;

    GridBoxRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(GridBoxBlockEntity box, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!box.isMain()) return;
        Vec3i size = box.size();
        var blocks = Minecraft.getInstance().getBlockRenderer();
        pose.pushPose();
        pose.scale(size.getX() / MODEL_SIZE, size.getY() / MODEL_SIZE, size.getZ() / MODEL_SIZE);
        pose.translate(-MODEL_INSET, 0, -MODEL_INSET);
        blocks.getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), box.getBlockState(),
                blocks.getBlockModel(box.getBlockState()), 1f, 1f, 1f, light, overlay);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(GridBoxBlockEntity box) {
        return box.bounds();
    }

    @Override
    public boolean shouldRenderOffScreen(GridBoxBlockEntity box) {
        return box.size().getX() > 1 || box.size().getY() > 1 || box.size().getZ() > 1;
    }

    @EventBusSubscriber(modid = TinyBlocks.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {}

        @SubscribeEvent
        public static void onRegister(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(Registration.GRID_BOX_ENTITY.get(), GridBoxRenderer::new);
        }
    }
}
