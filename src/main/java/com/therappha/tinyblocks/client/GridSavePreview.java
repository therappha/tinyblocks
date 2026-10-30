package com.therappha.tinyblocks.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.items.GridSaveItem;
import com.therappha.tinyblocks.network.GridSaveTurnPayload;
import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubgridBlock;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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
import java.util.Arrays;
import java.util.List;

/**
 * A full Grid Save in hand (GridSaveItem#heldFull) shows its grids, see-through, where a click would place them (GridSaveItem#target),
 * turned as they would be; R (rebindable) turns them a quarter turn more. An empty one with a corner set outlines the
 * box a click would take, green when it would take it, yellow when not.
 */
public final class GridSavePreview {

    public static final KeyMapping TURN = new KeyMapping("key.tinyblocks.grid_save_turn",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.tinyblocks");

    /** The ghosts last drawn and the custom data they were drawn from: drawn again only when the held grids change. */
    private static CustomData cachedData;
    private static List<Part> cachedGhost;

    /** One grid's ghost and where it goes in the turned box. */
    private record Part(BlockPos offset, Ghost ghost) {}

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
                if (mc.player != null && GridSaveItem.heldFull(mc.player) != null) {
                    PacketDistributor.sendToServer(GridSaveTurnPayload.INSTANCE);
                }
            }
        }

        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            ItemStack held = GridSaveItem.heldFull(mc.player);
            if (held == null) {
                cachedData = null;
                cachedGhost = null;
                outlineSelection(event, mc);
                return;
            }
            if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) return;
            CustomData data = GridSaveItem.data(held);
            CompoundTag grid = data.getUnsafe().getCompound(GridSaveItem.GRID_KEY);
            Vec3i turnedBox = GridSaveItem.turnedBox(GridSaveItem.box(grid), GridSaveItem.rotation(held));
            BlockPos origin = GridSaveItem.origin(GridSaveItem.target(hit), hit.getDirection(), turnedBox);
            List<Part> parts = ghost(data);
            // As GridSaveItem#place refuses (but for who is in the way).
            for (Part part : parts) {
                BlockPos target = origin.offset(part.offset());
                if (!mc.level.getBlockState(target).canBeReplaced() || !mc.level.isInWorldBounds(target)) return;
            }

            Vec3 camera = event.getCamera().getPosition();
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            VertexConsumer translucent = buffers.getBuffer(RenderType.translucent());
            for (Part part : parts) {
                BlockPos target = origin.offset(part.offset());
                part.ghost().replay(translucent, event.getPoseStack().last(),
                        (float) (target.getX() - camera.x), (float) (target.getY() - camera.y), (float) (target.getZ() - camera.z));
            }
            buffers.endBatch(RenderType.translucent());
            if (parts.size() > 1) {
                AABB box = new AABB(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + turnedBox.getX(),
                        origin.getY() + turnedBox.getY(), origin.getZ() + turnedBox.getZ());
                outline(event, buffers, box.move(camera.scale(-1)), 0xFFFFFF, 0.4f);
            }
        }

        /**
         * The box an empty Grid Save with a corner set would take, from the corner to the block looked at: drawn with
         * the level renderer's own lines, not Create's outliner (OutlinerMixin hands that one to a grid's insides
         * while the crosshair is on a grid, which it is when selecting grids).
         */
        private static void outlineSelection(RenderLevelStageEvent event, Minecraft mc) {
            ItemStack stack = mc.player.getMainHandItem();
            if (!(stack.getItem() instanceof GridSaveItem)) stack = mc.player.getOffhandItem();
            BlockPos corner = stack.getItem() instanceof GridSaveItem ? GridSaveItem.corner(stack) : null;
            if (corner == null) return;
            BlockPos other = mc.hitResult instanceof BlockHitResult hit && hit.getType() != HitResult.Type.MISS
                    ? hit.getBlockPos() : corner;
            BlockPos min = BlockPos.min(corner, other), max = BlockPos.max(corner, other);
            // As GridSaveItem#pickUpBox refuses, but for what only the server asks.
            int grids = 0;
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                if (mc.level.getBlockState(pos).getBlock() instanceof SubgridBlock) grids++;
            }
            BlockPos span = max.subtract(min);
            boolean fits = span.getX() < GridSaveItem.MAX_SPAN && span.getY() < GridSaveItem.MAX_SPAN && span.getZ() < GridSaveItem.MAX_SPAN;
            boolean takes = fits && grids > 0 && grids <= GridSaveItem.MAX_GRIDS;
            Vec3 camera = event.getCamera().getPosition();
            AABB box = new AABB(Vec3.atLowerCornerOf(min), Vec3.atLowerCornerOf(max).add(1, 1, 1)).move(camera.scale(-1));
            // Create's Super Glue colours.
            outline(event, mc.renderBuffers().bufferSource(), box, takes ? 0x68c586 : 0xc5b548, 1f);
        }

        private static void outline(RenderLevelStageEvent event, MultiBufferSource.BufferSource buffers, AABB box, int rgb, float alpha) {
            LevelRenderer.renderLineBox(event.getPoseStack(), buffers.getBuffer(RenderType.lines()), box.inflate(1 / 512d),
                    ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, alpha);
            buffers.endBatch(RenderType.lines());
        }
    }

    /**
     * The ghosts of the held grids, drawn once for each set of grids (and turn) it holds: a stack keeps its custom
     * data as it is until its grids change, and an equal one (the server sending the stack again) is still the same.
     */
    private static List<Part> ghost(CustomData data) {
        if (data != cachedData) {
            if (cachedGhost == null || !data.equals(cachedData)) cachedGhost = draw(data.getUnsafe().getCompound(GridSaveItem.GRID_KEY));
            cachedData = data;
        }
        return cachedGhost;
    }

    /** Each grid's ghost, and where it goes once the box is turned as placing turns it (GridSaveItem#turnedOffset). */
    private static List<Part> draw(CompoundTag grid) {
        Rotation rotation = Rotation.values()[Math.floorMod(grid.getInt("turns"), 4)];
        Vec3i box = GridSaveItem.box(grid);
        List<Part> parts = new ArrayList<>();
        for (GridSaveItem.Entry entry : GridSaveItem.entries(grid)) {
            parts.add(new Part(GridSaveItem.turnedOffset(entry.offset(), box, rotation), draw(entry, rotation)));
        }
        return parts;
    }

    /** The grid's pieces, turned as placing it turns them (SubgridBlockEntity#transformed), drawn as at the origin. */
    private static Ghost draw(GridSaveItem.Entry entry, Rotation rotation) {
        Minecraft mc = Minecraft.getInstance();
        var registries = mc.level.registryAccess();
        int size = entry.size();
        SubgridBlock block = Registration.subgridBlock(size);
        // A grid entity of its own, in no level, only to read the saved pieces with.
        SubgridBlockEntity reader = new SubgridBlockEntity(Registration.SUBGRID_BLOCK_ENTITY.get(), BlockPos.ZERO, block.defaultBlockState());
        reader.loadCustomOnly(entry.data(), registries);

        Ghost ghost = new Ghost();
        GhostVertexConsumer consumer = new GhostVertexConsumer(ghost, 120);
        var blockRenderer = mc.getBlockRenderer();
        RandomSource random = RandomSource.create();
        PoseStack poseStack = new PoseStack();
        for (PlacedPiece original : reader.getPieces()) {
            PlacedPiece piece = reader.transformed(original, rotation, Mirror.NONE, registries);
            BlockState state = piece.definition.renderState(piece);
            if (state == null) continue;
            var model = blockRenderer.getBlockModel(state);
            random.setSeed(42L);
            var layers = model.getRenderTypes(state, random, ModelData.EMPTY);
            // As SubgridRenderer draws a piece (PieceTransforms), but see-through, through every render type of
            // its model (a model hands out its quads per type) into the one translucent buffer.
            for (PieceTransforms.Slice slice : PieceTransforms.slices(piece, size)) {
                poseStack.pushPose();
                poseStack.translate(slice.translation().x, slice.translation().y, slice.translation().z);
                poseStack.scale(slice.scale().x, slice.scale().y, slice.scale().z);
                for (RenderType layer : layers) {
                    blockRenderer.getModelRenderer().renderModel(poseStack.last(), consumer, state, model,
                            1f, 1f, 1f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, layer);
                }
                poseStack.popPose();
            }
        }
        return ghost;
    }

    /**
     * Vertices as the model renderer wrote them, kept to be written again each frame moved to where the grid would
     * go: position, colour, uv, overlay, light and normal, in that order (what the translucent buffer doesn't take,
     * it skips, as it did when drawn straight into it).
     */
    private static final class Ghost implements VertexConsumer {
        private static final int FLOATS = 8, INTS = 4;
        private float[] floats = new float[FLOATS * 256];
        private int[] ints = new int[INTS * 256];
        private int count = -1;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            count++;
            if ((count + 1) * FLOATS > floats.length) {
                floats = Arrays.copyOf(floats, floats.length * 2);
                ints = Arrays.copyOf(ints, ints.length * 2);
            }
            floats[count * FLOATS] = x;
            floats[count * FLOATS + 1] = y;
            floats[count * FLOATS + 2] = z;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            ints[count * INTS] = (a << 24) | (r << 16) | (g << 8) | b;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            floats[count * FLOATS + 3] = u;
            floats[count * FLOATS + 4] = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            ints[count * INTS + 1] = (v << 16) | (u & 0xFFFF);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            ints[count * INTS + 2] = (v << 16) | (u & 0xFFFF);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float nx, float ny, float nz) {
            floats[count * FLOATS + 5] = nx;
            floats[count * FLOATS + 6] = ny;
            floats[count * FLOATS + 7] = nz;
            return this;
        }

        void replay(VertexConsumer into, PoseStack.Pose pose, float dx, float dy, float dz) {
            for (int i = 0; i <= count; i++) {
                int f = i * FLOATS, n = i * INTS;
                int color = ints[n];
                into.addVertex(pose, floats[f] + dx, floats[f + 1] + dy, floats[f + 2] + dz)
                        .setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, color >>> 24)
                        .setUv(floats[f + 3], floats[f + 4])
                        .setUv1(ints[n + 1] & 0xFFFF, ints[n + 1] >>> 16)
                        .setUv2(ints[n + 2] & 0xFFFF, ints[n + 2] >>> 16)
                        .setNormal(pose, floats[f + 5], floats[f + 6], floats[f + 7]);
            }
        }
    }
}
