package com.therappha.tinyblocks.subgrid;

/**
 * Whether a grid is worth a frame's work, from how far it is and if it's on screen, both measured where it's seen: it may
 * be on a sub-level. Far has a margin and off screen a delay, so that a grid at the edge doesn't flicker.
 */
public final class GridSight {

    /** Blocks short of the render distance at which a far grid counts as near again. */
    public static final double NEAR_AGAIN_MARGIN = 8;
    /** Frames a grid must stay off screen before it counts as off: turning fast doesn't flicker it. */
    public static final int OFF_AFTER_FRAMES = 20;
    /** How far what can't go in the chunk mesh is drawn: vanilla's distance for block entities. */
    public static final double PER_FRAME_DISTANCE = 64;

    private boolean far;
    private int offFrames;

    public void update(double distance, double renderDistance, boolean onScreen) {
        far = distance > (far ? renderDistance - NEAR_AGAIN_MARGIN : renderDistance);
        offFrames = onScreen ? 0 : offFrames + 1;
    }

    /** Past the render distance. */
    public boolean far() {
        return far;
    }

    /** Far, or off screen for long enough. */
    public boolean away() {
        return far || offFrames >= OFF_AFTER_FRAMES;
    }

    public static boolean drawnPerFrame(double distance) {
        return distance <= PER_FRAME_DISTANCE;
    }
}
