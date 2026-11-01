package com.therappha.tinyblocks.client;

import javax.annotation.Nullable;
import java.util.Objects;

/** A short notice: shown in full when what it says changes, then fading out until it is gone. */
final class FadingNotice {

    static final long HOLD_MILLIS = 2000, FADE_MILLIS = 1000;

    @Nullable private Object shown;
    private long since;

    /** How opaque the notice for {@code text} is at {@code now}, 0 to 255; null text means nothing to say. */
    int alpha(@Nullable Object text, long now) {
        if (!Objects.equals(text, shown)) {
            shown = text;
            since = now;
        }
        if (text == null) return 0;
        long left = HOLD_MILLIS + FADE_MILLIS - (now - since);
        if (left <= 0) return 0;
        return left >= FADE_MILLIS ? 255 : (int) (255 * left / FADE_MILLIS);
    }
}
