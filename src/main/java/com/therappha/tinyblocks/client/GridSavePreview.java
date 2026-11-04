package com.therappha.tinyblocks.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.therappha.tinyblocks.TinyBlocks;
import com.therappha.tinyblocks.compat.create.client.GearConnectorDrawing;
import com.therappha.tinyblocks.items.GridSaveItem;
import com.therappha.tinyblocks.network.GridSaveTurnPayload;
import com.therappha.tinyblocks.setup.Registration;
import com.therappha.tinyblocks.subgrid.PlacedPiece;
import com.therappha.tinyblocks.subgrid.SubLevels;
import com.therappha.tinyblocks.subgrid.SubgridBlock;
import com.therappha.tinyblocks.subgrid.SubgridBlockEntity;
import com.therappha.tinyblocks.v2.FakeLevel;
import com.therappha.tinyblocks.v2.VanillaBlockPiece;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
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
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A full Grid Save in hand (GridSaveItem#heldFull) shows its grids, see-through, where a click would place them (GridSaveItem#target),
 * turned as they would be; R (rebindable) turns them a quarter turn more. An empty one with a corner set outlines the
 * box a click would take, green when it would take it, yellow when not.
 */
public final class GridSavePreview {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final KeyMapping TURN = new KeyMapping("key.tinyblocks.grid_save_turn",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.tinyblocks");

    /** The ghosts last drawn and the custom data they were drawn from: drawn again only when the held grids change. */
    private static CustomData cachedData;
    private static List<Part> cachedGhost;

    /** One grid's ghost, by the render type it is written back into, and where it goes in the turned box. */
    private record Part(BlockPos offset, Map<RenderType, Ghost> ghost) {}

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

            Vec3 camera = SubLevelView.enter(event, origin);
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            for (Part part : parts) {
                BlockPos target = origin.offset(part.offset());
                part.ghost().forEach((type, ghost) -> {
                    ghost.replay(buffers.getBuffer(type), event.getPoseStack().last(),
                            (float) (target.getX() - camera.x), (float) (target.getY() - camera.y), (float) (target.getZ() - camera.z));
                    buffers.endBatch(type);
                });
            }
            if (parts.size() > 1) {
                AABB box = new AABB(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + turnedBox.getX(),
                        origin.getY() + turnedBox.getY(), origin.getZ() + turnedBox.getZ());
                outline(event, buffers, box.move(camera.scale(-1)), 0xFFFFFF, 0.4f);
            }
            SubLevelView.exit(event);
        }

        /**
         * The box an empty Grid Save with a corner set would take, from the corner to the block looked at: drawn with
         * the level renderer's own lines, not Create's outliner (OutlinerMixin hands that one to a grid's insides
         * while the crosshair is on a grid, which it is when selecting grids).
         */
        private static void outlineSelection(RenderLevelStageEvent event, Minecraft mc) {
            // The hand a click selects with (GridSaveItem#onItemUseFirst): the main one if it has a corner, else the
            // off one, as an empty Grid Save in the main hand off the grids passes the click on.
            ItemStack main = mc.player.getMainHandItem();
            BlockPos corner = main.getItem() instanceof GridSaveItem ? GridSaveItem.corner(main, mc.level) : null;
            ItemStack off = mc.player.getOffhandItem();
            boolean mainPasses = main.isEmpty() || main.getItem() instanceof GridSaveItem && !mc.player.isSecondaryUseActive();
            if (corner == null && mainPasses && off.getItem() instanceof GridSaveItem) corner = GridSaveItem.corner(off, mc.level);
            if (corner == null) return;
            BlockPos other = mc.hitResult instanceof BlockHitResult hit && hit.getType() != HitResult.Type.MISS
                    ? hit.getBlockPos() : corner;
            // A corner on a sub-level and one in the world are millions of blocks apart: no box, and no walk across it.
            if (!SubLevels.sameSpace(mc.level, corner, other)) other = corner;
            BlockPos min = BlockPos.min(corner, other), max = BlockPos.max(corner, other);
            // As GridSaveItem#pickUpBox refuses, but for what only the server asks.
            int grids = 0;
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                if (mc.level.getBlockState(pos).getBlock() instanceof SubgridBlock) grids++;
            }
            BlockPos span = max.subtract(min);
            boolean fits = span.getX() < GridSaveItem.MAX_SPAN && span.getY() < GridSaveItem.MAX_SPAN && span.getZ() < GridSaveItem.MAX_SPAN;
            boolean takes = fits && grids > 0 && grids <= GridSaveItem.MAX_GRIDS;
            Vec3 camera = SubLevelView.enter(event, min);
            AABB box = new AABB(Vec3.atLowerCornerOf(min), Vec3.atLowerCornerOf(max).add(1, 1, 1)).move(camera.scale(-1));
            // Create's Super Glue colours.
            outline(event, mc.renderBuffers().bufferSource(), box, takes ? 0x68c586 : 0xc5b548, 1f);
            SubLevelView.exit(event);
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

    /**
     * The grid's pieces, turned as placing it turns them (SubgridBlockEntity#transformed), drawn as at the origin, each
     * as SubgridRenderer draws it: its model and its block entity's renderer. What turns on a Create block (a shaft, a
     * cogwheel) is in no model: Flywheel draws it in a level, and the renderer draws it instead in a level Flywheel
     * doesn't visualize, which the fake space the phantoms are bound to here is.
     */
    private static Map<RenderType, Ghost> draw(GridSaveItem.Entry entry, Rotation rotation) {
        Minecraft mc = Minecraft.getInstance();
        var registries = mc.level.registryAccess();
        int size = entry.size();
        SubgridBlock block = Registration.subgridBlock(size);
        // A grid entity of its own, in no level, only to read the saved pieces with.
        SubgridBlockEntity reader = new SubgridBlockEntity(Registration.SUBGRID_BLOCK_ENTITY.get(), BlockPos.ZERO, block.defaultBlockState());
        reader.loadCustomOnly(entry.data(), registries);

        // Whatever samples the block atlas goes see-through into the one translucent buffer; anything else (a chest's
        // sheet, a font) only means something in its own render type, which draws it as that type draws it.
        Map<RenderType, Ghost> ghosts = new LinkedHashMap<>();
        MultiBufferSource source = type -> new GhostVertexConsumer(
                ghosts.computeIfAbsent(blockAtlas(type) ? RenderType.translucent() : type, t -> new Ghost()), 120);
        VertexConsumer consumer = source.getBuffer(RenderType.translucent());
        FakeLevel space = VanillaBlockPiece.buildClientFakeSpace(reader, mc.level);
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
            BlockEntity phantom = piece.definition instanceof VanillaBlockPiece
                    ? VanillaBlockPiece.blockEntityFor(piece, reader, space) : null;
            List<PieceTransforms.Slice> slices = PieceTransforms.slices(piece, size);
            for (int i = 0; i < slices.size(); i++) {
                PieceTransforms.Slice slice = slices.get(i);
                poseStack.pushPose();
                poseStack.translate(slice.translation().x, slice.translation().y, slice.translation().z);
                poseStack.scale(slice.scale().x, slice.scale().y, slice.scale().z);
                for (RenderType layer : layers) {
                    blockRenderer.getModelRenderer().renderModel(poseStack.last(), consumer, state, model,
                            1f, 1f, 1f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, layer);
                }
                if (phantom != null) drawBlockEntity(phantom, piece, reader, i, poseStack, source);
                poseStack.popPose();
            }
        }
        return ghosts;
    }

    /** As SubgridRenderer#render does, once: a block entity read back from an item in a space of its own can't take the game down with it. */
    private static void drawBlockEntity(BlockEntity phantom, PlacedPiece piece, SubgridBlockEntity grid, int slice,
                                        PoseStack poseStack, MultiBufferSource source) {
        int light = LightTexture.FULL_BRIGHT, overlay = OverlayTexture.NO_OVERLAY;
        SubgridRenderer.drawingPhantom = true;
        try {
            if (PieceTransforms.isGearConnector(grid, piece)) {
                if (phantom instanceof KineticBlockEntity gear) {
                    GearConnectorDrawing.draw(gear, slice, poseStack, source, light, BlockPos.ZERO.relative(piece.facing));
                }
                return;
            }
            BlockEntityRenderer<BlockEntity> renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(phantom);
            if (renderer != null) renderer.render(phantom, 0f, poseStack, source, light, overlay);
        } catch (RuntimeException e) {
            LOGGER.warn("Drawing the ghost of a piece at cell {} failed", piece.anchor, e);
        } finally {
            SubgridRenderer.drawingPhantom = false;
        }
    }

    /** Whether {@code type} draws from the block atlas, as the translucent buffer does. */
    private static boolean blockAtlas(RenderType type) {
        return RenderType.chunkBufferLayers().contains(type) || type == Sheets.solidBlockSheet()
                || type == Sheets.cutoutBlockSheet() || type == Sheets.translucentCullBlockSheet()
                || type == Sheets.translucentItemSheet();
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
