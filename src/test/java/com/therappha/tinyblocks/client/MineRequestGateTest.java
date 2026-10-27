package com.therappha.tinyblocks.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineRequestGateTest {

    /** Two instantly breakable pieces in line: the click takes the front one, holding on takes nothing more for 5 ticks. */
    @Test
    void theNextPieceWaitsForTheMineDelayEvenOnceTheFirstIsConfirmed() {
        MineRequestGate gate = new MineRequestGate();
        assertTrue(gate.canSend(100));
        gate.sent(100);
        gate.confirmed(); // the server removed it at once
        for (long tick = 101; tick < 100 + MineRequestGate.MINE_DELAY_TICKS; tick++) {
            assertFalse(gate.canSend(tick), "a second piece could be mined on tick " + tick);
        }
        assertTrue(gate.canSend(100 + MineRequestGate.MINE_DELAY_TICKS));
    }

    @Test
    void nothingNewGoesOutWhileARequestIsUnconfirmed() {
        MineRequestGate gate = new MineRequestGate();
        gate.sent(0);
        assertFalse(gate.canSend(MineRequestGate.MINE_DELAY_TICKS));
        assertFalse(gate.canSend(MineRequestGate.REQUEST_TIMEOUT_TICKS));
    }

    /** A request the server never confirms (the crosshair moved off, say) doesn't stop mining for good. */
    @Test
    void anUnconfirmedRequestTimesOut() {
        MineRequestGate gate = new MineRequestGate();
        gate.sent(0);
        assertTrue(gate.canSend(MineRequestGate.REQUEST_TIMEOUT_TICKS + 1));
    }
}
