package com.therappha.tinyblocks.subgrid;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Which Sable sub-level a block belongs to. A block on one has a position in its plot, millions of blocks from where it
 * is seen, so two positions only make a box together if they are in the same place: both on one sub-level, or both in
 * the world. Sable's companion library isn't on the compile classpath: it is reached by reflection.
 */
public final class SubLevels {

    private SubLevels() {}

    private static final class Sable {
        static final Object HELPER;
        static final Method CONTAINING, INTERSECTING, POSE, INTO_PLOT, PROJECT_OUT;
        static final Constructor<?> BOX;

        static {
            try {
                HELPER = Class.forName("dev.ryanhcode.sable.Sable").getField("HELPER").get(null);
                CONTAINING = Class.forName("dev.ryanhcode.sable.companion.SableCompanion").getMethod("getContaining", Level.class, Vec3i.class);
                INTERSECTING = Class.forName("dev.ryanhcode.sable.companion.SableCompanion").getMethod("getAllIntersecting", Level.class,
                        Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3dc"));
                PROJECT_OUT = Class.forName("dev.ryanhcode.sable.companion.SableCompanion").getMethod("projectOutOfSubLevel", Level.class, Vec3.class);
                BOX = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3d").getConstructor(AABB.class);
                POSE = Class.forName("dev.ryanhcode.sable.companion.SubLevelAccess").getMethod("logicalPose");
                INTO_PLOT = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc").getMethod("transformPositionInverse", Vec3.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    private static boolean failed;

    /** Whether {@code a} and {@code b} are on the same sub-level, or both in no sub-level. */
    public static boolean sameSpace(Level level, BlockPos a, BlockPos b) {
        if (failed) return true;
        try {
            return Sable.CONTAINING.invoke(Sable.HELPER, level, a) == Sable.CONTAINING.invoke(Sable.HELPER, level, b);
        } catch (ReflectiveOperationException | LinkageError e) {
            failed = true; // once: without this check a box can only get too big, which is refused anyway
            LogUtils.getLogger().warn("Couldn't ask Sable which sub-level a block is on", e);
            return true;
        }
    }

    /** Where {@code near} falls in the plot of each sub-level within {@code range} of it, none if there are none. */
    public static List<Vec3> inPlotsAround(Level level, Vec3 near, double range) {
        List<Vec3> plots = new ArrayList<>();
        if (failed) return plots;
        try {
            Object box = Sable.BOX.newInstance(new AABB(near, near).inflate(range));
            for (Object subLevel : (Iterable<?>) Sable.INTERSECTING.invoke(Sable.HELPER, level, box))
                plots.add((Vec3) Sable.INTO_PLOT.invoke(Sable.POSE.invoke(subLevel), near));
        } catch (ReflectiveOperationException | LinkageError e) {
            failed = true; // once: what's in the world goes on working
            LogUtils.getLogger().warn("Couldn't ask Sable which sub-levels are nearby", e);
        }
        return plots;
    }

    /** Where {@code at} is seen in the world: itself, or out of the plot of the sub-level holding it. */
    public static Vec3 inWorld(Level level, Vec3 at) {
        if (failed) return at;
        try {
            return (Vec3) Sable.PROJECT_OUT.invoke(Sable.HELPER, level, at);
        } catch (ReflectiveOperationException | LinkageError e) {
            failed = true;
            LogUtils.getLogger().warn("Couldn't ask Sable where a block is seen", e);
            return at;
        }
    }
}
