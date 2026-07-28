package com.therappha.tinyblocks.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SubgridHighlight {

    private static boolean enabled = false;
    private static int radius = 16;

    private SubgridHighlight() {}

    public static boolean isEnabled() { return enabled; }
    public static int getRadius() { return radius; }

    public static boolean toggle(int r) {
        if (enabled && radius == r) {
            enabled = false;
        } else {
            enabled = true;
            radius = r;
        }
        return enabled;
    }
}
