package com.therappha.tinyblocks.client.flywheel;

import net.neoforged.fml.ModList;

/** Flywheel-free guard: safe to touch without Flywheel on the classpath. */
public final class FlywheelLoaded {

    private FlywheelLoaded() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded("flywheel");
    }
}
