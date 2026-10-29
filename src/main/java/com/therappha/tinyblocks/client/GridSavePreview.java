package com.therappha.tinyblocks.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.items.GridSaveItem;
import com.therappha.tinyblocks.network.GridSaveTurnPayload;
import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlock;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * A full Grid Save in the main hand shows its grid, see-through, where a click would place it (GridSaveItem#target),
 * turned as it would be; R (rebindable) turns it a quarter turn more.
 */
public final class GridSavePreview {

    public static final KeyMapping TURN = new KeyMapping("key.tinyblocks.grid_save_turn",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.tinyblocks");

    /** The pieces last drawn, turned, and what they were made from: rebuilt only when the item's grid changes. */
    private static CompoundTag cachedGrid;
    private static List<PlacedPiece> cachedPieces = List.of();

    private GridSavePreview() {}

    @EventBusSubscriber(modid = TinyBlocks.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        private Keys() {}

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(TURN);
        }
    }

    @EventBusSubscriber(modid = TinyBlocks.MOD_ID, value = Dist.CLIENT)
    public static final class Events {
        private Events() {}

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            while (TURN.consumeClick()) {
                if (mc.player != null && GridSaveItem.isFull(mc.player.getMainHandItem())) {
                    PacketDistributor.sendToServer(GridSaveTurnPayload.INSTANCE);
                }
            }
        }

        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            ItemStack held = mc.player.getMainHandItem();
            if (!(held.getItem() instanceof GridSaveItem)) return;
            CompoundTag grid = GridSaveItem.grid(held);
            if (grid == null) return;
            if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) return;
            BlockPos target = GridSaveItem.target(hit);
            if (!mc.level.getBlockState(target).canBeReplaced()) return;

            int size = grid.getInt("size");
            List<PlacedPiece> pieces = pieces(grid, size);
            Vec3 camera = event.getCamera().getPosition();
            PoseStack poseStack = event.getPoseStack();
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            GhostVertexConsumer consumer = new GhostVertexConsumer(buffers.getBuffer(RenderType.translucent()), 120);
            var blockRenderer = mc.getBlockRenderer();
            float cell = 1f / size;

            poseStack.pushPose();
            poseStack.translate(target.getX() - camera.x, target.getY() - camera.y, target.getZ() - camera.z);
            for (PlacedPiece piece : pieces) {
                BlockState state = piece.definition.renderState(piece);
                if (state == null) continue;
                // As SubgridRenderer draws a piece (PieceTransforms), but see-through, through every render type of
                // its model (a model hands out its quads per type) into the one translucent buffer.
                for (PieceTransforms.Slice slice : PieceTransforms.slices(piece, size)) {
                    poseStack.pushPose();
                    poseStack.translate(slice.translation().x, slice.translation().y, slice.translation().z);
                    poseStack.scale(slice.scale().x, slice.scale().y, slice.scale().z);
                    var model = blockRenderer.getBlockModel(state);
                    for (RenderType layer : model.getRenderTypes(state, RandomSource.create(42L), ModelData.EMPTY)) {
                        blockRenderer.getModelRenderer().renderModel(poseStack.last(), consumer, state, model,
                                1f, 1f, 1f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, layer);
                    }
                    poseStack.popPose();
                }
            }
            poseStack.popPose();
            buffers.endBatch(RenderType.translucent());
        }
    }

    /** The grid's pieces, turned as placing it turns them (SubgridBlockEntity#transformed), read once per grid. */
    private static List<PlacedPiece> pieces(CompoundTag grid, int size) {
        if (grid.equals(cachedGrid)) return cachedPieces;
        var registries = Minecraft.getInstance().level.registryAccess();
        SubgridBlock block = Registration.subgridBlock(size);
        // A grid entity of its own, in no level, only to read the saved pieces with.
        SubgridBlockEntity reader = new SubgridBlockEntity(Registration.SUBGRID_BLOCK_ENTITY.get(), BlockPos.ZERO, block.defaultBlockState());
        reader.loadCustomOnly(grid.getCompound("data"), registries);
        Rotation rotation = Rotation.values()[Math.floorMod(grid.getInt("turns"), 4)];
        List<PlacedPiece> turned = new ArrayList<>();
        for (PlacedPiece piece : reader.getPieces()) turned.add(reader.transformed(piece, rotation, Mirror.NONE, registries));
        cachedGrid = grid;
        cachedPieces = turned;
        return turned;
    }
}
