package com.therappha.tinyblocks.client;

/**
 * The delay vanilla enforces between two consecutive block breaks while the attack key is held
 * (MultiPlayerGameMode#destroyDelay = 5). SubgridMiningInterceptor bypasses vanilla's
 * continueDestroyBlock entirely, so nothing else stops a held click from chaining straight into the
 * next piece the instant the previous removal is confirmed (issue #49). Pure game-time arithmetic,
 * kept separate from the interceptor so it needs no client to unit test.
 */
public final class MineCooldown {

    public static final long TICKS = 5;

    private long readyAt = Long.MIN_VALUE;

    /** Called when a piece removal is confirmed at game time now. */
    public void start(long now) {
        readyAt = now + TICKS;
    }

    /** True while a new piece must not start mining yet. */
    public boolean isActive(long now) {
        return now < readyAt;
    }

    public void reset() {
        readyAt = Long.MIN_VALUE;
    }
}
