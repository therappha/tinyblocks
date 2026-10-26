package com.therappha.tinyblocks.v2;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VirtualHitTest {

    private static final BlockPos REAL = new BlockPos(10, 64, -3);
    private static final BlockPos ORIGIN = new BlockPos(80, 512, -24);

    /** A click on the top face of cell (2,7,5) of an 8-grid, a quarter of the way across it, lands on the top face of that virtual block, a quarter of the way across. */
    @Test
    void aHitOnACellIsTheSameSpotOnItsVirtualBlock() {
        Vec3 real = new Vec3(10 + (2 + 0.25) / 8, 65, -3 + (5 + 0.5) / 8);
        Vec3 virtual = VirtualHit.toVirtual(real, REAL, ORIGIN, 8);
        assertEquals(80 + 2.25, virtual.x, 1e-9);
        assertEquals(512 + 8, virtual.y, 1e-9);
        assertEquals(-24 + 5.5, virtual.z, 1e-9);
    }

    @Test
    void toRealUndoesToVirtual() {
        Vec3 real = new Vec3(10.3, 64.9, -2.15);
        Vec3 back = VirtualHit.toReal(VirtualHit.toVirtual(real, REAL, ORIGIN, 16), REAL, ORIGIN, 16);
        assertEquals(real.x, back.x, 1e-9);
        assertEquals(real.y, back.y, 1e-9);
        assertEquals(real.z, back.z, 1e-9);
    }

    /** A virtual block past the grid's edge is drawn in the neighbouring real block, as the next cell over. */
    @Test
    void aVirtualBlockPastTheEdgeIsInTheNextRealBlock() {
        Vec3 corner = VirtualHit.toReal(Vec3.atLowerCornerOf(ORIGIN.offset(4, 0, 4)), REAL, ORIGIN, 4);
        assertEquals(new Vec3(11, 64, -2), corner);
    }
}
