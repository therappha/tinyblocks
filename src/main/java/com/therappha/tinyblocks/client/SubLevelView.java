package com.therappha.tinyblocks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3dc;

import java.lang.reflect.Method;

/**
 * Previews drawn on a Sable sub-level. What the crosshair hits on one has a position in its plot, far from where it is
 * seen, so a preview drawn there can't be seen at all. {@link #enter} moves the pose to the sub-level holding the block
 * and hands back what to subtract from positions in place of the camera; off a sub-level that is simply the camera.
 * Sable's companion library isn't on the compile classpath: it is reached by reflection.
 */
public final class SubLevelView {

    private SubLevelView() {}

    private static final class Sable {
        static final Object HELPER;
        static final Method CONTAINING, RENDER_POSE, POSITION, ORIENTATION, ROTATION_POINT, SCALE;

        static {
            try {
                HELPER = Class.forName("dev.ryanhcode.sable.Sable").getField("HELPER").get(null);
                // Through the public interfaces: the classes behind them need not be public.
                CONTAINING = Class.forName("dev.ryanhcode.sable.companion.SableCompanion").getMethod("getContainingClient", Position.class);
                RENDER_POSE = Class.forName("dev.ryanhcode.sable.companion.ClientSubLevelAccess").getMethod("renderPose", float.class);
                Class<?> pose = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc");
                POSITION = pose.getMethod("position");
                ORIENTATION = pose.getMethod("orientation");
                ROTATION_POINT = pose.getMethod("rotationPoint");
                SCALE = pose.getMethod("scale");
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    private static boolean failed;

    /** Pushes the pose, moved onto the sub-level holding {@code at} if there is one. {@link #exit} pops it. */
    public static Vec3 enter(RenderLevelStageEvent event, BlockPos at) {
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        pose.pushPose();
        if (failed) return camera;
        try {
            Object subLevel = Sable.CONTAINING.invoke(Sable.HELPER, Vec3.atCenterOf(at));
            if (subLevel == null) return camera;
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            Object seen = Sable.RENDER_POSE.invoke(subLevel, partialTick);
            Vector3dc position = (Vector3dc) Sable.POSITION.invoke(seen);
            Quaterniondc orientation = (Quaterniondc) Sable.ORIENTATION.invoke(seen);
            Vector3dc rotationPoint = (Vector3dc) Sable.ROTATION_POINT.invoke(seen);
            Vector3dc scale = (Vector3dc) Sable.SCALE.invoke(seen);
            pose.translate(position.x() - camera.x, position.y() - camera.y, position.z() - camera.z);
            pose.mulPose(new Quaternionf(orientation));
            pose.scale((float) scale.x(), (float) scale.y(), (float) scale.z());
            return new Vec3(rotationPoint.x(), rotationPoint.y(), rotationPoint.z());
        } catch (ReflectiveOperationException | LinkageError e) {
            failed = true; // once: previews go on working everywhere but on sub-levels
            LogUtils.getLogger().warn("Couldn't read a sub-level's pose for previews", e);
            return camera;
        }
    }

    public static void exit(RenderLevelStageEvent event) {
        event.getPoseStack().popPose();
    }
}
