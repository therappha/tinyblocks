package com.therappha.tinyblocks.client;

/**
 * When SubgridMiningInterceptor may ask the server to remove a mined piece. Kept apart from the event handling,
 * with no game classes, so the timing that keeps one click to one piece is unit-testable.
 *
 * At most one request is in flight: nothing new goes out until the server's removal delta confirms the previous
 * one, with a timeout in case it never does. And after a request the next one waits MINE_DELAY_TICKS, as vanilla
 * waits after breaking a block before the held button starts on the next one (MultiPlayerGameMode#destroyDelay);
 * without it the next piece in line — instantly breakable, or in creative — went within a tick or two of the
 * first: two pieces for one click.
 */
final class MineRequestGate {

    static final long REQUEST_TIMEOUT_TICKS = 10;
    static final long MINE_DELAY_TICKS = 5;

    private boolean inFlight = false;
    private long sentTick = Long.MIN_VALUE;
    private long nextMineTick = Long.MIN_VALUE;

    /** True if a request may go out at game tick {@code now}. */
    boolean canSend(long now) {
        if (inFlight && now - sentTick > REQUEST_TIMEOUT_TICKS) inFlight = false;
        return !inFlight && now >= nextMineTick;
    }

    void sent(long now) {
        inFlight = true;
        sentTick = now;
        nextMineTick = now + MINE_DELAY_TICKS;
    }

    void confirmed() {
        inFlight = false;
    }
}
