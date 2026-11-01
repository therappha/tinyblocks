package com.therappha.tinyblocks.gametest;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * Sub-levels for tests. Sable's companion library (bounding boxes, the plot's interfaces) is inside Sable's jar, not on
 * the compile classpath: what needs it is reached by reflection.
 */
final class SableSupport {

    private SableSupport() {}

    /** SubLevelAssemblyHelper#assembleBlocks of {@code blocks}, anchored on {@code anchor}: the new sub-level. */
    static Object assemble(ServerLevel level, BlockPos anchor, List<BlockPos> blocks, BlockPos low, BlockPos high) {
        try {
            ClassLoader loader = SubLevelAssemblyHelper.class.getClassLoader();
            Object bounds = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3i", true, loader)
                    .getConstructor(BlockPos.class, BlockPos.class).newInstance(low, high);
            return SubLevelAssemblyHelper.class.getMethod("assembleBlocks", ServerLevel.class, BlockPos.class, Iterable.class,
                            Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3ic", true, loader))
                    .invoke(null, level, anchor, blocks, bounds);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Where in the plot the anchor went. */
    static BlockPos plotCenter(Object subLevel) {
        return (BlockPos) call(call(subLevel, "getPlot"), "getCenterBlock");
    }

    static void remove(ServerLevel level, Object subLevel) {
        SubLevelContainer.getContainer(level).removeSubLevel((SubLevel) subLevel, SubLevelRemovalReason.REMOVED);
    }

    private static Object call(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
