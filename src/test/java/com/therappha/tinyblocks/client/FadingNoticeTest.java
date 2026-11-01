package com.therappha.tinyblocks.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FadingNoticeTest {

    @Test
    void holdsThenFadesThenStaysGone() {
        FadingNotice notice = new FadingNotice();
        assertEquals(255, notice.alpha("1/8", 0));
        assertEquals(255, notice.alpha("1/8", FadingNotice.HOLD_MILLIS));
        int fading = notice.alpha("1/8", FadingNotice.HOLD_MILLIS + FadingNotice.FADE_MILLIS / 2);
        assertTrue(fading > 0 && fading < 255, "halfway through the fade: " + fading);
        assertEquals(0, notice.alpha("1/8", FadingNotice.HOLD_MILLIS + FadingNotice.FADE_MILLIS));
        assertEquals(0, notice.alpha("1/8", 60_000));
    }

    @Test
    void aChangeShowsItAgain() {
        FadingNotice notice = new FadingNotice();
        notice.alpha("1/8", 0);
        assertEquals(0, notice.alpha("1/8", 60_000));
        assertEquals(255, notice.alpha("1/16", 60_000));
    }

    @Test
    void takingTheMinimizerOutAgainShowsIt() {
        FadingNotice notice = new FadingNotice();
        notice.alpha("1/8", 0);
        assertEquals(0, notice.alpha(null, 60_000));
        assertEquals(255, notice.alpha("1/8", 60_001));
    }
}
