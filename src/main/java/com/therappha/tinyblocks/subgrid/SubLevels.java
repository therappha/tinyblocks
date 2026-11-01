package com.therappha.tinyblocks.subgrid;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;

/**
 * Which Sable sub-level a block belongs to. A block on one has a position in its plot, millions of blocks from where it
 * is seen, so two positions only make a box together if they are in the same place: both on one sub-level, or both in
 * the world. Sable's companion library isn't on the compile classpath: it is reached by reflection.
 */
public final class SubLevels {

    private SubLevels() {}

    private static final class Sable {
        static final Object HELPER;
        static final Method CONTAINING;

        static {
            try {
                HELPER = Class.forName("dev.ryanhcode.sable.Sable").getField("HELPER").get(null);
                CONTAINING = Class.forName("dev.ryanhcode.sable.companion.SableCompanion").getMethod("getContaining", Level.class, Vec3i.class);
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
}
