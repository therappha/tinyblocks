package com.therappha.tinyblocks.subgrid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GridRayTest {

    private static final BlockPos POS = new BlockPos(10, 20, -30);

    @Test
    void clickingTopFaceCenterLandsInTopmostCell() {
        int gridSize = 8;
        Vec3 hit = Vec3.atCenterOf(POS).add(0, 0.5, 0); // top face, x/z centered
        Vec3i cell = GridRay.cellAt(POS, hit, Direction.UP, gridSize);
        assertEquals(new Vec3i(4, 7, 4), cell);
    }

    @Test
    void clickingBottomFaceCenterLandsInBottommostCell() {
        int gridSize = 8;
        Vec3 hit = Vec3.atCenterOf(POS).add(0, -0.5, 0); // bottom face
        Vec3i cell = GridRay.cellAt(POS, hit, Direction.DOWN, gridSize);
        assertEquals(new Vec3i(4, 0, 4), cell);
    }

    @Test
    void everyFaceNudgesTowardTheClickedSide() {
        // For each face, a hit exactly on that face's plane (center of the other two axes) must
        // resolve to the cell touching that face, not the one just past it — the nudge term's
        // whole job, per direction.
        int gridSize = 4;
        record Case(Direction face, Vec3 offset, Vec3i expected) {}
        Case[] cases = {
                new Case(Direction.UP, new Vec3(0, 0.5, 0), new Vec3i(2, 3, 2)),
                new Case(Direction.DOWN, new Vec3(0, -0.5, 0), new Vec3i(2, 0, 2)),
                new Case(Direction.EAST, new Vec3(0.5, 0, 0), new Vec3i(3, 2, 2)),
                new Case(Direction.WEST, new Vec3(-0.5, 0, 0), new Vec3i(0, 2, 2)),
                new Case(Direction.SOUTH, new Vec3(0, 0, 0.5), new Vec3i(2, 2, 3)),
                new Case(Direction.NORTH, new Vec3(0, 0, -0.5), new Vec3i(2, 2, 0)),
        };
        for (Case c : cases) {
            Vec3 hit = Vec3.atCenterOf(POS).add(c.offset());
            assertEquals(c.expected(), GridRay.cellAt(POS, hit, c.face(), gridSize),
                    "face " + c.face());
        }
    }

    @Test
    void resultIsClampedToGridBounds() {
        // A hit location slightly outside [pos, pos+1) shouldn't ever be possible from a real
        // vanilla hit result, but the clamp is what stands between that and an
        // ArrayIndexOutOfBoundsException in indexOf.
        int gridSize = 8;
        Vec3 farBelow = Vec3.atLowerCornerOf(POS).add(0.5, -5, 0.5);
        Vec3i cell = GridRay.cellAt(POS, farBelow, Direction.DOWN, gridSize);
        assertEquals(0, cell.getY());

        Vec3 farAbove = Vec3.atLowerCornerOf(POS).add(0.5, 5, 0.5);
        Vec3i cellAbove = GridRay.cellAt(POS, farAbove, Direction.UP, gridSize);
        assertEquals(gridSize - 1, cellAbove.getY());
    }

    @Test
    void everyGridSizeMapsTheCenterHitToItsOwnMiddleCells() {
        for (int gridSize : new int[] {2, 4, 8, 16}) {
            Vec3 hit = Vec3.atCenterOf(POS);
            Vec3i cell = GridRay.cellAt(POS, hit, Direction.UP, gridSize);
            int expectedMid = gridSize / 2;
            assertEquals(expectedMid, cell.getX(), "gridSize " + gridSize);
            assertEquals(expectedMid, cell.getZ(), "gridSize " + gridSize);
        }
    }
}
