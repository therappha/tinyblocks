package com.therappha.tinyblocks.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineCooldownTest {

    @Test
    void inactiveUntilStarted() {
        MineCooldown cooldown = new MineCooldown();
        assertFalse(cooldown.isActive(0));
        assertFalse(cooldown.isActive(1_000_000));
    }

    @Test
    void blocksTheNextPieceForExactlyFiveTicks() {
        MineCooldown cooldown = new MineCooldown();
        cooldown.start(100);
        assertTrue(cooldown.isActive(100));
        assertTrue(cooldown.isActive(104));
        assertFalse(cooldown.isActive(105));
    }

    @Test
    void restartingExtendsFromTheLatestRemoval() {
        MineCooldown cooldown = new MineCooldown();
        cooldown.start(100);
        cooldown.start(103);
        assertTrue(cooldown.isActive(107));
        assertFalse(cooldown.isActive(108));
    }

    @Test
    void resetClearsIt() {
        MineCooldown cooldown = new MineCooldown();
        cooldown.start(100);
        cooldown.reset();
        assertFalse(cooldown.isActive(100));
    }
}
