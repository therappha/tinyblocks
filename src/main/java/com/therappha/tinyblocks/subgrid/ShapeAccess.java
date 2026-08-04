package com.therappha.tinyblocks.subgrid;

import net.minecraft.world.phys.shapes.CubeVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.lang.reflect.Constructor;

/**
 * CubeVoxelShape(DiscreteVoxelShape) is protected with no public factory that accepts an
 * arbitrary equal-subdivision DiscreteVoxelShape (Shapes.create only takes AABB/doubles).
 * Reflects it open so rebuildShape() can build the merged occupancy shape in one pass from a
 * BitSetDiscreteVoxelShape instead of unioning one VoxelShape per piece via Shapes.or — same
 * idiom as v2's BlockAccess.
 */
final class ShapeAccess {

    private static final Constructor<CubeVoxelShape> CUBE_VOXEL_SHAPE_CTOR = find();

    @SuppressWarnings("unchecked")
    private static Constructor<CubeVoxelShape> find() {
        try {
            Constructor<CubeVoxelShape> c = CubeVoxelShape.class.getDeclaredConstructor(DiscreteVoxelShape.class);
            c.setAccessible(true);
            return c;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static VoxelShape wrap(DiscreteVoxelShape discrete) {
        try {
            return CUBE_VOXEL_SHAPE_CTOR.newInstance(discrete);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private ShapeAccess() {}
}
